# Spring Boot + JDBC Agent Memory

这个方案解决的是“普通多轮聊天已经不够，需要把 Agent 会话本身持久化下来”。

## 1. 适合什么场景

- `ReAct Agent`
- 带工具调用的业务 Agent
- 多轮任务代理
- 需要跨实例恢复的 Agent session

和 `ChatMemory` 场景不同，这里关心的不只是历史对话，还包括：

- 工具结果
- runtime state
- 压缩后的摘要
- 任务持续性

## 2. 核心模块组合

这条方案的主链是：

- `ai4j-agent`
- `ai4j-spring-boot-starter`
- `JdbcAgentMemory`
- `WindowedMemoryCompressor`
- `DataSource / MySQL`

它已经不再是“基础聊天持久化”，而是进入通用智能体 runtime 的会话层。

## 3. 这条方案的优点

- 工具调用结果能跨轮保留
- Agent 状态可跨进程恢复
- 适合逐步引入压缩策略
- 比手写 session state 更贴近 runtime 真相

## 4. 什么时候不该直接上它

如果你只是普通聊天，没有：

- 工具调用
- runtime state
- 会话级任务持续

那应先看：

- [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)

先从轻方案起步，往往更稳。

## 5. 先补哪些主线页

1. [Agent / Overview](/docs/agent/overview)
2. [Agent / Memory and State](/docs/agent/memory-and-state)
3. [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)

## 6. 继续看实现细节

如果你要看：

- `sessionId` 绑定方式
- `JdbcAgentMemory` 工厂
- `WindowedMemoryCompressor` 接法
- Agent 构造与 Controller 示例

继续看深页：

- [旧路径案例页](/docs/guides/springboot-jdbc-agent-memory)
