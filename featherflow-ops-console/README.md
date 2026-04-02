# FeatherFlow Ops Console

FeatherFlow Ops Console 是一个面向 FeatherFlow 的轻量级运维控制台。

它采用单体架构，使用 `Spring Boot + Thymeleaf + HTMX + JdbcTemplate` 实现，不做前后端分离，不新增额外表，直接连接 FeatherFlow 的数据库，读取并操作以下三张核心表：

- `workflow_instance`
- `activity_instance`
- `workflow_operation`

这个项目的目标是帮助运维或研发同学快速查看工作流执行状态、失败步骤、上下文快照，以及通过页面直接发起常见运维操作。

## 特性

- 工作流列表页，支持按 `workflowId`、`bizId`、`status`、`workflowName`、创建时间、更新时间筛选
- 工作流详情页，支持查看 workflow 基本信息、activity 时间线、步骤 `input/output`、失败信息
- 操作历史页，支持查看 `workflow_operation` 历史及其中的 `operator`、`reason`，以及旧记录中可能带上的 `activityId`
- 页面直接提供运维按钮：
  - `Terminate`
  - `Retry`
  - `Skip Latest Activity`
- `workflow` 列表默认每页 `10` 条，activity 时间线默认每页 `5` 条，均支持分页与页大小切换
- 所有运维动作统一写入 `workflow_operation`
- 使用 HTMX 自动刷新列表、详情摘要、详情时间线和操作历史

## 页面入口

- `/workflows`
  工作流列表页
- `/workflows/{workflowId}`
  工作流详情页
- `/operations`
  操作历史页

## 运维动作语义

当前页面不会直接修改 `workflow_instance` 或 `activity_instance` 来驱动流程，而是统一向 `workflow_operation` 写入待处理命令：

- `TERMINATE`
- `RETRY`
- `SKIP_ACTIVITY`

其中：

- `Terminate`
  只允许当前 workflow 为 `RUNNING`
- `Retry`
  只允许当前 workflow 为 `HUMAN_PROCESSING` 或 `TERMINATED`
- `Skip Latest Activity`
  只允许当前 workflow 为 `TERMINATED`，且只能跳过当前最新一条 activity

页面会要求填写：

- `operator`
- `reason`

这些信息会被编码进 `workflow_operation.input` JSON 中；`Skip Latest Activity` 默认由引擎跳过当前最新 activity，不再要求页面显式传入 `activityId`。

## 自动刷新策略

- 工作流列表：每 `5s` 自动刷新
- 工作流详情摘要：每 `3s` 自动刷新
- 工作流详情时间线：每 `3s` 自动刷新
- 操作历史：每 `5s` 自动刷新

刷新时会自动保留当前筛选条件。

## 日期筛选

列表页和操作历史页都支持日期时间筛选，格式为：

```text
yyyy-MM-dd HH:mm:ss
```

如果输入无效，页面不会静默忽略，而是会保留原始输入并展示错误提示。

## 本地启动

默认配置使用内存 H2，项目内置了三张核心表的建表脚本，适合本地开发或页面预览：

```bash
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run
```

启动后可访问：

- [http://localhost:8080/workflows](http://localhost:8080/workflows)
- [http://localhost:8080/operations](http://localhost:8080/operations)

默认内存 H2 会自动加载一组演示数据，页面启动后即可直接看到：

- 一个 `RUNNING` 的 workflow
- 一个 `SUCCESSFUL` 的 workflow
- 一个 `HUMAN_PROCESSING` 的 workflow

这样你可以立刻点进详情页查看 activity 时间线、`input/output` 和 operation 历史。

当前采用的是“方案 3”：

- [schema.sql](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/schema.sql) 只负责建表
- [demo-data.sql](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/demo-data.sql) 只负责默认演示数据
- [application.yml](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/application.yml) 显式指定这两份脚本

## 连接真实 FeatherFlow 数据库

正式使用时，请覆盖默认数据源配置，让运维台连接到 FeatherFlow 实际使用的数据库。

演示数据只会在默认嵌入式 H2 场景下自动加载；切换到 MySQL 等真实 FeatherFlow 数据库后，不会自动写入这组样例数据。

例如可以在本地新增 `src/main/resources/application-local.yml`，或通过环境变量覆盖：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/featherflow
    username: your_user
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
```

然后使用：

```bash
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 测试

运行测试：

```bash
/tmp/apache-maven-3.9.9/bin/mvn test
```

主要覆盖：

- 工作流列表筛选与行级运维按钮
- 工作流详情摘要、时间线与原始数据展示
- 运维命令校验与 `workflow_operation` 写入
- 操作历史查询与筛选

## 当前边界

- 当前项目不做登录和权限控制，建议仅部署在内网环境
- 当前项目不新增审计表，操作审计复用 `workflow_operation`
- 当前项目是运维台，不直接执行工作流逻辑，只负责展示和写入运维命令
