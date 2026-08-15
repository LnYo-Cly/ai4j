---
title: "Ingestion Pipeline"
description: "How AI4J's IngestionPipeline serves as the RAG ingestion orchestration layer: how source loading, text cleaning, chunking, metadata enrichment, batch embedding, and vector upsert chain into a unified pipeline, plus documentId/contentHash stability and pluggable extension points."
tags: [concept]
---

# Ingestion Pipeline

This page covers "how documents enter the knowledge base". In AI4J, `IngestionPipeline` is not a throwaway demo helper but a well-defined RAG ingestion orchestration layer.

If you want to truly understand AI4J's RAG foundation, this page must be read thoroughly. It determines:

- how document identity is established
- how chunks are cut
- how metadata is carried through the pipeline
- how embedding and vector writes are chained together

## 1. Core source entry points

- Main entry: `rag/ingestion/IngestionPipeline.java`
- Request object: `rag/ingestion/IngestionRequest.java`
- source object: `rag/ingestion/IngestionSource.java`
- Document object: `RagDocument`
- Chunk object: `RagChunk`

The default wiring is visible directly from the `IngestionPipeline` constructor:

- `TextDocumentLoader`
- `TikaDocumentLoader`
- `RecursiveTextChunker(1000, 200)`
- `WhitespaceNormalizingDocumentProcessor`
- `DefaultMetadataEnricher`

This is not "a few utility classes stitched together" — it is a pre-abstracted standard pipeline.

## 2. What an ingest request contains at minimum

From `IngestionRequest` you can read off the core fields:

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

This set of fields says a lot:

- `dataset` defines the write boundary
- `embeddingModel` defines the vector semantics
- `source` defines the raw corpus entry point
- `chunker / processors / enrichers` define the knowledge-engineering strategy

In other words, AI4J does not reduce ingest to "give me a file and I'll throw it into the store" — it keeps explicit control points over the key strategies.

## 3. A real execution flow

Looking down from the `ingest(...)` method, the full flow is roughly:

1. Validate `dataset` and `embeddingModel`
2. Load the raw source with `DocumentLoader`
3. Run text cleaning through `LoadedDocumentProcessor`
4. Construct or fill in `RagDocument`
5. Cut out `RagChunk` with the `Chunker`
6. Write a stable `contentHash` for each chunk
7. If `skipExistingContentHash` is enabled, use the metadata-lookup-capable `VectorStore.exists(...)` to skip chunks that already exist
8. Batch the remaining chunks for embedding
9. Assemble `VectorRecord`
10. Write to `VectorStore`

This order matters, because it shows:

- metadata is not patched in at vector write time
- chunk identity is not computed on the fly at search time
- the ingest stage already sets the ceiling for downstream citation and tracing

## 4. Why `documentId` and `chunkId` are the core of this chain

`IngestionPipeline` builds a stable `documentId` from source / path / uri information, and further normalizes:

- `chunkId`
- `chunkIndex`
- `documentId`

This means AI4J puts strong emphasis on:

- document identity being stable
- chunk identity being traceable

If this step is done poorly, anything you want to do afterward:

- citation tracing
- UI evidence display
- rerank result explanation
- document version comparison

becomes very hard.

## 5. The defaults are more than "just runnable"

The default constructor already ships:

- two loaders
- a recursive chunker
- a text-cleaning processor
- a metadata enricher
- batch embedding

This shows AI4J does not treat ingest as a peripheral capability where "users write their own for loop" — it bakes a common set of knowledge-engineering defaults directly into the foundation.

## 6. Incremental ingestion: `contentHash` and `skipExistingContentHash`

AI4J now writes, for each normalized chunk:

- `contentHash`: the SHA-256 of the chunk content, used to decide whether identical content has already been ingested

The default behavior is unchanged: even if identical content exists, embedding and upsert continue.
If you want to avoid redundant embedding when re-ingesting the same batch of material, you can enable it explicitly:

```java
IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .source(IngestionSource.text("员工手册内容"))
        .skipExistingContentHash(Boolean.TRUE)
        .build());

int skipped = result.getSkippedCount();
```

This is not a new indexing framework, nor does it handle version release, old-document deletion, or permission governance for you. It does exactly one thing:

1. Compute the chunk `contentHash` first
2. When the backend supports metadata-only lookup, use `VectorStore.exists(...)` to check whether this hash already exists
3. If it exists, skip embedding / upsert and count it in `skippedCount`
4. When the backend does not support it or lookup errors out, fail open and continue writing as a normal ingest

:::note
Among the built-in backends, Qdrant / Milvus / PgVector / Redis provide metadata lookup; the existing Pinecone wrapper still defaults to not skipping.
:::

## 7. Scanned documents and OCR: bringing non-text documents into the pipeline

