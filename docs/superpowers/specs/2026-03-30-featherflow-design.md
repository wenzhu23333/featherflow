# FeatherFlow Design

## 1. Goals

FeatherFlow is a self-developed lightweight workflow framework delivered as a reusable Java second-party package.
The first version focuses on single-node execution, persistent state, Spring Boot integration, and readable code.

Primary goals:

- Support workflow definitions in YAML and XML.
- Support sequential activities under one workflow.
- Persist workflow state, activity results, and external operations in a relational database.
- Allow local start/retry/terminate/skip calls to act directly on the runtime engine, while keeping `workflow_operation` as an external operations command channel.
- Use `input` and `output` as the workflow context contract between activities.
- Persist exception information into `activity_instance.output` when an activity fails.
- Stop retrying and move the workflow to `HUMAN_PROCESSING` after retries are exhausted.
- Prevent duplicate activity execution through lock + idempotency.
- Provide a Spring Boot starter for easy adoption.

Non-goals in V1:

- Distributed failover.
- Parallel branches or DAG execution.
- Long-running human approval nodes.
- Visual workflow designer.

## 2. Assumptions

- Workflow topology is linear only.
- Java baseline is compatible with Java 8 style source code and Spring Boot 2.x/3.x integration.
- Database access uses `JdbcTemplate`.
- JSON is the persistence format for `input`, `output`, and `ext_col`.
- The user-requested three tables remain the core business tables. A small JDBC lock table is added to back the default distributed-avoidance lock in clustered deployments.
- `workflow_operation.status` uses `PENDING`, `PROCESSING`, `SUCCESSFUL`, and `FAILED` for externally submitted commands.
- `activity_instance` is written only after execution finishes, so its persisted states are `SUCCESSFUL` and `FAILED`.

## 3. High-Level Architecture

The project uses a Maven multi-module layout:

- `featherflow-core`
  - Domain model, enums, parser SPI, definition registry, engine, scheduler, DAO SPI, lock/idempotency strategy, thread pool safety, and tests.
- `featherflow-spring-boot-starter`
  - Auto-configuration, property binding, `JdbcTemplate`-based repositories, resource loading, daemon startup, and business-facing bean registration.

Core runtime pieces:

1. `WorkflowDefinitionRegistry`
   - Loads and stores workflow definitions parsed from YAML or XML.
2. `WorkflowCommandService`
   - Exposes start, retry, terminate, skip, and status change operations.
3. `WorkflowOperationDaemon`
   - Polls externally written `workflow_operation`, atomically claims pending commands, and dispatches them into runtime service calls.
4. `WorkflowEngine`
   - Executes the next activity chain for a workflow until it reaches success, failure, termination, or human processing.
5. `WorkflowExecutionScheduler`
   - Submits claimed workflow runs into the workflow thread pool.
6. `WorkflowRetryScheduler`
   - Schedules internal automatic retries after activity failures without using `workflow_operation`.
7. `WorkflowLockService`
   - Prevents duplicate execution for the same `workflowId + activityName` key through a JDBC lock table when a database is present.
8. `WorkflowRepository` / `ActivityRepository` / `OperationRepository`
   - Encapsulate persistence.

## 4. Table Design

### 4.1 `workflow_instance`

Columns:

- `workflow_id` `varchar(19)` primary key
- `biz_id` `varchar(128)` not null
- `gmt_created` `timestamp` not null
- `gmt_modified` `timestamp` not null
- `input` `clob` not null
- `status` `varchar(32)` not null
- `ext_col` `clob` null

Indexes:

- `idx_workflow_instance_biz_id` on (`biz_id`)
- `idx_workflow_instance_status` on (`status`)

Notes:

- `workflow_id` format is `xxxx-xxxx-xxxx-xxxx`.
- `biz_id` defaults to `workflow_id` if absent.
- `ext_col` stores internal metadata such as definition name, current pointer, retry bookkeeping, and operator notes in JSON.

### 4.2 `activity_instance`

Columns:

- `activity_id` `varchar(64)` primary key
- `workflow_id` `varchar(19)` not null
- `activity_name` `varchar(128)` not null
- `gmt_created` `timestamp` not null
- `gmt_modified` `timestamp` not null
- `input` `clob` not null
- `output` `clob` null
- `status` `varchar(32)` null

Indexes:

- `idx_activity_instance_workflow_id` on (`workflow_id`)
- `idx_activity_instance_status` on (`status`)
- `idx_activity_instance_workflow_name` on (`workflow_id`, `activity_name`)

Notes:

