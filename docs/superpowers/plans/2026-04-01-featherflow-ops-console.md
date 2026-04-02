# FeatherFlow Ops Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lightweight single-project operations console that reads FeatherFlow workflow data from the database, shows workflow and activity execution clearly, and submits common operations through `workflow_operation`.

**Architecture:** Create a separate Spring Boot monolith project at `featherflow-ops-console` using Thymeleaf and HTMX. The server owns all database access through `JdbcTemplate`, reads only the existing three FeatherFlow tables, and writes only `workflow_operation` for start, terminate, retry, and skip actions.

**Tech Stack:** Java 17, Spring Boot, Thymeleaf, HTMX, Spring JDBC, Jackson, H2 for tests, JUnit 5, MockMvc

---

### Task 1: Bootstrap The New Ops Console Project

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/pom.xml`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/FeatherFlowOpsConsoleApplication.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/application.yml`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/resources/application-test.yml`

- [ ] **Step 1: Write the failing bootstrap test**

```java
package com.ywz.workflow.featherflow.ops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FeatherFlowOpsConsoleApplication.class)
class FeatherFlowOpsConsoleApplicationTest {

    @Test
    void shouldLoadApplicationContext() {
    }
}
```

- [ ] **Step 2: Run the bootstrap test to verify it fails**

Run: `mvn -q -Dtest=FeatherFlowOpsConsoleApplicationTest test`
Expected: FAIL because `FeatherFlowOpsConsoleApplication` and the project structure do not exist yet

- [ ] **Step 3: Create the project skeleton and minimal Boot application**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.ywz.workflow</groupId>
    <artifactId>featherflow-ops-console</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>17</java.version>
        <spring.boot.version>3.3.0</spring.boot.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

```java
package com.ywz.workflow.featherflow.ops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FeatherFlowOpsConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatherFlowOpsConsoleApplication.class, args);
    }
}
```

```yaml
spring:
  application:
    name: featherflow-ops-console
  thymeleaf:
    cache: false
```

- [ ] **Step 4: Run the bootstrap test again**

Run: `mvn -q -Dtest=FeatherFlowOpsConsoleApplicationTest test`
Expected: PASS

- [ ] **Step 5: Commit the bootstrap**

```bash
git add pom.xml src/main/java src/main/resources src/test
git commit -m "feat: bootstrap featherflow ops console"
```

### Task 2: Build Repository Queries And Workflow List Page

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/repository/WorkflowViewRepository.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/WorkflowListItemView.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/AllowedActionsView.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/list.html`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/list-table.html`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/resources/sql/featherflow-schema.sql`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/resources/sql/workflow-list-data.sql`

- [ ] **Step 1: Write the failing list page test**

```java
package com.ywz.workflow.featherflow.ops.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-list-data.sql"})
class WorkflowListPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderWorkflowListWithActions() throws Exception {
        mockMvc.perform(get("/workflows"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("wf-running-0001")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("终止")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("重试")));
    }
}
```

- [ ] **Step 2: Run the list page test to verify it fails**

Run: `mvn -q -Dtest=WorkflowListPageTest test`
Expected: FAIL because repositories, service, controller, and templates are missing

- [ ] **Step 3: Implement the repository and list view aggregation**

```java
package com.ywz.workflow.featherflow.ops.view;

public class AllowedActionsView {

    private final boolean canTerminate;
    private final boolean canRetry;
    private final boolean canSkipLatest;

    public AllowedActionsView(boolean canTerminate, boolean canRetry, boolean canSkipLatest) {
        this.canTerminate = canTerminate;
        this.canRetry = canRetry;
        this.canSkipLatest = canSkipLatest;
    }

    public boolean isCanTerminate() {
        return canTerminate;
    }

    public boolean isCanRetry() {
        return canRetry;
    }

    public boolean isCanSkipLatest() {
        return canSkipLatest;
    }
}
```

```java
package com.ywz.workflow.featherflow.ops.view;

import java.time.Instant;

public class WorkflowListItemView {

    private final String workflowId;
    private final String bizId;
    private final String workflowName;
    private final String workflowStatus;
    private final String latestActivityId;
    private final String latestActivityName;
    private final String latestActivityStatus;
    private final String latestFailureSummary;
    private final Instant gmtModified;
    private final AllowedActionsView allowedActions;

