---
title: "Embedding"
description: "Explains the AI4J embedding layer: a thin interface with strong constraints. It unifies provider calls but only supports OPENAI/OLLAMA, ingest and query must use the same model, and mixing is not auto-prevented by the framework — it is part of the index-level protocol."
tags: [concept]
---

# Embedding

In the current AI4J implementation, `embedding` is not a standalone API. It is a shared foundation that both ends of offline RAG depend on:

- At ingest time, it turns chunks into vectors
- At query time, it turns the question into a vector

If you get this layer wrong, everything you read afterward about `DenseRetriever`, `IngestionPipeline`, and `VectorStore` will be distorted.

## 1. The abstraction entry point is genuinely thin

There is a single unified interface:

- `service/IEmbeddingService.java`

The signature is deliberately restrained:

```java
EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq)
EmbeddingResponse embedding(Embedding embeddingReq)
```

This tells you the design intent of the AI4J embedding layer:

- Unify the input/output structure
- Push provider differences down into the implementation classes
- Do not pull RAG semantics up into the interface layer

In other words, the embedding layer itself does not know:

- What a dataset is
- What a chunk is
- How retrieval should be done

It is only responsible for "turning input text into a vector".

## 2. Which embedding providers the factory currently supports

`AiService.createEmbeddingService(platform)` currently supports only:

- `OPENAI`
- `OLLAMA`

This is worth spelling out in the docs, because it does not match the surface area of the chat models. That is, within AI4J's current platform support:

- There are more chat providers than embedding providers
- The rerank provider set is yet another separate collection

So "this platform can do chat" does not imply "this platform can also serve directly as an embedding provider".

## 3. How embedding is called inside the ingest chain

What actually wires embedding into the ingestion main line is:

- `rag/ingestion/IngestionPipeline.java`

Before `buildRecords(...)`, it first does:

```java
List<List<Float>> vectors = embed(contents, request.getEmbeddingModel(), request.getBatchSize());
```

This `embed(...)` method has a few key implementation details:

- Default batch size `DEFAULT_BATCH_SIZE = 32`
- Each batch is sent as a single `Embedding.input` carrying a `List<String>`
- Results are reordered from `EmbeddingResponse.data` using `EmbeddingObject.index`
- If the returned count is short, it throws immediately

This shows that AI4J's ingest embedding is not "one request per chunk" — it already does basic batching and order restoration.

## 4. Why `EmbeddingObject.index` is critical

`IngestionPipeline.extractEmbeddings(...)` puts the provider's returned results back into their original order by `index`.

This implementation detail dictates that:

- A provider may return multiple vectors per batch
- But the final result must be restorable to the original request text order

If the response has:

- Too many `index` values missing
- Fewer vectors than the batch size
- A vector missing for some index

The current implementation throws `IllegalStateException` in all of these cases.

This is stricter than "silently skipping bad data", but it is the correct stance for RAG ingest, because:

- Once the vector order is misaligned
- The subsequent `VectorRecord` will write the wrong vector onto the wrong chunk

This kind of error is harder to track down than a direct failure.

## 5. How embedding is called inside the query chain

On the query side, the component that actually uses embedding is:

- `rag/DenseRetriever.java`

It:

1. Validates that `query.query` is non-empty
2. Requires `query.embeddingModel` to be non-empty
3. Calls `embeddingService.embedding(...)` to generate the query vector
4. Hands the vector to `VectorStore.search(...)`

This shows that AI4J's dense retrieval does not hide embedding inside the vector store. It explicitly splits the work into two steps:

- query to vector
- vector to hits

The benefit of this design is clean layering; the cost is that embedding model consistency is something you have to govern yourself.

## 6. Why "same-model consistency" is a constraint here, not a suggestion

From the current implementation, both the ingest and query sides only honor the embedding model name you pass in. The framework does not automatically check:

- Whether the model used at ingest matches the one used at query
- Whether the dimensions line up
- Whether the index has mixed multiple embedding schemes

This means that once you:

- Ingest with model A
- Query with model B

Even if the code runs, retrieval quality will likely distort immediately, or the backend will error out on dimension mismatch.

So embedding, under AI4J's current semantics, is not "a provider config you can swap at any time". It is:

**part of the index-level protocol.**

## 7. Why embedding and `dataset` are not on the same layer

This structural decision is also worth spelling out.

`IEmbeddingService` does not care about `dataset`, because:

- dataset is a vector-storage and retrieval boundary
- embedding is just a text-to-vector transform

So in the current implementation:

- `IngestionPipeline` runs embedding first
- Then assembles vectors and metadata into a `VectorRecord`
- Finally hands everything, together with the `dataset`, to `VectorStore.upsert(...)`

This layering is sound. Because the moment the embedding layer becomes aware of dataset, it starts coupling to storage strategy.

## 8. The five most common pitfalls in the current implementation

### 8.1 Assuming that if chat works, embedding must work too

`AiService` currently has a noticeably narrower provider set for embedding.

### 8.2 Using different embedding models for ingest and query

This is the most common and most hidden source of RAG quality problems.

### 8.3 Ignoring batch behavior

`IngestionPipeline` batches by 32 by default. Different providers' throughput, rate-limiting, and timeout characteristics can all surface here.

### 8.4 Only checking that the provider returned success, not validating vector count and order

The AI4J implementation validates strictly. Do not "optimize" this check away in an upper-layer wrapper.

### 8.5 Treating embedding as the sole determinant of RAG quality

chunking, metadata, dataset, retrieval, and rerank all matter just as much.

## 9. What this layer does not do for you today

The embedding layer currently does not solve these for you directly:

- Text cleaning
- chunk boundary design
- Vector caching
- Model version governance
- Dimensionality migration
- Recall-quality evaluation

Its responsibility is deliberately narrow: unify provider calls and consume their results.

## 10. The most robust usage guidance, from the current source

If you want to build stable RAG on AI4J, the most robust practice at the embedding layer is:

1. Pin one embedding model first
2. Run it end-to-end through both ingest and query
3. Treat the model name as part of the index protocol
4. When changing models, rebuild against a new dataset / new index

:::warning
Do not keep reusing old vectors while swapping the query model. Under the current architecture, this kind of mixing is not auto-prevented.
:::

## 11. The takeaway from this page

AI4J's current embedding is the shared underlying transform layer for offline RAG:

- The ingest side relies on it to turn chunks into vectors in batches
- The query side relies on it to turn the question into a vector before handing it to `VectorStore`

The interface itself is thin. What matters is its position in the main chain and its consistency constraint. Treating it as "part of the index protocol" is more accurate than treating it as "a little config you can switch anytime".

## 12. Further reading

- [Ingestion Pipeline](/docs/capabilities/rag/ingestion-pipeline)
- [Vector Store and Backends](/docs/capabilities/rag/vector-store-and-backends)
- [Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
