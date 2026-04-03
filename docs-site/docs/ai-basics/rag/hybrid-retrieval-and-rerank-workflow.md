---
sidebar_position: 3
---

# 混合检索与 Rerank 实战工作流

如果你要在 AI4J 里做一条更像生产环境的 RAG 主线，推荐从这条链路开始：

```text
文档解析
  -> 分块与 metadata 设计
  -> embedding 入库
  -> DenseRetriever + Bm25Retriever
  -> HybridRetriever 融合
  -> ModelReranker 精排
  -> RagContextAssembler 生成上下文与引用
  -> Chat / Agent / Workflow 回答
```

这一页不绑定某一个向量库实现，而是讲统一抽象层应该怎么组合。

## 1. 这页解决什么问题

最常见的痛点不是“不会调用向量库”，而是：

- 文档能入库，但召回质量不稳定
- 语义召回能命中大意，但术语、编号、错误码不准
- 混合检索做了，但不知道怎么解释排序结果
- 回答出来了，却不知道引用来自哪份文档

AI4J 目前已经把这几层拆开：

- 存储层：`VectorStore`
- 召回层：`DenseRetriever`、`Bm25Retriever`
- 融合层：`HybridRetriever` + `FusionStrategy`
- 精排层：`Reranker` / `ModelReranker`
- 上下文层：`RagContextAssembler`
- 结果层：`RagResult`、`RagCitation`、`RagTrace`

## 2. 推荐的统一主线

最稳妥的默认方案是：

1. 语义召回：`DenseRetriever`
2. 关键词召回：`Bm25Retriever`
3. 融合：`HybridRetriever(new RrfFusionStrategy())`
4. 精排：`aiService.getModelReranker(...)`
5. 输出：`RagResult.context + RagResult.citations + RagResult.trace`

为什么推荐这条路径：

- `DenseRetriever` 负责自然语言问题
- `Bm25Retriever` 负责术语、缩写、编号、文件名命中
- `RRF` 不依赖不同检索器的分数尺度完全一致
- `Rerank` 再把 topK 候选排得更准

## 3. 文档入库：先把 metadata 设计对

向量化之前，不要只保留纯文本。

至少建议在 metadata 里放这些字段：

- `RagMetadataKeys.DOCUMENT_ID`
- `RagMetadataKeys.SOURCE_NAME`
- `RagMetadataKeys.SOURCE_PATH`
- `RagMetadataKeys.SOURCE_URI`
- `RagMetadataKeys.PAGE_NUMBER`
- `RagMetadataKeys.SECTION_TITLE`
- `RagMetadataKeys.CHUNK_INDEX`
- `RagMetadataKeys.TENANT`
- `RagMetadataKeys.BIZ`
- `RagMetadataKeys.VERSION`

原因很直接：

- `DenseRetriever` 会把这些字段还原成 `RagHit`
- `DefaultRagContextAssembler` 会把来源拼进引用
- `RagCitation` 会直接暴露这些来源信息

## 4. 入库示例：`VectorStore` + Embedding

下面示例用的是统一 `VectorStore`，不写死 `Pinecone/Qdrant/Milvus/pgvector`：

```java
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);
VectorStore vectorStore = aiService.getQdrantVectorStore();

String dataset = "tenant-a_hr_v1";
String documentId = "employee-handbook-2026";
String sourceName = "employee-handbook.pdf";
String sourcePath = "/docs/hr/employee-handbook.pdf";

List<String> chunks = Arrays.asList(
        "员工请假需至少提前 3 个工作日提交申请，紧急病假除外。",
        "年假审批通过后方可离岗，未审批擅自离岗按旷工处理。",
        "补充医疗报销需在费用发生后 30 日内提交单据。"
);

EmbeddingResponse embeddingResponse = embeddingService.embedding(Embedding.builder()
        .model("text-embedding-3-small")
        .input(chunks)
        .build());

List<VectorRecord> records = new ArrayList<VectorRecord>();
for (int i = 0; i < chunks.size(); i++) {
    records.add(VectorRecord.builder()
            .id(documentId + "#" + i)
            .vector(embeddingResponse.getData().get(i).getEmbedding())
            .content(chunks.get(i))
            .metadata(new LinkedHashMap<String, Object>() {{
                put(RagMetadataKeys.DOCUMENT_ID, documentId);
                put(RagMetadataKeys.SOURCE_NAME, sourceName);
                put(RagMetadataKeys.SOURCE_PATH, sourcePath);
                put(RagMetadataKeys.SECTION_TITLE, i == 2 ? "医疗报销" : "员工请假");
                put(RagMetadataKeys.CHUNK_INDEX, i);
                put(RagMetadataKeys.TENANT, "tenant-a");
                put(RagMetadataKeys.BIZ, "hr");
                put(RagMetadataKeys.VERSION, "2026.03");
            }})
            .build());
}

vectorStore.upsert(VectorUpsertRequest.builder()
        .dataset(dataset)
        .records(records)
        .build());
```

要点：

- `dataset` 是上层统一逻辑分区
- `record.content` 和 metadata 里的来源字段最好同时保留
- `documentId + chunkIndex` 是很稳的去重 key

## 5. 构建 BM25 语料：建议和向量分块共用同一批 chunk

`Bm25Retriever` 是内存检索器，所以它需要一份本地语料。

如果你想让混合检索结果更稳定，BM25 语料最好和入库时的 chunk 保持同一套边界：

