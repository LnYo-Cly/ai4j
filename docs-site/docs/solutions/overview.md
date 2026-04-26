# Solutions 总览

`Solutions` 这一章专门承接“组合场景”和“落地案例”，不承担基座说明职责。

## 1. 为什么要单独拆出案例层

如果把案例和产品说明混在同一条导航里，会同时伤害两类读者：

- 新用户会看不清 AI4J 的主能力线
- 老用户找落地方案时又要穿过大量概念说明

所以这里单独承接“已经知道自己要解决什么问题，现在想看一条实际方案怎么组织”的阅读阶段。

## 2. 这一章主要分哪几类场景

- `Spring Boot` 接入与会话持久化
- `RAG` 入库、检索、重排和证据化回答
- 联网搜索增强
- `Flowgram` 平台后端与任务存储
- 网络层与基础设施扩展

## 3. 怎么读这一章

建议先在对应主线里建立心智，再回来读案例：

- Spring 项目：先看 [Spring Boot / Overview](/docs/spring-boot/overview)
- RAG 项目：先看 [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
- Agent 项目：先看 [Agent / Overview](/docs/agent/overview)
- Flowgram 项目：先看 [Flowgram / Overview](/docs/flowgram/overview)

## 4. 快速选案例

- 只想做多轮聊天 + MySQL 持久化：看 [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)
- 需要持久化 Agent 会话：看 [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)
- 想做标准 RAG 入库与向量检索：看 [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)
- 已经确定使用 Pinecone：看 [Pinecone Vector Workflow](/docs/solutions/pinecone-vector-workflow)
- 需要联网搜索增强：看 [SearXNG Web Search](/docs/solutions/searxng-web-search)
- 需要流式 + 搜索 + RAG 的组合应用：看 [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag)
- 要做高证据要求的法律助手：看 [Legal Assistant](/docs/solutions/legal-assistant)
- 要把 Flowgram 任务落到 MySQL：看 [Flowgram MySQL Task Store](/docs/solutions/flowgram-mysql-taskstore)
- 要调优 OkHttp 并发和连接池：看 [SPI Dispatcher ConnectionPool](/docs/solutions/spi-dispatcher-connectionpool)
