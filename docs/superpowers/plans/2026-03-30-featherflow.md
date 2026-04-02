# FeatherFlow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight Java workflow framework with persistent execution state, YAML/XML workflow definitions, retry/terminate/skip operations, Spring Boot starter integration, and comprehensive tests.

**Architecture:** Use a Maven multi-module project with a pure `featherflow-core` engine and a `featherflow-spring-boot-starter` integration module. Keep the first version intentionally linear and single-node, with persistence through `JdbcTemplate`, a daemon scanner over `workflow_operation`, and TDD-driven implementation.

**Tech Stack:** Java, Maven, Spring Boot starter autoconfiguration, Spring JDBC, Jackson, SnakeYAML, JUnit 5, H2

---

### Task 1: Bootstrap The Maven Project

**Files:**
- Create: `pom.xml`
- Create: `featherflow-core/pom.xml`
- Create: `featherflow-spring-boot-starter/pom.xml`
- Create: `README.md`

- [ ] **Step 1: Write the failing build structure check**

```text
The first build should fail because module files do not exist yet.
```

- [ ] **Step 2: Create the parent POM and child module POMs**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.ywz.workflow</groupId>
  <artifactId>featherflow</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>pom</packaging>
  <modules>
    <module>featherflow-core</module>
    <module>featherflow-spring-boot-starter</module>
  </modules>
</project>
```

- [ ] **Step 3: Run the reactor build**

Run: `mvn -q test`
Expected: fail first on missing source/test files or pass once structure is complete

### Task 2: Define Domain Model And Parser Tests First

**Files:**
- Create: `featherflow-core/src/test/java/com/ywz/workflow/featherflow/definition/WorkflowDefinitionParserTest.java`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/definition/...`

- [ ] **Step 1: Write failing tests for YAML and XML parsing**

```java
@Test
void shouldParseYamlDefinition() { }

@Test
void shouldParseXmlDefinition() { }
```

- [ ] **Step 2: Run only parser tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowDefinitionParserTest test`
Expected: FAIL because parser classes do not exist

- [ ] **Step 3: Implement the minimal definition model and parser**

```java
public final class WorkflowDefinition {
    private final String name;
    private final List<ActivityDefinition> activities;
}
```

- [ ] **Step 4: Re-run parser tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowDefinitionParserTest test`
Expected: PASS

### Task 3: Drive Workflow Command Service And Repository Contracts With Tests

**Files:**
- Create: `featherflow-core/src/test/java/com/ywz/workflow/featherflow/service/WorkflowCommandServiceTest.java`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/service/...`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/...`

- [ ] **Step 1: Write failing tests for start, retry, terminate, and skip semantics**

```java
@Test
void shouldGenerateWorkflowIdAndDefaultBizIdOnStart() { }

@Test
void shouldOnlyAllowRetryFromHumanProcessing() { }

@Test
void shouldOnlyAllowSkipFromTerminatedWorkflow() { }
```

- [ ] **Step 2: Run the command service tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowCommandServiceTest test`
Expected: FAIL because services and repositories are missing

- [ ] **Step 3: Implement the service contracts with in-memory fakes for tests**

```java
public interface WorkflowCommandService {
    StartWorkflowResult startWorkflow(String definitionName, String bizId, String input);
}
```

- [ ] **Step 4: Re-run command service tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowCommandServiceTest test`
Expected: PASS

### Task 4: Build Engine Retry And Idempotency Behavior Through Tests

**Files:**
- Create: `featherflow-core/src/test/java/com/ywz/workflow/featherflow/engine/WorkflowEngineTest.java`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/engine/...`

- [ ] **Step 1: Write failing tests for successful execution, retry scheduling, human processing, and idempotency**

```java
@Test
void shouldExecuteActivitiesSequentiallyAndFinishWorkflow() { }

@Test
void shouldScheduleRetryWhenActivityFails() { }

@Test
void shouldMoveWorkflowToHumanProcessingAfterRetryExhausted() { }

@Test
void shouldReuseSuccessfulActivityOutputForIdempotency() { }
```

- [ ] **Step 2: Run the engine tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowEngineTest test`
Expected: FAIL because engine classes are missing

