# Spring Boot Common Patterns

这一页只讲高频工程组织方式。

## 1. 这页真正解决什么

当前很多用户的问题不是“第一个请求怎么发”，而是：

- Spring 容器已经接好了，业务层应该怎么组织
- Tool、RAG、Workflow、配置这些东西应该摆在哪
- 怎样避免把 AI4J 调用和 Web/controller 逻辑搅在一起

所以这页的重点是工程组织，而不是 API 语法。

## 2. 推荐分层

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

## 3. 高价值模式

- `Controller -> Service -> AI4J interface`
- 通过 `AiServiceRegistry` 做多实例路由
- 用 `ChatMemory` 先解决基础会话，再决定是否升级到 `Agent`
- 把 RAG、Tool、Workflow 组织成显式业务模块

这些模式的共同点是：

- 先让容器和能力入口稳定
- 再决定业务如何组合这些能力

## 4. 不推荐的组织方式

更容易失控的写法通常包括：

- Controller 里直接堆满 prompt、平台切换和工具路由
- 同一层既处理 Web 参数，又处理检索、memory、模型调用
- RAG、Tool、Workflow 没有显式目录或责任边界

这类写法短期能跑，长期很难维护，也不利于面试和架构说明。

## 5. 继续看哪些案例

当前正式案例入口建议看：

- [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)
- [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)

## 6. 推荐连读

1. [Bean Extension](/docs/spring-boot/bean-extension)
2. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
3. [Solutions / Overview](/docs/solutions/overview)
