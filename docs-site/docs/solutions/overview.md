# Solutions 总览

`Solutions` 是 AI4J 的场景组合入口。它不重新定义 SDK 能力，而是把 Core SDK、Spring Boot、RAG、MCP、Agent、FlowGram 等能力按常见问题组合成可复制路径。

如果你还没理解基础概念，先看 [Start Here](/docs/intro) 和 [Feature Map](/docs/start-here/feature-map)。如果你已经知道要解决什么业务问题，再从本章选方案。

## 一句话定位

Solutions 解决的是：

> 当一个真实场景需要组合多个 AI4J 模块时，告诉你该从哪条方案开始、需要哪些模块、边界在哪里。

它不是 source of truth。每个方案页都会把你导回对应主线，例如 Spring Boot、Search & RAG、Agent 或 FlowGram。

## 快速选路线

| 目标 | 方案 | 组合能力 |
| --- | --- | --- |
| 多轮聊天并持久化会话 | [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory) | Spring Boot、Chat Memory、MySQL |
| 持久化 Agent 会话 | [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory) | Spring Boot、Agent、JDBC Memory |
| 搭建 RAG 入库和检索流水线 | [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store) | Ingestion、Embedding、Vector Store、Retrieval |
| 使用 Pinecone | [Pinecone Vector Workflow](/docs/solutions/pinecone-vector-workflow) | Vector Store、Pinecone、RAG |
| 接入联网搜索 | [SearXNG Web Search](/docs/solutions/searxng-web-search) | Online Search、SearXNG |
| 组合流式输出、搜索和 RAG | [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag) | Streaming、Search、RAG |
| 做高证据要求问答 | [Legal Assistant](/docs/solutions/legal-assistant) | RAG、Citation、Trace |
| 持久化 FlowGram task | [FlowGram MySQL Task Store](/docs/solutions/flowgram-mysql-taskstore) | FlowGram、Task Store、MySQL |
| 调整 HTTP 并发和连接池 | [SPI Dispatcher ConnectionPool](/docs/solutions/spi-dispatcher-connectionpool) | HTTP Stack、SPI、OkHttp |

## 怎么读方案页

建议按这个顺序读：

1. 先看方案是否解决你的问题。
2. 再看需要组合哪些模块。
3. 确认不适合的场景和限制。
4. 跳回对应主线补完整概念。
5. 上线前看生产检查、安全和排障页面。

不要从方案页反推整个项目架构。方案页是组合路径，不是模块边界定义。

## 方案页应该讲清楚什么

每个方案后续都应稳定回答：

- 解决什么问题。
- 不解决什么问题。
- 需要哪些模块和配置。
- 最小可运行路径是什么。
- 哪些点需要生产前检查。
- 继续深入应该回到哪条主线。

这个结构比堆代码更重要。代码可以多，但必须服务于路径和边界。

## 回到主线

| 如果你发现自己缺的是 | 回到这里 |
| --- | --- |
| 模型调用、Tool、MCP、RAG 基础能力 | [Core SDK](/docs/core-sdk/overview) |
| Spring 配置、Bean 和自动装配 | [Spring Boot](/docs/spring-boot/overview) |
| 多步推理、workflow、trace | [Agent](/docs/agent/overview) |
| 本地代码仓任务、CLI、ACP | [Coding Agent](/docs/coding-agent/overview) |
| 可视化工作流后端 | [FlowGram](/docs/flowgram/overview) |
| 版本、安全、生产检查和排障 | [Production Checklist](/docs/operations/production-checklist) |

如果一个方案页没有把这些路径讲清楚，它就还没有达到本章应有的质量。
