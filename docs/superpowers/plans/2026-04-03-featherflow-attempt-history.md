# FeatherFlow Attempt History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `workflow_instance.ext_col` with explicit node fields and change `activity_instance` to persist one row per execution attempt, with retry decisions derived from persisted failed attempts.

**Architecture:** This change touches the core persistence model, engine retry semantics, and ops-console schema/query assumptions. The implementation should proceed from schema-contract tests, to repository/model updates, to engine behavior changes, and finally to ops-console and documentation alignment. Activity history becomes append-only per attempt, while workflow runtime metadata becomes explicit columns.

**Tech Stack:** Java 8, Spring Boot 2.7, JdbcTemplate, H2/MySQL SQL scripts, JUnit 5, AssertJ, MockMvc.

---

## File Structure

### Core engine and persistence

- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/WorkflowInstance.java`
  - Remove `extCol`, add `startNode`.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/ActivityInstance.java`
  - Add `executedNode`.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowCommandService.java`
  - Persist `startNode` at workflow creation.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowRuntimeService.java`
  - Remove retry-reset behavior that depended on `ext_col`.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/WorkflowEngine.java`
  - Append one activity row per attempt, count failed rows for retry policy, use latest successful row for idempotency, append skip rows instead of mutating old rows.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/ActivityRepository.java`
  - Expose latest-attempt lookup and failed-attempt counting APIs.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcActivityRepository.java`
  - Switch from update-in-place to append-per-attempt semantics.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcWorkflowRepository.java`
  - Remove `ext_col`, add `start_node`.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/support/InMemoryActivityRepository.java`
  - Mirror append-per-attempt semantics.

### SQL and starter resources

- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/resources/db/featherflow-h2.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/resources/db/featherflow-mysql.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/resources/schema.sql`

### Ops console

- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/schema.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/demo-data.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/repository/WorkflowViewRepository.java`
  - Stop selecting `ext_col`, select `start_node`.
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
  - Remove any remaining `ext_col` assumptions.
- Modify test SQL under:
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/resources/sql/`

### Tests

- Modify core tests under:
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java`
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java`
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/service/WorkflowRuntimeFlowTest.java`
- Modify ops-console tests under:
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/`

### Docs

- Modify:
  - `/Users/yangwenzhuo/Code/Codex/featherflow/README.md`
  - `/Users/yangwenzhuo/Code/Codex/featherflow/README.en.md`
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/README.md`

---

### Task 1: Lock In the New Schema Contract with Failing Tests

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/resources/schema.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/resources/sql/featherflow-schema.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/resources/sql/workflow-list-data.sql`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java`

- [ ] **Step 1: Write the failing repository contract assertions**

```java
WorkflowInstance workflowInstance = new WorkflowInstance(
    "abcd-ef01-2345-6789",
    "biz-001",
    "orderWorkflow",
    "10.9.8.7:host-a:1234:seed",
    Instant.parse("2026-03-30T10:00:00Z"),
    Instant.parse("2026-03-30T10:00:00Z"),
    "{\"amount\":100}",
    WorkflowStatus.RUNNING
);

repository.save(workflowInstance);

WorkflowInstance loaded = repository.findRequired("abcd-ef01-2345-6789");
assertThat(loaded.getWorkflowName()).isEqualTo("orderWorkflow");
assertThat(loaded.getStartNode()).isEqualTo("10.9.8.7:host-a:1234:seed");
```

- [ ] **Step 2: Update the test schemas to the target shape**

```sql
create table workflow_instance (
    workflow_id varchar(19) primary key,
    biz_id varchar(128) not null,
    workflow_name varchar(128) not null,
    start_node varchar(128) not null,
    gmt_created timestamp not null,
    gmt_modified timestamp not null,
    input clob not null,
    status varchar(32) not null
);

create index idx_workflow_instance_name_modified on workflow_instance (workflow_name, gmt_modified);
```

- [ ] **Step 3: Run the targeted repository test and confirm it fails**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-core \
  -Dtest=JdbcRepositoryIntegrationTest \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because production model/repository code still expects `ext_col` and has no `startNode`.

- [ ] **Step 4: Commit the red test state**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java \
  featherflow-core/src/test/resources/schema.sql \
  featherflow-ops-console/src/test/resources/sql/featherflow-schema.sql \
  featherflow-ops-console/src/test/resources/sql/workflow-list-data.sql
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "test: define explicit workflow node schema contract"
```

---

### Task 2: Implement `workflow_instance.start_node` and Remove `ext_col`

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/WorkflowInstance.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcWorkflowRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowCommandService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/resources/db/featherflow-h2.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/resources/db/featherflow-mysql.sql`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java`

- [ ] **Step 1: Rewrite `WorkflowInstance` to remove `extCol` and add `startNode`**

