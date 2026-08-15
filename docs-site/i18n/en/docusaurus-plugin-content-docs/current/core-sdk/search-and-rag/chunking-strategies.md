---
title: "Chunking Strategies"
description: "Explains the actual behavior and boundaries of AI4J's default RecursiveTextChunker: it only fills documentId/content/chunkIndex, does not auto-generate chunkId, page numbers, or section metadata, and how chunk boundary stability governs downstream retrieval deduplication and citation quality."
tags: [concept]
---

# Chunking Strategies

Many RAG docs describe `chunking` as "cutting a long document into a few pieces."
In AI4J, that framing is too thin. From the source, chunking determines far more than text length — it also determines:

- whether hit boundaries are stable
- whether `documentId + chunkIndex` is reusable
- whether citations can trace back to the correct passage
- whether metadata can flow all the way through to retrieval results

So what this page actually covers is: **what AI4J's current default chunker does, and what it deliberately does not do for you.**

## 1. Where the source entry points are

The four most important entry points today are:

- `rag/ingestion/Chunker.java`
- `rag/ingestion/RecursiveTextChunker.java`
- `rag/RagChunk.java`
- `document/RecursiveCharacterTextSplitter`

The default implementation is:

```java
public class RecursiveTextChunker implements Chunker
```

Behind it is not a semantic splitter; it is a wrapper layer over a character-based recursive splitter.

## 2. The `Chunker` contract is deliberately thin

The interface signature is:

```java
List<RagChunk> chunk(RagDocument document, String content)
```

This shows AI4J's design intent at the chunking layer is unambiguous:

- give you a document object
- give you a piece of raw text
- you return a list of `RagChunk`

It does not mandate:

- a PDF parsing strategy
- a Markdown structure preservation strategy
- a heading detection strategy
- a table-specific splitting strategy

In other words, the current chunking layer is an **open contract**, not a product layer with "enough document intelligence built in."

## 3. What the default `RecursiveTextChunker` actually does

The default implementation is very direct:

1. If `content` is empty, return an empty list
2. Call `splitter.splitText(content)` to obtain a number of text fragments
3. Generate a `RagChunk` per fragment
4. Populate only:
   - `documentId`
   - `content`
   - `chunkIndex`

The most important fact here is:

:::note
**The default chunker does not automatically populate `chunkId`, `pageNumber`, `sectionTitle`, or `metadata`.**
:::

Yet `RagChunk` explicitly supports these fields:

- `chunkId`
- `documentId`
- `content`
- `chunkIndex`
- `pageNumber`
- `sectionTitle`
- `metadata`

This shows the default implementation is positioned as "a text-splitting base component that runs end to end," not "a complete semantic-structure splitter."

## 4. Why the default strategy is sufficient, but far from complete

The default `RecursiveTextChunker` has these strengths:

- simple
- stable
- no extra model dependencies
- high reuse from the existing `RecursiveCharacterTextSplitter`

But its boundaries are equally clear:

- it splits by text fragment, not by business structure
- it does not understand heading hierarchy
- it does not know whether paragraphs belong to the same section
- it does not know how to preserve boundaries for tables, code blocks, FAQs, or configuration blocks
- it does not auto-produce a reusable chunk primary key

So if your corpus is:

- API documentation
- regulations
- contracts
- academic papers
- multi-page PDFs

the default splitting is usually fine as a starting point, not as a final solution.

## 5. Why `chunkIndex` matters more than many people think

In AI4J's current implementation, `chunkIndex` is not just a display field.

Several downstream stages may rely on it:

- `HybridRetriever`'s dedup key may fall back to `documentId + chunkIndex`
- `RagHitSupport.stableKey(...)` also uses `chunkIndex`
- during citation / trace troubleshooting, it is often used to locate "which chunk of the document this is"

This implies:

:::warning
Once chunk order becomes unstable, downstream retrieval deduplication, citation positioning, and evaluation comparison all become brittle.
:::

So when customizing a chunker, a robust principle is:

- when document content does not change, keep `chunkIndex` stable
- when re-running ingest, avoid letting chunk boundaries drift frequently

## 6. Why the default implementation does not generate `chunkId`

From the current source, the default chunker does not generate a `chunkId`. This is deliberately deferring an important decision to the business layer:

- whether to use `documentId + chunkIndex`
- whether to use a hash
- whether to encode page, section, or dataset into it

This is not an oversight; it is a boundary choice.

Because once the framework forces a `chunkId` definition on your behalf, everything downstream:

- vector store primary keys
- overwrite-on-write
- incremental updates
- deduplication strategy

gets locked in prematurely.

So AI4J is effectively saying:
**"I give you the `RagChunk` structure, but the chunk primary key strategy is yours to decide."**

## 7. Why chunking directly governs citation quality

When `DefaultRagContextAssembler` generates a citation, it uses the final hit `RagHit.content` directly as the snippet.

This means citation quality depends first on:

- whether the chunk is complete enough
- whether it happens to preserve the context the answer needs
- whether it has not stitched two unrelated passages together

If the chunk is too fragmented:

- the snippet loses semantics
- the answer may be missing its causal prelude

If the chunk is too large:

- the snippet becomes too long
- irrelevant context dilutes recall quality
- the model reads more noise

So citation quality is not decided by the citation subsystem alone — a large part of it is decided upstream by chunking.

## 8. The most common pitfalls in the current default chunking

### 8.1 Splitting text only, without populating metadata

`DenseRetriever` later tries to recover the following from metadata:

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`

If this information is not populated during ingest, downstream retrieval hits and citations become noticeably "blind."

### 8.2 Tuning chunk size by token cost alone

Many people stare only at context cost when tuning `chunkSize`, ignoring:

- query hit granularity
- section completeness
- table/list fidelity
- rerank input quality

This makes the chunk look token-cheap while actual recall quality drops.

### 8.3 Letting chunk boundaries drift on every corpus rebuild

Once boundaries drift, comparing historical evaluations against production issues becomes hard — especially once you already have traces, citations, and offline evaluations.

## 9. When you should implement `Chunker` yourself

For these scenarios, customizing directly is recommended:

- you need to split by heading hierarchy
- you need to preserve page numbers
- you need to push section information into metadata
- you need a stable `chunkId`
- you are handling structured documents rather than plain text

A dependable custom chunker should at least consider:

- whether chunk boundaries are stable
- whether `documentId` is traceable
- whether `pageNumber` / `sectionTitle` can be backfilled
- whether the metadata is rich enough to support downstream citation / rerank / trace

## 10. The most robust design suggestions given AI4J's current implementation

If you are building production-grade RAG on this framework today, the most robust advice for the chunking layer is:

1. First run the pipeline end to end with the default `RecursiveTextChunker`
2. Pin down your primary key and metadata conventions
3. Then switch to a custom `Chunker` based on document type

Do not shove all the complexity into the retriever or reranker upfront.
Because the root cause of many "inaccurate retrieval" issues is actually settled here, at chunking time.

## 11. The conclusion worth remembering from this page

The essence of AI4J's current default chunking is:

- use `RecursiveCharacterTextSplitter` for recursive text splitting
- produce the most basic `RagChunk`
- populate only `documentId`, `content`, `chunkIndex` by default

It solves "cut text into ingestable blocks"; it does not solve for you:

- structural semantic preservation
- a stable primary key
- page-number and section metadata
- production-grade citation friendliness

So chunking in AI4J is not a minor detail — it is the upstream switch for the entire RAG quality chain.

## 12. Further reading

- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
