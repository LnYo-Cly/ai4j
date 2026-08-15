---
title: "RAG Evaluation"
description: "Clarifies the boundary between AI4J's two evaluators: the offline RagEvaluator computes precision/recall/F1/MRR/NDCG from human-annotated relevant ids to measure retrieval quality, while the online RagOnlineEvaluator scores faithfulness with a judge model after an answer is generated. The two are neither mixed nor interchangeable."
tags: [concept]
---

# RAG Evaluation

In AI4J, "evaluating RAG" is not one thing but two things with completely different boundaries:

- **Offline retrieval quality evaluation**: `RagEvaluator`, which measures "is the recall accurate"
- **Online answer quality evaluation**: `RagOnlineEvaluator`, which measures "is the final answer faithful"

This page is centered on the former — `RagEvaluator`. It is most useful when you change chunking strategy, swap the embedding model, or switch the fusion algorithm (RRF/RSF/DBSF): you run a batch of queries that already have annotated "correct answer ids" and watch whether precision, recall, MRR, and NDCG move.

The premise for understanding this page is: **offline evaluation only looks at retrieval results — it does not call models, count tokens, or judge whether the answer is good.**

## 1. Core source entry points

- `rag/RagEvaluator.java` — offline metric calculator
- `rag/RagEvaluation.java` — evaluation result object
- `rag/RagHitSupport.java` — `stableKey(...)` used to align hits with annotations
- `rag/RagHit.java` — the hit being evaluated

If you are looking for "score with a large model after the answer is generated", the corresponding classes are `rag/RagOnlineEvaluator.java` and `rag/RagJudge.java`; the last section of this page briefly points out the difference.

## 2. What it actually computes

`RagEvaluator.evaluate(hits, relevantIds)` currently returns 5 standard retrieval metrics in one shot:

| Metric | Meaning | Question it addresses |
| --- | --- | --- |
| `precisionAtK` | Proportion of relevant items among the top K hits | Are the top-ranked results accurate |
| `recallAtK` | Proportion of relevant documents recalled | Is anything missing that should have come back |
| `f1AtK` | Harmonic mean of precision and recall | A combined view of the two |
| `mrr` | Reciprocal rank of the first relevant hit | Did the first correct one arrive fast |
| `ndcg` | Rank-discounted cumulative gain | Overall ranking quality |

Plus a few counts:

- `evaluatedAtK` — the position actually evaluated up to
- `retrievedCount` — total number of retrieved hits
- `relevantCount` — total number of relevant ids you annotated
- `truePositiveCount` — number of hits that are both retrieved and relevant

## 3. How "hits" and "annotations" are aligned

This is the place where misunderstanding is most likely.

`RagEvaluator` decides whether a `RagHit` counts as "relevant" using:

```java
String key = RagHitSupport.stableKey(hit, i);
```

That is, the **same stable-key logic** used for hybrid deduplication and rerank identity, with priority roughly:

1. `hit.id`
2. `documentId + chunkIndex`
3. `sourcePath + chunkIndex`
4. `sourceUri + chunkIndex`
5. `sourceName + sectionTitle + chunkIndex`
6. `content`
7. fallback index

And `relevantIds` is the annotation set you provide. The two sides must match for it to count as a true positive.

:::note
This means: the id you use when annotating must be the same kind of thing as the stable key `RagHit` can compute. If your hits have no stable `id` and the `documentId` drifts, the offline metrics will silently compute wrong values — not because the formula is wrong, but because alignment failed.
:::

## 4. Minimum usage

`RagEvaluator` is pure computation: it depends on no platform and calls no model. Hand it a `List<RagHit>` and it runs:

