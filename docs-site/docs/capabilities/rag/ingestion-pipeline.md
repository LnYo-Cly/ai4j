---
title: Ingestion Pipeline
description: 讲清 AI4J IngestionPipeline 这条 RAG 入库编排层：source 加载、文本清洗、chunk、metadata 富化、批量 embedding 与 vector upsert 如何串成统一流水线，以及 documentId/contentHash 稳定性与可插拔扩展位点。
tags: [concept]
---

# Ingestion Pipeline

这页讲“文档如何进入知识库”。在 AI4J 里，`IngestionPipeline` 不是一个随手拼出来的 demo helper，而是一条明确的 RAG 入库编排层。

如果你想真正看懂 AI4J 的 RAG 基座，这页必须讲透。因为它决定了：

- 文档身份是怎么建立的
- chunk 是怎么切的
- metadata 是怎么一路带下去的
- embedding 和向量写入怎么串起来

## 1. 核心源码入口

- 主入口：`rag/ingestion/IngestionPipeline.java`
- 请求对象：`rag/ingestion/IngestionRequest.java`
- source 对象：`rag/ingestion/IngestionSource.java`
- 文档对象：`RagDocument`
- chunk 对象：`RagChunk`

默认装配还能直接从 `IngestionPipeline` 构造器看到：

- `TextDocumentLoader`
- `TikaDocumentLoader`
- `RecursiveTextChunker(1000, 200)`
- `WhitespaceNormalizingDocumentProcessor`
- `DefaultMetadataEnricher`

这不是“几段工具类拼起来”，而是一条已经预先抽象好的标准流水线。

## 2. 一条 ingest 请求至少包含什么

从 `IngestionRequest` 可以直接看到，核心字段有：

- `dataset`
- `embeddingModel`
- `document`
- `source`
- `chunker`
- `documentProcessors`
- `metadataEnrichers`
- `batchSize`
- `upsert`
- `skipExistingContentHash`

这组字段很说明问题：

- `dataset` 定义写入边界
- `embeddingModel` 定义向量语义
- `source` 定义原始语料入口
- `chunker / processors / enrichers` 定义知识工程策略

也就是说，AI4J 没把 ingest 简化成“给我一个文件，我帮你随便入库”，而是保留了对关键策略的显式控制位。

## 3. 一次真正的执行流程

从 `ingest(...)` 方法往下看，完整流程大致是：

1. 校验 `dataset` 和 `embeddingModel`
2. 用 `DocumentLoader` 加载原始 source
3. 通过 `LoadedDocumentProcessor` 做文本清洗
4. 构造或补齐 `RagDocument`
5. 用 `Chunker` 切出 `RagChunk`
6. 为每个 chunk 写入稳定 `contentHash`
7. 如果开启 `skipExistingContentHash`，先用支持 metadata lookup 的 `VectorStore.exists(...)` 跳过已存在 chunk
8. 把剩余 chunk 批量送去 embedding
9. 组装 `VectorRecord`
10. 写入 `VectorStore`

这个顺序非常重要，因为它说明：

- metadata 不是向量写入时临时补的
- chunk identity 不是搜索时临时算的
- ingest 阶段已经决定了后续引用和 trace 的上限

## 4. `documentId` 和 `chunkId` 为什么是这条链的核心

`IngestionPipeline` 会根据 source / path / uri 等信息构造稳定 `documentId`，并进一步规范化：

- `chunkId`
- `chunkIndex`
- `documentId`

这意味着 AI4J 非常强调：

- 文档身份要稳定
- chunk 身份要可追踪

如果这一步做不好，后面你再想做：

- 引用回溯
- UI 证据展示
- rerank 结果解释
- 文档版本比较

都会变得很难。

## 5. 默认给你的不只是“能跑通”

默认构造器已经内置了：

- 两种 loader
- 递归 chunker
- 文本清洗 processor
- metadata enricher
- batch embedding

