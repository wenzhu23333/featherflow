# FeatherFlow

[English README](./README.en.md)

FeatherFlow 是一个自研轻量级 Java 工作流框架，提供持久化运行状态、YAML/XML 工作流定义、重试与人工干预控制，以及 Spring Boot Starter 接入能力。

## 模块说明

- `featherflow-core`
  - 工作流模型、定义解析、执行引擎、重试逻辑、守护线程扫描、锁与幂等、仓储接口和测试。
- `featherflow-spring-boot-starter`
  - Spring Boot 自动装配、资源加载、默认 JDBC 仓储实现和守护线程生命周期管理。
- `featherflow-spring-boot-demo`
  - 一个可直接启动的 Spring Boot 示例工程，演示 Handler、YAML 工作流定义、REST 接口和 H2 本地运行方式。
- `featherflow-ops-console`
  - 一个轻量级运维控制台，使用 `Spring Boot + Thymeleaf + HTMX` 直接连接 FeatherFlow 数据库，展示 workflow / activity / operation 状态，并通过写入 `workflow_operation` 发起运维动作。

## 功能特性

- 支持顺序编排的工作流执行。
- 支持 YAML 和 XML 两种工作流定义格式。
- 持久化 `workflow_instance`、`activity_instance` 和 `workflow_operation` 三张核心表。
- 每个 Activity 支持独立的重试间隔和最大重试次数。
- Activity 执行失败时将异常信息写入 `activity_instance.output`。
- 重试耗尽后进入 `HUMAN_PROCESSING`，支持人工重试。
- 本地 `start/retry/terminate/skip` 直接走运行时服务；`workflow_operation` 仅用于外部运维系统下发命令。
- 默认使用数据库锁和幂等检查避免同一 Activity 被并发重复执行。
- 提供 Spring Boot Starter，方便业务系统快速接入。

## Maven 依赖

```xml
<dependency>
    <groupId>com.ywz.workflow</groupId>
    <artifactId>featherflow-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Spring Boot Demo

仓库里提供了一个最小可运行示例模块：

- `featherflow-spring-boot-demo`

这个 demo 展示了：

- 如何通过 starter 自动装配接入 FeatherFlow
- 如何编写 `WorkflowActivityHandler`
- 如何放置 YAML 工作流定义
- 如何通过 HTTP 调用 `start / terminate / retry / skip`
- 如何通过 `workflowId` / `bizId` 串联业务日志

启动命令：

```bash
mvn -q -pl featherflow-spring-boot-demo -am spring-boot:run
```

启动工作流：

```bash
curl -X POST http://localhost:8080/demo/workflows/start \
  -H 'Content-Type: application/json' \
  -d '{"bizId":"demo-biz-001","amount":100,"customerName":"Alice"}'
```

查询工作流：

```bash
curl http://localhost:8080/demo/workflows/{workflowId}
```

终止与重试：

```bash
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/terminate
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/retry
```

跳过最新步骤：

```bash
curl -X POST http://localhost:8080/demo/workflows/{workflowId}/skip
```

说明：

- `skip` 仅在工作流已经是 `TERMINATED` 时允许。
- 如果想观察失败与重试，可以在启动请求里加入 `"forceNotifyFailure": true`。
- demo 使用 H2 内存库和框架自带建表 SQL，无需手工准备数据库。
- demo 源码入口在 `featherflow-spring-boot-demo/src/main/java/com/ywz/workflow/featherflow/demo`。

## Ops Console

仓库内置了一个运维控制台模块：

- `featherflow-ops-console`

这个模块提供：

- 工作流列表页 `/workflows`
- 工作流详情页 `/workflows/{workflowId}`
- 操作历史页 `/operations`
- 在列表页和详情页直接发起 `terminate / retry / skip latest activity`

启动命令：

```bash
mvn -q -pl featherflow-ops-console -am spring-boot:run
```

说明：

- 默认使用 H2 内存库和内置 `schema.sql`，适合本地页面预览。
- 正式环境下可以通过 `spring.datasource.*` 连接真实的 FeatherFlow 数据库。
- 运维台不会直接修改核心状态表来驱动动作，而是统一向 `workflow_operation` 写入运维命令。

## 数据库表结构

参考 SQL 文件：

- `featherflow-core/src/main/resources/db/featherflow-h2.sql`
- `featherflow-core/src/main/resources/db/featherflow-mysql.sql`

## Spring Boot 配置

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

配置说明：

- `enabled`：是否启用 FeatherFlow。
- `auto-start-daemon`：是否自动启动扫描 `workflow_operation` 的守护线程，供外部运维命令消费使用。
- `definition-locations`：工作流定义文件加载路径。
- `instance-id`：可选的实例标识，建议配置成 `IP:节点名` 或 `IP:服务名`；不配置时默认生成 `IP:hostname:PID:随机串`。
- `persistence-write-retry-max-attempts`：框架关键写库的最大重试次数。
- `persistence-write-retry-initial-delay-millis`：第一次重试前的等待时间。
- `persistence-write-retry-max-delay-millis`：指数退避的最大等待时间上限。
- 守护线程只负责认领并调度外部写入的 `workflow_operation`；本地 API 直接把 workflow 投递到执行线程池。
- 工作流真正的推进在线程池中完成；同一个执行线程会顺序完成 activity 业务逻辑和后续状态机流转。
- Activity 自动重试使用框架内部延迟调度器，不写 `workflow_operation`。
- 框架自己的关键写库默认会对瞬时性数据库异常做有限重试；若重试耗尽仍失败，会记录高优先级错误日志并按当前已落库状态结束当前线程。

## 工作流定义

YAML 示例：

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

XML 示例：

```xml
<workflow name="sampleOrderWorkflow">
  <activity name="createOrder" handler="createOrderHandler" retryInterval="PT5S" maxRetryTimes="2"/>
  <activity name="notifyCustomer" handler="notifyCustomerHandler" retryInterval="PT10S" maxRetryTimes="1"/>
