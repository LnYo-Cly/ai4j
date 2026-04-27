# Spring Boot + JDBC Agent Memory

这个案例解决的是“普通多轮聊天已经不够，需要把 Agent 会话本身持久化下来”。

## 1. 适合什么场景

- `ReAct Agent`
- 带工具调用的业务 Agent
- 多轮任务代理
- 需要跨实例恢复的 Agent session

和 `ChatMemory` 场景不同，这里关心的不只是历史对话，还包括工具结果、运行时状态和压缩后的摘要。

## 2. 技术链路

核心组合是：

- `ai4j-agent`
- `ai4j-spring-boot-starter`
- `JdbcAgentMemory`
- `DataSource / MySQL`

这说明它已经不再是“基础聊天持久化”，而是进入了通用智能体 runtime 的会话层。

## 3. 什么时候不该直接用它

如果你只是做普通聊天，而没有：

- 工具调用
- runtime state
- 会话级任务持续

那应先看：

- [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)

## 4. 先补哪些主线页

1. [Agent / Overview](/docs/agent/overview)
2. [Agent / Memory and State](/docs/agent/memory-and-state)
3. [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)

## 5. 深入实现细节

如果你要看 `sessionId` 绑定方式、持久化工厂、Agent 构造代码和示例 Controller，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/springboot-jdbc-agent-memory)
