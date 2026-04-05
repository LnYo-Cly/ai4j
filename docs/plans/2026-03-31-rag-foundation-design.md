# 2026-03-31 RAG 基础设施设计

- 状态：Approved
- 目标模块：`ai4j`
- 关联模块：`ai4j-agent`、`ai4j-spring-boot-starter`、`docs-site`

## 1. 背景

当前仓库已经具备一条最小可用的 RAG 链路：

- `TikaUtil` 负责文档解析
- `RecursiveCharacterTextSplitter` 负责文本切片
- `IEmbeddingService` 负责向量化
- `PineconeService` 负责向量入库和查询

这条链路可以跑通，但它更接近“Pinecone 工作流示例”，而不是“面向 Chat / Agent / Workflow / FlowGram 复用的 RAG 基础设施”。当前主要问题包括：

- 向量存储能力绑定到 `PineconeService`
- 查询结果容易在过早阶段被压平成字符串上下文
- 缺少统一的检索、重排序、上下文装配抽象
- 文档、分块、召回结果的数据模型过薄
- 来源引用、调试召回、切换向量库的成本偏高

因此，需要为 ai4j 补齐一套轻量、清晰、可扩展的 RAG 基础设施设计。

## 2. 目标

本设计的目标是：

- 在 `ai4j` 内建立统一的 RAG 抽象层
- 支持 `Pinecone`、`Qdrant`、`pgvector`、`Milvus`
- 让普通 `Chat`、`Responses`、`Agent`、`Workflow`、`FlowGram` 可复用同一套知识检索能力
- 支持“召回 -> 重排序 -> 上下文装配 -> 来源引用”完整链路
- 允许回答中标记来源文件、页码、章节、片段
- 保持首版设计轻量，不引入过重的平台级复杂度

## 3. 非目标

首版不做以下内容：

- 不做混合检索默认实现
- 不做图谱检索
- 不做索引管理后台
- 不做文档审核、分片审批、数据生命周期后台
- 不做复杂评测平台
- 不强制引入新的 Maven 大拆分

这些能力后续可以建立在本设计之上扩展。

## 4. 方案比较

### 4.1 方案 A：继续沿用各向量库独立 Service

做法：

- 保持 `PineconeService`
- 再增加 `QdrantService`
- 再增加 `PgvectorService`
- 再增加 `MilvusService`

优点：

- 实现快
- 对已有调用方改动小

缺点：

- Chat / Agent / FlowGram 上层会重复对接多套 API
- 无法形成统一的重排序与来源引用能力
- 检索结果对象难统一
- 长期维护成本高

### 4.2 方案 B：统一 RAG 抽象层，多库适配

做法：

- 在 `ai4j` 中定义统一的 `VectorStore`、`Retriever`、`Reranker`、`RagService`
- 底层分别提供 `Pinecone`、`Qdrant`、`pgvector`、`Milvus` 的适配实现

优点：

- 架构清晰
- 复用性强
- 上层接入成本最低
- 最适合后续做来源引用、FlowGram 节点和 Agent 工具化

缺点：

- 首次设计需要把边界定义清楚
- 需要做一定的兼容迁移

### 4.3 方案 C：一步到位做完整企业级 RAG 平台

优点：

- 能覆盖更多高级场景

缺点：

- 过重
- 超出当前 ai4j SDK 阶段需求

### 4.4 推荐结论

采用方案 B。

原因很直接：

- 它能解决当前“只有 Pinecone 工作流，没有统一 RAG 基建”的问题
- 它不会把系统一次性做重
- 它最适合当前 ai4j 作为基础设施 SDK 的定位

## 5. 分层设计

RAG 基础设施建议拆成四层。

### 5.1 文档入库层

职责：

- 文档解析
- 文本切片
- 元数据补全
- embedding 生成
- 向量入库

建议复用或扩展现有能力：

- `TikaUtil`
- `RecursiveCharacterTextSplitter`
- `IEmbeddingService`

### 5.2 向量存储层

职责：

- 统一屏蔽不同向量数据库差异
- 提供写入、检索、删除、过滤等核心能力

首版统一抽象：

- `VectorStore`
- `VectorRecord`
- `VectorSearchRequest`
- `VectorSearchResult`
- `VectorStoreCapabilities`

### 5.3 检索编排层

职责：

- 文本查询转 embedding
- metadata 过滤
- 向量粗召回
- 可选重排序
- 邻接 chunk 扩展
- 上下文预算裁剪

核心抽象：

- `Retriever`
- `Reranker`
- `RagService`

### 5.4 上下文装配层

职责：

- 把召回结果转成模型可消费的上下文
- 为回答生成引用编号
- 输出结构化来源清单

核心抽象：

- `RagContext`
- `RagCitation`
- `RagContextAssembler`

## 6. 统一数据模型

首版 RAG 不应继续只使用“向量 + 文本”两列数据，而应至少建立以下对象。

### 6.1 RagDocument

表示原始文档：

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `title`
- `tenant`
- `biz`
- `version`
- `metadata`

### 6.2 RagChunk

表示切片后的标准对象：

