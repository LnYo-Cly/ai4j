---
title: "Rerank"
description: "Explains the AI4J rerank layer: it sits between retrieval and context assembly to correct ranking, defaults to NoopReranker with no remote reranking, ModelReranker maps results by the provider-returned index, finalTopK trims after reranking, and returnDocuments may rewrite hit content."
tags: [concept]
---

# Rerank

The role of `rerank` in AI4J is easy to understate.
It is neither a "second retriever" nor a "prompt optimizer handed to the model at the end"; it is a ranking-correction layer sandwiched between **retrieval** and **context assembly**.

From the source, this layer's responsibilities are very specific:

- Take a set of already-recalled `RagHit`s as input
- Re-rank them according to the query
- Optionally keep the tail of the original hits
- Write the rerank score back into the result

## 1. Where the source entry points are

The most important classes on this page are:

- `rag/DefaultRagService.java`
- `rag/Reranker.java`
- `rag/NoopReranker.java`
- `rag/ModelReranker.java`
- `rag/RagHitSupport.java`
- `service/IRerankService.java`

The provider side also has a few implementations:

- `platform/standard/rerank/StandardRerankService.java`
- `platform/doubao/rerank/DoubaoRerankService.java`
- `platform/jina/rerank/JinaRerankService.java`
- `platform/ollama/rerank/OllamaRerankService.java`

But the thing that actually wires rerank into the main RAG chain is `DefaultRagService.search(...)`.

## 2. Exactly where rerank happens in the default RAG chain

The main flow of `DefaultRagService.search(query)` is:

1. `retriever.retrieve(query)`
2. `RagHitSupport.prepareRetrievedHits(...)`
3. Copy the hits as `rerankInput`
4. `reranker.rerank(query.getQuery(), rerankInput)`
5. `RagHitSupport.prepareRerankedHits(...)`
6. `trim(..., query.finalTopK)`
7. `contextAssembler.assemble(query, finalHits)`

This order matters, because it shows that:

- rerank comes after retrieval
- rerank comes before context assembly
- `finalTopK` is trimmed only after rerank

So if you only crank up `topK` without reranking, the context handed to the model is not necessarily more accurate;
conversely, if you do rerank but set `finalTopK` too small, you may truncate valuable tail hits a second time.

## 3. Why you get `NoopReranker` by default

The default constructor of `DefaultRagService` is:

```java
this(retriever, new NoopReranker(), new DefaultRagContextAssembler());
```

The implementation of `NoopReranker` is equally straightforward:

- Return empty if hits is empty
- Otherwise return a verbatim copy

This shows the framework authors' default stance on this layer:

**Rerank is an optional enhancement, not a precondition for the RAG chain to function.**

This has two engineering benefits:

- The chain still works when there is no extra service
- You are not forced to bind to a specific rerank provider

But the cost is also clear:

- The top hits of pure dense / hybrid retrieval are not necessarily the context best suited to answer the question
- Fine-grained relevance correction between the query and the chunk does not happen by default

## 4. How `ModelReranker` actually works

The current implementation of `ModelReranker` is the backbone most worth reading.

It does its work in 6 steps:

1. Validate that `rerankService` and `model` are required
2. Return empty immediately if hits is empty
3. Return a copy of the original hits if the query is empty
4. Convert `RagHit` to `RerankDocument`
5. Call `IRerankService.rerank(...)`
6. Map the results back to the original hits by the provider-returned `index`

There are a few key implementation details here.

### 4.1 The document primary key uses `stableKey`

What `ModelReranker` fills into `RerankDocument.id` is:

```java
RagHitSupport.stableKey(hit)
```

This is the same lineage as the stable key logic used for hybrid deduplication.
In other words, whether the hit identifier is stable affects all of:

- hybrid merging
- rerank document identity
- trace troubleshooting

### 4.2 Result mapping relies on the provider-returned `index`

When `ModelReranker` writes hits back, it does not look them up by document id; it pulls them from the original hits by the returned `index`.

This means the provider's results must preserve the same ordering semantics as the `documents` sent in.
If a provider:

- Returns an empty index
- Has an out-of-bounds index
- Has inconsistent ordering semantics

These results are all ignored.

### 4.3 `appendRemainingHits` defaults to `true`

In the default constructor:

```java
this(rerankService, model, null, null, false, true);
```

That is, even if the rerank provider only returns the top `topN` results, the remaining documents not hit by rerank are appended to the tail in their original order by default.

This default favors "lose as little recall as possible", but it also means:

- Rerank is not a hard cutoff
- You had better pair it with `finalTopK` afterwards
- Otherwise the final result may still mix in tail hits that were never actually reranked