这说明 AI4J 不是把 ingest 当成“用户自己随便写一条 for 循环”的外围能力，而是直接把一套常见知识工程默认值做到了基座层。


## 6. 增量入库：`contentHash` 与 `skipExistingContentHash`

AI4J 现在会为每个标准化后的 chunk 写入：

- `contentHash`：chunk content 的 SHA-256，用来判断同一份内容是否已经入过库

默认行为不变：即使已有相同内容，也会继续 embedding 和 upsert。
如果你希望重复 ingest 同一批资料时少做无效 embedding，可以显式开启：

```java
IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text("员工手册内容"))
        .skipExistingContentHash(Boolean.TRUE)
        .build());

int skipped = result.getSkippedCount();
```

这不是新的索引框架，也不会替你做版本发布、删除旧文档或权限治理。它只做一件事：

1. 先算 chunk `contentHash`
2. 后端支持 metadata-only lookup 时，用 `VectorStore.exists(...)` 查这个 hash 是否存在
3. 已存在则跳过 embedding / upsert，并计入 `skippedCount`
4. 后端不支持或 lookup 出错时 fail-open，按普通 ingest 继续写入

:::note
当前内置后端中，Qdrant / Milvus / PgVector / Redis 提供 metadata lookup；Pinecone 的现有封装仍保持默认不跳过。
:::

## 7. 扫描件与 OCR：把非文本文档纳入流水线

`TextDocumentLoader` / `TikaDocumentLoader` 能处理可复制文本的文档（txt、markdown、带文本层的 PDF）。但扫描件、纯图片、无文本层的 PDF 加载出来 `content` 是空的——这一步如果直接进 chunker，整篇文档会被丢掉。

AI4J 把“为空白文档补文本”做成了 **ingestion 阶段的 `LoadedDocumentProcessor`**，在 chunking **之前**执行（见第 3 节流程的第 3 步）。涉及三个类：

- `rag/ingestion/OcrTextExtractor.java` —— OCR 引擎的扩展点（SPI）
- `rag/ingestion/OcrTextExtractingDocumentProcessor.java` —— 调 OCR 引擎补文本
- `rag/ingestion/OcrNoiseCleaningDocumentProcessor.java` —— 清洗 OCR 常见噪声

:::note
AI4J 本身不内置任何 OCR 引擎。`OcrTextExtractor` 是一个接口，你在它后面接 Tesseract、PaddleOCR 或云端 OCR 服务。
:::

### 7.1 `OcrTextExtractingDocumentProcessor`：空白文档的 OCR 兜底

它的触发条件非常克制，**只在 content 为空时才跑 OCR**：

```java
public LoadedDocument process(IngestionSource source, LoadedDocument document) {
    if (document == null || !isBlank(document.getContent())) {
        return document;            // 已有文本，跳过
    }
    if (!extractor.supports(source, document)) {
        return document;            // 引擎不支持该来源，跳过
    }
    String extracted = extractor.extractText(source, document);
    if (isBlank(extracted)) {
        return document;            // 没抽出东西，跳过
    }
    // 写回 content，并打上 ocrApplied = true
}
```

也就是说：可复制文本的文档不会多花一次 OCR 成本；只有真正空白的扫描件才会走 OCR 分支。

### 7.2 `OcrTextExtractor`：你来实现 OCR 引擎适配

```java
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractor;
import io.github.lnyocly.ai4j.rag.ingestion.LoadedDocument;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionSource;

// 例：包装一个真实 OCR 服务（Tesseract / PaddleOCR / 云端 API）
public class MyOcrExtractor implements OcrTextExtractor {
    @Override
    public boolean supports(IngestionSource source, LoadedDocument document) {
        // 只对图片和扫描型 PDF 做 OCR
        String name = source == null ? null : source.getName();
        return name != null && (name.endsWith(".png") || name.endsWith(".pdf"));
    }

    @Override
    public String extractText(IngestionSource source, LoadedDocument document) throws Exception {
        // 调用你的 OCR 引擎，返回识别出的纯文本
        return callOcrEngine(source, document);
    }
}
```