`TextDocumentLoader` / `TikaDocumentLoader` handle documents with selectable text (txt, markdown, PDF with a text layer). But scanned documents, pure images, and PDFs without a text layer come back with empty `content` — if these go straight into the chunker, the entire document is dropped.

AI4J implements "filling text for blank documents" as an **ingestion-stage `LoadedDocumentProcessor`**, executed **before** chunking (step 3 of the flow in section 3). Three classes are involved:

- `rag/ingestion/OcrTextExtractor.java` — the extension point (SPI) for the OCR engine
- `rag/ingestion/OcrTextExtractingDocumentProcessor.java` — calls the OCR engine to fill in text
- `rag/ingestion/OcrNoiseCleaningDocumentProcessor.java` — cleans common OCR noise

:::note
AI4J itself does not ship any OCR engine. `OcrTextExtractor` is an interface; you plug Tesseract, PaddleOCR, or a cloud OCR service in behind it.
:::

### 7.1 `OcrTextExtractingDocumentProcessor`: OCR fallback for blank documents

Its trigger condition is deliberately restrained — **OCR runs only when content is empty**:

```java
public LoadedDocument process(IngestionSource source, LoadedDocument document) {
    if (document == null || !isBlank(document.getContent())) {
        return document;            // already has text, skip
    }
    if (!extractor.supports(source, document)) {
        return document;            // engine does not support this source, skip
    }
    String extracted = extractor.extractText(source, document);
    if (isBlank(extracted)) {
        return document;            // nothing extracted, skip
    }
    // write back content and set ocrApplied = true
}
```

In other words: documents with selectable text never pay an extra OCR cost; only genuinely blank scanned documents go down the OCR branch.

### 7.2 `OcrTextExtractor`: you implement the OCR engine adapter

```java
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractor;
import io.github.lnyocly.ai4j.rag.ingestion.LoadedDocument;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionSource;

// Example: wrap a real OCR service (Tesseract / PaddleOCR / cloud API)
public class MyOcrExtractor implements OcrTextExtractor {
    @Override
    public boolean supports(IngestionSource source, LoadedDocument document) {
        // Only run OCR on images and scanned PDFs
        String name = source == null ? null : source.getName();
        return name != null && (name.endsWith(".png") || name.endsWith(".pdf"));
    }

    @Override
    public String extractText(IngestionSource source, LoadedDocument document) throws Exception {
        // Call your OCR engine and return the recognized plain text
        return callOcrEngine(source, document);
    }
}
```

`supports(...)` decides which sources go through OCR; `extractText(...)` returns the recognized text. When extraction comes back blank, the processor returns the document unchanged instead of writing in an empty string.

### 7.3 `OcrNoiseCleaningDocumentProcessor`: cleaning OCR text noise

OCR output often carries formatting noise; chunking it directly degrades recall quality. This processor handles three common problems:

| Noise | Example | After cleaning |
| --- | --- | --- |
| Cross-line hyphenation | `docu-\nment` | `document` |
| Letter-by-letter spacing | `H e l l o` | `Hello` |
| Extra whitespace / consecutive blank lines | multiple spaces, consecutive blank lines | collapsed, at most one blank line kept |

After cleaning it sets an `ocrNoiseCleaned = true` flag for downstream tracing.

### 7.4 How to wire OCR into ingest

The OCR processors are **not wired by default**; you have to pass them in explicitly via `IngestionRequest.documentProcessors` or the pipeline constructor. The typical combination is OCR extraction first, then noise cleaning:

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

Order matters: **extract first, clean second**. Cleaning only takes effect on documents that already have text, and the scanned document's text is supplied by the extraction step.

## 8. Canonical metadata: `RagMetadataKeys` and tenant / business / version isolation

Every chunk in the vector store carries a set of metadata; these keys are not arbitrary names but **canonical constants** defined by `RagMetadataKeys`. They are written into each chunk's metadata at ingestion time by `DefaultMetadataEnricher`, and downstream capabilities — retrieval, citation, deletion — depend on them.

Source entry points:

- `rag/RagMetadataKeys.java` — canonical key constants
- `rag/ingestion/DefaultMetadataEnricher.java` — writes these keys into chunk metadata

### 8.1 Full canonical key list

| Constant | Literal value | Meaning |
| --- | --- | --- |
| `RagMetadataKeys.CONTENT` | `content` | chunk text, for display |
| `RagMetadataKeys.DOCUMENT_ID` | `documentId` | stable document identity |
| `RagMetadataKeys.CHUNK_ID` | `chunkId` | stable chunk identity |
| `RagMetadataKeys.SOURCE_NAME` | `sourceName` | source file name |
| `RagMetadataKeys.SOURCE_PATH` | `sourcePath` | source path |
| `RagMetadataKeys.SOURCE_URI` | `sourceUri` | source URI |
| `RagMetadataKeys.SECTION_TITLE` | `sectionTitle` | section title |
| `RagMetadataKeys.CHUNK_INDEX` | `chunkIndex` | chunk index |
| `RagMetadataKeys.PAGE_NUMBER` | `pageNumber` | page number |
| `RagMetadataKeys.TENANT` | `tenant` | tenant identifier |
| `RagMetadataKeys.BIZ` | `biz` | business line identifier |
| `RagMetadataKeys.VERSION` | `version` | document version |
| `RagMetadataKeys.CONTENT_HASH` | `contentHash` | SHA-256 of chunk content (written by the pipeline, used for incremental skip) |

