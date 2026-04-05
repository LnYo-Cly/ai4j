---
sidebar_position: 4
---

# RAG 入库实践：IngestionPipeline + Qdrant / Milvus / pgvector

这页不再只讲抽象能力，而是给一条“文档进库到可查询”的完整链路：

1. Spring Boot 注入 `AiService`
2. 选择一个 `VectorStore`
3. 用 `IngestionPipeline` 统一装载、清洗、分块、向量化、入库
4. 用 `RagService` 做查询
5. 把引用、来源、分数、trace 暴露给前端或 Agent

## 1. 这条链路适合什么场景

适合：

- 企业知识库
- 文档问答
- FAQ / Wiki / 制度库
- Flowgram 的知识检索节点
- Agent 的外部知识增强

重点收益有三点：

- 文档入库方式统一
- 查询结果自带来源信息
- 后续能平滑升级到混合检索与 rerank

## 2. 依赖

```xml
<dependencies>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.0.0</version>
  </dependency>
</dependencies>
```

如果你还要直接接 `Agent` 或 Flowgram，只需要继续在上层加：

- `ai4j-agent`
- `ai4j-flowgram-spring-boot-starter`

底层知识库主线不变。

## 3. 先选一个向量库

### 方案 A：Qdrant

最适合先跑通一版：

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/
  vector:
    qdrant:
      enabled: true
      host: http://127.0.0.1:6333
      api-key: ""
      vector-name: ""
```

### 方案 B：Milvus

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/
  vector:
    milvus:
      enabled: true
      host: http://127.0.0.1:19530
      token: ""
      db-name: default
      partition-name: ""
```

### 方案 C：pgvector

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/
  vector:
    pgvector:
      enabled: true
      jdbc-url: jdbc:postgresql://127.0.0.1:5432/postgres
      username: postgres
      password: postgres
      table-name: ai4j_vectors
```

这三种都接在统一抽象：

- `VectorStore`

所以你的上层入库和检索代码可以保持一致。

## 4. 注入一个向量库 Bean

### Qdrant

```java
import io.github.lnyocly.ai4j.vector.store.qdrant.QdrantVectorStore;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeStoreProvider {

    private final QdrantVectorStore vectorStore;

    public KnowledgeStoreProvider(QdrantVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public QdrantVectorStore getVectorStore() {
        return vectorStore;
    }
}
```

如果你切到 Milvus 或 pgvector，只需要把构造参数改成：

- `MilvusVectorStore`
- `PgVectorStore`

## 5. 入库服务：最小可运行版本

```java
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionRequest;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionResult;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionSource;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class KnowledgeIngestionService {

    private final AiService aiService;
    private final VectorStore vectorStore;

    public KnowledgeIngestionService(AiService aiService, KnowledgeStoreProvider provider) {
        this.aiService = aiService;
        this.vectorStore = provider.getVectorStore();
    }

    public IngestionResult ingestPdf(File file) throws Exception {
        IngestionPipeline pipeline = aiService.getIngestionPipeline(PlatformType.OPENAI, vectorStore);

        return pipeline.ingest(IngestionRequest.builder()
                .dataset("company_kb")
                .embeddingModel("text-embedding-3-small")
                .document(RagDocument.builder()
                        .sourceName(file.getName())
                        .sourcePath(file.getAbsolutePath())
                        .tenant("acme")
                        .biz("knowledge")
                        .version("2026.03")
                        .build())
                .source(IngestionSource.file(file))
                .build());
    }
}
```

执行后会统一完成：

- 文档装载
- 文本清洗
- 分块
- metadata 填充
- embedding
- `VectorStore.upsert(...)`

## 6. 如果文档是扫描件：加 OCR 扩展点

`IngestionPipeline` 现在已经预留了 OCR 扩展点。你只需要实现：

- `OcrTextExtractor`

例如：

```java
import io.github.lnyocly.ai4j.rag.ingestion.IngestionSource;
import io.github.lnyocly.ai4j.rag.ingestion.LoadedDocument;
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractor;
import org.springframework.stereotype.Component;

@Component
public class MyOcrTextExtractor implements OcrTextExtractor {

    @Override
    public boolean supports(IngestionSource source, LoadedDocument document) {
        return document != null && (document.getContent() == null || document.getContent().trim().isEmpty());
    }

    @Override
    public String extractText(IngestionSource source, LoadedDocument document) {
        // 这里接你自己的 OCR 引擎，例如 PaddleOCR、Tesseract、云 OCR 服务
        return "";
    }
}
```

然后把它挂进请求：

```java
import io.github.lnyocly.ai4j.rag.ingestion.OcrNoiseCleaningDocumentProcessor;
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractingDocumentProcessor;
import io.github.lnyocly.ai4j.rag.ingestion.WhitespaceNormalizingDocumentProcessor;

IngestionResult result = pipeline.ingest(IngestionRequest.builder()
        .dataset("company_kb")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.file(file))
        .documentProcessors(java.util.Arrays.asList(
                new OcrTextExtractingDocumentProcessor(myOcrTextExtractor),
                new OcrNoiseCleaningDocumentProcessor(),
                new WhitespaceNormalizingDocumentProcessor()
        ))
        .build());
