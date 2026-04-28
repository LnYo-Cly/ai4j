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

这类写法短期能跑，长期很难维护，也不利于架构演进和代码治理。

## 7. 关键对象

如果你要把这一页落到代码结构，优先关注：

- Spring 容器中的 `AiService`
- 业务 `Service` 层的 AI4J 入口接口
- `AiServiceRegistry` 的多实例路由场景
- `ChatMemory`、Tool、RAG、Workflow 的独立业务模块

这些对象共同决定“AI 能力是被组织起来”，还是“被散落在控制器里”。

## 8. 这一页真正想建立的边界

- Web 层只负责请求输入输出，不负责 AI 能力编排
- AI4J 接入点应收敛到业务服务层，而不是散在多个 controller
- Tool、RAG、Workflow 应是显式模块，而不是隐含在 prompt 或 util 类里

把这三条边界立住后，Spring Boot 接入才会真正变成可维护系统，而不是能跑的示例。

## 5. 继续看哪些案例

当前正式案例入口建议看：

- [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)
- [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)

## 6. 推荐连读

1. [Bean Extension](/docs/spring-boot/bean-extension)
2. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
3. [Solutions / Overview](/docs/solutions/overview)
