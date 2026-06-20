# Spring Boot + MySQL Chat Memory

这个方案解决的是“先把多轮聊天和会话持久化做好”，而不是一上来就引入更重的 `Agent runtime`。

## 1. 适合什么场景

- Web 聊天页
- 企业问答助手
- 多轮客服机器人
- 同一用户会话需要跨实例恢复

如果你当前只需要稳定的多轮上下文，而不需要工具循环、runtime state、handoff 或 team，这通常是更合适的第一站。

## 2. 核心模块组合

这条方案的主链很清楚：

- `ai4j-spring-boot-starter`
- `ChatMemory`
- `JdbcChatMemory`
- `DataSource / MySQL`
- 基于 `sessionId` 的会话恢复

它本质上是：

> Spring 容器化接入 + 基础会话记忆持久化

而不是完整 Agent 会话系统。

## 3. 这条方案的优点

- 对 Spring Boot 项目最直接
- 架构简单，容易先上线
- 能把“多轮上下文”从 Controller 手写列表提升成正式能力
- 跨实例恢复会话比内存列表可靠得多

## 4. 不适合什么场景

如果你已经出现下面这些需求，就说明该升级了：

- 需要工具结果回写
- 需要 runtime state
- 需要任务级记忆压缩
- 需要 Agent 自己决定下一步

这时更适合看：

- [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)
- [Agent / Memory and State](/docs/agent/memory-and-state)

## 5. 先建立哪些主线心智

1. [Spring Boot / Quickstart](/docs/spring-boot/quickstart)
2. [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)
3. [Core SDK / Memory](/docs/core-sdk/memory/overview)

## 6. 继续看实现细节

如果你要看：

- 依赖配置
- `application.yml`
- `JdbcChatMemory` 工厂写法
- Controller / Service 组织方式

继续看深页：

- [旧路径案例页](/docs/guides/springboot-mysql-chat-memory)

## 7. 关键对象

这一页最值得继续看的对象通常是：

- `memory/ChatMemory.java`
- `memory/JdbcChatMemory.java`
- `memory/MessageWindowChatMemoryPolicy.java`
- Spring 容器中的 `DataSource`

它们分别对应上下文契约、持久化实现、裁剪策略和数据库接入面。

## 8. 这条方案真正解决的边界

这条方案解决的是“多轮聊天的上下文如何稳定进入模型请求”，不解决：

- 工具结果如何进入长期任务状态
- Agent runtime 如何恢复执行上下文
- 多角色协作如何管理共享记忆

如果问题已经升级到这些层面，就说明该切到 agent memory 方案了。

## 9. 实施时的注意事项

- `sessionId` 必须有稳定绑定规则，否则数据库持久化没有意义
- 历史消息的裁剪和压缩策略要先定，否则会话会无限膨胀
- 持久化成功不等于语义正确，仍要验证记忆窗口是否符合业务预期