```java
List<RagHit> bm25Corpus = new ArrayList<RagHit>();
for (int i = 0; i < chunks.size(); i++) {
    bm25Corpus.add(RagHit.builder()
            .id(documentId + "#" + i)
            .documentId(documentId)
            .content(chunks.get(i))
            .sourceName(sourceName)
            .sourcePath(sourcePath)
            .sectionTitle(i == 2 ? "医疗报销" : "员工请假")
            .chunkIndex(i)
            .build());
}

Retriever bm25Retriever = new Bm25Retriever(bm25Corpus);
```

适用场景：

- 制度编号
- 产品名
- 错误码
- API 名称
- 中英文混合术语

## 6. 构建混合检索：Dense + BM25 + RRF

```java
Retriever denseRetriever = new DenseRetriever(
        embeddingService,
        vectorStore
);

Retriever hybridRetriever = new HybridRetriever(
        Arrays.asList(denseRetriever, bm25Retriever),
        new RrfFusionStrategy()
);
```

为什么默认先用 `RrfFusionStrategy`：

- 不依赖 dense 分数和 bm25 分数处在同一尺度
- 对多路结果合并更稳
- 适合作为 SDK 默认推荐策略

如果你后续已经验证过分数尺度，可以再尝试：

- `RsfFusionStrategy`
- `DbsfFusionStrategy`

## 7. 接入 Rerank：把候选排得更准

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先保留制度原文、审批规则、时间约束和报销时限明确的片段"
);
```

这一步的意义是：

- 混合检索负责把“候选找全”
- `Rerank` 负责把前几条排得更准

如果你的系统延迟很敏感，也可以先只做：

```text
Dense/BM25 -> HybridRetriever -> 直接输出
```

不是必须每次都接 rerank 模型。

## 8. 组合成正式 `RagService`

```java
RagService ragService = new DefaultRagService(
        hybridRetriever,
        reranker,
        new DefaultRagContextAssembler()
);
```

查询时建议明确几个参数：

```java
RagQuery ragQuery = RagQuery.builder()
        .query("员工年假审批通过前是否可以先离岗？医疗报销多久内提交？")
        .dataset(dataset)
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .finalTopK(4)
        .includeCitations(true)
        .includeTrace(true)
        .build();

RagResult ragResult = ragService.search(ragQuery);
```

推荐理解：

- `topK`：检索候选池大小
- `finalTopK`：最终注入上下文的片段数
- `includeCitations`：是否把 `[S1]` 这类引用头拼进上下文
- `includeTrace`：是否保留完整召回/重排 trace

## 9. 结果里能拿到什么

### 9.1 可直接注入模型的上下文

```java
String context = ragResult.getContext();
```

默认 `DefaultRagContextAssembler` 会生成这种结构：

```text
[S1] employee-handbook.pdf / 员工请假
员工请假需至少提前 3 个工作日提交申请，紧急病假除外。

[S2] employee-handbook.pdf / 医疗报销
补充医疗报销需在费用发生后 30 日内提交单据。
```

### 9.2 来源引用

```java
List<RagCitation> citations = ragResult.getCitations();
```

每条 `RagCitation` 里可直接拿到：

- `citationId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `snippet`

这意味着你可以在回答区直接展示：

- 来源文件名
- 页码 / 小节标题
- 对应片段摘要

### 9.3 运行时 trace

```java
RagTrace trace = ragResult.getTrace();
```

如果开启 `includeTrace`，你可以直接拿到：

- `trace.getRetrievedHits()`
- `trace.getRerankedHits()`

每个 `RagHit` 里还包含：

- `rank`
- `retrieverSource`
- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

这对排查这些问题很关键：

- 是 dense 命中的，还是 bm25 命中的
- 是融合阶段把它顶上来的，还是 rerank 把它提上来的
- 为什么最终第 1 条不是你以为的那一条

## 10. 注入 Chat：回答时保留来源感知

最简单的做法是把 `ragResult.getContext()` 直接塞回 `ChatCompletion`：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withSystem(
                "你是企业知识库助手。只能基于给定资料回答。若资料不足，请明确说明。回答时尽量保留引用编号。"))
        .message(ChatMessage.withUser(
                "问题：员工年假审批通过前是否可以先离岗？医疗报销多久内提交？\n\n资料：\n" + ragResult.getContext()))
        .build();

ChatCompletionResponse response = aiService.getChatService(PlatformType.OPENAI).chatCompletion(request);
```

这样做的好处是：

- 回答和知识库片段保持一致
- 引用编号可直接映射到 `ragResult.getCitations()`
- 前端可以把 `[S1]`、`[S2]` 渲染成可点击来源

## 11. 推荐的调优顺序

如果效果不稳，不要一上来就怀疑模型或 rerank。

先按这个顺序查：

1. 分块边界是否合理
2. metadata 是否完整
3. `DenseRetriever` 单独效果是否可用
4. `Bm25Retriever` 是否真的覆盖了术语/编号场景
5. `HybridRetriever` 的融合结果是否更好
6. `Rerank` 是否真的提升了 top3/top5
7. 回答 prompt 是否限制“仅基于资料”

## 12. 什么时候适合这条主线

优先推荐：

- 企业制度问答
- 法务/合同检索
- 产品手册与 API 文档问答
- 多来源内部知识库搜索
- Agent / Workflow 的知识检索节点

可以先简化：

- 小型 demo
- 文档少、术语简单
- 对延迟极敏感

这类场景可以先从：

```text
DenseRetriever -> DefaultRagService
```

开始，后面再加 BM25、Hybrid、Rerank。

## 13. 继续阅读

1. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
2. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
3. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
4. [Rerank 接口](/docs/ai-basics/services/rerank)
5. [Pinecone RAG 工作流](/docs/ai-basics/rag/pinecone-workflow)
