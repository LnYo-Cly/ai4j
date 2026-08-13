---
title: "Hybrid Retrieval"
description: "Explains that the AI4J HybridRetriever is essentially a multi-retriever result fusion component rather than a fixed Dense+BM25 bundle: default RRF fuses by rank, deduplicates with a stable key, the fusion score semantics change, and there is deliberately no getHybridRagService convenience entry point."
tags: [concept]
---

# Hybrid Retrieval

In AI4J, `hybrid retrieval` is not a "mysterious black-box retriever" but a very concrete combiner:

- First, let multiple `Retriever`s each produce their own results
- Then deduplicate, fuse, and rerank those results
- Finally produce a unified `List<RagHit>`

The key point of this page is: **AI4J's current hybrid is essentially retrieval-result fusion, not a multi-stage agent.**

## 1. Where the source entry point is

First, six core classes:

- `rag/HybridRetriever.java`
- `rag/Retriever.java`
- `rag/DenseRetriever.java`
- `rag/Bm25Retriever.java`
- `rag/RrfFusionStrategy.java`
- `rag/RagHitSupport.java`

If you only read the doc name, it is easy to think of hybrid as "a fixed Dense + BM25 product capability". But from the implementation, it is really just:

```java
public class HybridRetriever implements Retriever
```

That is, it still obeys the ordinary `Retriever` contract. The upper-layer `DefaultRagService` does not care how many sub-retrievers are inside it; it simply calls it as one retrieval implementation.

## 2. The path a real hybrid retrieval goes through

The current execution order of `HybridRetriever.retrieve(query)` is clear:

1. Iterate over the `retrievers` passed in at construction
2. Each sub-`retriever` runs its own `retrieve(query)`
3. Normalize the hits with `RagHitSupport.prepareRetrievedHits(...)`
4. Let `FusionStrategy` compute a fusion contribution for each hit position
5. Merge identical hits by a stable key
6. Write the fusion score back into `RagHit`
7. Sort by final score in descending order
8. Trim the results by `query.topK`

If you want to grasp the main thread, the one sentence to remember is:

**HybridRetriever is not responsible for "finding knowledge", it is responsible for "merging the results of multiple knowledge finders".**

## 3. The default is not a "fixed Dense + BM25 bundle"

The constructor is:

```java
new HybridRetriever(List<Retriever> retrievers)
```

By default it only gives you two things:

- Combines a set of `Retriever`s
- If you do not specify a fusion strategy, uses `RrfFusionStrategy`

It does not require:

- A dense retriever
- A bm25 retriever
- One dense plus one sparse

So strictly speaking, a more accurate description of AI4J's hybrid is:

**A multi-retriever result fusion component.**

If you only pass in a single `Retriever`, the code still runs; it is just that "hybrid" has no engineering significance at that point.

### 3.1 Minimum usage

Default RAG is dense embedding retrieval:

```java
RagService rag = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);
```

If you want BM25, just give `Bm25Retriever` an in-memory corpus:

```java
Retriever bm25 = new Bm25Retriever(bm25Corpus);
RagService rag = new DefaultRagService(bm25);
```

If you want Dense + BM25 hybrid recall, assemble a `HybridRetriever` yourself:

```java
Retriever dense = new DenseRetriever(
        aiService.getEmbeddingService(PlatformType.OPENAI),
        vectorStore
);

Retriever bm25 = new Bm25Retriever(bm25Corpus);

Retriever hybrid = new HybridRetriever(Arrays.asList(dense, bm25));
RagService rag = new DefaultRagService(hybrid);
```

At query time it is still the ordinary `rag.search(...)`:

```java
RagResult result = rag.search(RagQuery.builder()
        .query("How long do I have to submit a medical reimbursement")
        .dataset("hr-docs")
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .finalTopK(4)
        .includeTrace(true)
        .build());
```

Choosing among these three forms is simple:

| Need | Use |
| --- | --- |
| Semantic similarity, variable phrasing | `DenseRetriever` |
| Proper nouns, error codes, policy numbers, API names | `Bm25Retriever` |
| Both matter | `HybridRetriever(Arrays.asList(dense, bm25))` |

:::note
AI4J currently has no `getHybridRagService(...)` convenience entry point. This is deliberate: hybrid requires you to explicitly provide the BM25 corpus and the sub-retriever combination, so it is not hidden inside `AiService` for now.
:::

### 3.2 Using it together with Query Planning

If you add a `RagQueryPlanner`:

```java
RagService rag = new DefaultRagService(
        hybrid,
        new NoopReranker(),
        new DefaultRagContextAssembler(),
        queryPlanner
);
```

The execution cost becomes:

```text
query variants × retrievers
```

For example, 3 query variants and 2 retrievers means 6 underlying retrievals. Turn it on only when recall quality demands it; do not enable it by default.

### 3.3 How it degrades when a sub-retriever fails

`HybridRetriever` is best-effort:

- When a sub-`Retriever` throws an exception, that path is skipped
- As long as at least one sub-`Retriever` succeeds, fusion continues and a successful result is returned
- Only if all non-empty sub-`Retriever`s fail is the first exception thrown

So in a Dense + BM25 scenario, a briefly unavailable vector store does not necessarily fail the whole RAG retrieval; as long as the BM25 path can still return results, the upper-layer `rag.search(...)` can keep working.

## 4. Fusion algorithms: RRF / RSF / DBSF

`FusionStrategy` is the extension point for fusion strategies. Three built-in implementations fall into two families:

- **Rank-based**: `RrfFusionStrategy` — looks only at rank, not at raw scores
- **Score-based**: `RsfFusionStrategy`, `DbsfFusionStrategy` — normalizes raw scores to the same scale and then sums them

They all implement the same interface:

```java
public interface FusionStrategy {
    List<Double> scoreContributions(List<RagHit> hits);
}
```

`HybridRetriever` calls `scoreContributions(...)` once for each sub-retriever's result list and accumulates the returned contributions onto the deduplicated hits.

### 4.1 Default RRF (by rank)

The default constructor uses:

```java
new RrfFusionStrategy()
```

`RrfFusionStrategy` defaults to `rankConstant = 60`; the contribution formula is effectively:

```java
1.0 / (rankConstant + rank)
```

This is the classic Reciprocal Rank Fusion idea.

This default has an important consequence:

**The default hybrid cares more about "a hit's rank within each retriever" than about the absolute value of the raw score.**

This means:

- A dense hit with a very high score but ranked 8th may not beat a bm25 rank-1 hit
- Retrievers do not need to normalize their scores to the same scale first
- Fusion is more stable, but some "raw similarity magnitude information" is sacrificed

### 4.2 RSF: Relative Score Fusion

If you want to preserve more of the raw score semantics, use `RsfFusionStrategy`:

```java
Retriever hybrid = new HybridRetriever(
        Arrays.asList(dense, bm25),
        new RsfFusionStrategy()
);
```

What it does: within each sub-retriever, it applies **min-max normalization** to the raw scores, mapping them to `[0, 1]`:

```java
contribution = (rawScore - min) / (max - min)
```

- The highest-scoring hit in that retriever gets 1.0
- The lowest-scoring gets 0.0
- The middle ones are linearly interpolated

Effect: scores from different retrievers are pulled to the same scale before being summed. A dense 0.95 and a bm25 12.0 are no longer swallowed by rank; they are compared by their relative position within each distribution.

### 4.3 DBSF: Distribution-Based Score Fusion

`DbsfFusionStrategy` goes one step further, using **z-score + sigmoid** normalization:

```java
zScore      = (rawScore - mean) / standardDeviation
contribution = 1.0 / (1.0 + exp(-zScore))
```

```java
Retriever hybrid = new HybridRetriever(
        Arrays.asList(dense, bm25),
        new DbsfFusionStrategy()
);
```

