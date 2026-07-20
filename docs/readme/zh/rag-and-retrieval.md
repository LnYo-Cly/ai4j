# Embedding / Rerank / RAG

[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

## Embedding服务

```java
public void test_embed() throws Exception {
    // 获取embedding服务实例
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // 构建请求参数
    Embedding embeddingReq = Embedding.builder().input("1+1").build();

    // 发送embedding请求
    EmbeddingResponse embeddingResp = embeddingService.embedding(embeddingReq);

    System.out.println(embeddingResp);
}
```

## Rerank服务

### 直接调用统一重排服务

```java
IRerankService rerankService = aiService.getRerankService(PlatformType.JINA);

RerankRequest request = RerankRequest.builder()
        .model("jina-reranker-v2-base-multilingual")
        .query("哪段最适合回答 Java 8 为什么仍然常见")
        .documents(Arrays.asList(
                RerankDocument.builder().id("doc-1").text("Java 8 仍是很多传统系统的默认运行时").build(),
                RerankDocument.builder().id("doc-2").text("AI4J 提供统一 Chat、Responses 和 RAG 接口").build(),
                RerankDocument.builder().id("doc-3").text("历史中间件和升级成本让很多企业延后 JDK 升级").build()
        ))
        .topN(2)
        .build();

RerankResponse response = rerankService.rerank(request);
System.out.println(response.getResults());
```

### 作为 RAG 精排器接入

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先保留制度原文、版本说明和编号明确的片段"
);
```

## RAG
### 推荐：使用统一 IngestionPipeline 入库

```java
VectorStore vectorStore = aiService.getQdrantVectorStore();

IngestionPipeline ingestionPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        vectorStore
);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/employee-handbook.md")
                .tenant("acme")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());

System.out.println(ingestResult.getUpsertedCount());
```

如果你已经走 Pinecone，也可以直接：

```java
IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);
```

推荐主线是：

1. `IngestionPipeline` 负责文档入库
2. `VectorStore` 负责底层向量存储
3. `DenseRetriever / HybridRetriever / ModelReranker / RagService` 负责查询阶段

完整说明见：

+ `docs-site/docs/ai-basics/rag/ingestion-pipeline.md`
+ `docs-site/docs/ai-basics/rag/overview.md`

### 配置向量数据库
```yml
ai:
  vector:
    pinecone:
      host: ""
      key: ""
```
### 推荐：Pinecone 也走统一 `VectorStore + IngestionPipeline`

```java
VectorStore vectorStore = aiService.getPineconeVectorStore();

IngestionPipeline ingestionPipeline = aiService.getPineconeIngestionPipeline(PlatformType.OPENAI);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("tenant_a_hr_v202603")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/employee-handbook.pdf")
                .tenant("tenant_a")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.file(new File("D:/data/employee-handbook.pdf")))
        .build());

System.out.println("upserted=" + ingestResult.getUpsertedCount());
```

### 查询阶段：直接走统一 `RagService`

```java
RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagQuery ragQuery = RagQuery.builder()
        .query("年假如何计算")
        .dataset("tenant_a_hr_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build();

RagResult ragResult = ragService.search(ragQuery);

System.out.println(ragResult.getContext());
System.out.println(ragResult.getCitations());
```

### 如果需要更高精度，再接 Rerank

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先制度原文、章节标题和编号明确的片段"
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

### 什么时候还需要直接用已废弃的 `PineconeService`（Deprecated）

`PineconeService` 目前在文档层已视为 Deprecated。只有在你明确需要 Pinecone 特有的底层控制时，才建议继续直接用：

+ namespace 级底层操作
+ 兼容旧项目里已经写死的 `PineconeQuery / PineconeDelete`
+ 你就是在做 Pinecone 专用封装，而不是面向统一 RAG 抽象开发