```java
public class WorkflowInstance {

    private final String workflowId;
    private final String bizId;
    private final String workflowName;
    private final String startNode;
    private Instant gmtCreated;
    private Instant gmtModified;
    private String input;
    private WorkflowStatus status;

    public WorkflowInstance(
        String workflowId,
        String bizId,
        String workflowName,
        String startNode,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        WorkflowStatus status
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.workflowName = workflowName;
        this.startNode = startNode;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.input = input;
        this.status = status;
    }
}
```

- [ ] **Step 2: Update JDBC SQL to persist `start_node` and drop `ext_col`**

```java
"insert into workflow_instance "
    + "(workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status) "
    + "values (?, ?, ?, ?, ?, ?, ?, ?)"

"update workflow_instance set biz_id = ?, workflow_name = ?, start_node = ?, gmt_modified = ?, input = ?, status = ? "
    + "where workflow_id = ?"

"select workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status "
    + "from workflow_instance where workflow_id = ?"
```

- [ ] **Step 3: Persist `start_node` when creating a workflow**

```java
WorkflowInstance workflowInstance = new WorkflowInstance(
    workflowId,
    effectiveBizId,
    definition.getName(),
    nodeIdentity.currentNode(),
    now,
    now,
    effectiveInput,
    WorkflowStatus.RUNNING
);
```

Note:

- Reuse the existing readable node identity source already used by the JDBC lock service.
- Introduce a shared `NodeIdentity` abstraction if needed instead of duplicating hostname/IP resolution logic.

- [ ] **Step 4: Run the repository test and confirm it passes**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-core \
  -Dtest=JdbcRepositoryIntegrationTest \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/WorkflowInstance.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcWorkflowRepository.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowCommandService.java \
  featherflow-core/src/main/resources/db/featherflow-h2.sql \
  featherflow-core/src/main/resources/db/featherflow-mysql.sql
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "refactor: persist workflow start node explicitly"
```

---

### Task 3: Convert `activity_instance` to Attempt History Rows

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/ActivityInstance.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/ActivityRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcActivityRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/support/InMemoryActivityRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java`

- [ ] **Step 1: Write failing tests for append-per-attempt behavior**

```java
@Test
void shouldPersistMultipleFailedAttemptsForTheSameActivity() {
    handlerRegistry.register("notifyHandler", context -> {
        throw new IllegalStateException("boom");
    });

    workflowEngine.continueWorkflow(workflowId);
    workflowEngine.continueWorkflow(workflowId);

    List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflowId);
    assertThat(activities)
        .filteredOn(activity -> activity.getActivityName().equals("notifyCustomer"))
        .hasSize(2);
    assertThat(activities)
        .filteredOn(activity -> activity.getActivityName().equals("notifyCustomer"))
        .allMatch(activity -> activity.getStatus() == ActivityExecutionStatus.FAILED);
}
```

- [ ] **Step 2: Extend `ActivityInstance` and repository contracts**

```java
public interface ActivityRepository {
    void saveAttempt(
        String activityId,
        String workflowId,
        String activityName,
        String executedNode,
        String input,
        String output,
        ActivityExecutionStatus status,
        Instant modifiedAt
    );

    ActivityInstance findLatestByWorkflowIdAndActivityName(String workflowId, String activityName);
    long countByWorkflowIdAndActivityNameAndStatus(String workflowId, String activityName, ActivityExecutionStatus status);
}
```

- [ ] **Step 3: Implement append-only JDBC persistence**

```java
jdbcTemplate.update(
    "insert into activity_instance "
        + "(activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status) "
        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    activityId,
    workflowId,
    activityName,
    executedNode,
    Timestamp.from(modifiedAt),
    Timestamp.from(modifiedAt),
    input,
    output,
    status.name()
);
```

And latest-attempt lookup:

```java
"select activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status "
    + "from activity_instance where workflow_id = ? and activity_name = ? "
    + "order by gmt_created desc, activity_id desc"
```

- [ ] **Step 4: Run the focused engine tests**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-core \
  -Dtest=WorkflowEngineTest \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS after repository/model updates are complete

- [ ] **Step 5: Commit**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/model/ActivityInstance.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/ActivityRepository.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/JdbcActivityRepository.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/support/InMemoryActivityRepository.java \
  featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "refactor: persist activity history per attempt"
```

---

### Task 4: Derive Retry Decisions from Failed Attempt History

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/WorkflowEngine.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowRuntimeService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/service/WorkflowRuntimeFlowTest.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-integration-tests/src/test/java/com/ywz/workflow/featherflow/service/WorkflowBestPracticeScenariosTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/service/WorkflowRuntimeFlowTest.java`

- [ ] **Step 1: Write failing tests for non-resetting retry history**

