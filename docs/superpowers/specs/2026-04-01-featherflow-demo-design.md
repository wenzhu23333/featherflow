# FeatherFlow Spring Boot Demo Design

## Goals

Add a runnable demo module inside the repository so business teams can learn FeatherFlow through working code instead of copying snippets from the README.

The demo should:

- Start as a normal Spring Boot application.
- Show the minimum Spring Boot configuration required to use `featherflow-spring-boot-starter`.
- Include a linear YAML workflow definition and example `WorkflowActivityHandler` beans.
- Expose REST endpoints for `start`, `terminate`, `retry`, and `skip`.
- Include tests that show both direct service usage and HTTP usage.

## Non-goals

- Do not change FeatherFlow runtime semantics.
- Do not introduce a new framework API layer.
- Do not build a production-ready business service.
- Do not add extra persistence abstractions beyond a local H2 datasource.

## Module Layout

Add a new Maven module:

- `featherflow-spring-boot-demo`

This module depends on:

- `featherflow-spring-boot-starter`
- `spring-boot-starter-web`
- `spring-boot-starter-test`
- `h2`

## Demo Application Design

### Application

Create one Spring Boot application class under:

- `com.ywz.workflow.featherflow.demo.FeatherFlowDemoApplication`

The application loads:

- demo workflow YAML from `classpath:/workflows/demo-order-workflow.yml`
- H2 datasource
- FeatherFlow starter auto-configuration

### Workflow Definition

Use one small order-style workflow:

1. `createOrder`
2. `notifyCustomer`

The workflow should be simple enough to read in under a minute.

### Handlers

Provide two handlers:

- `createOrderHandler`
  - reads incoming context
  - writes business fields such as `orderCreated`, `orderNo`, and `currentActivityIdHint`
  - logs with `workflowId` and `bizId`
- `notifyCustomerHandler`
  - writes `customerNotified`
  - optionally fails when input requests a simulated failure so retry behavior remains demonstrable

The handlers should mutate and return the incoming map because that matches FeatherFlow's current context model and keeps the example easy to follow.

### Service Facade

Add a small demo service that wraps `WorkflowCommandService`.

Purpose:

- keep controller code thin
- show business-side in-process integration
- centralize JSON payload handling for demo endpoints

### REST API

Expose these endpoints:

- `POST /demo/workflows/start`
- `POST /demo/workflows/{workflowId}/terminate`
- `POST /demo/workflows/{workflowId}/retry`
- `POST /demo/workflows/{workflowId}/skip/{activityId}`
- `GET /demo/workflows/{workflowId}`

REST endpoints are demo-only adapters around `WorkflowCommandService` and repository reads.

### Response Model

Use small response DTOs so example payloads are easy to read:

- workflow id
- biz id
- current workflow status
- latest activity id when useful

## Persistence And Bootstrapping

The demo should bootstrap itself with H2 and schema SQL on startup.

Requirements:

- no manual database preparation
- no external dependencies
- works with `mvn spring-boot:run` or direct application startup

Use the existing SQL schema from `featherflow-core`.

## Testing Strategy

Keep tests in the demo module itself.

### Service integration test

Verify:

- application context starts
- `WorkflowCommandService.startWorkflow()` runs successfully
- workflow reaches `SUCCESSFUL`
- activity records are written

### REST integration test

Verify:

- `POST /start` returns workflow id and biz id
- `GET /{workflowId}` returns current state
- terminate and retry endpoints can be called against a live app

Tests should be readable enough to act as executable examples.

## Documentation

Update both `README.md` and `README.en.md` with:

- module purpose
- startup command
- demo endpoint list
- `curl` examples
- where to find the demo source code

## Risks And Mitigations

- Demo complexity grows too much:
  - keep only one workflow and two handlers.
- Demo drifts from starter defaults:
  - reuse starter configuration patterns already covered by integration tests.
- Example code becomes harder to read than the framework:
  - prefer short files, obvious names, and comments only where intent is not self-evident.
