# FeatherFlow Ops Console

FeatherFlow Ops Console 是一个面向 FeatherFlow 的轻量级运维控制台。

它采用单体架构，使用 `Spring Boot + Thymeleaf + HTMX + JdbcTemplate` 实现，不做前后端分离，不新增额外表，直接连接 FeatherFlow 的数据库，读取并操作以下三张核心表：

- `workflow_instance`
- `activity_instance`
- `workflow_operation`

其中页面上的 `workflowName` 直接读取 `workflow_instance.workflow_name`，工作流启动节点展示 `workflow_instance.start_node`，每个 activity 尝试的执行节点展示 `activity_instance.executed_node`。

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
- `activity_instance` 采用 append-only 历史模式，每次 activity 完成后都会新增一条成功或失败记录，自动重试预算通过同一 `workflow_id + activity_name` 下的 `FAILED` 历史记录数推导

## 页面入口

Ops Console 没有配置额外的 `context-path`，所以完整访问地址按下面这个规则拼：

```text
http://<机器 IP 或域名>:<端口><页面路径>
```

如果通过 Jar 启动时指定了 `--server.port=8080`，并且是在本机浏览器访问，入口就是：

```text
http://127.0.0.1:8080/workflows
```

如果部署平台把服务暴露在 `80` 端口，浏览器里通常访问：

```text
http://<服务域名或机器 IP>/workflows
```

- `/workflows`
  工作流列表页，也是运维控制台的主入口。这里可以按 `workflowId`、`bizId`、状态、名称和时间范围检索工作流，并直接进入详情页或发起可用的运维动作。
- `/workflows/{workflowId}`
  工作流详情页。把 `{workflowId}` 替换成真实工作流 ID，例如 `http://127.0.0.1:8080/workflows/demo-human-0001`。详情页会展示 workflow 基础信息、activity 执行时间线、每一步的执行节点、输入输出和相关 operation 历史。
- `/operations`
  操作历史页。用于查看外部运维命令写入 `workflow_operation` 后的消费状态，包括 `PENDING`、`PROCESSING`、`SUCCESSFUL`、`FAILED` 等状态。

常用访问示例：

```bash
curl -i http://127.0.0.1:8080/workflows
curl -i http://127.0.0.1:8080/operations
```

浏览器建议优先打开 `/workflows`，它是日常排查工作流状态和进入详情页的主入口。

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

## 配置 Profiles

Ops Console 现在内置三套清晰分离的配置：

- `mysql`
  默认运行 profile，用于正式连接 FeatherFlow MySQL 数据库，不自动初始化表结构和演示数据
- `h2`
  本地页面预览 profile，使用内存 H2，并自动加载 `schema.sql` 和 `demo-data.sql`
- `test`
  单元测试 profile，使用隔离的内存 H2，测试数据由测试用例自己准备

## 启动命令

本地快速预览页面，使用内置 H2 和演示数据：

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

连接真实 MySQL 数据库，使用默认 `mysql` profile：

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console
export FEATHERFLOW_OPS_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/featherflow?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export FEATHERFLOW_OPS_DATASOURCE_USERNAME='your_user'
export FEATHERFLOW_OPS_DATASOURCE_PASSWORD='your_password'
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run
```

如果需要打包后以 Jar 方式启动：

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -pl featherflow-ops-console -am package -DskipTests
java -jar featherflow-ops-console/target/featherflow-ops-console-0.0.1-SNAPSHOT.jar
```

如果需要修改端口：

```bash
java -jar featherflow-ops-console/target/featherflow-ops-console-0.0.1-SNAPSHOT.jar --server.port=8081
```

Maven 启动时也可以这样指定端口：

```bash
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=8081'
```

## 本地 H2 预览

本地只想快速看页面时，显式启用 `h2` profile：

```bash
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

启动后可访问：

- [http://localhost:8080/workflows](http://localhost:8080/workflows)
- [http://localhost:8080/operations](http://localhost:8080/operations)

`h2` profile 会自动加载一组演示数据，页面启动后即可直接看到：

- 一个 `RUNNING` 的 workflow
- 一个 `COMPLETED` 的 workflow
- 一个 `HUMAN_PROCESSING` 的 workflow

这样你可以立刻点进详情页查看 activity 时间线、`input/output` 和 operation 历史。

当前采用的是“方案 3”：

- [schema.sql](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/schema.sql) 只负责建表
- [demo-data.sql](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/demo-data.sql) 只负责默认演示数据
- [application-h2.yml](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/application-h2.yml) 显式指定这两份脚本

## 连接真实 FeatherFlow 数据库

正式使用时使用默认 `mysql` profile，让运维台连接到 FeatherFlow 实际使用的数据库。

演示数据只会在 `h2` profile 下自动加载；切换到 MySQL 后，`spring.sql.init.mode=never`，不会自动建表或写入样例数据。

默认 MySQL 配置在 [application-mysql.yml](/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/application-mysql.yml)，正式环境推荐通过环境变量覆盖：

```bash
export FEATHERFLOW_OPS_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/featherflow?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export FEATHERFLOW_OPS_DATASOURCE_USERNAME='your_user'
export FEATHERFLOW_OPS_DATASOURCE_PASSWORD='your_password'
```

然后使用：

```bash
/tmp/apache-maven-3.9.9/bin/mvn spring-boot:run
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
- 当前项目的运行元数据统一落到显式字段中，不再保留额外的扩展元数据列
- 当前项目是运维台，不直接执行工作流逻辑，只负责展示和写入运维命令
