---
title: "Search and RAG Overview"
description: "Establish the source-code mental model of AI4J Search & RAG: two parallel boundaries — the offline private-domain route IngestionPipeline→VectorStore→Retriever→Reranker→ContextAssembler and the online public-web route ChatWithWebSearchEnhance — plus the distinction between the default skeleton and optional enhancements."
tags: [concept]
---

# Search and RAG Overview

If this chapter were written only as "supports online search, vector store, reranking, and citations," it would miss the real point.

What actually matters is: **how AI4J chains these scattered capabilities into a single knowledge-augmentation pipeline.**

From the source, this chapter has at least two main branches:

- Online open-knowledge augmentation: `ChatWithWebSearchEnhance`
- Offline private-domain knowledge augmentation: `IngestionPipeline -> VectorStore -> Retriever -> Reranker -> ContextAssembler`

Treat these two lines separately, and you will not conflate Search, RAG, online search, and vector store into one vague term.

## 1. First, its real position inside the Core SDK

The core packages this chapter spans are more than one:

- `rag`
- `rag.ingestion`
- `vector.store`
- `rerank`
- `websearch`
- `document`
- `service`

This already tells you it is not a single-point API, but a cross-package, cooperating knowledge-augmentation subsystem.

## 2. The real main wiring point in the source

If you want to find "how the default chain is assembled," the most worthwhile place to look is not a demo, but:

- `service/factory/AiService.java`

From here you can directly see several default wirings:

```java
getIngestionPipeline(platform, vectorStore)
getRagService(platform, vectorStore)
webSearchEnhance(chatService)
getModelReranker(platform, model)
```

That is, in the current AI4J code, Search & RAG is not a loose concept but has already been abstracted by the factory layer into a few fixed entry points.

## 3. What the default main line of offline RAG actually is

If you go through `AiService.getRagService(platform, vectorStore)`, the default wiring is:

```java
new DefaultRagService(
    new DenseRetriever(getEmbeddingService(platform), vectorStore),
    new NoopReranker(),
    new DefaultRagContextAssembler()
)
```

If you need pre-retrieval query planning, use the overloaded entry points:

```java
getRagService(platform, vectorStore, queryPlanner)
getModelRagQueryPlanner(platform, model)
```

These few lines are critical, because they spell out the true shape of the default RAG:

- The default retriever is `DenseRetriever`
- By default no model reranking runs; it uses `NoopReranker`
- Default citation and context assembly go through `DefaultRagContextAssembler`
- By default no query planning runs; only an explicitly passed `RagQueryPlanner` enables pre-retrieval processing, and the built-in model planner only does rewrite by default — multi-strategy requires explicit opt-in

So if you have not explicitly wired in hybrid retrieval or a model reranker, AI4J's default is not a "full-featured RAG bundle," but rather:

**dense retrieval + no-op rerank + default citation-aware assembly**

If you need BM25 or Dense + BM25 hybrid recall, you do not switch to another `RagService` factory — you swap the underlying `Retriever`:

```java
Retriever hybrid = new HybridRetriever(Arrays.asList(denseRetriever, bm25Retriever));
RagService rag = new DefaultRagService(hybrid);
```

See [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval).

## 4. How the default ingestion main line runs

If you go through `AiService.getIngestionPipeline(platform, vectorStore)`, the default wiring is:

- `TextDocumentLoader`
- `TikaDocumentLoader`
- `RecursiveTextChunker(1000, 200)`
- `WhitespaceNormalizingDocumentProcessor`
- `DefaultMetadataEnricher`

And `IngestionPipeline` itself splits a single ingest request into:

1. source load
2. loaded document process
3. `RagDocument` resolve
4. chunk normalize
5. embedding batch generate
6. vector record build
7. `VectorStore.upsert(...)`

This shows that AI4J's current offline RAG is not "done once the vector store is connected," but an independent ingestion orchestration chain.

## 5. Why online search does not belong to the offline RAG main line

The online search entry point is:

- `websearch/ChatWithWebSearchEnhance.java`

What it does is not go through a `Retriever`, but wrap an `IChatService`, and then:

- Use the last message as the search query
- Call SearXNG
- Splice the search result JSON directly into the prompt

So the positioning of Online Search is:

- Real-time public-web supplementation
- Prompt-level augmentation

Rather than:

- Private-domain knowledge base retrieval
- Part of the chunk / vector / rerank chain

At the product layer both lines might be called "retrieval augmentation," but at the source level they are entirely different implementation routes.

## 6. The 4 pivotal objects in this chapter

If you only grasp the core skeleton, the 4 most worth remembering first are:

- `IngestionPipeline`
- `VectorStore`
- `DefaultRagService`
- `ChatWithWebSearchEnhance`

They correspond respectively to:

- How knowledge enters the system
- How vectors are stored and fetched
- How knowledge is recalled and turned into context
- How open-web knowledge is temporarily injected into a conversation

The later `HybridRetriever`, `ModelReranker`, `RagTrace`, and `RagCitation` are all enhancement parts built around this main skeleton.

## 7. Which capabilities in the current default chain are "optional enhancements"

From the code, the following are not part of the default main line — you only get them by wiring them in explicitly:

- `HybridRetriever`
- `RagQueryPlanner`
- `ModelReranker`
- A custom `Chunker`
- A custom `MetadataEnricher`
- A custom `RagContextAssembler`, e.g. `TokenAwareRagContextAssembler` which uses a token budget to cap the injected context length

This matters, because a lot of documentation writes these as "complete capabilities RAG has by default."
AI4J is currently not designed that way; it is closer to:

**First give you a stable skeleton, then let you replace and enhance it layer by layer.**

## 8. Which boundaries you most need to hold while reading this chapter

### 8.1 `Model Access` is not `Search & RAG`

`Model Access` addresses:

- How to call the chat model
- How to call the embedding model
- How to call the rerank provider

`Search & RAG` addresses:

- How external knowledge enters the system
- How, once inside, it is retrieved, reranked, and cited

### 8.2 `MCP` is not `Search & RAG`

MCP addresses protocol-based external capability wiring.
RAG addresses knowledge retrieval and context augmentation.
The two can cooperate, but they are not the same layer of abstraction.

### 8.3 `Agent` is also not `Search & RAG`

Agent handles multi-step decisions, tool usage, and state progression; RAG supplies knowledge to the model.
It is more accurate to think of RAG as a knowledge-supply layer for agents than as "another agent runtime."

## 9. Where the current implementation is most easily misdocumented

### 9.1 Writing Online Search as a unified search framework

It is currently only an `IChatService` wrapper layer, not part of the unified `Retriever` system.

### 9.2 Writing default RAG as "reranking and hybrid retrieval already integrated"

The default factory is actually `DenseRetriever + NoopReranker`.

### 9.3 Treating Vector Store as the sole protagonist

The vector store is only the middle layer. Without ingest, metadata, retriever, and assembler, this chain is not complete at all.

### 9.4 Using only `finalTopK` to control context length

`finalTopK` controls the final hit count, not the token count. If enterprise RAG needs to avoid overflowing the context window, it should explicitly swap the default assembler for `TokenAwareRagContextAssembler` and give it a genuinely usable context token budget.

### 9.5 Treating citation as proof of final answer grounding

The current citation is a product of context assembly, not strict proof that "the model actually used this evidence."

## 10. Recommended reading order

If you want to build the most reliable source-code mental model, read in this order:

1. [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
2. [Chunking Strategies](/docs/core-sdk/search-and-rag/chunking-strategies)
3. [Embedding](/docs/core-sdk/search-and-rag/embedding)
4. [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
5. [Query Planning](/docs/core-sdk/search-and-rag/query-planning)
6. [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
7. [Rerank](/docs/core-sdk/search-and-rag/rerank)
8. [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
9. [Online Search](/docs/core-sdk/search-and-rag/online-search)

The core logic of this order is:

- First see how knowledge comes in
- Then see how knowledge is stored and fetched
- Then see how fetched results are corrected and assembled
- Finally see the public-web augmentation side branch

## 11. The conclusion most worth remembering from this page

AI4J's current Search & RAG is not a set of scattered feature points, but two parallel, clearly bounded knowledge-augmentation routes:

- Offline private-domain route: `IngestionPipeline -> VectorStore -> Retriever -> Reranker -> ContextAssembler`
- Online public-web route: `ChatWithWebSearchEnhance -> prompt augmentation`

The skeleton the default factory gives you runs out of the box, but the enhancement parts do not turn on automatically.
Hold on to this "default skeleton + optional enhancements" structure, and the pages that follow will not lose focus.