`supports(...)` 决定哪些来源走 OCR；`extractText(...)` 返回识别文本。抽出空白时处理器会原样返回，不会把空串写进去。

### 7.3 `OcrNoiseCleaningDocumentProcessor`：清洗 OCR 文本噪声

OCR 输出常带格式噪声，直接切块会污染召回质量。这个 processor 处理三类常见问题：

| 噪声 | 例子 | 清洗后 |
| --- | --- | --- |
| 跨行连字符 | `docu-\nment` | `document` |
| 字母逐字间距 | `H e l l o` | `Hello` |
| 多余空白/连续空行 | 多空格、连续空行 | 折叠、最多保留一个空行 |

清洗后会打上 `ocrNoiseCleaned = true` 标记，便于后续 trace。

### 7.4 怎么把 OCR 接进 ingest

OCR 处理器**不是默认装配**，需要你在 `IngestionRequest.documentProcessors` 或 pipeline 构造器里显式传入。典型组合是先 OCR 抽取、再做噪声清洗：

```java
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractingDocumentProcessor;
import io.github.lnyocly.ai4j.rag.ingestion.OcrNoiseCleaningDocumentProcessor;

import java.util.Arrays;

IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_scan")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.builder().name("scanned-contract.pdf").build())
        .documentProcessors(Arrays.asList(
                new OcrTextExtractingDocumentProcessor(new MyOcrExtractor()),
                new OcrNoiseCleaningDocumentProcessor()
        ))
        .build());
```

顺序很重要：**先抽取，后清洗**。因为清洗只对已经有文本的文档生效，而扫描件的文本要靠抽取这一步补上。

## 8. 规范 metadata：`RagMetadataKeys` 与租户/业务/版本隔离

向量库里每个 chunk 都带着一串 metadata，这些 key 不是随手取的名字，而是 `RagMetadataKeys` 定义的**规范常量**。它们被 `DefaultMetadataEnricher` 在入库时统一写进每个 chunk 的 metadata，并被检索、引用、删除等下游能力依赖。

源码入口：

- `rag/RagMetadataKeys.java` —— 规范 key 常量
- `rag/ingestion/DefaultMetadataEnricher.java` —— 把这些 key 写进 chunk metadata

### 8.1 完整的规范 key 清单

| 常量 | 字面值 | 含义 |
| --- | --- | --- |
| `RagMetadataKeys.CONTENT` | `content` | chunk 文本，便于回显 |
| `RagMetadataKeys.DOCUMENT_ID` | `documentId` | 文档稳定身份 |
| `RagMetadataKeys.CHUNK_ID` | `chunkId` | chunk 稳定身份 |
| `RagMetadataKeys.SOURCE_NAME` | `sourceName` | 源文件名 |
| `RagMetadataKeys.SOURCE_PATH` | `sourcePath` | 源路径 |
| `RagMetadataKeys.SOURCE_URI` | `sourceUri` | 源 URI |
| `RagMetadataKeys.SECTION_TITLE` | `sectionTitle` | 章节标题 |
| `RagMetadataKeys.CHUNK_INDEX` | `chunkIndex` | chunk 序号 |
| `RagMetadataKeys.PAGE_NUMBER` | `pageNumber` | 页码 |
| `RagMetadataKeys.TENANT` | `tenant` | 租户标识 |
| `RagMetadataKeys.BIZ` | `biz` | 业务线标识 |
| `RagMetadataKeys.VERSION` | `version` | 文档版本 |
| `RagMetadataKeys.CONTENT_HASH` | `contentHash` | chunk 内容 SHA-256（由 pipeline 写入，用于增量跳过） |

