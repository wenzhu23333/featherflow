# FeatherFlow Ops Console Design

## 1. Goals

Build a lightweight operations console as a separate project for FeatherFlow.

The console should:

- Connect directly to the FeatherFlow database.
- Provide a clear web UI for workflow status, activity timeline, and failure details.
- Support common operations actions without changing FeatherFlow runtime code.
- Stay lightweight by using a single Spring Boot web application instead of a separated frontend and backend.
- Reuse only the existing FeatherFlow tables:
  - `workflow_instance`
  - `activity_instance`
  - `workflow_operation`

## 2. Non-goals

- No frontend-backend separation.
- No browser-to-database direct connection.
- No login or RBAC in V1.
- No new database tables.
- No real-time websocket push.
- No DAG or parallel workflow visualization.

## 3. Product Positioning

FeatherFlow Ops Console is an internal operations web console.

It serves two responsibilities:

- Read:
  - show workflow execution state, activity history, failure details, and external operations history
- Write:
  - submit controlled operations commands through `workflow_operation`

The console is not a workflow engine.
It does not execute workflows itself.
It only reads persisted state and writes externally-consumed operations commands.

## 4. Architecture

Use a lightweight monolith:

- `Spring Boot`
- `Thymeleaf`
- `HTMX`
- `JdbcTemplate`

Recommended project name:

- `featherflow-ops-console`

Recommended package:

- `com.ywz.workflow.featherflow.ops`

### 4.1 Why this architecture

- It is lighter than a separated React/Vue + backend stack.
- It keeps database access and operations validation safely on the server side.
- It is easy for Java teams to build and maintain.
- HTMX is enough for table refresh, details refresh, and operations dialogs without introducing a frontend build system.

## 5. Core Design Principles

### 5.1 Read directly from database

The console reads workflow state directly from:

- `workflow_instance`
- `activity_instance`
- `workflow_operation`

### 5.2 Write operations only through `workflow_operation`

The console should not directly mutate `workflow_instance.status` or `activity_instance.status` for normal operations flows.

Instead, it writes:

- `START`
- `TERMINATE`
- `RETRY`
- `SKIP_ACTIVITY`

to `workflow_operation` with `status = PENDING`.

This keeps the console aligned with the existing FeatherFlow design, where external operations systems act through `workflow_operation`.

### 5.3 Reuse `workflow_operation.input` as operations metadata

Because no new table may be added, operation metadata must be stored in `workflow_operation.input` as JSON.

Examples:

Terminate:

```json
{
  "operator": "alice",
  "reason": "manual-stop"
}
```

Retry:

```json
{
  "operator": "alice",
  "reason": "retry-after-check"
}
```

Skip:

```json
{
  "operator": "alice",
  "reason": "manual-skip",
  "activityId": "abcd-1234-abcd-1234-02"
}
```

Start:

```json
{
  "operator": "alice",
  "reason": "manual-start",
  "definitionName": "sampleOrderWorkflow",
  "bizId": "biz-001",
  "input": {
    "amount": 100
  }
}
```

## 6. UI Information Architecture

V1 should contain only three main pages.

### 6.1 Workflow list page

Route:

- `GET /workflows`

Purpose:

- let operations users quickly search, filter, inspect, and trigger common actions

Filters:

- `workflowId`
- `bizId`
- `status`
- `workflowName`
- `createdFrom`
- `createdTo`
- `modifiedFrom`
- `modifiedTo`

Columns:

- workflowId
- bizId
- workflow name
- current status
- latest activity name
- latest activity status
- last modified time
- latest failure summary
- operations column

Operations column:

- `View`
- `Terminate`
- `Retry`
- `Skip Latest Activity`
- `Copy Workflow ID`

The available actions must be computed by the server.

### 6.2 Workflow detail page

Route:

- `GET /workflows/{workflowId}`

Purpose:

- provide a full operational view of one workflow

Sections:

1. Basic information
   - workflowId
   - bizId
   - workflow name
   - status
   - gmtCreated
   - gmtModified

2. Operations toolbar
   - `Terminate`
   - `Retry`
   - `Skip Latest Activity`
   - `Refresh`

3. Activity timeline
   - vertical ordered timeline by activity sequence
   - each step shows:
     - activityId
     - activityName
     - status
     - gmtCreated
     - gmtModified
     - input preview
     - output preview
     - failure summary

4. Workflow input panel
   - show `workflow_instance.input`

5. Operations history
   - derived from `workflow_operation`
   - show:
     - operationType
     - operationStatus
     - operator
     - reason
     - activityId if present
     - gmtCreated
     - gmtModified

### 6.3 Operations history page

Route:

- `GET /operations`

Purpose:

- provide a global operations audit-like list without a separate audit table

Filters:

- workflowId
- bizId
- operationType
- status
- operator
- time range

Columns:

- operationId
- workflowId
- bizId
- operationType
- operation status
- operator
- reason
- created time
- modified time

## 7. Interaction Design

### 7.1 HTMX usage

HTMX should be used for:

- filter form submission
- table partial refresh
- details panel refresh
- modal dialog content loading
- operations button submission

Recommended refresh intervals:

- workflow list page:
  - refresh list fragment every 5 seconds
- workflow detail page:
  - refresh summary and timeline fragments every 3 seconds

### 7.2 Operations dialogs

Each dangerous action should open a small confirmation dialog.

Required form fields:

- `operator`
- `reason`

Additional field for skip:

- `activityId`

Validation rules:

- `operator` must be non-empty
- `reason` must be non-empty

## 8. Backend Module Structure

Suggested package layout:

- `controller`
- `service`
- `repository`
- `view`
- `templates`
- `static`

### 8.1 Controller layer

Responsibilities:

- page routing
- HTMX fragment endpoints
- operations form submission endpoints

Suggested controllers:

- `WorkflowPageController`
- `WorkflowOperationController`
- `OperationHistoryController`

### 8.2 Service layer

Responsibilities:

- query aggregation
- state-based action eligibility
- operations input JSON construction
- command submission validation

Suggested services:

- `WorkflowQueryService`
- `WorkflowOperationService`

### 8.3 Repository layer

Responsibilities:

- SQL execution only
- no UI logic
- no operations eligibility logic

Suggested repositories:

- `WorkflowViewRepository`
- `ActivityViewRepository`
- `OperationViewRepository`

## 9. View Models

The UI should not consume raw table rows directly.

Suggested view models:

- `WorkflowListItemView`
- `WorkflowListPageView`
- `WorkflowDetailView`
- `ActivityTimelineItemView`
- `OperationRecordView`
- `AllowedActionsView`

### 9.1 `WorkflowListItemView`

Fields:

- workflowId
- bizId
- workflowName
- workflowStatus
- latestActivityId
- latestActivityName
- latestActivityStatus
- latestFailureSummary
- gmtModified
- allowedActions

### 9.2 `WorkflowDetailView`

Fields:

- workflowId
- bizId
- workflowName
- workflowStatus
- workflowInput
- gmtCreated
- gmtModified
- activities
- operations
- latestActivityId
- latestFailedActivityId
- allowedActions

### 9.3 `AllowedActionsView`

Fields:

- canTerminate
- canRetry
- canSkipLatest

Server-side rules:

- `canTerminate` when workflow status is `RUNNING`
- `canRetry` when workflow status is `HUMAN_PROCESSING` or `TERMINATED`
- `canSkipLatest` when workflow status is `TERMINATED` and a latest activity exists

## 10. Query Design

### 10.1 Workflow list query

Use a paged query over `workflow_instance`.

Enrich each row with:

- workflow name from `ext_col.definitionName`
- latest activity from `activity_instance`
- latest operation summary if useful

Because activity data is needed per row, prefer one of these:

- a query per page row if page size is small
- or a batched query using workflow ids for latest activity lookup

V1 recommendation:

- page size default 20
- use a batched latest-activity query for all workflow ids on the page

### 10.2 Workflow detail query

Load:

- one `workflow_instance`
- all `activity_instance` rows ordered by `activity_id`
- all `workflow_operation` rows ordered by `operation_id desc`

### 10.3 Operation metadata parsing

Parse `workflow_operation.input` into a metadata object:

- operator
- reason
- activityId
- definitionName
- bizId
- nested workflow input if present

If parsing fails:

- do not fail the page
- keep raw JSON visible

## 11. Operations Submission Design

### 11.1 Start workflow

Because the console talks only to the database, start must be represented as an external command flow.

Recommended sequence:

1. generate a workflowId in ops console with the same `xxxx-xxxx-xxxx-xxxx` format
2. resolve bizId
3. insert `workflow_instance`
4. insert `workflow_operation` with:
   - `operation_type = START`
   - `status = PENDING`
   - `input` containing operator, reason, definitionName, bizId, and input payload

This assumes the engine daemon can consume externally submitted `START` rows.

### 11.2 Terminate workflow

Insert:

- `operation_type = TERMINATE`
- `status = PENDING`
- `input` JSON with operator and reason

### 11.3 Retry workflow

Insert:

- `operation_type = RETRY`
- `status = PENDING`
- `input` JSON with operator and reason

### 11.4 Skip latest activity

Insert:

- `operation_type = SKIP_ACTIVITY`
- `status = PENDING`
- `input` JSON with:
  - operator
  - reason
  - activityId

The backend must validate before insert:

- workflow is `TERMINATED`
- latest activity exists
- target activityId equals latest activity id

## 12. Validation Rules

### 12.1 Shared validations

- `workflowId` must exist
- `operator` must be non-empty
- `reason` must be non-empty

### 12.2 Terminate

- allowed only when workflow status is `RUNNING`

### 12.3 Retry

- allowed only when workflow status is `HUMAN_PROCESSING` or `TERMINATED`

### 12.4 Skip

- allowed only when workflow status is `TERMINATED`
- latest activity must exist
- submitted activityId must equal latest activity id

## 13. Page Rendering Strategy

Recommended Thymeleaf templates:

- `templates/layout/main.html`
- `templates/workflows/list.html`
- `templates/workflows/list-table.html`
- `templates/workflows/detail.html`
- `templates/workflows/detail-summary.html`
- `templates/workflows/detail-timeline.html`
- `templates/workflows/detail-operations.html`
- `templates/operations/list.html`
- `templates/fragments/operation-dialog.html`

Recommended static assets:

- `static/css/app.css`
- `static/js/app.js`

## 14. UX Details

- use color-coded status badges
- let workflowId and bizId be one-click copyable
- collapse long JSON by default
- show failure output in expandable sections
- surface the latest failed step at the top of detail page
- keep the list row operations visible without opening details first

## 15. Risks And Trade-offs

### 15.1 No separate audit table

Trade-off:

- lighter schema
- but less structured audit data

Mitigation:

- standardize `workflow_operation.input` JSON shape for all operations console actions

### 15.2 No login

Trade-off:

- fastest to build
- but weaker security and traceability

Mitigation:

- deploy only in trusted internal network
- require `operator` and `reason` for every mutation
- add reverse-proxy IP restrictions if needed

### 15.3 Pure database integration

Trade-off:

- decoupled from engine service
- but all mutations must be carefully validated before writing commands

Mitigation:

- keep writes limited to `workflow_operation`
- compute allowed actions server-side

## 16. V1 Scope

Build only:

- workflow list page
- workflow detail page
- operations history page
- activity timeline
- failure details
- start / terminate / retry / skip operations
- HTMX partial refresh

Do not build yet:

- login
- role control
- websocket push
- dashboard analytics
- multi-tenant support
- visual DAG rendering