- `chunkId`
- `documentId`
- `content`
- `chunkIndex`
- `pageNumber`
- `sectionTitle`
- `metadata`

### 6.3 VectorRecord

表示向量库中的标准写入对象：

- `id`
- `dataset`
- `vector`
- `content`
- `metadata`

其中 `metadata` 内至少应保留：

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`
- `tenant`
- `biz`
- `version`

### 6.4 RagHit

表示一次召回命中的结果：

- `id`
- `score`
- `content`
- `metadata`
- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`

### 6.5 RagResult

表示完整检索结果：

- `query`
- `hits`
- `context`
- `citations`
- `sources`

这样既适合直接给 Chat 用，也适合给 FlowGram 节点、Agent trace、前端 UI 展示引用来源。

## 7. 统一接口设计

### 7.1 VectorStore

职责：

- 屏蔽 `Pinecone / Qdrant / pgvector / Milvus` 差异

核心能力：

- `upsert`
- `search`
- `delete`
- `capabilities`

设计原则：

- 上层不暴露具体厂商术语
- 统一使用逻辑概念 `dataset`
- 底层实现各自映射到 `namespace / collection / table`

映射关系建议：

- `Pinecone` -> `index + namespace`
- `Qdrant` -> `collection`
- `Milvus` -> `collection`
- `pgvector` -> `table` 或逻辑 dataset 字段

### 7.2 Retriever

职责：

- 把自然语言查询转为向量检索结果

默认流程：

1. 对 query 做 embedding
2. 构建 `VectorSearchRequest`
3. 调用 `VectorStore.search`
4. 得到 `RagHit` 列表

### 7.3 BM25 与关键词检索

`BM25` 对以下场景非常有价值：

- 文件名、类名、方法名、表名
- 错误码、订单号、SKU、版本号
- 法条编号、制度编号、规范条目
- 用户 query 中含有明确精确关键词
- embedding 对短 query 或专有词召回不稳定的场景

因此，ai4j 的 RAG 体系应当支持 `BM25`，但不建议在首版把“完整关键词检索平台”硬塞入核心主线。

推荐定位：

- 在统一 RAG 架构中，为 `BM25` 预留标准检索器扩展位
- `BM25` 作为可选 `Retriever` 实现
- 不把它设为首版默认主路径
- 不在首版强制引入重型全文检索依赖

建议支持三类检索模式：

- `DenseRetriever`
- `Bm25Retriever`
- `HybridRetriever`

其中：

- `DenseRetriever` 负责语义召回
- `Bm25Retriever` 负责关键词精确召回
- `HybridRetriever` 负责融合两路结果

首版建议：

- 默认主线仍为 dense retrieval
- `BM25` 作为第二阶段增强能力纳入
- `HybridRetriever` 在 `BM25` 和 dense 都稳定后再补

工程上，`BM25` 应支持两种落位方式：

- 轻量本地实现，例如基于 Lucene
- 外部检索系统适配，例如 Elasticsearch / OpenSearch / PostgreSQL Full Text Search

设计原则：

- 抽象进入 `ai4j`
- 具体实现允许按依赖重量分层
- 不要求所有调用方必须携带 BM25 依赖

### 7.4 Reranker

职责：

- 对粗召回结果做二次排序

首版接口即可，不强制默认实现：

- `NoopReranker`
- 预留第三方 API / 本地模型扩展位

推荐使用方式：

- 先粗召回 `20~50`
- 再重排收敛到 `5~8`

### 7.5 RagContextAssembler

职责：

- 根据命中的 `RagHit` 生成：
  - 模型上下文文本
  - 引用编号
  - 来源清单

建议默认输出形态：

```text
[S1] <文件名 / 页码 / 章节>
片段内容...

[S2] <文件名 / 页码 / 章节>
片段内容...
```

### 7.6 RagService

职责：

- 串起完整知识检索链路

首版责任边界：

- 输入 `RagQuery`
- 调用 `Retriever`
- 可选调用 `Reranker`
- 调用 `RagContextAssembler`
- 返回 `RagResult`

`RagService` 在检索模式上不应被固定为单一路径，而应允许：

- 仅 dense
- 仅 BM25
- dense + BM25 融合

推荐的阶段性策略：

- 首版默认：仅 dense
- 第二阶段：dense + BM25 可选
- 后续：支持更稳定的融合排序策略

## 8. 向量库适配策略

### 8.1 Pinecone

定位：

- 保留现有能力
- 作为首个 `VectorStore` 实现迁移目标

迁移原则：

- `PineconeService` 逐步下沉为适配实现
- 不再让上层直接依赖 Pinecone 专属查询字符串拼接逻辑

### 8.2 Qdrant

定位：

- 作为首批通用向量库适配之一

适合原因：

- payload filter 语义清晰
- Java 集成友好

### 8.3 pgvector

定位：

- 面向普通业务系统集成的首批适配之一

适合原因：

- 易于和现有 PostgreSQL 系统整合
- 便于事务和业务数据联动

### 8.4 Milvus

定位：

- 偏重型的向量平台实现

适配原则：

- 架构上纳入统一抽象
- 实现上允许比其他库更重
- 不把其特有索引术语泄漏到上层