    public WorkflowListItemView(
        String workflowId,
        String bizId,
        String workflowName,
        String workflowStatus,
        String latestActivityId,
        String latestActivityName,
        String latestActivityStatus,
        String latestFailureSummary,
        Instant gmtModified,
        AllowedActionsView allowedActions
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.workflowName = workflowName;
        this.workflowStatus = workflowStatus;
        this.latestActivityId = latestActivityId;
        this.latestActivityName = latestActivityName;
        this.latestActivityStatus = latestActivityStatus;
        this.latestFailureSummary = latestFailureSummary;
        this.gmtModified = gmtModified;
        this.allowedActions = allowedActions;
    }

    public String getWorkflowId() { return workflowId; }
    public String getBizId() { return bizId; }
    public String getWorkflowName() { return workflowName; }
    public String getWorkflowStatus() { return workflowStatus; }
    public String getLatestActivityId() { return latestActivityId; }
    public String getLatestActivityName() { return latestActivityName; }
    public String getLatestActivityStatus() { return latestActivityStatus; }
    public String getLatestFailureSummary() { return latestFailureSummary; }
    public Instant getGmtModified() { return gmtModified; }
    public AllowedActionsView getAllowedActions() { return allowedActions; }
}
```

```java
package com.ywz.workflow.featherflow.ops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.ops.repository.WorkflowViewRepository;
import com.ywz.workflow.featherflow.ops.view.AllowedActionsView;
import com.ywz.workflow.featherflow.ops.view.WorkflowListItemView;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkflowQueryService {

    private final WorkflowViewRepository workflowViewRepository;
    private final ObjectMapper objectMapper;

    public WorkflowQueryService(WorkflowViewRepository workflowViewRepository, ObjectMapper objectMapper) {
        this.workflowViewRepository = workflowViewRepository;
        this.objectMapper = objectMapper;
    }

    public List<WorkflowListItemView> listWorkflows() {
        return workflowViewRepository.findWorkflowList();
    }

    public String extractWorkflowName(String extCol) {
        try {
            Map<String, Object> metadata = objectMapper.readValue(extCol, new TypeReference<Map<String, Object>>() {});
            Object definitionName = metadata.get("definitionName");
            return definitionName == null ? "-" : String.valueOf(definitionName);
        } catch (Exception ex) {
            return "-";
        }
    }

    public AllowedActionsView buildAllowedActions(String workflowStatus, String latestActivityId) {
        boolean canTerminate = "RUNNING".equals(workflowStatus);
        boolean canRetry = "HUMAN_PROCESSING".equals(workflowStatus) || "TERMINATED".equals(workflowStatus);
        boolean canSkipLatest = "TERMINATED".equals(workflowStatus) && latestActivityId != null;
        return new AllowedActionsView(canTerminate, canRetry, canSkipLatest);
    }
}
```

```java
package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkflowPageController {

    private final WorkflowQueryService workflowQueryService;

    public WorkflowPageController(WorkflowQueryService workflowQueryService) {
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/workflows")
    public String workflows(Model model) {
        model.addAttribute("workflows", workflowQueryService.listWorkflows());
        return "workflows/list";
    }
}
```

- [ ] **Step 4: Re-run the list page test**

Run: `mvn -q -Dtest=WorkflowListPageTest test`
Expected: PASS

- [ ] **Step 5: Commit the list page**

```bash
git add src/main/java src/main/resources/templates src/test
git commit -m "feat: add workflow list page"
```

### Task 3: Build Workflow Detail Page And Activity Timeline

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/ActivityTimelineItemView.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/OperationRecordView.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/WorkflowDetailView.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/repository/WorkflowViewRepository.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail.html`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail-summary.html`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail-timeline.html`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail-operations.html`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/resources/sql/workflow-detail-data.sql`

- [ ] **Step 1: Write the failing detail page test**

```java
package com.ywz.workflow.featherflow.ops.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-detail-data.sql"})
class WorkflowDetailPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderTimelineAndOperationHistory() throws Exception {
        mockMvc.perform(get("/workflows/wf-detail-0001"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("createOrder")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("notifyCustomer")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("manual-stop")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("TERMINATED")));
    }
}
```

- [ ] **Step 2: Run the detail page test to verify it fails**

Run: `mvn -q -Dtest=WorkflowDetailPageTest test`
Expected: FAIL because detail query aggregation and templates do not exist yet

- [ ] **Step 3: Implement detail aggregation and JSON metadata parsing**

```java
package com.ywz.workflow.featherflow.ops.view;

import java.time.Instant;

public class ActivityTimelineItemView {

    private final String activityId;
    private final String activityName;
    private final String status;
    private final Instant gmtCreated;
    private final Instant gmtModified;
    private final String input;
    private final String output;
    private final String failureSummary;

    public ActivityTimelineItemView(
        String activityId,
        String activityName,
        String status,
        Instant gmtCreated,
        Instant gmtModified,
        String input,
        String output,
        String failureSummary
    ) {
        this.activityId = activityId;
        this.activityName = activityName;
        this.status = status;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.input = input;
        this.output = output;
        this.failureSummary = failureSummary;
    }

    public String getActivityId() { return activityId; }
    public String getActivityName() { return activityName; }
    public String getStatus() { return status; }
    public Instant getGmtCreated() { return gmtCreated; }
    public Instant getGmtModified() { return gmtModified; }
    public String getInput() { return input; }
    public String getOutput() { return output; }
    public String getFailureSummary() { return failureSummary; }
}
```

```java
package com.ywz.workflow.featherflow.ops.view;

import java.time.Instant;

public class OperationRecordView {

    private final Long operationId;
    private final String operationType;
    private final String status;
    private final String operator;
    private final String reason;
    private final String activityId;
    private final String rawInput;
    private final Instant gmtCreated;
    private final Instant gmtModified;

    public OperationRecordView(
        Long operationId,
        String operationType,
        String status,
        String operator,
        String reason,
        String activityId,
        String rawInput,
        Instant gmtCreated,
        Instant gmtModified
    ) {
        this.operationId = operationId;
        this.operationType = operationType;
        this.status = status;
        this.operator = operator;
        this.reason = reason;
        this.activityId = activityId;
        this.rawInput = rawInput;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
    }

    public Long getOperationId() { return operationId; }
    public String getOperationType() { return operationType; }
    public String getStatus() { return status; }
    public String getOperator() { return operator; }
    public String getReason() { return reason; }
    public String getActivityId() { return activityId; }
    public String getRawInput() { return rawInput; }
    public Instant getGmtCreated() { return gmtCreated; }
    public Instant getGmtModified() { return gmtModified; }
}
```

```java
package com.ywz.workflow.featherflow.ops.view;

import java.time.Instant;
import java.util.List;

public class WorkflowDetailView {

    private final String workflowId;
    private final String bizId;
    private final String workflowName;
    private final String workflowStatus;
    private final String workflowInput;
    private final Instant gmtCreated;
    private final Instant gmtModified;
    private final List<ActivityTimelineItemView> activities;
    private final List<OperationRecordView> operations;
    private final String latestActivityId;
    private final AllowedActionsView allowedActions;

    public WorkflowDetailView(
        String workflowId,
        String bizId,
        String workflowName,
        String workflowStatus,
        String workflowInput,
        Instant gmtCreated,
        Instant gmtModified,
        List<ActivityTimelineItemView> activities,
        List<OperationRecordView> operations,
        String latestActivityId,
        AllowedActionsView allowedActions
    ) {
        this.workflowId = workflowId;
        this.bizId = bizId;
        this.workflowName = workflowName;
        this.workflowStatus = workflowStatus;
        this.workflowInput = workflowInput;
        this.gmtCreated = gmtCreated;
        this.gmtModified = gmtModified;
        this.activities = activities;
        this.operations = operations;
        this.latestActivityId = latestActivityId;
        this.allowedActions = allowedActions;
    }

    public String getWorkflowId() { return workflowId; }
    public String getBizId() { return bizId; }
    public String getWorkflowName() { return workflowName; }
    public String getWorkflowStatus() { return workflowStatus; }
    public String getWorkflowInput() { return workflowInput; }
    public Instant getGmtCreated() { return gmtCreated; }
    public Instant getGmtModified() { return gmtModified; }
    public List<ActivityTimelineItemView> getActivities() { return activities; }
    public List<OperationRecordView> getOperations() { return operations; }
    public String getLatestActivityId() { return latestActivityId; }
    public AllowedActionsView getAllowedActions() { return allowedActions; }
}
```

- [ ] **Step 4: Re-run the detail page test**

Run: `mvn -q -Dtest=WorkflowDetailPageTest test`
Expected: PASS

- [ ] **Step 5: Commit the detail page**

```bash
git add src/main/java src/main/resources/templates src/test
git commit -m "feat: add workflow detail timeline"
```

### Task 4: Implement Operations Submission And List-Row Actions

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowOperationService.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowOperationController.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/web/OperationForm.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/fragments/operation-dialog.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/list-table.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail-summary.html`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowOperationControllerTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/resources/sql/workflow-operation-data.sql`

- [ ] **Step 1: Write the failing operations submission test**

```java
package com.ywz.workflow.featherflow.ops.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-operation-data.sql"})
class WorkflowOperationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldInsertTerminateOperation() throws Exception {
        mockMvc.perform(post("/workflows/wf-running-0001/terminate")
                .param("operator", "alice")
                .param("reason", "manual-stop"))
            .andExpect(status().is3xxRedirection());

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from workflow_operation where workflow_id = ? and operation_type = ? and status = ?",
            Integer.class,
            "wf-running-0001",
            "TERMINATE",
            "PENDING"
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the operations test to verify it fails**

Run: `mvn -q -Dtest=WorkflowOperationControllerTest test`
Expected: FAIL because the operations service and controller do not exist

- [ ] **Step 3: Implement controlled writes into `workflow_operation`**

```java
package com.ywz.workflow.featherflow.ops.web;

public class OperationForm {

    private String operator;
    private String reason;
    private String activityId;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getActivityId() { return activityId; }
    public void setActivityId(String activityId) { this.activityId = activityId; }
}
```

```java
package com.ywz.workflow.featherflow.ops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywz.workflow.featherflow.ops.web.OperationForm;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkflowOperationService {

    private final JdbcTemplate jdbcTemplate;
    private final WorkflowQueryService workflowQueryService;
    private final ObjectMapper objectMapper;

    public WorkflowOperationService(JdbcTemplate jdbcTemplate, WorkflowQueryService workflowQueryService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowQueryService = workflowQueryService;
        this.objectMapper = objectMapper;
    }

    public void submitTerminate(String workflowId, OperationForm form) {
        validateRequired(form);
        assertWorkflowStatus(workflowId, "RUNNING");
        insertOperation(workflowId, "TERMINATE", form);
    }

    public void submitRetry(String workflowId, OperationForm form) {
        validateRequired(form);
        String status = workflowQueryService.getWorkflowStatus(workflowId);
        if (!"HUMAN_PROCESSING".equals(status) && !"TERMINATED".equals(status)) {
            throw new IllegalStateException("Only HUMAN_PROCESSING or TERMINATED workflows support retry");
        }
        insertOperation(workflowId, "RETRY", form);
    }

    public void submitSkip(String workflowId, OperationForm form) {
        validateRequired(form);
        assertWorkflowStatus(workflowId, "TERMINATED");
        if (form.getActivityId() == null || form.getActivityId().trim().isEmpty()) {
            throw new IllegalArgumentException("activityId is required for skip");
        }
        if (!form.getActivityId().equals(workflowQueryService.getLatestActivityId(workflowId))) {
            throw new IllegalStateException("Skip only supports the latest activity");
        }
        insertOperation(workflowId, "SKIP_ACTIVITY", form);
    }

    private void assertWorkflowStatus(String workflowId, String expectedStatus) {
        String actualStatus = workflowQueryService.getWorkflowStatus(workflowId);
        if (!expectedStatus.equals(actualStatus)) {
            throw new IllegalStateException("Expected workflow status " + expectedStatus + " but was " + actualStatus);
        }
    }

    private void validateRequired(OperationForm form) {
        if (form.getOperator() == null || form.getOperator().trim().isEmpty()) {
            throw new IllegalArgumentException("operator is required");
        }
        if (form.getReason() == null || form.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("reason is required");
        }
    }

    private void insertOperation(String workflowId, String operationType, OperationForm form) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("operator", form.getOperator());
            payload.put("reason", form.getReason());
            if (form.getActivityId() != null && !form.getActivityId().trim().isEmpty()) {
                payload.put("activityId", form.getActivityId());
            }
            Instant now = Instant.now();
            jdbcTemplate.update(
                "insert into workflow_operation (workflow_id, operation_type, input, status, gmt_created, gmt_modified) values (?, ?, ?, ?, ?, ?)",
                workflowId,
                operationType,
                objectMapper.writeValueAsString(payload),
                "PENDING",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to insert workflow operation", ex);
        }
    }
}
```

```java
package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowOperationService;
import com.ywz.workflow.featherflow.ops.web.OperationForm;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WorkflowOperationController {

    private final WorkflowOperationService workflowOperationService;

    public WorkflowOperationController(WorkflowOperationService workflowOperationService) {
        this.workflowOperationService = workflowOperationService;
    }

    @PostMapping("/workflows/{workflowId}/terminate")
    public String terminate(@PathVariable String workflowId, OperationForm form, RedirectAttributes redirectAttributes) {
        workflowOperationService.submitTerminate(workflowId, form);
        redirectAttributes.addFlashAttribute("message", "Terminate command submitted");
        return "redirect:/workflows/" + workflowId;
    }

    @PostMapping("/workflows/{workflowId}/retry")
    public String retry(@PathVariable String workflowId, OperationForm form, RedirectAttributes redirectAttributes) {
        workflowOperationService.submitRetry(workflowId, form);
        redirectAttributes.addFlashAttribute("message", "Retry command submitted");
        return "redirect:/workflows/" + workflowId;
    }

    @PostMapping("/workflows/{workflowId}/skip")
    public String skip(@PathVariable String workflowId, OperationForm form, RedirectAttributes redirectAttributes) {
        workflowOperationService.submitSkip(workflowId, form);
        redirectAttributes.addFlashAttribute("message", "Skip command submitted");
        return "redirect:/workflows/" + workflowId;
    }
}
```

- [ ] **Step 4: Re-run the operations submission test**

Run: `mvn -q -Dtest=WorkflowOperationControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit operations submission**

