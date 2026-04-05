---
sidebar_position: 41
---

# Pinecone 向量检索工作流

本页按“入库 -> 召回 -> 生成”给出 ai4j 当前更推荐的 Pinecone RAG 流程。

核心类：

- `VectorStore`
- `PineconeVectorStore`
- `IngestionPipeline`
- `IngestionRequest`
- `IngestionSource`
- `RagService`
- `RagQuery`

## 1. 配置

```java
PineconeConfig pineconeConfig = new PineconeConfig();
pineconeConfig.setHost("https://<index-host>");
pineconeConfig.setKey(System.getenv("PINECONE_API_KEY"));

configuration.setPineconeConfig(pineconeConfig);
VectorStore vectorStore = aiService.getPineconeVectorStore();
```

## 2. 入库（推荐路径）

```java
IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("tenant_a_legal_v202603")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("法规汇编")
                .sourcePath("/docs/law.txt")
                .tenant("tenant_a")
                .biz("legal")
                .version("2026.03")
                .build())
        .source(IngestionSource.file(new File("D:/data/law.txt")))
        .build());

System.out.println("upserted=" + ingestResult.getUpsertedCount());
```

## 3. 查询

```java
RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagQuery ragQuery = RagQuery.builder()
        .query("违约金怎么算")
        .dataset("tenant_a_legal_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build();

RagResult ragResult = ragService.search(ragQuery);
String context = ragResult.getContext();
```

## 4. 可选：接入 Rerank

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先法规原文、条款标题和编号明确的片段"
);
```

## 5. 参数建议

- `dataset`：建议直接编码 tenant / biz / version
- `topK`：先从 3~8 调优
- `chunkSize`：600~1200
- `chunkOverlap`：10%~25%

## 6. 工程实践

- metadata 至少保留：`content/source/title/version/updatedAt`
- 向量模型固定，不要混维度
- 索引重建策略提前设计（版本迁移）
- 如果只是做通用 RAG，优先面向 `VectorStore / IngestionPipeline / RagService`

## 7. 与 Chat/Agent 结合

召回出的 `context` 可注入 Chat 或 Agent 的输入：

- Chat：拼接到 user/system
- Agent：写入上下文状态，再交给回答节点

## 8. 常见问题

### 8.1 召回为空

- dataset 错
- 向量维度不一致
- 数据未成功 upsert

### 8.2 召回有内容但回答不准

- 分块策略不合理
- topK 不合适
- prompt 未限制“必须基于证据”

### 8.3 成本过高

- 批量 embedding 做缓存
- 去重后再向量化
- 低价值文档定期清理

### 8.4 什么时候还需要直接用已废弃的 `PineconeService`（Deprecated）

`PineconeService` 目前在文档层已视为 Deprecated。只有在你明确需要 Pinecone 特有底层能力时，才建议继续直接使用：

- namespace 级专用管理操作
- 已有旧项目已经大量使用 `PineconeQuery / PineconeDelete`
- 你在封装 Pinecone 专用能力，而不是统一 RAG 能力