```java
@Test
void manualRetryShouldNotResetFailedAttemptHistory() {
    // first failure moves workflow to HUMAN_PROCESSING when maxRetryTimes is 0
    workflowRuntimeService.retryWorkflow(workflowId);

    long failedCount = activityRepository.countByWorkflowIdAndActivityNameAndStatus(
        workflowId,
        "alwaysFail",
        ActivityExecutionStatus.FAILED
    );

    assertThat(failedCount).isEqualTo(2L);
    assertThat(workflowRepository.findRequired(workflowId).getStatus()).isEqualTo(WorkflowStatus.HUMAN_PROCESSING);
}
```

- [ ] **Step 2: Replace `retryCounts` logic in `WorkflowEngine`**

```java
private void handleRetry(WorkflowInstance workflowInstance, ActivityDefinition activityDefinition) {
    Instant now = clock.instant();
    long failedCount = activityRepository.countByWorkflowIdAndActivityNameAndStatus(
        workflowInstance.getWorkflowId(),
        activityDefinition.getName(),
        ActivityExecutionStatus.FAILED
    );

    workflowInstance.setGmtModified(now);
    if (failedCount <= activityDefinition.getMaxRetryTimes()) {
        workflowRetryScheduler.scheduleRetry(workflowInstance.getWorkflowId(), activityDefinition.getRetryInterval());
    } else {
        workflowInstance.setStatus(WorkflowStatus.HUMAN_PROCESSING);
    }
    workflowRepository.update(workflowInstance);
}
```

- [ ] **Step 3: Remove retry-reset logic from manual retry**

```java
public void retryWorkflow(String workflowId) {
    WorkflowInstance workflowInstance = workflowRepository.findRequired(workflowId);
    if (workflowInstance.getStatus() != WorkflowStatus.HUMAN_PROCESSING
        && workflowInstance.getStatus() != WorkflowStatus.TERMINATED) {
        throw new IllegalStateException("Only HUMAN_PROCESSING or TERMINATED workflows support retry");
    }
    workflowInstance.setStatus(WorkflowStatus.RUNNING);
    workflowInstance.setGmtModified(clock.instant());
    workflowRepository.update(workflowInstance);
    workflowExecutionScheduler.schedule(workflowId);
}
```

- [ ] **Step 4: Run runtime and integration tests**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-core,featherflow-integration-tests -am \
  -Dtest=WorkflowRuntimeFlowTest,WorkflowBestPracticeScenariosTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/WorkflowEngine.java \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/DefaultWorkflowRuntimeService.java \
  featherflow-core/src/test/java/com/ywz/workflow/featherflow/service/WorkflowRuntimeFlowTest.java \
  featherflow-integration-tests/src/test/java/com/ywz/workflow/featherflow/service/WorkflowBestPracticeScenariosTest.java
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "refactor: derive retry policy from failed attempt history"
```

---

### Task 5: Rework Activity IDs, Skip Semantics, and Node Recording

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/WorkflowEngine.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/daemon/DefaultWorkflowOperationHandlerTest.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/daemon/WorkflowOperationDaemonTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java`

- [ ] **Step 1: Write failing tests for skip-as-new-attempt and unique attempt IDs**

```java
@Test
void skipShouldAppendSuccessfulAttemptInsteadOfMutatingFailedRow() {
    workflowEngine.skipActivity(workflowId, "{\"operator\":\"ops\"}");

    List<ActivityInstance> activities = activityRepository.findByWorkflowId(workflowId);
    assertThat(activities)
        .filteredOn(activity -> activity.getActivityName().equals("riskReview"))
        .hasSize(2);
    assertThat(activities)
        .filteredOn(activity -> activity.getActivityName().equals("riskReview"))
        .anyMatch(activity -> activity.getStatus() == ActivityExecutionStatus.FAILED);
    assertThat(activities)
        .filteredOn(activity -> activity.getActivityName().equals("riskReview"))
        .anyMatch(activity -> activity.getStatus() == ActivityExecutionStatus.SUCCESSFUL);
}
```

- [ ] **Step 2: Generate per-attempt activity IDs**

```java
private String buildActivityId(String workflowId, int sequence, long attempt) {
    return workflowId
        + "-"
        + String.format("%02d", Integer.valueOf(sequence))
        + "-"
        + String.format("%02d", Long.valueOf(attempt));
}
```

Attempt source:

- `attempt = failedCount + 1` before inserting a new row for that activity

- [ ] **Step 3: Write `executed_node` for every attempt, including skip**

```java
activityRepository.saveAttempt(
    activityId,
    workflowInstance.getWorkflowId(),
    activityDefinition.getName(),
    nodeIdentity.currentNode(),
    workflowContext,
    output,
    ActivityExecutionStatus.SUCCESSFUL,
    executeTime
);
```