</workflow>
```

字段说明：

- `name`：工作流或步骤名称。
- `handler`：Spring 容器中的 Activity Handler Bean 名称。
- `retryInterval`：失败后的重试间隔，使用 `Duration` 格式。
- `maxRetryTimes`：最大重试次数，超过后进入人工处理状态。

## Activity 处理器

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

说明：

- Handler 输入输出都是上下文 `Map<String, Object>`。
- 返回值会被序列化后写入当前步骤的 `output`，并作为后续步骤的上下文基础。
- `activity_instance` 只会在步骤执行结束后落库，成功写 `SUCCESSFUL`，失败写 `FAILED`。
- Handler 无论抛出 `Exception` 还是 `Error`，引擎都会按失败处理，记录失败输出，并继续走重试或 `HUMAN_PROCESSING` 判定。
- FeatherFlow 会在执行线程和守护线程中自动写入 `MDC` 的 `workflowId` 与 `bizId`，业务 Handler 内的日志也可以直接复用这两个字段。

## 日志串联

如果你希望通过日志快速串起整条工作流链路，建议把日志格式中加入 `workflowId` 和 `bizId`。

Spring Boot `application.yml` 示例：

```yaml
logging:
  pattern:
    level: "%5p [workflowId:%X{workflowId:-}] [bizId:%X{bizId:-}]"
```

如果你希望在 `startWorkflow()` 返回之后，当前业务线程继续打印同一条链路上的日志，可以显式打开一次日志上下文：

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

## 命令服务使用示例

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

接口语义：

- `startWorkflow`：同步写入工作流实例，然后直接把执行任务放入线程池；若调度失败会直接抛错。
- `terminateWorkflow`：本地 API 直接把工作流改成 `TERMINATED`；引擎在下一个步骤前检查状态并停止。外部运维系统也可以写入 `TERMINATE` operation，由守护线程消费后执行同样的逻辑。
- `retryWorkflow`：仅 `HUMAN_PROCESSING` 或 `TERMINATED` 状态允许，本地 API 会直接恢复为 `RUNNING` 并调度到统一执行线程池；恢复上下文直接取最新 activity 的持久化快照，若最新 activity 成功则复用其 `output`，若失败则复用其 `input`。外部运维系统也可以写入 `RETRY` operation，由守护线程消费后执行同样的逻辑。
- `skipActivity`：默认跳过当前最新一条 activity，仅 `TERMINATED` 状态允许。
- `workflow_operation.status`：`PENDING -> PROCESSING -> SUCCESSFUL/FAILED`，仅表示外部操作命令的消费状态，不代表整个工作流是否成功。

## 构建方式

```bash
mvn test
```

如果本地没有全局 Maven，可使用项目里的 Maven settings 配合本地 Maven 二进制执行。