- First centers the scores using mean and standard deviation
- Then compresses them with a sigmoid into `(0, 1)`

Compared with RSF, DBSF is less sensitive to "individual extreme high scores": an outlier high score would skew RSF's min-max (other hits get pushed close to 0), but z-score + sigmoid converges it to near 1, leaving discrimination room for the remaining hits.

### 4.4 Fallback: fall back to rank when there is no score or scores have no discrimination

This is the most easily overlooked safety detail of the two score-based families.

Both `RsfFusionStrategy` and `DbsfFusionStrategy` extend `AbstractScoreFusionStrategy`. When a sub-retriever's results hit the following conditions, they **do not force normalization**; instead they fall back to an RRF-style rank-based contribution:

- `RagHit.score` is `null`
- The score set has no variance (all equal, `max - min ≈ 0`)

The fallback formula is:

```java
contribution = 1.0 / (rank)
```

:::note
This means: if one of your retrievers does not fill in `score` (for example a custom `Retriever` forgot to assign scores), RSF/DBSF will not crash but silently degrade to "by rank". If you assume that enabling DBSF guarantees score fusion, the result may be indistinguishable from RRF — in that case first check whether your sub-retrievers actually emit a discriminative `score`.
:::

### 4.5 How to choose among the three strategies

| Strategy | Looks at | Suitable for | Caveat |
| --- | --- | --- | --- |
| `RrfFusionStrategy` (default) | Rank | Retrievers whose score semantics and scales differ widely | Drops raw score magnitude |
| `RsfFusionStrategy` | Relative position of raw scores | Comparable score semantics, want to preserve magnitude | Sensitive to outlier high scores |
| `DbsfFusionStrategy` | Distribution of raw scores | Skewed score distributions, outliers present | Slightly more computation, more robust to extremes |

Switching strategies does not require changing the retrievers; only swap the second argument to `HybridRetriever`:

```java
// Default RRF
new HybridRetriever(Arrays.asList(dense, bm25));

// Custom RRF rankConstant
new HybridRetriever(Arrays.asList(dense, bm25), 40);

// Switch to score fusion
new HybridRetriever(Arrays.asList(dense, bm25), new RsfFusionStrategy());
new HybridRetriever(Arrays.asList(dense, bm25), new DbsfFusionStrategy());
```

There is no universally better choice — it depends on your embedding model, your BM25 corpus distribution, and your recall set size. The recommendation is to use the NDCG metric from [RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation) to run an A/B comparison on the same batch of labeled queries, rather than picking by gut feel.

## 5. How "the same hit" is determined

This is the most easily overlooked part of the hybrid implementation, yet the one that most affects result quality.

The key priority of `HybridRetriever.keyOf(hit, fallbackIndex)` is roughly:

1. `hit.id`
2. `documentId + chunkIndex`
3. `sourcePath + chunkIndex`
4. `sourceUri + chunkIndex`
5. `sourceName + sectionTitle + chunkIndex`
6. `content`
7. fallback index

This means the deduplication quality of hybrid depends heavily on whether you gave stable identifiers during the ingestion and retrieval stages.

If your `RagHit`:

- Has no `id`
- Has an unstable `documentId`
- Has a `chunkIndex` that changes on every chunking
- Or different sources merely happen to share the same content

Then the fusion results may exhibit two kinds of problems:

- Hits that should have been merged are not merged
- Hits that should not have been merged are merged by mistake

So hybrid quality is not only an algorithm problem but also an **identifier design problem**.

## 6. Which fields change on `RagHit` after fusion

After fusion completes, AI4J writes back several pieces of key information:

- `retrieverSource = "hybrid"`
- `retrievalScore = bestRetrievalScore`
- `fusionScore = accumulated fusion score`
- `score = fusionScore`
- `scoreDetails = source, rank, raw retrieval score, and fusion contribution for each sub-retriever`

Pay special attention to the semantics of `score` here.

