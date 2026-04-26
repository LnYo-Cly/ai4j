# Flowgram MySQL Task Store

这个案例解决的是“让 Flowgram 任务状态可落库、可恢复、可查询”，把默认 demo 级内存任务存储推进到平台后端能力。

## 1. 适合什么场景

- Flowgram 服务重启后仍要查任务
- 多实例部署
- 需要按任务 ID 查询历史结果
- 平台化接入任务审计、归属和报表

如果你只是演示单机工作流，内存 `TaskStore` 已经够用；如果你要做后端平台，这页才真正相关。

## 2. 技术链路

核心组合是：

- `ai4j-flowgram-spring-boot-starter`
- `JdbcFlowGramTaskStore`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `MySQL / DataSource`

重点不是前端画布，而是后端 runtime 和任务生命周期如何进入可持久化状态。

## 3. 这页和 Flowgram 主线的关系

这不是 `Flowgram.ai` 前端库文档，而是 AI4J 在 Flowgram 后端 runtime 路径上的平台化案例。

所以如果你还没建立前后端边界，先看：

- [Flowgram / Overview](/docs/flowgram/overview)
- [Flowgram / Runtime](/docs/flowgram/runtime)

## 4. 先补哪些主线页

1. [Flowgram / Runtime](/docs/flowgram/runtime)
2. [Flowgram / Frontend Backend Integration](/docs/flowgram/frontend-backend-integration)
3. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)

## 5. 深入实现细节

如果你要看最小配置、JDBC `TaskStore` 启用方式和 API 验证示例，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/flowgram-mysql-taskstore)