Skip path:

```java
activityRepository.saveAttempt(
    buildActivityId(workflowId, activitySequence, attempt),
    workflowId,
    target.getActivityName(),
    nodeIdentity.currentNode(),
    target.getInput(),
    skipOutput,
    ActivityExecutionStatus.SUCCESSFUL,
    clock.instant()
);
```

- [ ] **Step 4: Run focused engine/daemon tests**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-core \
  -Dtest=WorkflowEngineTest,DefaultWorkflowOperationHandlerTest,WorkflowOperationDaemonTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/WorkflowEngine.java \
  featherflow-core/src/test/java/com/ywz/workflow/featherflow/daemon/DefaultWorkflowOperationHandlerTest.java \
  featherflow-core/src/test/java/com/ywz/workflow/featherflow/daemon/WorkflowOperationDaemonTest.java
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "refactor: append skip and retry attempts to activity history"
```

---

### Task 6: Align Ops Console, Demo Data, and Documentation

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/schema.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/demo-data.sql`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/repository/WorkflowViewRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/README.md`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/README.en.md`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/README.md`
- Modify tests under:
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/`
  - `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/resources/sql/`

- [ ] **Step 1: Write failing ops-console tests against the new schema**

```sql
insert into workflow_instance (
    workflow_id, biz_id, workflow_name, start_node, gmt_created, gmt_modified, input, status
) values (
    'wf-running-0001', 'biz-001', 'orderWorkflow', '10.9.8.7:host-a:1234:seed',
    timestamp '2026-04-02 10:00:00', timestamp '2026-04-02 10:05:00',
    '{}', 'RUNNING'
);
```

And activity fixture:

```sql
insert into activity_instance (
    activity_id, workflow_id, activity_name, executed_node, gmt_created, gmt_modified, input, output, status
) values (
    'wf-running-0001-01-01', 'wf-running-0001', 'createOrder', '10.9.8.7:host-a:1234:seed',
    timestamp '2026-04-02 10:00:01', timestamp '2026-04-02 10:00:01',
    '{}', '{\"created\":true}', 'SUCCESSFUL'
);
```

- [ ] **Step 2: Remove `ext_col` from ops-console queries and select `start_node`**

```java
private static final String DETAIL_SQL =
    "select w.workflow_id, w.biz_id, w.workflow_name, w.start_node, w.gmt_created, w.gmt_modified, "
        + "w.input, w.status "
        + "from workflow_instance w where w.workflow_id = ?";
```

- [ ] **Step 3: Update docs to reflect the final persistence model**

```markdown
- `workflow_instance.start_node` stores the node that created the workflow.
- `activity_instance.executed_node` stores the node that executed each attempt.
- `activity_instance` now stores one row per execution attempt.
- Automatic retry budget is derived from persisted `FAILED` attempts for the same `workflow_id + activity_name`.
```

- [ ] **Step 4: Run module and full-repo verification**

Run:

```bash
/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -pl featherflow-ops-console \
  -Dmaven.repo.local=/tmp/m2repo test

/tmp/apache-maven-3.9.9/bin/mvn -q -f /Users/yangwenzhuo/Code/Codex/featherflow/pom.xml \
  -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- both commands PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/yangwenzhuo/Code/Codex/featherflow add \
  featherflow-ops-console/src/main/resources/schema.sql \
  featherflow-ops-console/src/main/resources/demo-data.sql \
  featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/repository/WorkflowViewRepository.java \
  featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java \
  README.md \
  README.en.md \
  featherflow-ops-console/README.md
git -C /Users/yangwenzhuo/Code/Codex/featherflow commit -m "docs: align console and persistence model with attempt history"
```

---

## Self-Review

Spec coverage check:

- remove `ext_col`: covered by Tasks 1, 2, and 6
- add `workflow_instance.start_node`: covered by Tasks 1, 2, and 6
- add `activity_instance.executed_node`: covered by Tasks 3, 5, and 6
- keep `(workflow_id, activity_name)` index: covered by Tasks 1 and 6 schema updates
- add workflow name index: covered by Tasks 1 and 2 schema updates
- one row per attempt: covered by Tasks 3 and 5
- retry derived from `FAILED` history without reset: covered by Task 4
- skip as appended successful attempt: covered by Task 5

Placeholder scan:

- no `TBD`, `TODO`, or implicit "handle later" steps remain
- each task contains concrete file paths, commands, and code snippets

Type consistency check:

- `startNode` is the workflow field used consistently across model/repository/docs
- `executedNode` is the activity field used consistently across model/repository/docs
- `saveAttempt(...)`, `findLatestByWorkflowIdAndActivityName(...)`, and `countByWorkflowIdAndActivityNameAndStatus(...)` are used consistently after introduction

