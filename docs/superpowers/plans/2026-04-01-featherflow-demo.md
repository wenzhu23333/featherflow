# FeatherFlow Demo Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a runnable Spring Boot demo module that shows direct `WorkflowCommandService` usage, REST-based workflow operations, and starter-based configuration with H2.

**Architecture:** Introduce a new `featherflow-spring-boot-demo` Maven module that depends only on the published starter plus standard Spring Boot web/test dependencies. Keep the demo intentionally small: one application class, one workflow YAML file, two handlers, one facade service, one controller, and a few focused integration tests.

**Tech Stack:** Java 8, Maven, Spring Boot, Spring MVC, FeatherFlow starter, H2, JUnit 5, Spring Boot Test

---

### Task 1: Add The Demo Module To The Maven Reactor

**Files:**
- Modify: `pom.xml`
- Create: `featherflow-spring-boot-demo/pom.xml`

- [ ] **Step 1: Write the failing reactor expectation**

```text
The new module is not in the parent reactor yet, so `-pl featherflow-spring-boot-demo` should fail.
```

- [ ] **Step 2: Run the targeted Maven command and verify it fails**

Run: `mvn -q -pl featherflow-spring-boot-demo test`
Expected: FAIL because the module does not exist in the reactor

- [ ] **Step 3: Add the new module and its dependencies**

```xml
<module>featherflow-spring-boot-demo</module>
```

```xml
<dependency>
    <groupId>com.ywz.workflow</groupId>
    <artifactId>featherflow-spring-boot-starter</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: Re-run the targeted Maven command**

Run: `mvn -q -pl featherflow-spring-boot-demo test`
Expected: FAIL later because source and tests are still missing

### Task 2: Add Failing Demo Integration Tests First

**Files:**
- Create: `featherflow-spring-boot-demo/src/test/java/com/ywz/workflow/featherflow/demo/FeatherFlowDemoApplicationTests.java`
- Create: `featherflow-spring-boot-demo/src/test/java/com/ywz/workflow/featherflow/demo/FeatherFlowDemoControllerTests.java`

- [ ] **Step 1: Write a failing service-style integration test**

```java
@SpringBootTest
class FeatherFlowDemoApplicationTests {

    @Test
    void shouldStartWorkflowThroughWorkflowCommandService() {
    }
}
```

- [ ] **Step 2: Write a failing REST integration test**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeatherFlowDemoControllerTests {

    @Test
    void shouldStartWorkflowThroughHttpEndpoint() {
    }
}
```

- [ ] **Step 3: Run demo tests and verify they fail for the right reason**

Run: `mvn -q -pl featherflow-spring-boot-demo -am -Dtest=FeatherFlowDemoApplicationTests,FeatherFlowDemoControllerTests test`
Expected: FAIL because the demo application classes do not exist yet

### Task 3: Implement The Demo Application And Runtime Example

**Files:**
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/FeatherFlowDemoApplication.java`
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/handler/CreateOrderHandler.java`
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/handler/NotifyCustomerHandler.java`
- Create: `featherflow-spring-boot-demo/src/main/resources/application.yml`
- Create: `featherflow-spring-boot-demo/src/main/resources/workflows/demo-order-workflow.yml`

- [ ] **Step 1: Add the minimal Spring Boot application**

```java
@SpringBootApplication
public class FeatherFlowDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeatherFlowDemoApplication.class, args);
    }
}
```

- [ ] **Step 2: Add the example handlers and YAML workflow**

```java
@Component("createOrderHandler")
public class CreateOrderHandler implements WorkflowActivityHandler {
    @Override
    public Map<String, Object> handle(Map<String, Object> context) {
        return context;
    }
}
```

- [ ] **Step 3: Add H2 and FeatherFlow configuration**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:featherflow-demo;MODE=MYSQL;DB_CLOSE_DELAY=-1
```

- [ ] **Step 4: Re-run demo tests**

Run: `mvn -q -pl featherflow-spring-boot-demo -am -Dtest=FeatherFlowDemoApplicationTests,FeatherFlowDemoControllerTests test`
Expected: FAIL later because REST adapters are still missing

### Task 4: Add Demo Service And REST Controller

**Files:**
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/service/DemoWorkflowFacade.java`
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/web/DemoWorkflowController.java`
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/web/StartWorkflowRequest.java`
- Create: `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo/web/WorkflowViewResponse.java`

- [ ] **Step 1: Implement the thin facade over `WorkflowCommandService`**

```java
public class DemoWorkflowFacade {
    public WorkflowInstance start(String bizId, String input) {
        return workflowCommandService.startWorkflow("demoOrderWorkflow", bizId, input);
    }
}
```

- [ ] **Step 2: Implement the demo controller endpoints**

```java
@RestController
@RequestMapping("/demo/workflows")
public class DemoWorkflowController {
}
```

- [ ] **Step 3: Re-run the demo tests**

Run: `mvn -q -pl featherflow-spring-boot-demo -am -Dtest=FeatherFlowDemoApplicationTests,FeatherFlowDemoControllerTests test`
Expected: PASS

### Task 5: Update Readmes And Verify The Full Build

**Files:**
- Modify: `README.md`
- Modify: `README.en.md`

- [ ] **Step 1: Document how to run the demo**

```text
Add startup command, module path, and curl examples.
```

- [ ] **Step 2: Run targeted demo verification**

Run: `mvn -q -pl featherflow-spring-boot-demo -am test`
Expected: PASS

- [ ] **Step 3: Run the full reactor verification**

Run: `mvn -q test`
Expected: PASS