During dense retrieval, `score` is more like a vector retrieval score; after rerank, `score` gets replaced by the rerank score; and in hybrid results, `score` has already become the effective post-fusion score.
So when reading `RagHit.score`, never interpret it without considering the stage.

The safest approach is to look at these together:

- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

## 7. The roles Dense and BM25 each play in this chain

The logic of `DenseRetriever` is:

1. Use `IEmbeddingService` to generate a vector for the query
2. Call `VectorStore.search(...)`
3. Convert the returned `VectorSearchResult` into `RagHit`

It depends on:

- `query.embeddingModel`
- `query.dataset`
- The metadata quality in the vector store

The logic of `Bm25Retriever` is completely different:

1. Build a local BM25 index from the in-memory `corpus`
2. Tokenize the query
3. Compute term frequency, inverse document frequency, and length-normalized score
4. Output hits sorted by score

It does not depend on embeddings, nor on an external vector store.

So the real value of the hybrid combination is not "sticking two popular buzzwords together", but putting:

- Dense semantic recall
- Sparse / bm25 term-matching capability

into the same fusion chain.

## 8. Which layers `topK` actually applies to

This point is easy to misunderstand.

In the current implementation, `query.topK` affects at least two layers:

1. How many results each sub-`Retriever` returns on its own
2. How many results `HybridRetriever` keeps after fusion

If it is then handed to `DefaultRagService`, there is a third layer:

3. `query.finalTopK` trims again after rerank

So when you feel "hybrid recall is too small", do not stare at only one layer. It could be:

- The dense sub-retriever trimmed first
- The bm25 sub-retriever trimmed first
- Hybrid trimmed again after fusion
- `finalTopK` trimmed once more after rerank

## 9. What the current implementation does not do

This layer of AI4J hybrid is practical, but its boundaries are also clear. It currently does not directly do:

- Execute multiple sub-retrievers in parallel
- Query rewriting or query expansion (this kind of pre-retrieval processing belongs to `RagQueryPlanner`)
- Retry, timeout control, or circuit breaking
- Business-rule-based secondary filtering
- rerank
- context assembly

That is, it only solves "how to merge multi-path retrieval results", not "how to make the query smarter".

## 10. The 4 most common pitfalls

### 10.1 Treating the default RRF as score fusion

The default implementation mainly consumes rank, not raw score magnitude. Do not assume that a dense 0.91 and a bm25 17.4 will be directly compared numerically.

### 10.2 Hits without a stable id

Without stable identifiers, the deduplication quality of hybrid drops noticeably, and `scoreDetails` also becomes harder to interpret.

### 10.3 Looking only at the final `score`

The post-fusion `score` is no longer the underlying retrieval score. When troubleshooting, always look at `scoreDetails` together with it.

### 10.4 Assuming rerank is unnecessary after hybrid

Hybrid solves "multi-source recall fusion", not "final query-oriented relevance ranking". These two layers are not mutually exclusive.

## 11. When to extend it

If you encounter any of the following situations, you should consider extending rather than forcing the defaults:

- You want to preserve different retriever score semantics more prominently
- You have strong domain rules and need to weight by source
- Your hit key needs a more stable business primary key
- You want hybrid to accommodate more than just dense/bm25 paths

There are two main extension points:

- A custom `Retriever`
- A custom `FusionStrategy`

Rather than modifying `DefaultRagService`.

## 12. The conclusion to remember from this page

AI4J's current hybrid retrieval is essentially a **`Retriever`-level result fusion component**:

- It accepts multiple sub-retrievers
- By default it fuses using RRF rank-based contributions
- It deduplicates by a stable key
- It writes the fused score and details back into `RagHit`

Once you understand this, you will not mistakenly write hybrid, rerank, context assembly, and online search as the same layer of capability.

## 13. Further reading

- [RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation)
- [Query Planning](/docs/core-sdk/search-and-rag/query-planning)
- [Rerank](/docs/core-sdk/search-and-rag/rerank)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
