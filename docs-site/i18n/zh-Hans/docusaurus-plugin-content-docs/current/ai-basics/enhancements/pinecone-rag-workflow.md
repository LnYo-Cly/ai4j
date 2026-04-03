---
sidebar_position: 41
---

# Pinecone RAG 工作流

如果你已经确定底层向量库存储用 Pinecone，当前推荐的主线不是再手写一整套 Pinecone 专用流程，而是：

1. `IngestionPipeline` 统一入库
2. `PineconeVectorStore` 作为底层存储
3. `RagService` / `Retriever` 负责查询
4. 可选 `ModelReranker` 做精排

---

## 1. 推荐核心类

- `VectorStore`
- `PineconeVectorStore`
- `IngestionPipeline`
- `IngestionRequest`
- `IngestionSource`
- `RagService`
- `RagQuery`
- `DenseRetriever`
- `ModelReranker`

---

## 2. 配置 Pinecone

```java
PineconeConfig pineconeConfig = new PineconeConfig();
pineconeConfig.setHost("https://<index-host>");
pineconeConfig.setKey(System.getenv("PINECONE_API_KEY"));

configuration.setPineconeConfig(pineconeConfig);
VectorStore vectorStore = aiService.getPineconeVectorStore();
```

---

## 3. 入库流程

### 3.1 推荐：直接使用 `IngestionPipeline`

```java
VectorStore vectorStore = aiService.getPineconeVectorStore();

IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("tenant_a_contract_v202603")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("合同模板")
                .sourcePath("/docs/contract-template.pdf")
                .tenant("tenant_a")
                .biz("legal")
                .version("2026.03")
                .build())
        .source(IngestionSource.file(new File("D:/data/contract-template.pdf")))
        .build());

System.out.println("upserted=" + ingestResult.getUpsertedCount());
```

---

## 4. 查询流程

```java
RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagQuery ragQuery = RagQuery.builder()
        .query("违约金怎么算")
        .dataset("tenant_a_contract_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build();

RagResult ragResult = ragService.search(ragQuery);
String context = ragResult.getContext();
System.out.println(ragResult.getCitations());
```

---

## 5. 如需更高精度，再接 Rerank

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先合同原文、章节标题和编号明确的条款"
);

RagService ragService = new DefaultRagService(
        new DenseRetriever(
                aiService.getEmbeddingService(PlatformType.OPENAI),
                vectorStore
        ),
        reranker,
        new DefaultRagContextAssembler()
);
```

---

## 6. 参数建议

- `dataset`：建议直接编码 tenant / biz / version
- `topK`：先从 `3~8` 调优
- `chunkSize`：先从 `600~1200` 试
- `chunkOverlap`：先从 `10%~25%` 试

---

## 7. 生产实践

- metadata 至少保留 `content/source/title/version/updatedAt`
- embedding 模型固定，不要混维度
- 文档更新要有索引重建策略
- 召回时优先加 metadata 过滤，不要只靠向量相似度
- 如果只是做通用 RAG，不要把业务逻辑直接写死在已废弃的 `PineconeService` 上
- 统一优先面向 `VectorStore / IngestionPipeline / RagService`

---

## 8. 常见问题

### 8.1 召回为空

- dataset 错
- 向量维度不一致
- 数据没有成功 upsert

### 8.2 召回有内容但回答不准

- 分块策略不合理
- `topK` 不合适
- prompt 没限制“必须基于证据”

### 8.3 成本过高

- 批量 embedding 做缓存
- 去重后再向量化
- 定期清理低价值文档

### 8.4 什么时候还需要直接用已废弃的 `PineconeService`（Deprecated）

`PineconeService` 目前在文档层已视为 Deprecated。只有在你明确需要 Pinecone 特有底层能力时，才建议继续直接使用：

- namespace 级专用操作
- 已有旧项目已经大量使用 `PineconeQuery / PineconeDelete`
- 你在封装 Pinecone 专用管理能力，而不是统一 RAG 能力

---

## 9. 继续阅读

1. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
2. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
3. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
4. [Embedding 接口](/docs/ai-basics/services/embedding)
