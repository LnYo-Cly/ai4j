# Solutions 总览

`Solutions` 这一章承接的是“组合场景”和“落地案例”，不承担基座说明职责。

它的定位很明确：

- 前面的主线章节负责解释模块、边界、能力
- `Solutions` 负责告诉你，具体业务问题该怎样组合这些能力

## 1. 为什么单独拆一层案例

如果把案例和产品说明混在一起，会同时伤害两类读者：

- 新读者会分不清哪些是产品主线，哪些只是某个落地组合
- 老读者想找解决方案时，又要重新穿过大量概念页

所以 `Solutions` 更像“带路页”：

- 先帮你快速选路线
- 再把你导回真正需要的主线和深页

## 2. 这一章覆盖哪些问题类型

- Spring Boot 接入与会话持久化
- Agent 会话持久化与压缩
- RAG 入库、检索、重排和证据化输出
- 联网搜索增强
- Flowgram 任务平台后端
- 网络栈和基础设施扩展

## 3. 怎么读这一章才对

不要把案例页当成 SSoT。

更合适的阅读方式是：

1. 先在对应主线建立心智
2. 再回到案例页选择组合方式
3. 最后进入旧路径深页看具体配置和代码

推荐起点：

- Spring 项目：看 [Spring Boot / Overview](/docs/spring-boot/overview)
- Agent 项目：看 [Agent / Overview](/docs/agent/overview)
- RAG 项目：看 [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
- Flowgram 项目：看 [Flowgram / Overview](/docs/flowgram/overview)

## 4. 快速选路线

- 只想做多轮聊天 + MySQL 持久化：看 [Spring Boot + MySQL Chat Memory](/docs/solutions/springboot-mysql-chat-memory)
- 需要持久化 Agent 会话：看 [Spring Boot + JDBC Agent Memory](/docs/solutions/springboot-jdbc-agent-memory)
- 想搭标准 RAG 入库与检索流水线：看 [RAG Ingestion Vector Store](/docs/solutions/rag-ingestion-vector-store)
- 已经确定底层使用 Pinecone：看 [Pinecone Vector Workflow](/docs/solutions/pinecone-vector-workflow)
- 需要公网联网搜索增强：看 [SearXNG Web Search](/docs/solutions/searxng-web-search)
- 需要流式输出 + 搜索 + RAG 组合链：看 [DeepSeek Stream Search RAG](/docs/solutions/deepseek-stream-search-rag)
- 要做高证据要求的法律助手：看 [Legal Assistant](/docs/solutions/legal-assistant)
- 要把 Flowgram 任务落到 MySQL：看 [Flowgram MySQL Task Store](/docs/solutions/flowgram-mysql-taskstore)
- 要调优 OkHttp 并发和连接池：看 [SPI Dispatcher ConnectionPool](/docs/solutions/spi-dispatcher-connectionpool)

## 5. 这些案例页怎么组织

本章每一页都优先回答四件事：

- 这个方案解决什么问题
- 该用哪些模块组合
- 不适合什么场景
- 读完后该回到哪条主线继续深入

这样它既能承担场景入口，也能承担方案索引。

## 6. 判断一个方案页是否适合你

在进入某个 `Solutions` 页面前，建议先判断三件事：

- 你当前缺的是基础能力理解，还是组合落地路径
- 你需要的是通用基线，还是某个特定后端或运行时的具体方案
- 你的问题更偏 `Core SDK`、`Spring Boot`、`Agent` 还是 `Flowgram`

这个判断能避免在尚未建立主线边界时，过早进入细分方案页。

## 7. 方案页应该带你看到什么

本章的每个方案页，后续都按同一类结构组织：

- 方案解决的问题和不解决的问题
- 需要组合哪些模块和关键对象
- 为什么这套组合成立
- 什么时候应该升级到更复杂的方案

这样读者既能快速选路径，也能知道回到哪条主线继续深入。
