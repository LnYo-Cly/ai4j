---
sidebar_position: 12
title: "LLM-as-Judge (Answer Quality)"
description: "A thorough walkthrough of ai4j's RAG answer-quality judge layer: the RagJudge SPI, the built-in ChatRagJudge three-dimension scoring protocol (faithfulness/contextRelevance/answerRelevance, temperature 0, json_object, fault-tolerant parsing, score clamping to [0,1]), how RagOnlineEvaluator writes the judgment into RagTrace, and where it draws the line against the offline RagEvaluator retrieval metrics."
tags: [reference]
---

# LLM-as-Judge (Answer Quality)

The `RagEvaluator` in [RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation) measures **retrieval quality** — whether the recalled ids are the right ones. But accurate recall does not imply a good answer: a system with recall@K = 1.0 can still go off-topic or hallucinate during generation. Answer quality is estimated by a **judge layer** (LLM-as-judge).

This layer is small, but it is not interchangeable with retrieval metrics. This page covers its SPI, the fixed protocol of the built-in implementation, where the judgments end up, and the boundary between it and the offline metrics.

## 1. Core source entry points

| Class | Role |
| --- | --- |
| `RagJudge` | **SPI interface**, a single method `judge(RagJudgeRequest) -> RagJudgeEvaluation`. Implement it to swap in your own judging logic.|
| `ChatRagJudge` | Built-in implementation: calls the model via `IChatService` and produces three-dimensional scores against a fixed rubric.|
| `RagJudgeRequest` | Judge input: `query` / `answer` / `context` / `hits`.|
| `RagJudgeEvaluation` | Judge output: three scores + `reason` + `rawOutput`.|
| `RagOnlineEvaluator` | Wraps a judge to "judge a single RAG result + answer online" and writes the result into `RagTrace`.|

## 2. The three scoring dimensions

The system prompt of `ChatRagJudge` requires the model to return scores on three dimensions (each from 0 to 1):

| Dimension | What it scores (from the rubric in the user prompt) |
| --- | --- |
| `faithfulnessScore` | Whether the answer is **supported by the retrieved context** (no fabrication) |
| `contextRelevanceScore` | Whether the retrieved context is **relevant to the question** |
| `answerRelevanceScore` | Whether the answer **directly addresses the question** |

faithfulness guards against "hallucination", contextRelevance guards against "poor recall", and answerRelevance guards against "answering the wrong question". The three dimensions together describe the quality of a single RAG answer — none of them alone is enough.

## 3. The fixed protocol of `ChatRagJudge`

The judging call of the built-in judge is a **fully deterministic protocol**, not an invitation to freelance the prompt:

- **System prompt is fixed**: `"You are a strict RAG evaluator. Return only JSON with faithfulnessScore, contextRelevanceScore, answerRelevanceScore, reason. Scores must be numbers from 0 to 1."`
- **temperature = 0.0**: minimize the randomness of the judgment.
- **response_format = json_object**: force the model to return JSON.
- **User prompt has a fixed structure**: three sections — Question / Answer / Retrieved context — plus a scoring rubric for each dimension.

```java
ChatRagJudge judge = new ChatRagJudge(chatService, "glm-4.6");   // any IChatService + model name
RagJudgeEvaluation result = judge.judge(RagJudgeRequest.builder()
        .query("Which vector stores does AI4J support?")
        .answer(answerText)
        .context(retrievedContext)
        .hits(hits)
        .build());

Double faithfulness = result.getFaithfulnessScore();    // 0..1, may be null
String reason = result.getReason();                      // the model's stated reason
```

### 3.1 Fault-tolerant parsing and score clamping

The model is not a schema-guaranteed API, so the judge adds two layers of defense to its output:

- **JSON extraction**: the model is not required to return only JSON — the substring between the first `{` and the last `}` of the output is taken and parsed. Explanatory text before or after the JSON is tolerated.
- **Score clamping**: each score read by `score(...)` is clamped to the `[0, 1]` range (values below 0 become 0, values above 1 become 1); if a key is missing, that dimension is `null`, **not 0** — null and a real 0 are kept distinct.

`rawOutput` keeps the raw model output verbatim, for troubleshooting.

## 4. `RagOnlineEvaluator`: wiring the judge into the RAG flow