## 5. `topN` and `finalTopK` are not the same thing

This is the most common conceptual confusion.

`ModelReranker.topN` means:

- How many results you want the rerank provider to focus on returning when sent to it

`RagQuery.finalTopK` means:

- How many hits `DefaultRagService` ultimately keeps after rerank

When the two are not equal, behavior diverges sharply:

- `topN < finalTopK`: the tail may come from `appendRemainingHits`
- `topN > finalTopK`: rerank did more work, but the extra is cut again at the end
- `topN = null`: by default, rerank runs over all hits

So in production, do not usually tune one number while ignoring the other.

## 6. Which fields change after rerank

`RagHitSupport.prepareRerankedHits(...)` merges two sets of data:

- The original retrieved hits
- The hits returned by the reranker

Then it does several things:

- Merge metadata and source fields
- If rerank took effect, write `rerankScore`
- Reassign `rank`
- Use `normalizeEffectiveScore(...)` to normalize `score` into the effective score for the current stage

The current effective-score priority is:

1. `rerankScore`
2. `fusionScore`
3. `retrievalScore`
4. `score`

This means that once rerank takes effect, the meaning of `score` changes.
So during troubleshooting you cannot just say "this hit has a high score"; you also have to ask:

- Is this a retrieval score?
- A fusion score?
- Or a rerank score?

## 7. What side effects `returnDocuments` brings

`ModelReranker` supports:

- `returnDocuments`

If you turn it on and the provider returns `document.content`, the code will:

```java
copy.setContent(result.getDocument().getContent());
```

:::warning
This means the rerank stage may change not only the order but also the content.
:::

This capability is powerful, but use it carefully:

- If the provider returns cleaned content, the citation snippet may change
- If the provider truncates or normalizes the original text, trace comparison becomes harder to interpret

So the default of `false` is a reasonable conservative policy.

## 8. What you can actually see in the trace

When `query.includeTrace = true`, `DefaultRagService` puts:

- `retrievedHits`
- `rerankedHits`

into `RagTrace`.

This is not "the trace of the model's final answer"; it is the **trace of the RAG ranking chain**.

The questions it can help you answer are:

- Which hits were recalled
- How the order changed after rerank
- How the score semantics of a given hit shifted

It cannot directly tell you:

- Why the model ultimately used a given piece of context
- How the provider computes rerank internally
- What happened at the prompt stage

## 9. What this layer does not do for you today

The AI4J rerank layer currently does not do these for you directly:

- query rewrite
- business-rule filtering
- Scoring by source allowlist / blocklist
- Automatic fallback retry on failure
- Parallel comparison of multiple rerank providers

What it provides is a very clear framework slot that lets you plug "second-pass relevance ranking" into the main RAG chain.

## 10. The 5 most common pitfalls

### 10.1 Treating rerank as a retriever

Rerank does not recall new documents; it only reorders the already-recalled hits.

### 10.2 Forgetting that `embedding` / `hybrid` quality is still an upstream precondition

If the recall set itself is wrong, rerank cannot save it.

### 10.3 Enabling rerank without setting `finalTopK`

This way, unreranked tail hits may still make it all the way into the context.

### 10.4 Misreading `score`

After rerank takes effect, `score` is usually no longer the original retrieval score.

### 10.5 Assuming the default chain has "automatically wired up LLM reranking"

The default is actually `NoopReranker`, with no remote rerank service at all.

## 11. When it is worth introducing rerank

The following scenarios usually justify turning on rerank:

- Dense/hybrid can recall, but the ordering of the top hits is often unstable
- Document chunks are fairly large and need stronger query-aware ranking
- You want to widen the recall set first, then do precision ranking with a more expensive model
- You already have a trace / evaluation system and can quantify the ranking gain

Conversely, if your corpus is small, your queries are simple, and recall is already stable, the default `NoopReranker` is a perfectly reasonable starting point.

## 12. The takeaway from this page

AI4J's current rerank is not a separate retrieval framework; it is a ranking-correction layer inside `DefaultRagService` between retrieval and context assembly:

- No remote reranking by default; goes through `NoopReranker`
- Real model reranking is done by `ModelReranker + IRerankService`
- `score` is rewritten into the rerank effective score at this layer
- `finalTopK` takes effect only after rerank

Once you see this layer clearly, you will not conflate layers when tuning dense, hybrid, and citation.

## 13. Further reading

- [Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
- [Citations and Trace](/docs/capabilities/rag/citations-and-trace)
- [Embedding](/docs/capabilities/rag/embedding)