```

这样一来，扫描 PDF、拍照文档、OCR 噪音文本都能走同一条入库主线。

## 7. 查询服务：把入库结果真正用起来

最小查询版本可以直接走 dense retrieval：

```java
import io.github.lnyocly.ai4j.rag.RagQuery;
import io.github.lnyocly.ai4j.rag.RagResult;
import io.github.lnyocly.ai4j.rag.RagService;
import io.github.lnyocly.ai4j.service.PlatformType;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private final AiService aiService;
    private final VectorStore vectorStore;

    public KnowledgeSearchService(AiService aiService, KnowledgeStoreProvider provider) {
        this.aiService = aiService;
        this.vectorStore = provider.getVectorStore();
    }

    public RagResult search(String query) throws Exception {
        RagService ragService = aiService.getRagService(PlatformType.OPENAI, vectorStore);
        return ragService.search(RagQuery.builder()
                .query(query)
                .dataset("company_kb")
                .embeddingModel("text-embedding-3-small")
                .topK(8)
                .finalTopK(4)
                .includeCitations(true)
                .includeTrace(true)
                .build());
    }
}
```

## 8. 返回给前端时能拿到什么

`RagResult` 不只是上下文文本，还包含完整来源信息：

```java
RagResult result = knowledgeSearchService.search("报销多久内提交？");

String context = result.getContext();
System.out.println(context);

result.getCitations().forEach(citation -> {
    System.out.println(citation.getCitationId());
    System.out.println(citation.getSourceName());
    System.out.println(citation.getSourcePath());
    System.out.println(citation.getPageNumber());
    System.out.println(citation.getSectionTitle());
});
```

这意味着你前端可以直接展示：

- 来源文件名
- 页码
- 章节标题
- 引用片段

也就是说，“回答里标记来源于哪个文件”这件事，AI4J 当前已经有基础能力。

## 9. 召回分数、融合分数、trace 怎么看

如果你在查询时开启：

```java
.includeTrace(true)
```

就能拿到：

- `result.getHits()`
- `result.getTrace().getRetrievedHits()`
- `result.getTrace().getRerankedHits()`

每个 `RagHit` 里当前可直接读到：

- `score`
- `rank`
- `retrieverSource`
- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

这对排查下面几类问题很有用：

- 为什么这条命中了
- 它是 dense 命中的，还是 hybrid 融上来的
- rerank 是否真的改变了排序

## 10. 如何升级到混合检索和 rerank

这页先让你把“入库到可查”跑通。

如果你后面还要继续提升检索效果，推荐升级路径是：

```text
DenseRetriever
  -> HybridRetriever(RRF)
  -> ModelReranker
  -> DefaultRagContextAssembler
```

详细组合方式继续看：

- [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)

## 11. 这条能力如何复用到 Chat / Agent / Workflow

这套 RAG 能力不是只给单一入口用的。

它可以直接接到：

- 普通 `Chat`
- `Agent`
- Flowgram 的知识检索节点

原因是上层只需要消费统一输出：

- `RagResult.context`
- `RagResult.citations`
- `RagResult.trace`

所以你先把知识库主线建设好，后面无论对接聊天页、Agent 还是工作流平台，都不需要重新发明一套知识库层。

## 12. 上线建议

- 第一版优先把 dataset、documentId、sourceName、sourcePath 这些 metadata 设计好
- Qdrant 最适合先跑通，pgvector 最适合和现有 PostgreSQL 平台一起落地
- 如果文档质量差，优先补 OCR 和清洗，不要先怪检索器
- 如果要做用户可见答案，建议后续补 `HybridRetriever + Reranker`

## 13. 继续阅读

1. [Ingestion Pipeline 文档入库流水线](/docs/ai-basics/rag/ingestion-pipeline)
2. [Chunking 分块策略](/docs/ai-basics/rag/chunking-strategies)
3. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
4. [引用、来源追踪与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