Using `ChatRagJudge.judge(...)` directly means assembling the request yourself. `RagOnlineEvaluator` does that step for you: it takes a `RagResult` (the outcome of one retrieval) plus the generated answer, assembles the request automatically, and judges it.

```java
RagOnlineEvaluator evaluator = new RagOnlineEvaluator(judge);

RagResult ragResult = ragTool.retrieve(...);   // one retrieval, with query/context/hits
String answer = ...;                            // the answer produced by the generation stage

RagJudgeEvaluation evaluation = evaluator.evaluate(ragResult, answer);
```

`evaluate` pulls `query` / `context` / `hits` from the `RagResult`, pairs them with the `answer`, and hands them to the judge.

## 5. Where the judgment goes: into `RagTrace`

`RagOnlineEvaluator.evaluate(...)` does more than return the judgment — it also **stuffs the judgment into the `RagTrace` of this retrieval**:

```
result.getTrace().setJudgeEvaluation(evaluation);
```

So the answer-quality score travels with the trace, and is available later in [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace). This pins "retrieval citations" and "answer-quality judgment" onto the same trace, so audit and UI rendering can pick them up together.

## 6. Custom judge: implement the `RagJudge` SPI

Don't want the fixed rubric of `ChatRagJudge`? Just implement the `RagJudge` interface:

```java
public class MyPairwiseJudge implements RagJudge {
    @Override
    public RagJudgeEvaluation judge(RagJudgeRequest request) throws Exception {
        // your judging logic: pairwise comparison, custom rubric, calling a different model...
        return RagJudgeEvaluation.builder()
                .faithfulnessScore(...)
                .answerRelevanceScore(...)
                .reason(...)
                .rawOutput(...)
                .build();
    }
}

RagOnlineEvaluator evaluator = new RagOnlineEvaluator(new MyPairwiseJudge());
```

`RagOnlineEvaluator` depends only on the `RagJudge` interface, not on `ChatRagJudge`. Swapping the judge requires no change to the evaluator.

## 7. Division of labor with the offline `RagEvaluator` (don't conflate)

| | `RagEvaluator` (offline) | `RagOnlineEvaluator` + `RagJudge` (online) |
| --- | --- | --- |
| What it measures | **Retrieval** quality (is the recall accurate) | **Answer** quality (is the answer supported, does it address the question) |
| Needs a model? | **No** (purely local, compares hits against ground-truth ids) | **Yes** (the judge is an LLM call) |
| Determinism | Fully deterministic (same hits/labels → same scores) | Non-deterministic (LLM, even at temperature=0) |
| Metrics | precision/recall/F1@K, MRR, NDCG | faithfulness/contextRelevance/answerRelevance |
| Usage | Offline batch evaluation of retrieval strategies (A/B) | Online judging of a single answer's quality, written into the trace |

One guards "recall", the other guards "answer", and **they are not interchangeable**. A system with recall@K = 1.0 may still have low faithfulness (accurate recall but off-topic generation); a system with faithfulness = 1.0 may still have low recall (faithful answer but missed recall).

## 8. Boundaries and pitfalls

- **The judge is an LLM; its score is an "opinion", not ground truth**. temperature=0 reduces randomness but does not eliminate it — the same input may drift slightly across models or over time. Do not treat 0.83 as a precise reading; use it **comparatively** (before/after a strategy or prompt change).
- **You need a working IChatService + model**. The cost of the judge is one extra LLM call; for batch evaluation, count the call volume up front.
- **A missing dimension is `null`, not `0`**. When the model omits a score, that dimension is null — handle null explicitly in aggregation statistics, don't default it to 0 and drag down the mean.
- **contextRelevance and offline recall take different views**: recall looks at "did the relevant ids make it into hits" (label-level), while contextRelevance looks at "is the retrieved text on-topic" (semantic-level). The two usually correlate but do not align.
- **The judge does not manage your evaluation set for you**. Batch orchestration (running the judge over N queries, summarizing means/distributions) is the upper-layer application's job; this layer only "judges once".

## 9. Further reading

- **Offline retrieval metrics** (recall/NDCG, and the split of labor with the judge): [RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation)
- **Judgments travel with the trace**: [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- **How retrieval itself works**: [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