- The extra `(workflow_id, activity_name)` index is added for idempotency lookup.
- Rows are persisted only after the activity finishes, so stored values are `SUCCESSFUL` or `FAILED`.
- `activity_id` is deterministic: `<workflowId>-<sequence>` to support skip by activity id.

### 4.3 `workflow_operation`

Columns:

- `operation_id` `bigint` primary key auto increment
- `workflow_id` `varchar(19)` not null
- `operation_type` `varchar(32)` not null
- `input` `clob` null
- `status` `varchar(32)` not null
- `gmt_created` `timestamp` not null
- `gmt_modified` `timestamp` not null

Indexes:

- `idx_workflow_operation_workflow_id` on (`workflow_id`)
- `idx_workflow_operation_status_modified` on (`status`, `gmt_modified`)

Notes:

- `PENDING` means not yet claimed by any daemon.
- `PROCESSING` means already claimed and being handled by one daemon.
- `SUCCESSFUL` means the command has been consumed successfully.
- `FAILED` means command handling failed.
- The table is reserved for external operations commands such as maintenance-triggered start, retry, terminate, and skip.

## 5. State Model

### 5.1 Workflow states

- `RUNNING`
- `HUMAN_PROCESSING`
- `TERMINATED`
- `SUCCESSFUL`

Transitions:

- `START` -> `RUNNING`
- `RUNNING` + all activities successful -> `SUCCESSFUL`
- `RUNNING` + retries exhausted -> `HUMAN_PROCESSING`
- `RUNNING` + terminate operation -> `TERMINATED`
- `HUMAN_PROCESSING` + retry operation -> `RUNNING`
- `TERMINATED` + skip activity operation -> `RUNNING`
- `TERMINATED` may remain `TERMINATED` if the operator only wants to stop.

### 5.2 Activity states

- `FAILED`
- `SUCCESSFUL`

### 5.3 Operation states

- `PENDING`
- `PROCESSING`
- `FAILED`
- `SUCCESSFUL`

## 6. Workflow Definition DSL

Both YAML and XML share the same logical schema:

- `workflow.name`
- `workflow.activities[]`
- `activity.name`
- `activity.handler`
- `activity.retryInterval`
- `activity.maxRetryTimes`

YAML example:

```yaml
workflow:
  name: sample-order-workflow
  activities:
    - name: createOrder
      handler: createOrderHandler
      retryInterval: PT5S
      maxRetryTimes: 3
    - name: notifyCustomer
      handler: notifyCustomerHandler
      retryInterval: PT10S
      maxRetryTimes: 2
```

XML example:

```xml
<workflow name="sample-order-workflow">
  <activity name="createOrder" handler="createOrderHandler" retryInterval="PT5S" maxRetryTimes="3"/>
  <activity name="notifyCustomer" handler="notifyCustomerHandler" retryInterval="PT10S" maxRetryTimes="2"/>
</workflow>
```

Business handlers are resolved by Spring bean name.

## 7. Execution Flow

### 7.1 Start

1. `WorkflowCommandService.startWorkflow()` resolves the definition by name.
2. Generate `workflow_id`.
3. Persist `workflow_instance` with `RUNNING`.
4. Dispatch the workflow directly into the execution thread pool through `WorkflowRuntimeService`.
5. If an external operations system writes a `PENDING` `START` operation, the daemon claims it and delegates to the same runtime dispatch logic.

### 7.2 Continue execution

1. The engine loads the workflow instance, definition, and activity instances after a local dispatch or an externally claimed operation delegates into runtime service.
4. For each next unfinished activity in order:
   - Reload workflow state before each step. If status is not `RUNNING`, stop immediately.
   - Acquire lock with key `workflowId + ":" + activityName`.
   - Perform idempotency lookup: if the same `workflow_id + activity_name` is already `SUCCESSFUL`, take its `output` as the current context and continue to the next step.
   - Merge current context into the activity `input`.
   - Execute the handler directly inside the current workflow execution thread.
   - On success, write serialized context to `activity_instance.output` and persist `SUCCESSFUL`.
   - On failure, capture exception message and stack summary into `activity_instance.output`, persist `FAILED`, and evaluate retry policy.
5. If an activity fails and retry remains:
   - Use the internal `WorkflowRetryScheduler` to re-dispatch the workflow after `retryInterval`.
   - Leave workflow as `RUNNING`.
6. If an activity fails and retry is exhausted:
   - Update workflow to `HUMAN_PROCESSING`.
   - Stop execution.
7. If all activities complete:
   - Update workflow to `COMPLETED`.

### 7.3 Retry

