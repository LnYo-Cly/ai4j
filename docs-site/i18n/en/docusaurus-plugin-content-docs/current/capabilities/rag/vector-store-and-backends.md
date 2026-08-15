---
title: "Vector Store and Backends"
description: "How the AI4J VectorStore contract unifies five backends (Pinecone/Qdrant/Milvus/PgVector/Redis): dataset is a hard boundary, capabilities() explicitly exposes returnStoredVector/metadataLookup differences — one call surface, without flattening storage reality."
tags: [concept]
---

# Vector Store and Backends

If this layer of AI4J were described only as "supports Pinecone / Qdrant / Milvus / PgVector / Redis", the information density would be very low.
What actually matters is: **how it uses a unified `VectorStore` contract to bring different backends together, without pretending those backends are fully equivalent.**

## 1. How broad is the unified contract

The `VectorStore` interface itself has only 5 methods:

```java
int upsert(VectorUpsertRequest request) throws Exception;
List<VectorSearchResult> search(VectorSearchRequest request) throws Exception;
boolean delete(VectorDeleteRequest request) throws Exception;
boolean exists(VectorExistsRequest request) throws Exception; // default false
VectorStoreCapabilities capabilities();
```

This abstraction is deliberately restrained, yet it already covers the three core things RAG needs most:

- vector writes
- vector retrieval
- data deletion
- metadata-only existence check (optional capability)

plus one very important thing:

- backend capability declaration

In other words, AI4J today does not tell you about backend differences through "doc footnotes"; it has formally turned those differences into `capabilities()`.

## 2. `dataset` at this layer is not an auxiliary field, it is a hard boundary

Look at the request objects and you will see:

- `VectorUpsertRequest.dataset`
- `VectorSearchRequest.dataset`

are both primary fields, not optional tags.

More importantly, all five built-in backends treat `dataset` as required:

- Pinecone: `requiredDataset(...)`
- Qdrant: `requiredDataset(...)`
- Milvus: `requiredDataset(...)`
- PgVector: `requiredDataset(...)`
- Redis: `requiredDataset(...)`

That is, in the current AI4J implementation, `dataset` is not "fill this in if you want to split databases"; it is:

**the default boundary for every vector write, search, and delete operation.**

## 3. The same `dataset` lands in different places per backend

This is exactly where a unified interface does not imply identical semantics.

### Pinecone

`dataset` is mapped to:

- `namespace`

### Qdrant

`dataset` is embedded into:

- the URL template path

It acts more like collection-level scope.

### Milvus

`dataset` is written to:

- `collectionName`

### PgVector

`dataset` is treated as:

- a filter field / query condition within the table

### Redis

`dataset` is used as:

- Hash key segment (`<keyPrefix><dataset>:<id>`) + a RediSearch `dataset` TAG field filter

It lands in a single Redis instance that serves many purposes, isolating the key space and applying index filtering by dataset.

:::warning Redis backend dependencies
Redis is an **opt-in backend**: it requires Redis Stack (with the RediSearch module); Jedis is an optional dependency, and users must add `redis.clients:jedis:4.x` to their pom themselves — 4.x is the last major version that supports JDK 8, while 5.x requires JDK 17 and is not bytecode-compatible with ai4j's JDK 8 target. If your project is already pinned to Jedis 5.x and cannot downgrade, use one of the other vector backends instead (Milvus/Qdrant/Pinecone/PgVector); they all go through the same `VectorStore` contract.
:::

So from a business viewpoint they are all called `dataset`, but from a storage reality viewpoint, it corresponds to different things per backend:

- namespace
- collection
- URL scope
- relational filter column
- redis key prefix segment + TAG filter

This is also why you should not read "switch backends" as "swap the connection string".

## 4. What retrieval semantics `VectorSearchRequest` actually abstracts

The search request currently exposes these key fields:

- `dataset`
- `vector`
- `topK`, default `10`
- `filter`
- `includeMetadata`, default `true`
- `includeVector`, default `false`

This design shows that AI4J's current vector search abstraction is not just "give a vector, find the nearest neighbors"; it has already brought:

- the retrieval boundary
- filter conditions
- the granularity of returned content

into the unified interface.

The return object `VectorSearchResult` correspondingly provides:

- `id`
- `score`
- `content`
- `vector`
- `metadata`

This is precisely the precondition for `DenseRetriever` to later load those results back into `RagHit`.

## 5. Why `capabilities()` is the most valuable design in this layer

All five built-in backends explicitly return `VectorStoreCapabilities`.
And this is not decorative information — there are real differences inside.

In the current source:

- Pinecone: `dataset=true` `metadataFilter=true` `metadataLookup=false` `deleteByFilter=true` `returnStoredVector=true`
- Qdrant: `dataset=true` `metadataFilter=true` `metadataLookup=true` `deleteByFilter=true` `returnStoredVector=true`
- Milvus: `dataset=true` `metadataFilter=true` `metadataLookup=true` `deleteByFilter=true` `returnStoredVector=false`
- PgVector: `dataset=true` `metadataFilter=true` `metadataLookup=true` `deleteByFilter=true` `returnStoredVector=false`
- Redis: `dataset=true` `metadataFilter=true` `metadataLookup=true` `deleteByFilter=true` `returnStoredVector=false`

The most notable points here:

- **`returnStoredVector` is not supported by every backend.**
- **`metadataLookup` is not supported by every backend either.**

`metadataLookup` corresponds to `VectorStore.exists(...)`: it serves the "does a record matching this metadata exist" check, for example letting ingestion skip duplicate chunks by `contentHash`.
The current Pinecone wrapper has no metadata-only lookup, so it keeps the default `false` and does not fake the capability just to "look unified".

That means: if your upper-layer logic relies on "fetching the stored vector back during search", then Pinecone / Qdrant are currently the more natural fit; if you rely on "checking by hash whether a record already exists before ingestion", prefer Qdrant / Milvus / PgVector / Redis.

This is the point of `capabilities()`:
on top of a unified interface, it still lets you see the real differences.

### 5.1 `exists(...)` is not vector search

`VectorStore.exists(VectorExistsRequest)` is a metadata-only lookup for incremental ingestion skipping.
It takes no query vector and returns no similarity result; it only answers "does a record matching this filter exist under this dataset".

So it is an optional capability:

- Qdrant: via scroll/filter
- Milvus: via query/filter
- PgVector: via SQL metadata filter
- Redis: via a RediSearch filter-only query
- Pinecone: the current wrapper keeps the default `false` and does not fake a metadata-only lookup

:::note
Callers must check `capabilities().isMetadataLookup()` and must not assume every vector store supports it.
:::

## 6. How `VectorStore` relates to `DenseRetriever`

`DenseRetriever` does not care which vector store sits behind it.
It only does two things:

1. Generate the query vector with `IEmbeddingService`
2. Call `vectorStore.search(...)`

This means `VectorStore` sits in the RAG chain as:

- downstream of embedding / query vector
- upstream of the concrete backend
- emitting a unified `VectorSearchResult`

That is its real abstraction value.
The point is not "collect 4 SDK names into one list", but to decouple the upstream and downstream of the retrieval chain.

## 7. Why this layer still cannot hide every backend reality from you

Although `VectorStore` already provides a unified abstraction, AI4J today does not fully flatten the following for you:

- backend operations and maintenance
- initialization strategy for the vector column or collection
- dimension management
- performance characteristics
- filter expression cost
- SaaS vs. self-hosted deployment differences

So the correct reading of this layer's abstraction is not "backends are fully isomorphic", but rather:

**The main-chain call style is unified, but storage reality still exists.**

## 8. The 5 things most likely to trip you up in the current implementation

### 8.1 Treating `dataset` as a tag

In the current implementation it is the boundary for writes, searches, and deletes — not casually-nullable metadata.

### 8.2 Ignoring `capabilities()` differences

Especially `returnStoredVector` / `metadataLookup`, which are not supported by every backend.

### 8.3 Choosing a backend purely by product name

What you should really look at is:

- SaaS-first or self-hosted-first
- whether you want to leverage existing PostgreSQL infrastructure
- whether you need to retrieve stored vectors back

### 8.4 Letting upper layers depend directly on backend-specific semantics

Once you write it that way, the `VectorStore` abstraction is bypassed and hollowed out.

### 8.5 Assuming the unified interface will automatically handle dimension and schema governance

Today it only unifies the call; it does not do data governance for you.

## 9. From the current source, the safest usage guidance

Within AI4J's current architecture, a very stable strategy is:

1. Design `dataset` as a stable boundary up front
2. Write the business main chain against `VectorStore`
3. Only branch on `capabilities()` to handle backend differences when necessary
4. Do not leak backend-specific logic into the retriever / ingestion upper layers

This way you get the benefits of a unified interface without being misled by "false equivalence".

## 10. The conclusion worth remembering from this page

AI4J's current vector storage layer is, in essence:

- unifying upsert / search / delete / exists through `VectorStore`
- unifying the knowledge boundary through `dataset`
- explicitly exposing backend differences through `VectorStoreCapabilities`

What it does is "unify the main-chain abstraction", not "make backend reality disappear".
Once you see this layer clearly, you can move on to ingest, dense retrieval, and hybrid, and know which problems belong to the vector layer and which do not.

## 11. Further reading

- [Embedding](/docs/capabilities/rag/embedding)
- [Ingestion Pipeline](/docs/capabilities/rag/ingestion-pipeline)
- [Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
