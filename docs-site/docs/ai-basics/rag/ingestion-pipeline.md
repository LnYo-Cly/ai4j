---
sidebar_position: 3
---

# Ingestion Pipeline 文档入库流水线

如果说 `Retriever / Reranker / RagService` 解决的是“查的时候怎么查”，那么 `IngestionPipeline` 解决的是“资料一开始怎么进库”。

它把原来分散的几步串成了一条统一主线：

```text
source
  -> DocumentLoader
  -> LoadedDocumentProcessor
  -> Chunker
  -> MetadataEnricher
  -> embedding
  -> VectorStore.upsert
```

---

## 1. 适合解决什么问题

在很多项目里，RAG 第一版通常是手写这些步骤：

1. 用 `TikaUtil` 把文件转文本
2. 用 `RecursiveCharacterTextSplitter` 切块
3. 手动给每个 chunk 绑定 metadata
4. 调 `IEmbeddingService`
5. 拼 `VectorUpsertRequest`
6. 写入 `VectorStore`

这条链路并不复杂，但如果每个项目都重复拼一次，很快就会出现：

- 元数据字段不一致
- chunk id 规则不一致
- 批量 embedding 写法不一致
- 文件和纯文本两套入口各写一遍

`IngestionPipeline` 就是把这条最小工程主线收拢成一套轻量编排。

---

## 2. 当前内置的组成

当前内置的最小实现包括：

- `IngestionPipeline`
- `IngestionRequest`
- `IngestionResult`
- `IngestionSource`
- `DocumentLoader`
- `TextDocumentLoader`
- `TikaDocumentLoader`
- `Chunker`
- `RecursiveTextChunker`
- `LoadedDocumentProcessor`
- `WhitespaceNormalizingDocumentProcessor`
- `OcrNoiseCleaningDocumentProcessor`
- `OcrTextExtractor`
- `OcrTextExtractingDocumentProcessor`
- `MetadataEnricher`
- `DefaultMetadataEnricher`

设计原则很明确：

- 不引入新的大框架
- 不替代你现有的向量库选型
- 只把“文档入库”这条通用链路标准化

---

## 3. 最小示例

```java
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);
VectorStore vectorStore = new QdrantVectorStore(qdrantConfig);

IngestionPipeline ingestionPipeline = new IngestionPipeline(embeddingService, vectorStore);

IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/handbook.md")
                .tenant("acme")
                .biz("hr")
                .version("2026.03")
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());
```

执行后会完成：

- 文本装载
- 默认分块
- 默认 metadata 绑定
- 批量 embedding
- `VectorStore.upsert(...)`

---

## 4. 文件入库示例

如果你的输入是本地文件，不需要自己先调 `TikaUtil`：

```java
IngestionResult ingestResult = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_files")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.file(new File("docs/employee-handbook.pdf")))
        .build());
```

默认会走：

- `TikaDocumentLoader`
- `WhitespaceNormalizingDocumentProcessor`
- `RecursiveTextChunker`
- `DefaultMetadataEnricher`

其中 `TikaDocumentLoader` 会补齐：

- `sourceName`
- `sourcePath`
- `sourceUri`
- `mimeType`

---

## 5. 默认写入哪些 metadata

默认会统一写入这些关键字段：

- `content`
- `documentId`
- `chunkId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`
- `tenant`
- `biz`
- `version`

这意味着后面的：

- `DenseRetriever`
- 引用展示
- trace 调试
- 租户过滤
- 版本隔离

都可以直接复用这套元数据约定。

---

## 6. 如何自定义分块

如果你不想继续用默认字符分块，可以直接实现 `Chunker`：

```java
public class MarkdownHeadingChunker implements Chunker {
    @Override
    public List<RagChunk> chunk(RagDocument document, String content) {
        // 先按标题切，再构造 RagChunk
        return ...;
    }
}
```

然后在请求里替换：

```java
IngestionRequest request = IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.file(file))
        .chunker(new MarkdownHeadingChunker())
        .build();
```

适合场景：

- Markdown / Wiki
- FAQ
- API 文档
- 表格型知识库

---

## 7. 如何自定义元数据

如果你有自己的业务字段，比如：

- `docType`
- `department`
- `updatedAt`
- `language`

可以追加自己的 `MetadataEnricher`：

```java
MetadataEnricher customEnricher = new MetadataEnricher() {
    @Override
    public void enrich(RagDocument document, RagChunk chunk, Map<String, Object> metadata) {
        metadata.put("docType", "policy");
        metadata.put("department", "hr");
    }
};

IngestionRequest request = IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text(content))
        .metadataEnrichers(Collections.singletonList(customEnricher))
        .build();
```

默认 `DefaultMetadataEnricher` 仍然会先执行，你追加的是业务增强，而不是把基础字段打散。

---

## 8. 如何扩展新的文档来源

如果你的资料不是本地文件，也不是已经得到的纯文本，而是：

- OSS / S3
- 数据库 blob
- CMS 页面
- 内部知识平台 API

可以实现自己的 `DocumentLoader`：

```java
public class CmsDocumentLoader implements DocumentLoader {
    @Override
    public boolean supports(IngestionSource source) {
        return source != null && source.getUri() != null && source.getUri().startsWith("cms://");
    }

    @Override
    public LoadedDocument load(IngestionSource source) {
        return LoadedDocument.builder()
                .content(fetchCmsText(source.getUri()))
                .sourceName(source.getName())
                .sourceUri(source.getUri())
                .build();
    }
}
```

然后把它注册到自定义 pipeline：

