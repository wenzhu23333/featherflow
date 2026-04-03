# FeatherFlow Attempt History Design

## 1. Goals

This design refactors FeatherFlow persistence so that workflow runtime metadata becomes explicit relational data instead of hidden JSON.

Primary goals:

- Remove `workflow_instance.ext_col`.
- Persist the workflow start node in `workflow_instance.start_node`.
- Persist the actual execution node for every activity attempt in `activity_instance.executed_node`.
- Change `activity_instance` from "one row per activity definition" to "one row per activity attempt".
- Derive retry budget usage from persisted failed activity attempts instead of storing retry counters in workflow metadata.
- Keep the existing activity lookup index shape `idx_activity_instance_workflow_name (workflow_id, activity_name)`.
- Add an index for `workflow_instance.workflow_name` to support ops-console filtering.

Non-goals:

- No new retry-count column.
- No new audit table.
- No node-based indexes in this round.
- No compatibility layer for the removed `ext_col`.

## 2. Why `ext_col` Should Be Removed

`workflow_instance.ext_col` is no longer a good fit for the current engine.

Its remaining responsibilities are currently:

- internal retry bookkeeping
- ad hoc status change notes

Both are weak reasons to keep a generic JSON column:

- Retry bookkeeping is core runtime state, so it should either be modeled explicitly or derived from persisted facts.
- Status notes are not part of the core workflow engine contract, and existing external operations already record operator/reason in `workflow_operation.input`.

After this refactor:

- retry bookkeeping is derived from `activity_instance`
- workflow definition name already lives in `workflow_instance.workflow_name`
- no remaining core behavior requires `ext_col`

Conclusion:

- `workflow_instance.ext_col` should be removed.

## 3. Table Design

### 3.1 `workflow_instance`

Target columns:

- `workflow_id` `varchar(19)` primary key
- `biz_id` `varchar(128)` not null
- `workflow_name` `varchar(128)` not null
- `start_node` `varchar(128)` not null
- `gmt_created` `timestamp` not null
- `gmt_modified` `timestamp` not null
- `input` `clob`/`longtext` not null
- `status` `varchar(32)` not null

Changes from current schema:

- keep `workflow_name`
- add `start_node`
- delete `ext_col`

Field semantics:

- `workflow_id`: workflow runtime instance ID
- `biz_id`: business identifier passed by caller, defaulting to `workflow_id`
- `workflow_name`: workflow definition name from YAML/XML
- `start_node`: node where the workflow instance was created and initially dispatched
- `input`: original workflow input JSON
- `status`: workflow status machine

### 3.2 `activity_instance`

Target columns:

- `activity_id` `varchar(64)` primary key
- `workflow_id` `varchar(19)` not null
- `activity_name` `varchar(128)` not null
- `executed_node` `varchar(128)` not null
- `gmt_created` `timestamp` not null
- `gmt_modified` `timestamp` not null
- `input` `clob`/`longtext` not null
- `output` `clob`/`longtext` null
- `status` `varchar(32)` not null

Changes from current schema:

- add `executed_node`
- keep the rest of the existing columns
- change row semantics from "latest result for one activity" to "one persisted row per attempt"

Field semantics:

- `activity_id`: unique ID for one attempt
- `workflow_id`: owning workflow instance
- `activity_name`: logical activity definition name
- `executed_node`: node that executed this attempt
- `input`: activity input snapshot before this attempt ran
- `output`: success context or failure payload for this attempt
- `status`: `SUCCESSFUL` or `FAILED`

## 4. New Activity Persistence Semantics

This is the key behavior change.

Current behavior:

- one logical activity usually maps to one persistent row
- retries overwrite the same row

New behavior:

- every execution attempt inserts a new `activity_instance`
- failed retry attempts produce additional `FAILED` rows
- a later successful retry produces an additional `SUCCESSFUL` row

Examples:

### Example A: success on first try

- `createOrder` attempt 1 -> insert one `SUCCESSFUL` row

### Example B: fail twice, then succeed

- `notifyCustomer` attempt 1 -> insert `FAILED`
- `notifyCustomer` attempt 2 -> insert `FAILED`
- `notifyCustomer` attempt 3 -> insert `SUCCESSFUL`

### Example C: manual skip after terminate

- latest failed `riskReview` row remains unchanged
- skip action inserts a new `SUCCESSFUL` row for `riskReview`
- skipped metadata is persisted inside that row's `output`

This approach preserves full attempt history and makes retry usage a queryable fact instead of hidden runtime metadata.

## 5. Retry Semantics Without Stored Retry Counters

The engine will no longer store `retryCounts`.

Instead, when an activity attempt fails, the engine computes:

- `failedCount = count(activity_instance where workflow_id = ? and activity_name = ? and status = FAILED)`

Retry decision:

- if `failedCount <= maxRetryTimes`, schedule another automatic retry
- otherwise move workflow to `HUMAN_PROCESSING`

This matches the user-requested rule:

- manual `retry` does **not** reset retry history
- if operators fix the problem and the next attempt succeeds, workflow continues naturally
- if the next attempt still fails, the historical failed attempts remain part of the same retry budget

Why this works:

- every attempt is preserved
- failed history is durable across process restarts
- no extra retry counter field is required

## 6. Idempotency and Latest-State Rules

The existing idempotency principle remains:

- if a given `workflow_id + activity_name` already has a `SUCCESSFUL` row, the engine reuses that successful `output` and skips re-execution

Because activity history now contains many attempts for the same activity name, repository queries must distinguish between:

- "does any successful attempt already exist?"
- "what is the latest attempt for this activity?"
- "how many failed attempts exist?"