:::note
`contentHash` is not written by `DefaultMetadataEnricher`; `IngestionPipeline` computes it separately after chunk normalization and stuffs it into metadata, specifically to serve the `skipExistingContentHash` incremental ingestion in section 6. The other keys are written by the enricher.
:::

### 8.2 `tenant / biz / version`: the trio for retrieval isolation

These three keys solve the isolation problem of "one vector store serving multiple tenants, multiple business lines, and multiple versions". They originate from `RagDocument` and are set along with the document at ingestion:

```java
IngestionResult result = ingestionPipeline.ingest(IngestionRequest.builder()
        .dataset("kb_docs")
        .embeddingModel("text-embedding-3-small")
        .document(RagDocument.builder()
                .sourceName("员工手册")
                .sourcePath("/docs/handbook.md")
                .tenant("acme")        // which tenant
                .biz("hr")             // which business line
                .version("2026.03")    // which version
                .build())
        .source(IngestionSource.text("第一章 假期政策。第二章 报销政策。"))
        .build());
```

After ingestion, every chunk's metadata carries `tenant=acme`, `biz=hr`, and `version=2026.03`. At retrieval time you can use these three keys as metadata filters:

- **Tenant isolation**: company A's queries only hit `tenant=A` chunks; company B's data is never recalled
- **Business isolation**: the HR knowledge base and the finance knowledge base (`biz=hr` vs `biz=finance`) share one store without cross-contamination
- **Version isolation**: when new and old document versions coexist (`version=2026.03` vs `version=2025.12`), filter by version or switch over gradually

You can also skip constructing `RagDocument` explicitly and instead drop these keys directly into the source/document metadata map; `IngestionPipeline.resolveDocument(...)` reads them out and attaches them to the document identity.

### 8.3 Why you must use the canonical keys

If you casually use custom names like `"tenantId"`, `"businessLine"`, `"ver"`, two problems arise:

- `DefaultMetadataEnricher` will not write them, and downstream retrieval / deletion / citation capabilities cannot find them either
- Different ingestion code paths each invent their own names, leaving multiple coexisting key schemes in the same store, so isolation is effectively void

So any field intended for isolation, scope filtering, or version management must go through the canonical constants in `RagMetadataKeys`; only genuinely business-specific tags (e.g. `department`, `customTag`) use arbitrary keys.

## 9. Where you can extend

This is the most engineering-valuable part of this page.

You can replace or extend:

- `DocumentLoader`
- `LoadedDocumentProcessor` (OCR extraction and noise cleaning both hook in here, see section 7)
- `Chunker`
- `MetadataEnricher`
- `batchSize`
- `upsert` strategy

This means AI4J's ingest is not a closed black box but a **pluggable knowledge-engineering pipeline**.

## 10. What it is not responsible for

This chain is only responsible for getting knowledge into the store properly. It is not responsible for:

- retrieval ranking
- final answer assembly
- user permission filtering
- citation UI rendering

These capabilities belong to:

- retriever / reranker
- rag service
- the upstream application

Pushing all of these into `IngestionPipeline` would blur the boundaries.

## 11. Common pitfalls

### 11.1 Sloppy `dataset` design

Later retrieval isolation, deletion, and replay all become hard to govern.

### 11.2 Caring only about embedding, not metadata

Vectors can be recalled, but the evidence chain is unclear; tenant / business / version isolation (section 8) also cannot be built.

### 11.3 Treating chunking as a minor detail

In practice, many RAG quality problems are rooted here.

### 11.4 Ingesting scanned documents without OCR

Scanned PDFs load with empty content; without the `OcrTextExtractingDocumentProcessor` fallback, the entire document is silently dropped.

## 12. Design summary

> AI4J's `IngestionPipeline` is an explicit RAG ingestion orchestration layer: source loading, text processing, chunking, metadata, embedding, and vector upsert are all chained into a unified pipeline here. Its value is not "saving you glue code" but stabilizing document identity, knowledge-engineering strategy, and tenant / business / version isolation.

## 13. Further reading

- [Search and RAG / Chunking Strategies](/docs/capabilities/rag/chunking-strategies)
- [Search and RAG / Vector Store and Backends](/docs/capabilities/rag/vector-store-and-backends)
- [Search and RAG / Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
