# Spring Boot + MySQL Chat Memory

这个案例解决的是“先把多轮聊天和会话持久化做好”，而不是一上来就引入更重的 Agent runtime。

## 1. 适合什么场景

- Web 聊天页
- 企业问答助手
- 多轮客服机器人
- 同一用户会话需要跨实例恢复

如果你当前只需要稳定的多轮上下文，而不需要自动工具循环、复杂推理状态或 handoff，这通常是最稳的起步方案。

## 2. 技术链路

这条方案的核心组合是：

- `ai4j-spring-boot-starter`
- `JdbcChatMemory`
- `DataSource / MySQL`
- 基于 `sessionId` 的会话恢复

也就是说，它本质上是“Spring 容器化接入 + 基础 memory 持久化”，不是 Agent 会话系统。

## 3. 不适合什么场景

下面这些情况通常说明你应该继续升级：

- 需要工具结果回写
- 需要 runtime state
- 需要跨轮任务规划或压缩摘要

这时更适合看：

- [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)
- [Agent / Memory and State](/docs/agent/memory-and-state)

## 4. 先补哪些主线页

1. [Spring Boot / Quickstart](/docs/spring-boot/quickstart)
2. [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)
3. [Core SDK / Memory](/docs/core-sdk/memory/overview)

## 5. 深入实现细节

如果你要看完整依赖、`application.yml`、工厂代码和 Controller 组织，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/springboot-mysql-chat-memory)