Required repository semantics:

- find latest attempt by `workflow_id + activity_name`, ordered by `gmt_created` and then `activity_id`
- find latest workflow activity by `workflow_id`, ordered by `gmt_created` and then `activity_id`
- check whether any `SUCCESSFUL` row exists for `workflow_id + activity_name`
- count `FAILED` rows for `workflow_id + activity_name`

## 7. Skip Semantics

`skip` should no longer mutate an older failed row into successful state.

New behavior:

1. workflow must be `TERMINATED`
2. find the latest persisted activity attempt for the workflow
3. latest attempt must not already be `SUCCESSFUL`
4. create a new `SUCCESSFUL` attempt for the same `activity_name`
5. write skip metadata into `output`
6. set `executed_node` for this skip-generated attempt
7. reopen the workflow and continue execution

Benefits:

- failed history is preserved
- skip becomes an explicit event in activity history
- later debugging can clearly distinguish "activity succeeded" from "activity was manually skipped"

## 8. Node Recording Strategy

### 8.1 `workflow_instance.start_node`

Persist once when workflow starts.

Value source:

- use the same resolved local node identity already used by the JDBC lock service instance identity base
- recommended readable format remains `IP:hostname:PID:random-suffix` unless explicitly configured

Semantics:

- records where the workflow instance entered the engine
- this is a stable creation-time field, not a "latest execution node"

### 8.2 `activity_instance.executed_node`

Persist on every activity attempt, including:

- normal success
- normal failure
- skip-generated successful attempt

Semantics:

- records where that specific attempt actually ran
- gives enough information to reconstruct cross-node execution history without adding another workflow-level mutable node field

## 9. Activity ID Strategy

The current deterministic activity ID pattern `<workflowId>-<sequence>` is no longer sufficient because retries now create multiple rows for the same activity.

New requirement:

- `activity_id` must be unique per attempt

Recommended pattern:

- `<workflowId>-<sequence>-<attempt>`

Example:

- `abcd-1234-0001-01-01`
- `abcd-1234-0001-01-02`
- `abcd-1234-0001-02-01`

Where:

- first logical segment identifies workflow
- second identifies activity sequence within the definition
- third identifies the attempt number for that activity

Any equivalent deterministic unique scheme is acceptable as long as:

- attempts for the same activity are sortable
- latest-attempt lookup remains straightforward

## 10. Index Assessment

### 10.1 `workflow_instance.workflow_id`

No additional index needed.

Reason:

- it is already the primary key
- adding another index would be redundant

### 10.2 `workflow_instance.workflow_name`

Add an index.

Recommended index:

- `(workflow_name, gmt_modified)`

Reason:

- ops console filters by workflow name
- filtered results are then typically read in "recent first" order
- the compound index supports both the filter and common ordering pattern better than a plain single-column index

### 10.3 `workflow_instance.start_node`

Do not add an index now.

Reason:

- node values are expected to have low or medium cardinality
- writes happen for every workflow creation
- no current core query path filters by start node
- a node index can be added later if operational queries prove necessary

### 10.4 `activity_instance.executed_node`

Do not add an index now.

Reason:

- same trade-off as `start_node`
- current hot queries are by `workflow_id`, `activity_name`, and status/history, not by execution node

### 10.5 `activity_instance (workflow_id, activity_name)`

Keep this composite index exactly as requested.

Reason:

- it remains the main lookup path for:
  - activity success idempotency checks
  - latest-attempt lookup per activity
  - failed-attempt counting by activity name

### 10.6 Existing additional indexes

Keep current supporting indexes for now:

- `idx_workflow_instance_biz_id`
- `idx_workflow_instance_status`
- `idx_activity_instance_workflow_id`
- `idx_activity_instance_status`

They still serve valid list/detail/filter patterns, and removing them in the same refactor would mix schema cleanup with performance tuning.

## 11. Code Impact

Expected code changes:

- `WorkflowInstance` removes `extCol`, adds `startNode`
- `ActivityInstance` adds `executedNode`
- `JdbcWorkflowRepository` removes `ext_col` persistence, adds `start_node`
- `JdbcActivityRepository` changes from update-in-place semantics to append-per-attempt semantics
- `WorkflowEngine`
  - stops reading/writing `retryCounts`
  - counts failed attempts from `activity_instance`
  - generates unique activity IDs per attempt
  - writes `executed_node` for every attempt
- `DefaultWorkflowRuntimeService`
  - stops resetting retry metadata during manual retry
- ops console schema/demo data/test SQL
  - remove `ext_col`
  - include `start_node`
  - include `executed_node`

## 12. Risks and Trade-offs

### Benefits

- schema becomes easier to understand
- retry behavior becomes auditable from actual persisted history
- failures and manual skip actions remain visible instead of being overwritten
- node-level execution tracing becomes more useful

### Costs

- this is a breaking schema and behavior change
- repository interfaces and tests need wider updates
- activity history will grow faster because retries append rows instead of overwriting one row

This trade-off is acceptable because:

- the user explicitly approved a non-compatible cleanup
- the new persistence model is substantially clearer and more operationally useful

## 13. Final Recommendation

Implement the refactor with these hard rules:

- remove `workflow_instance.ext_col`
- add `workflow_instance.start_node`
- add `activity_instance.executed_node`
- keep `(workflow_id, activity_name)` as the activity composite index
- add a `workflow_name` index
- represent each activity attempt as a separate `activity_instance` row
- derive retry usage by counting persisted `FAILED` attempts
- do not reset retry history during manual retry