```java
IngestionPipeline ingestionPipeline = new IngestionPipeline(
        embeddingService,
        vectorStore,
        Arrays.asList(new CmsDocumentLoader(), new TextDocumentLoader(), new TikaDocumentLoader()),
        new RecursiveTextChunker(1000, 200),
        Collections.<MetadataEnricher>singletonList(new DefaultMetadataEnricher())
);
```

---

## 9. OCR 与复杂文档清洗扩展点

现在 `IngestionPipeline` 在 `DocumentLoader` 和 `Chunker` 之间增加了一层：

- `LoadedDocumentProcessor`

这层适合做：

- OCR 回填
- PDF / 扫描件文本修复
- 多余空白清理
- 连字符断行修复
- 页眉页脚、噪声文本清洗

### 9.1 内置清洗器

当前内置了：

- `WhitespaceNormalizingDocumentProcessor`
- `OcrNoiseCleaningDocumentProcessor`

例如：

```java
IngestionRequest request = IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text("docu-\nment\n\n\nH e l l o"))
        .documentProcessors(Collections.singletonList(
                new OcrNoiseCleaningDocumentProcessor()
        ))
        .build();
```

### 9.2 接入你自己的 OCR 引擎

如果你要接 PaddleOCR、Tesseract、云 OCR 或内部文档解析服务，可以实现 `OcrTextExtractor`：

```java
OcrTextExtractor extractor = new OcrTextExtractor() {
    @Override
    public boolean supports(IngestionSource source, LoadedDocument document) {
        return source != null && source.getFile() != null;
    }

    @Override
    public String extractText(IngestionSource source, LoadedDocument document) {
        return callYourOcrEngine(source.getFile());
    }
};

IngestionPipeline ingestionPipeline = new IngestionPipeline(
        embeddingService,
        vectorStore,
        Arrays.asList(new TextDocumentLoader(), new TikaDocumentLoader()),
        new RecursiveTextChunker(1000, 200),
        Collections.singletonList(new OcrTextExtractingDocumentProcessor(extractor)),
        Collections.<MetadataEnricher>singletonList(new DefaultMetadataEnricher())
);
```

这个设计的重点是：

- Pipeline 不强绑具体 OCR 依赖
- 你可以按项目接任意 OCR 后端
- 清洗链和分块链天然解耦

### 9.3 Qdrant / Milvus / pgvector 接线方式

`IngestionPipeline` 自身不绑具体向量库，接线点始终是 `VectorStore`。

如果你直接用 `AiService`，可以这样接：

```java
AiService aiService = new AiService(configuration);

IngestionPipeline qdrantPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getQdrantVectorStore()
);

IngestionPipeline milvusPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getMilvusVectorStore()
);

IngestionPipeline pgvectorPipeline = aiService.getIngestionPipeline(
        PlatformType.OPENAI,
        aiService.getPgVectorStore()
);
```

如果你是 Spring Boot，通常就是直接注入具体 `VectorStore` Bean，或者自己显式构造：

```java
IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

VectorStore qdrantStore = aiService.getQdrantVectorStore();
VectorStore milvusStore = aiService.getMilvusVectorStore();
VectorStore pgvectorStore = aiService.getPgVectorStore();
```

对应配置前缀分别是：

- `ai.vector.qdrant.*`
- `ai.vector.milvus.*`
- `ai.vector.pgvector.*`

示例：

```yaml
ai:
  vector:
    qdrant:
      enabled: true
      host: http://localhost:6333
      api-key: ""
    milvus:
      enabled: false
      host: http://localhost:19530
      token: ""
      db-name: default
    pgvector:
      enabled: false
      jdbc-url: jdbc:postgresql://localhost:5432/postgres
      username: postgres
      password: postgres
      table-name: ai4j_vectors
```

建议：

- 业务代码尽量面向 `VectorStore`
- 只在配置层决定底层是 `Qdrant / Milvus / pgvector`
- Flowgram 知识检索节点也复用这层统一抽象

---

## 10. `IngestionResult` 可以拿到什么

当前结果里可以直接拿到：

- 标准化后的 `RagDocument`
- 最终 `RagChunk` 列表
- 即将入库的 `VectorRecord` 列表
- 实际 upsert 数量

这对两类场景很有用：

- 入库后做审计和调试
- 做“预览模式”，先看 chunk 和 metadata 再决定是否真正写库

如果你只是想预览，不真正写入，可以：

```java
IngestionRequest request = IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text(content))
        .upsert(Boolean.FALSE)
        .build();
```

---

## 11. 当前边界

这套内置实现故意保持轻量，所以当前边界也很明确：

- 默认还是字符分块，不是语义分块
- 不直接内置父子块、层级块、表格专用块
- 不直接承担重排序，它只负责“写库前”的链路

也就是说：

- OCR / 文档清洗现在已经有扩展点
- 但具体 OCR 引擎、复杂版面解析、表格结构恢复仍然由你按项目接入

也就是说，它是一个稳定起点，不是最终形态的知识库工程平台。

---

## 12. 和后续检索链路怎么接

典型接法就是：

```text
IngestionPipeline
  -> VectorStore
  -> DenseRetriever / Bm25Retriever / HybridRetriever
  -> 可选 ModelReranker
  -> RagService
```

所以它和检索层是上下游关系：

- `IngestionPipeline` 负责把资料变成可检索索引
- `Retriever / Reranker / RagService` 负责把索引真正用起来

---

## 13. 继续阅读

1. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
2. [RAG 架构、分块与索引设计](/docs/ai-basics/rag/architecture-and-indexing)
3. [Chunking 策略详解](/docs/ai-basics/rag/chunking-strategies)
4. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
5. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