```bash
git add src/main/java src/main/resources/templates src/test
git commit -m "feat: add workflow operations submission"
```

### Task 5: Add Global Operations History Page And HTMX Refresh Fragments

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/OperationHistoryController.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/operations/list.html`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/static/css/app.css`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/static/js/app.js`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/list.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/main/resources/templates/workflows/detail.html`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/OperationHistoryPageTest.java`

- [ ] **Step 1: Write the failing operations history page test**

```java
package com.ywz.workflow.featherflow.ops.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(scripts = {"/sql/featherflow-schema.sql", "/sql/workflow-detail-data.sql"})
class OperationHistoryPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderOperationsHistoryPage() throws Exception {
        mockMvc.perform(get("/operations"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("TERMINATE")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("alice")));
    }
}
```

- [ ] **Step 2: Run the operations history page test to verify it fails**

Run: `mvn -q -Dtest=OperationHistoryPageTest test`
Expected: FAIL because the page and controller do not exist yet

- [ ] **Step 3: Implement the operations page and HTMX wiring**

```java
package com.ywz.workflow.featherflow.ops.controller;

import com.ywz.workflow.featherflow.ops.service.WorkflowQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OperationHistoryController {

    private final WorkflowQueryService workflowQueryService;

    public OperationHistoryController(WorkflowQueryService workflowQueryService) {
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/operations")
    public String operations(Model model) {
        model.addAttribute("operations", workflowQueryService.listOperations());
        return "operations/list";
    }
}
```

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
  <title>FeatherFlow Ops Console</title>
  <script src="https://unpkg.com/htmx.org@1.9.12"></script>
  <link rel="stylesheet" th:href="@{/css/app.css}">
</head>
<body>
  <main class="page">
    <section hx-get="/workflows" hx-trigger="every 5s" hx-select="#workflow-table" hx-swap="outerHTML">
      <div th:replace="workflows/list-table :: workflowTable"></div>
    </section>
  </main>
</body>
</html>
```

- [ ] **Step 4: Re-run the operations history page test**

Run: `mvn -q -Dtest=OperationHistoryPageTest test`
Expected: PASS

- [ ] **Step 5: Commit the operations history page**

```bash
git add src/main/java src/main/resources src/test
git commit -m "feat: add operations history page"
```

### Task 6: Final Documentation And Verification

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow-ops-console/README.md`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/docs/superpowers/specs/2026-04-01-featherflow-ops-console-design.md`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/docs/superpowers/plans/2026-04-01-featherflow-ops-console.md`

- [ ] **Step 1: Write the project README**

```markdown
# FeatherFlow Ops Console

A lightweight operations console for FeatherFlow built with Spring Boot, Thymeleaf, and HTMX.

## Run

```bash
mvn spring-boot:run
```

## Pages

- `/workflows`
- `/workflows/{workflowId}`
- `/operations`
```
```

- [ ] **Step 2: Run the focused page tests**

Run: `mvn -q -Dtest=WorkflowListPageTest,WorkflowDetailPageTest,WorkflowOperationControllerTest,OperationHistoryPageTest test`
Expected: PASS

- [ ] **Step 3: Run the full test suite**

Run: `mvn -q test`
Expected: PASS

- [ ] **Step 4: Commit the final documentation and verification pass**

```bash
git add README.md src docs
git commit -m "docs: finalize ops console plan and docs"
```