:::note
`contentHash` 不是由 `DefaultMetadataEnricher` 写的，而是 `IngestionPipeline` 在 chunk 标准化后单独计算并塞进 metadata，专门服务于第 6 节的 `skipExistingContentHash` 增量入库。其余 key 由 enricher 写入。
:::

### 8.2 `tenant / biz / version`：检索隔离的三件套

这三个 key 解决的是“同一套向量库服务多租户、多业务、多版本”的隔离问题。它们来自 `RagDocument`，入库时随文档一起设置：

```java
IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/handbook.md")
                .tenant("acme")        // 哪个租户
                .biz("hr")             // 哪条业务线
                .version("2026.03")    // 哪个版本
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());
```

入库后，每个 chunk 的 metadata 都会带上 `tenant=acme`、`biz=hr`、`version=2026.03`。检索时就可以把这三个 key 当作 metadata filter：

- **租户隔离**：A 公司的查询只命中 `tenant=A` 的 chunk，不会召回 B 公司的数据
- **业务隔离**：HR 知识库和财务知识库（`biz=hr` vs `biz=finance`）共库不串扰
- **版本隔离**：新旧版文档（`version=2026.03` vs `version=2025.12`）共存时，按版本过滤或灰度切换

也可以不显式构造 `RagDocument`，而是在 source/document 的 metadata map 里直接放这几个 key，`IngestionPipeline.resolveDocument(...)` 会把它们读出来补到文档身份上。

### 8.3 为什么必须用规范 key

如果你随手用 `"tenantId"`、`"businessLine"`、`"ver"` 这种自定义名字，会出现两个问题：

- `DefaultMetadataEnricher` 不会写它们，下游检索/删除/引用能力也找不到
- 不同入库代码各自命名，同一库里出现多套并存的 key，隔离形同虚设

所以凡是想做隔离、范围过滤、版本管理的字段，一律走 `RagMetadataKeys` 的规范常量；只有真正业务自定义的标签（如 `department`、`customTag`）才用任意 key。

## 9. 你可以在哪些位置扩展

这是这页最有工程价值的部分。

你可以替换或扩展：

- `DocumentLoader`
- `LoadedDocumentProcessor`（OCR 抽取、噪声清洗都挂在这里，见第 7 节）
- `Chunker`
- `MetadataEnricher`
- `batchSize`
- `upsert` 策略

这意味着 AI4J 的 ingest 不是封闭黑盒，而是一条**可插拔的知识工程流水线**。

## 10. 它不负责什么

这条链只负责把知识规范地“送进库里”，不负责：

- 检索排序
- 最终回答拼接
- 用户权限过滤
- 引用 UI 呈现

这些能力属于：

- retriever / reranker
- rag service
- 上层应用

如果把这些都压进 `IngestionPipeline`，反而会让边界失真。

## 11. 常见坑

### 11.1 `dataset` 设计随意

后面检索隔离、删除和回放都会很难治理。

### 11.2 只关心 embedding，不关心 metadata

向量能召回，但证据链讲不清；租户/业务/版本隔离（第 8 节）也建不起来。

### 11.3 把 chunking 当成次要细节

实际很多 RAG 效果问题根源就在这里。

### 11.4 扫描件不做 OCR 就直接入库

扫描型 PDF 加载出来 content 为空，没有 `OcrTextExtractingDocumentProcessor` 兜底，整篇文档会被静默丢弃。

## 12. 设计摘要

> AI4J 的 `IngestionPipeline` 是一条显式的 RAG 入库编排层：source 加载、文本处理、chunk、metadata、embedding、vector upsert 都在这里被串成统一流水线。它的价值不是“帮你省胶水代码”，而是把文档 identity、知识工程策略和租户/业务/版本隔离稳定下来。

## 13. 继续阅读

- [Search and RAG / Chunking Strategies](/docs/capabilities/rag/chunking-strategies)
- [Search and RAG / Vector Store and Backends](/docs/capabilities/rag/vector-store-and-backends)
- [Search and RAG / Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