- [ ] **Step 3: Implement the minimal workflow engine and safe activity executor**

```java
public final class WorkflowEngine {
    public void continueWorkflow(String workflowId) { }
}
```

- [ ] **Step 4: Re-run the engine tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowEngineTest test`
Expected: PASS

### Task 5: Add Daemon And Operation Scanner Tests

**Files:**
- Create: `featherflow-core/src/test/java/com/ywz/workflow/featherflow/daemon/WorkflowOperationDaemonTest.java`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/daemon/...`

- [ ] **Step 1: Write failing tests for pending operation polling and safe processing**

```java
@Test
void shouldProcessPendingStartAndRetryOperations() { }

@Test
void shouldCatchThrowableFromDaemonTasks() { }
```

- [ ] **Step 2: Run daemon tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowOperationDaemonTest test`
Expected: FAIL because daemon classes do not exist

- [ ] **Step 3: Implement daemon scheduler and operation dispatcher**

```java
public final class WorkflowOperationDaemon {
    public void pollOnce() { }
}
```

- [ ] **Step 4: Re-run daemon tests**

Run: `mvn -q -pl featherflow-core -Dtest=WorkflowOperationDaemonTest test`
Expected: PASS

### Task 6: Add JDBC Repositories And H2 Integration Tests

**Files:**
- Create: `featherflow-core/src/test/java/com/ywz/workflow/featherflow/repository/JdbcRepositoryIntegrationTest.java`
- Create: `featherflow-core/src/main/java/com/ywz/workflow/featherflow/repository/jdbc/...`
- Create: `featherflow-core/src/test/resources/schema.sql`

- [ ] **Step 1: Write failing H2 integration tests for workflow, activity, and operation persistence**

```java
@Test
void shouldPersistAndLoadWorkflowRecords() { }
```

- [ ] **Step 2: Run H2 integration tests**

Run: `mvn -q -pl featherflow-core -Dtest=JdbcRepositoryIntegrationTest test`
Expected: FAIL because JDBC repositories do not exist

- [ ] **Step 3: Implement JdbcTemplate repositories and SQL schema**

```java
public final class JdbcWorkflowRepository implements WorkflowRepository { }
```

- [ ] **Step 4: Re-run H2 integration tests**

Run: `mvn -q -pl featherflow-core -Dtest=JdbcRepositoryIntegrationTest test`
Expected: PASS

### Task 7: Add Spring Boot Starter Autoconfiguration Tests

**Files:**
- Create: `featherflow-spring-boot-starter/src/test/java/com/ywz/workflow/featherflow/starter/FeatherFlowAutoConfigurationTest.java`
- Create: `featherflow-spring-boot-starter/src/main/java/com/ywz/workflow/featherflow/starter/...`
- Create: `featherflow-spring-boot-starter/src/main/resources/META-INF/spring/...`

- [ ] **Step 1: Write failing autoconfiguration tests**

```java
@Test
void shouldAutoConfigureCoreBeansWhenJdbcTemplateExists() { }
```

- [ ] **Step 2: Run starter tests**

Run: `mvn -q -pl featherflow-spring-boot-starter -Dtest=FeatherFlowAutoConfigurationTest test`
Expected: FAIL because autoconfiguration classes do not exist

- [ ] **Step 3: Implement properties, autoconfiguration, and definition resource loading**

```java
@Configuration
public class FeatherFlowAutoConfiguration { }
```

- [ ] **Step 4: Re-run starter tests**

Run: `mvn -q -pl featherflow-spring-boot-starter -Dtest=FeatherFlowAutoConfigurationTest test`
Expected: PASS

### Task 8: Finish Documentation And Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-03-30-featherflow-design.md`
- Modify: `docs/superpowers/plans/2026-03-30-featherflow.md`

- [ ] **Step 1: Document quick start, schema SQL, and YAML/XML examples**

```markdown
## Quick Start
1. Add the starter dependency
2. Implement activity handler beans
3. Place workflow YAML/XML under configured resources
```

- [ ] **Step 2: Run the full project build**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 3: Review generated artifacts and file layout**

Run: `find . -maxdepth 3 | sort`
Expected: project contains parent pom, two modules, docs, and test resources