```java
import io.github.lnyocly.ai4j.rag.RagEvaluator;
import io.github.lnyocly.ai4j.rag.RagEvaluation;
import io.github.lnyocly.ai4j.rag.RagHit;
import io.github.lnyocly.ai4j.rag.RagResult;

import java.util.Arrays;
import java.util.List;

// 1. First run a real retrieval to get the hits
RagResult result = rag.search(RagQuery.builder()
        .query("How long do I have to submit an employee medical reimbursement")
        .dataset("hr-docs")
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .build());
List<RagHit> hits = result.getHits();

// 2. Provide the human-annotated relevant ids (from the evaluation set)
List<String> relevantIds = Arrays.asList("doc-1#chunk-0", "doc-7#chunk-3");

// 3. Compute the metrics
RagEvaluation evaluation = new RagEvaluator().evaluate(hits, relevantIds);

System.out.println("precision@K = " + evaluation.getPrecisionAtK());
System.out.println("recall@K    = " + evaluation.getRecallAtK());
System.out.println("f1@K        = " + evaluation.getF1AtK());
System.out.println("mrr         = " + evaluation.getMrr());
System.out.println("ndcg        = " + evaluation.getNdcg());
```

When `evaluate(hits, relevantIds)` is called without a third argument, `K` defaults to `hits.size()` — i.e. all recalled results are evaluated. If you only want to look at the top few, use the overload that takes `topK`:

```java
// Only evaluate the metrics for the top 4 hits
RagEvaluation evaluation = new RagEvaluator().evaluate(hits, relevantIds, 4);
```

When `topK <= 0` it falls back to "evaluate all"; when `topK` exceeds `hits.size()` it takes the actual hit count, so it never goes out of bounds.

## 5. How the metrics are computed (walked through with an example)

Suppose 4 hits are recalled, and the annotated relevant ones are `b` and `d`:

```java
List<RagHit> hits = Arrays.asList(
        RagHit.builder().id("a").content("A").build(),  // rank 1
        RagHit.builder().id("b").content("B").build(),  // rank 2 relevant
        RagHit.builder().id("c").content("C").build(),  // rank 3
        RagHit.builder().id("d").content("D").build()   // rank 4 relevant
);

RagEvaluation eval = new RagEvaluator().evaluate(hits, Arrays.asList("b", "d"), 4);
```

The result is:

| Metric | Value | Derivation |
| --- | --- | --- |
| `evaluatedAtK` | 4 | All evaluated |
| `truePositiveCount` | 2 | b, d hit |
| `precisionAtK` | 0.5 | 2 / 4 |
| `recallAtK` | 1.0 | 2 / 2 (all relevant recalled) |
| `f1AtK` | 0.6667 | 2·0.5·1.0 / (0.5+1.0) |
| `mrr` | 0.5 | 1 / 2 (first relevant at rank 2) |
| `ndcg` | 0.6509 | DCG / IDCG |

Note that `mrr` takes the reciprocal rank of the **first** relevant hit; `ndcg` uses rank-based gain `1 / log2(rank+1)` and normalizes against the ideal ordering. These are all standard formulas — AI4J invents no variants of its own.

## 6. How edge cases are handled

`RagEvaluator` has well-defined behavior for several degenerate scenarios:

- **`hits` is empty**: all metrics are 0, `truePositiveCount = 0`.
- **No relevant hits at all**: precision/recall/F1/MRR/NDCG are all 0.
- **`relevantIds` is empty or all blank**: `relevantCount = 0`, recall is guarded to 0, no division by zero.
- **`relevantIds` contains blank strings or null**: filtered out by `normalize(...)`.
- **`topK` out of bounds or non-positive**: safely clamped to the actual hit count, or falls back to evaluating all.

So it does not throw exceptions for normal degeneracies like "the annotation set is empty" or "nothing was recalled this time".

## 7. Typical usage: A/B comparison

The real value of `RagEvaluator` is running **controlled-variable comparisons**, not reporting a single absolute score. Common scenarios:

| What you changed | Which metrics to watch |
| --- | --- |
| Switching the embedding model | recall@K, ndcg |
| Tuning chunk size / overlap | recall@K, precision@K |
| Switching fusion from RRF to RSF/DBSF | ndcg, mrr |
| Adding rerank | ndcg, mrr (ranking correction gains) |
| Changing query planning to recall more variants | recall@K (watch whether more is recalled, but precision may drop) |

:::tip
When running offline evaluation, fix a batch of queries and an annotation set, and change only one variable at a time. Otherwise if precision goes from 0.5 to 0.6, you cannot tell whether the credit belongs to the embedding or the chunking.
:::

## 8. Offline `RagEvaluator` vs online `RagOnlineEvaluator`

The two names look alike, but they do completely different things — do not conflate them:

| Dimension | `RagEvaluator` (offline) | `RagOnlineEvaluator` (online) |
| --- | --- | --- |
| Evaluation target | Retrieval result `List<RagHit>` | Final answer + context |
| Judgment source | Relevant-id annotations you provide | `RagJudge` (usually a judge large model) |
| Metrics | precision/recall/F1/MRR/NDCG | Subjective scores like faithfulness, relevance |
| Calls a model | No, pure local computation | Yes, goes through the judge |
| Typical timing | Offline regression before switching strategies | Online/batch evaluation after an answer is generated |
| Writes to trace | No | Yes, writes to `trace.judgeEvaluation` |

In one sentence: `RagEvaluator` answers "is the recall set accurate", and `RagOnlineEvaluator` answers "did the model hallucinate against the recall set". The former measures the retrieval pipeline; the latter measures generation faithfulness. They do not substitute for each other.

For a full explanation of the online judging layer (`RagJudge` SPI, the `ChatRagJudge` three-dimensional scoring protocol, and how judgments are written into `RagTrace`), see [LLM-as-Judge](/docs/capabilities/rag/llm-as-judge).

If you want to see how the online judge is used, refer to [Citations and Trace](/docs/capabilities/rag/citations-and-trace).

## 9. What this layer does not do for you

- No annotation-set management or evaluation-set storage
- No automatic batch query execution (you call `evaluate(...)` yourself in a loop)
- No metric-difference attribution after cross-encoder rerank
- No generation-side metrics (faithfulness/answer-relevance — that is the domain of `RagOnlineEvaluator`)
- No report or chart output

It does exactly one thing: **given hits and relevant ids, compute the standard retrieval metrics.** Leaving batch orchestration and report rendering to the upper-layer application is a reasonable boundary.

## 10. The 3 most common pitfalls

### 10.1 Annotation id and hit key do not match

This is the number-one cause of distorted offline metrics. You annotate with `doc-1`, but the stable key the hit computes is `doc-1#chunk-0`; as a result every relevant item is treated as a false negative and recall collapses to zero.

### 10.2 Judging "good or bad" by an absolute score

The number precision@K = 0.6 is meaningless on its own; it only means something compared to "0.5 before the strategy change". The core of offline evaluation is **comparison**, not a single point.

### 10.3 Treating offline metrics as answer quality

Accurate retrieval does not mean a good answer. A system with recall@K = 1.0 can still go off-topic or hallucinate during generation. Answer quality is the job of `RagOnlineEvaluator`.

## 11. The conclusions to remember from this page

- `RagEvaluator` is a pure local, model-free offline retrieval-quality evaluator
- One call yields precision/recall/F1@K, MRR, and NDCG
- Hits and annotations are aligned via `RagHitSupport.stableKey(...)`; **alignment failure silently distorts the metrics**
- It and `RagOnlineEvaluator` split the work — one owns recall, the other owns the answer — and neither can replace the other

## 12. Further reading

- [Hybrid Retrieval](/docs/capabilities/rag/hybrid-retrieval)
- [Rerank](/docs/capabilities/rag/rerank)
- [Citations and Trace](/docs/capabilities/rag/citations-and-trace)
- [Query Planning](/docs/capabilities/rag/query-planning)
