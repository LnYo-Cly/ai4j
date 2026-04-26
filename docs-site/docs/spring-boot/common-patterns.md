# Spring Boot Common Patterns

这一页只讲高频工程组织方式。

## 1. 推荐分层

```text
controller
service
ai/prompts
ai/tools
ai/workflow
config
```

这类分层的重点不是好看，而是把：

- Web 入口
- AI4J 调用
- Tool / RAG / Workflow 组织
- Spring 配置

分到不同责任层里。

## 2. 高价值模式

- `Controller -> Service -> AI4J interface`
- 通过 `AiServiceRegistry` 做多实例路由
- 用 `ChatMemory` 先解决基础会话，再决定是否升级到 `Agent`
- 把 RAG、Tool、Workflow 组织成显式业务模块

## 3. 继续看哪些案例

当前正式案例入口建议看：

- [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)
- [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)

## 4. 推荐连读

1. [Bean Extension](/docs/spring-boot/bean-extension)
2. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
3. [Solutions / Overview](/docs/solutions/overview)
