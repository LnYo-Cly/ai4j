---
sidebar_position: 5
---

# Pinecone 向量库完整工作流

这页给你一条当前更推荐的 Pinecone RAG 基线：

1. `IngestionPipeline` 统一入库
2. `PineconeVectorStore` 作为底层存储
3. `RagService` 统一查询
4. 需要时再接 `ModelReranker`

## 1. 配置 Pinecone

### 1.1 非 Spring

```java
PineconeConfig pineconeConfig = new PineconeConfig();
pineconeConfig.setHost("https://<index-host>");
pineconeConfig.setKey(System.getenv("PINECONE_API_KEY"));

Configuration configuration = new Configuration();
configuration.setPineconeConfig(pineconeConfig);
configuration.setOpenAiConfig(openAiConfig);
configuration.setOkHttpClient(okHttpClient);

AiService aiService = new AiService(configuration);
VectorStore vectorStore = aiService.getPineconeVectorStore();
```

### 1.2 Spring Boot

```yaml
ai:
  vector:
    pinecone:
      host: https://<index-host>
      key: ${PINECONE_API_KEY}
      upsert: /vectors/upsert
      query: /query
      delete: /vectors/delete
```

## 2. 入库流程（IngestionPipeline）

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

## 3. 查询流程（RagService）

```java
String question = "合同违约金条款如何计算？";

RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagQuery ragQuery = RagQuery.builder()
        .query(question)
        .dataset("tenant_a_contract_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build();

RagResult ragResult = ragService.search(ragQuery);
String context = ragResult.getContext();
System.out.println(context);
System.out.println(ragResult.getCitations());
```

接下来把 `context` 注入聊天请求即可：

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withSystem("请仅基于给定资料回答，并引用关键依据"))
        .message(ChatMessage.withUser("问题：" + question + "\n\n资料：\n" + context))
        .build();
```

## 4. 可选：接入 Rerank

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先合同原文、章节标题和编号明确的条款"
);
```

## 5. 参数说明

### 5.1 `dataset`

- 建议直接编码 `tenant + biz + version`
- 在 Pinecone 下，它会映射到逻辑 namespace 隔离语义

### 5.2 `chunkSize / chunkOverlap`

- `chunkSize`：单块最大长度
- `chunkOverlap`：相邻块重叠长度

经验值：

- 技术文档：`800~1200`
- 法律条文：`600~1000`
- 重叠一般取 `10%~25%`

### 5.3 `topK`

- `3~8` 通常是更稳妥的起点
- 太大容易把 Pinecone 召回噪声直接送进上下文

## 6. 生产化建议

- 使用固定 embedding 模型，避免向量维度不一致。
- `dataset` 设计成 `tenant + biz + version`，方便回滚。
- metadata 至少保留：`content/source/title/version/updatedAt`。
- 定期清理过期数据，避免召回污染。
- 如果只是做通用 RAG，优先面向 `VectorStore / IngestionPipeline / RagService`，不要把业务逻辑直接写死在已废弃的 `PineconeService` 上。

## 7. 与联网增强的边界

这条 Pinecone 工作流解决的是私域知识库检索，不是联网搜索。

边界可以简单记成：

- Pinecone / RAG：查内部资料
- SearXNG：查公网网页

如果你的问题主要依赖内部文档、制度、合同、知识库，这页就是主线。

## 8. 常见错误

### 8.1 查询结果为空

- dataset 不一致
- 向量维度与索引配置不匹配
- topK 太小 / 文本分块质量差

### 8.2 回答“看起来有依据但不准确”

- 分块粒度不合适
- 未做 metadata 过滤
- prompt 没有限定“仅基于上下文”

建议先把检索质量做稳，再调模型生成风格。

### 8.3 什么时候还需要直接用已废弃的 `PineconeService`（Deprecated）

`PineconeService` 目前在文档层已视为 Deprecated。只有在你明确需要 Pinecone 特有底层能力时，才建议继续直接使用：

- namespace 级专用管理操作
- 已有旧项目已经大量使用 `PineconeQuery / PineconeDelete`
- 你在封装 Pinecone 专用能力，而不是统一 RAG 能力