## 9. 召回效果的推荐默认链路

为了兼顾效果、可解释性和复杂度，首版推荐标准链路如下：

1. 原始文档解析为纯文本，并保留基础来源元数据
2. 按段落和标题边界切片
3. 生成 `RagChunk`
4. 向量化后写入 `VectorStore`
5. 查询时先做 metadata 过滤
6. 向量粗召回 `topK = 20~50`
7. 可选重排序
8. 保留最终 `topN = 5~8`
9. 可选扩展相邻 chunk
10. 由 `RagContextAssembler` 生成带来源编号的上下文

如果后续启用 `BM25`，推荐链路为：

1. dense retrieval 粗召回
2. BM25 retrieval 关键词召回
3. 结果去重与融合
4. 可选 `Reranker` 二次重排
5. 再进入上下文装配

这套链路优先解决：

- 召回不准
- 引用不清
- 上游向量库替换困难
- 下游 Chat / Agent / Workflow 接入不统一

## 10. 来源引用设计

回答中标记来源文件，是本设计的核心能力之一。

### 10.1 入库阶段必须保存来源信息

至少包括：

- 文件名
- 文件路径或 URI
- 文档 ID
- 页码
- 章节标题
- 分块序号

### 10.2 上下文阶段生成标准引用编号

由 `RagContextAssembler` 为每个命中片段生成稳定编号：

- `S1`
- `S2`
- `S3`

### 10.3 生成阶段支持两种引用模式

模式一：

- 让模型直接输出 `[S1] [S2]`

模式二：

- 模型只输出正文
- 服务端根据命中结果补充 `sources`

推荐默认策略：

- 同时支持两者
- 服务端保留最终权威来源清单

### 10.4 最终返回结果建议

- `answer`
- `citations`
- `sources`

其中 `sources` 每条至少包含：

- `citationId`
- `sourceName`
- `sourcePath`
- `pageNumber`
- `sectionTitle`
- `snippet`

## 11. 对上层能力的接入方式

### 11.1 普通 Chat / Responses

接法：

- 在模型调用前执行 `RagService`
- 将 `context` 注入 system 或 user 消息
- 将 `sources` 作为额外结构化结果返回

### 11.2 Agent

接法有两种：

- 预检索注入：先做检索，再将 `context` 写入 memory
- 工具化检索：把 `RagService` 暴露为一个知识检索工具

推荐：

- 两种都支持
- 简单问答优先预检索
- 多轮复杂任务优先工具化

### 11.3 Workflow

接法：

- 提供标准知识检索步骤
- 输出 `hits / context / citations / sources`

### 11.4 FlowGram

接法：

- 将当前 `KnowledgeRetrieve` 节点从 `PineconeService` 解耦
- 改为依赖通用 `Retriever` 或 `RagService`

这样 FlowGram 上层无需关心底层向量库类型，只关心：

- 查询内容
- 数据集
- 过滤条件
- 召回数量

## 12. 包结构建议

在不新增 Maven 模块的前提下，建议先在 `ai4j` 内整理以下包：

- `io.github.lnyocly.ai4j.rag`
- `io.github.lnyocly.ai4j.rag.document`
- `io.github.lnyocly.ai4j.rag.retrieve`
- `io.github.lnyocly.ai4j.rag.rerank`
- `io.github.lnyocly.ai4j.rag.context`
- `io.github.lnyocly.ai4j.vector.store`
- `io.github.lnyocly.ai4j.vector.store.pinecone`
- `io.github.lnyocly.ai4j.vector.store.qdrant`
- `io.github.lnyocly.ai4j.vector.store.pgvector`
- `io.github.lnyocly.ai4j.vector.store.milvus`

这样可以先保持 Maven 结构稳定，再逐步演进代码结构。

## 13. 实施顺序

建议分三期实施。

### 13.1 第一期

- 定义统一对象模型
- 定义 `VectorStore`、`Retriever`、`Reranker`、`RagService`
- 将现有 `PineconeService` 迁移为首个适配实现
- 支持来源引用输出

### 13.2 第二期

- 增加 `Qdrant`
- 增加 `pgvector`
- FlowGram `KnowledgeRetrieve` 节点解耦到统一抽象

### 13.3 第三期

- 增加 `Milvus`
- 为 Agent 提供标准知识检索工具封装
- 增加更完整的召回调试与 trace 输出

## 14. 兼容性策略

- 现有 `PineconeService` 不立即硬删除
- 先通过适配器方式过渡
- 新文档以统一 RAG 抽象为主
- 旧用法保留兼容期，但不再作为主线推荐

## 15. 结论

AI4J 作为大模型基础设施 SDK，应该把 RAG 视为一等公民能力。

最合适的路线不是继续堆叠 `Pinecone` 风格的独立工作流，而是建立：

- 统一数据模型
- 统一向量存储抽象
- 统一召回与重排序扩展点
- 统一来源引用输出
- 统一上层接入方式

在此基础上，`Pinecone / Qdrant / pgvector / Milvus` 可以成为底层实现差异，而不是上层使用差异。
