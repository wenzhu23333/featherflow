# FeatherFlow

[中文 README](./README.md)

FeatherFlow is a self-developed lightweight Java workflow framework with persistent runtime state, YAML/XML workflow definitions, retry and manual operation control, and Spring Boot starter integration.

## Modules

- `featherflow-core`
  - Workflow model, definition parsing, execution engine, retry logic, daemon scanning, locking/idempotency, repositories, and tests.
- `featherflow-spring-boot-starter`
  - Spring Boot auto-configuration, resource loading, default JDBC repositories, and daemon lifecycle management.
- `featherflow-spring-boot-demo`
  - A runnable Spring Boot sample that demonstrates handlers, YAML workflow definitions, REST endpoints, and local H2 execution.
- `featherflow-ops-console`
  - A lightweight operations console built with `Spring Boot + Thymeleaf + HTMX` that connects directly to the FeatherFlow database, visualizes workflow/activity/operation state, and submits operations by writing `workflow_operation`.

## Features

- Supports sequential workflow orchestration.
- Supports both YAML and XML workflow definitions.
- Persists the three core tables: `workflow_instance`, `activity_instance`, and `workflow_operation`.
- Supports per-activity retry interval and maximum retry count.
- Persists exception details into `activity_instance.output` when an activity fails.
- Moves the workflow into `HUMAN_PROCESSING` after retries are exhausted and supports manual retry.
- Local `start/retry/terminate/skip` calls go directly through the runtime service, while `workflow_operation` is reserved for commands written by external operations systems.
- Prevents duplicate concurrent activity execution through a database-backed lock and idempotency checks by default.
- Provides a Spring Boot starter for fast integration into business applications.

## Maven Dependency

```xml
<dependency>
    <groupId>com.ywz.workflow</groupId>
    <artifactId>featherflow-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Spring Boot Demo

The repository includes a runnable sample module:

- `featherflow-spring-boot-demo`

The demo shows:

- how to integrate FeatherFlow through the starter
- how to implement `WorkflowActivityHandler`
- how to place YAML workflow definitions
- how to call `start / terminate / retry / skip` through HTTP
- how to correlate logs with `workflowId` and `bizId`

Run the demo:

```bash
mvn -q -pl featherflow-spring-boot-demo -am spring-boot:run
```

Start a workflow:

```bash
curl -X POST http://localhost:8080/demo/workflows/start \
  -H 'Content-Type: application/json' \
  -d '{"bizId":"demo-biz-001","amount":100,"customerName":"Alice"}'