- `retryWorkflow(workflowId)` is allowed when the workflow is `HUMAN_PROCESSING` or `TERMINATED`.
- The local service reopens the workflow to `RUNNING` and dispatches it directly into the unified execution thread pool.
- An external operations system may also write a `PENDING` `RETRY` operation, and the daemon will forward it to the same runtime retry logic.
- The engine resumes from the first activity whose status is not `SUCCESSFUL`.
- Resume context comes from the latest persisted activity snapshot:
  - if the latest activity is `SUCCESSFUL`, continue from its `output`
  - if the latest activity is `FAILED`, continue from its `input`

### 7.4 Terminate

- `terminateWorkflow(workflowId, input)` directly moves the workflow to `TERMINATED`.
- An external operations system may also write a `PENDING` `TERMINATE` operation, and the daemon forwards it to the same runtime terminate logic.
- The engine checks workflow status before the next activity and stops.

### 7.5 Skip activity

- `skipActivity(workflowId, activityId, input)` directly validates that the workflow is currently `TERMINATED`.
- The target activity instance is marked `SUCCESSFUL`, and its `output` becomes the merged context of:
  - the last available workflow context
  - the skip input payload
- The skip output also stores a `_featherflowSkip` marker so later troubleshooting can see that the step was skipped manually.
- The workflow is moved back to `RUNNING`.
- The engine resumes from the next activity.
- An external operations system may also write a `PENDING` `SKIP_ACTIVITY` operation, and the daemon forwards it to the same runtime skip logic.

## 8. Locking and Idempotency

V1 uses a pluggable lock abstraction:

- `WorkflowLockService`
  - `boolean tryLock(String key)`
  - `void unlock(String key)`

Default implementation:

- JDBC-backed lock using `workflow_lock`
- Fallback local lock only when the starter runs without JDBC

Why this is acceptable in V1:

- The explicit requirement excludes distributed failover recovery.
- The goal is only to avoid concurrent duplicate consumption across nodes.
- Idempotency is still enforced at the database layer.

Idempotency rule:

- Before executing an activity, query by `workflow_id + activity_name`.
- If status is already `SUCCESSFUL`, skip actual execution and reuse its `output` as the next context.

## 9. Thread Pool Strategy

Recommended default:

- `corePoolSize = max(2, cpuCount)`
- `maxPoolSize = max(4, cpuCount * 2)`
- `queueCapacity = cpuCount * 100`
- rejection policy: caller-runs to avoid silent loss

Bounded queue is chosen over an unbounded queue because:

- It provides memory safety under burst traffic.
- It makes back-pressure visible.
- It avoids buffering unbounded tasks in memory while keeping the execution model simple.

Safety measures:

- The workflow execution scheduler catches uncaught workflow task failures and moves still-running workflows into `HUMAN_PROCESSING`.
- Automatic activity retries are rescheduled through `WorkflowRetryScheduler`.
- Rejected execution falls back to `CallerRunsPolicy` so the task is still executed instead of being dropped.

## 10. Public Services

### 10.1 `WorkflowCommandService`

- `startWorkflow(String definitionName, String bizId, String input)`
- `changeWorkflowStatus(String workflowId, WorkflowStatus status, String note)`
- `retryWorkflow(String workflowId)`
- `terminateWorkflow(String workflowId, String input)`
- `skipActivity(String workflowId, String activityId, String input)`

### 10.2 `WorkflowQueryService`

- `findWorkflow(String workflowId)`
- `findActivities(String workflowId)`
- `findPendingOperations(String workflowId)`

## 11. Starter Integration

Starter components:

- `FeatherFlowProperties`
- `FeatherFlowAutoConfiguration`
- `JdbcRepositoryAutoConfiguration`
- `WorkflowDaemonLifecycle`
- `WorkflowDefinitionResourceLoader`

Business extension SPI:

- `WorkflowActivityHandler`
- `WorkflowContextSerializer`
- `WorkflowDefinitionCustomizer`
- `WorkflowLockService`

## 12. Testing Strategy

Unit tests:

- UUID generation and default `bizId`
- YAML and XML parsing
- workflow state transition rules
- retry exhaustion to `HUMAN_PROCESSING`
- skip only from `TERMINATED`
- idempotency reuse of prior successful output
- scheduler-level guard catches unexpected workflow task crashes

Integration tests:

- H2 schema + JdbcTemplate repositories
- start -> success happy path
- failure -> retry -> success
- failure -> retry exhausted -> human processing -> manual retry
- terminate + skip + resume

## 13. Packaging

- Maven multi-module build
- JAR artifacts for `featherflow-core` and `featherflow-spring-boot-starter`
- starter follows Spring Boot auto-configuration conventions
- README contains quick-start, schema SQL, YAML/XML examples, and service usage