```

Query the current workflow view:

```bash
curl http://localhost:8080/demo/workflows/{workflowId}
```

Terminate and retry:

```bash
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/terminate
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/retry
```

Skip the latest activity:

```bash
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/skip
```

Notes:

- `skip` is only allowed when the workflow is already `TERMINATED`.
- To observe retry behavior, include `"forceNotifyFailure": true` in the start payload.
- The demo uses an in-memory H2 database and the built-in schema SQL. No manual database setup is required.
- The sample source entry point lives in `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo`.

## Ops Console

The repository also includes an operations console module:

- `featherflow-ops-console`

The console provides:

- workflow list page at `/workflows`
- workflow detail page at `/workflows/{workflowId}`
- operation history page at `/operations`
- direct `terminate / retry / skip latest activity` actions on both the list and detail pages

Run the console:

```bash
mvn -q -pl featherflow-ops-console -am spring-boot:run
```

Notes:

- It uses in-memory H2 plus the built-in `schema.sql` by default, which is convenient for local UI preview.
- In real environments, point `spring.datasource.*` to the actual FeatherFlow database.
- The console does not directly mutate core workflow state tables to drive actions; it writes operational commands into `workflow_operation`.

## Database Schema

Reference SQL files:

- `featherflow-core/src/main/resources/db/featherflow-h2.sql`
- `featherflow-core/src/main/resources/db/featherflow-mysql.sql`

## Spring Boot Configuration

```yaml
featherflow:
  enabled: true
  auto-start-daemon: true
  poll-interval-millis: 1000
  core-pool-size: 4
  max-pool-size: 8
  queue-capacity: 200
  persistence-write-retry-max-attempts: 4
  persistence-write-retry-initial-delay-millis: 100
  persistence-write-retry-max-delay-millis: 1000
  instance-id: 10.9.8.7:workflow-engine-a
  definition-locations:
    - classpath:/workflows/*.yml
    - classpath:/workflows/*.xml
```

Configuration notes:

- `enabled`: whether FeatherFlow is enabled.
- `auto-start-daemon`: whether to automatically start the daemon that scans `workflow_operation` for externally submitted commands.
- `definition-locations`: resource locations used to load workflow definition files.
- `instance-id`: optional instance identifier. A readable value such as `IP:node-name` or `IP:service-name` is recommended; if omitted, FeatherFlow generates `IP:hostname:PID:random-suffix`.
- `persistence-write-retry-max-attempts`: maximum retry attempts for framework-owned persistence writes.
- `persistence-write-retry-initial-delay-millis`: delay before the first retry.
- `persistence-write-retry-max-delay-millis`: upper bound of the exponential backoff delay.
- The daemon only claims and dispatches externally written `workflow_operation` rows; local API calls submit workflows directly into the execution scheduler.
- Actual workflow execution runs in a unified execution thread pool, where the same worker thread performs both activity logic and the subsequent state transitions.
- Automatic activity retries use an internal delayed retry scheduler instead of writing `workflow_operation` rows.
- Framework-owned critical writes retry transient database failures by default; if retries are exhausted, FeatherFlow emits high-signal error logs and stops the current worker with whatever state was last persisted.

## Workflow Definition

YAML example:

```yaml
workflow:
  name: sampleOrderWorkflow
  activities:
    - name: createOrder
      handler: createOrderHandler
      retryInterval: PT5S
      maxRetryTimes: 2
    - name: notifyCustomer
      handler: notifyCustomerHandler
      retryInterval: PT10S
      maxRetryTimes: 1
```

XML example:

```xml
<workflow name="sampleOrderWorkflow">
  <activity name="createOrder" handler="createOrderHandler" retryInterval="PT5S" maxRetryTimes="2"/>
  <activity name="notifyCustomer" handler="notifyCustomerHandler" retryInterval="PT10S" maxRetryTimes="1"/>
</workflow>
```

Field notes:

- `name`: workflow or activity name.
- `handler`: the Activity Handler bean name in the Spring container.
- `retryInterval`: retry interval after failure, using `Duration` format.
- `maxRetryTimes`: maximum retry count before entering manual processing.

## Activity Handler

```java
@Component("createOrderHandler")
public class CreateOrderHandler implements WorkflowActivityHandler {

    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        context.put("created", true);
        return context;
    }
}
```

Notes:

- The handler input and output are both context maps of type `Map<String, Object>`.
- The return value is serialized into the current activity `output` and becomes the base context for subsequent activities.
- `activity_instance` is written only after an activity finishes, with `SUCCESSFUL` on success and `FAILED` on failure.
- Whether the handler throws an `Exception` or an `Error`, the engine treats it as an activity failure, persists the failure output, and then applies retry or `HUMAN_PROCESSING` rules.
- FeatherFlow automatically injects `workflowId` and `bizId` into `MDC` for daemon threads and workflow execution threads, so business handler logs can reuse the same correlation fields directly.

## Logging Correlation

To correlate the full workflow chain through logs, include `workflowId` and `bizId` in your logging pattern.

Spring Boot `application.yml` example:

```yaml
logging:
  pattern:
    level: "%5p [workflowId:%X{workflowId:-}] [bizId:%X{bizId:-}]"
```

If you want the current business thread to continue logging on the same trace after `startWorkflow()` returns, open the workflow log context explicitly:

```java
WorkflowInstance workflow = workflowCommandService.startWorkflow(
    "sampleOrderWorkflow",
    "biz-token-001",
    "{\"amount\":100}"
);

try (WorkflowLogContext.Scope ignored = WorkflowLogContext.open(workflow)) {
    log.info("workflow accepted by business service");
}
```

## Command Service Usage

```java
WorkflowInstance workflow = workflowCommandService.startWorkflow(
    "sampleOrderWorkflow",
    "biz-token-001",
    "{\"amount\":100}"
);

workflowCommandService.terminateWorkflow(workflow.getWorkflowId(), "{\"reason\":\"manual-stop\"}");
workflowCommandService.retryWorkflow(workflow.getWorkflowId());
workflowCommandService.skipActivity(workflow.getWorkflowId(), "{\"manual\":true}");
```

Command semantics:

- `startWorkflow`: synchronously persists the workflow instance and immediately submits it into the execution scheduler; submission failure is raised to the caller.
- `terminateWorkflow`: the local API directly moves the workflow to `TERMINATED`; the engine stops before the next activity. External operations systems can also write a `TERMINATE` operation, which the daemon consumes and forwards to the same runtime logic.
- `retryWorkflow`: allowed only when the workflow is `HUMAN_PROCESSING` or `TERMINATED`; the local API reopens the workflow to `RUNNING` and submits it into the unified execution pool. The resumed context is derived from the latest persisted activity snapshot, reusing `output` after success and `input` after failure. External operations systems can also write a `RETRY` operation, which the daemon consumes and forwards to the same runtime logic.
- `skipActivity`: skips the latest recorded activity, allowed only when the workflow is `TERMINATED`.
- `workflow_operation.status`: `PENDING -> PROCESSING -> SUCCESSFUL/FAILED`, which represents only external command-consumption state rather than overall workflow success.

## Build

```bash
mvn test
```

If Maven is not installed globally, you can run the build with the project settings file and a local Maven binary.
