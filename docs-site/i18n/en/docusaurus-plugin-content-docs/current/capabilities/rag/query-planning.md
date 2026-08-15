---
title: "Query Planning"
description: "Explains the AI4J RagQueryPlanner retrieval preprocessing layer: before the Retriever it produces rewrite/multi-query/HyDE/step-back retrieval plans, fuses multi-variant results with RRF, keeps rerank and context assembly on the original query, and falls back automatically when the planner throws."
tags: [concept]
---

# Query Planning

`Query Planning` is the RAG retrieval preprocessing layer. It answers a single question:

> Before the original user question enters the `Retriever`, should it first be turned into one or more queries that are better suited for retrieval?

This layer is not responsible for multi-way recall, rerank, or answer generation. Its position in AI4J is:

```text
RagQuery(original + optional history)
  -> RagQueryPlanner(optional)
  -> Retriever
  -> Reranker
  -> RagContextAssembler
```

The planner is not enabled by default; if you do not configure it, `rag.search(query)` keeps the original behavior.

## 1. Why not `QueryTransformer`

This version is deliberately placed inside RAG and named `RagQueryPlanner`:

- rewrite, multi-query, HyDE, and step-back are pre-retrieval strategies, not ordinary text polishing.
- Some strategies produce multiple queries, not a single `String -> String` transformation.
- The original query must still be retained for rerank, context assembly, and answer generation.

So its output is not a new string but a retrieval plan:

```java
public interface RagQueryPlanner {
    RagQueryPlan plan(RagQuery query) throws Exception;
}
```

A `RagQueryPlan` can hold one or more `RagQueryVariant` entries.

## 2. Default execution semantics

When `DefaultRagService` is configured with a planner:

1. The original `RagQuery` is first handed to the `RagQueryPlanner`; if `RagQuery.history` is present, the built-in planner uses it as conversation context.
2. The planner returns a `RagQueryPlan`.
3. For each `RagQueryVariant`, the SDK copies the original `RagQuery`, replaces only the `query` field, and then runs the underlying `Retriever`.
4. Hits from multiple variants are deduplicated and fused with RRF-style rank fusion.
5. The `Reranker` still uses the original query.
6. `RagResult.query` and `RagContextAssembler` still use the original query.
7. If the planner throws an exception or returns no usable query, the SDK falls back to the original query and marks the fallback in `RagTrace.queryPlan`.

In other words, the caller still only writes:

```java
RagResult result = rag.search(RagQuery.builder()
        .query("原始用户问题")
        .dataset("knowledge-base")
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .finalTopK(4)
        .build());
```

There is no need for the business code to manually wrap a `PlanningRetriever`.

## 3. Minimal usage

If you want a model to rewrite the query before retrieval out of the box, first create a `ModelRagQueryPlanner`:

```java
RagQueryPlanner planner = aiService.getModelRagQueryPlanner(
        PlatformType.OPENAI,
        "gpt-4o-mini"
);

RagService rag = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore,
        planner
);
```

By default this only does `REWRITE`. It adds one extra model call to turn follow-up questions, omissions, and colloquial queries into a standalone retrieval query.

For multi-turn dialogue, do not build a separate `RagMemory`; reuse the core `ChatMemory` directly:

```java
ChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(6));
memory.addUser("我想接入 ChatFire 视频生成");
memory.addAssistant("可以先接 OpenAI-compatible videos");

RagResult result = rag.search(RagQuery.builder()
        .query("那 Suno 呢？")
        .history(memory.getItems())
        .dataset("ai4j-docs")
        .build());
```

`history` should be the recent conversation or summary that does not include the current query; if the history is long, converge it first with `MessageWindowChatMemoryPolicy` or `SummaryChatMemoryPolicy`.

If you want full control over the strategy, you can also implement `RagQueryPlanner` yourself and wire it into `DefaultRagService`:

```java
Retriever retriever = new DenseRetriever(embeddingService, vectorStore);

RagQueryPlanner planner = new RagQueryPlanner() {
    @Override
    public RagQueryPlan plan(RagQuery query) {
        return RagQueryPlan.of(query.getQuery(), Arrays.asList(
                RagQueryVariant.rewrite("员工福利政策有哪些"),
                RagQueryVariant.stepBack("员工福利制度")
        ));
    }
};

RagService rag = new DefaultRagService(
        retriever,
        new NoopReranker(),
        new DefaultRagContextAssembler(),
        planner
);
```

If you go through the `AiService` default factory, you can also use the overloaded entry point:

```java
RagService rag = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore,
        planner
);
```

If you actually need multi-query / HyDE / step-back, you can specify the strategies explicitly:

```java
RagQueryPlanner planner = aiService.getModelRagQueryPlanner(
        PlatformType.OPENAI,
        "gpt-4o-mini",
        Arrays.asList(RagQueryVariantType.REWRITE, RagQueryVariantType.STEP_BACK),
        3,
        true
);
```

`ModelRagQueryPlanner` does not generate all strategies in one big prompt. When you pass multiple strategies explicitly, it calls the model separately per strategy:

```text
REWRITE     -> a standalone retrieval query
MULTI_QUERY -> multiple alternative phrasings of the query
HYDE        -> a hypothetical answer/document passage
STEP_BACK   -> a higher-level background query
```

This keeps the prompt goal clearer; the trade-off is that multiple strategies bring multiple model calls.

## 4. How to express the four common strategies

### 4.1 Rewrite

Used to turn omitted, follow-up, or colloquial questions into a standalone retrieval query.

```java
return RagQueryPlan.single(
        query.getQuery(),
        RagQueryVariant.rewrite("AI4J RAG 如何配置向量检索")
);
```

Suitable for:

- "Then how do I integrate it?" in a multi-turn dialogue
- Highly colloquial user input
- Queries with pronouns, omissions, and context dependencies

### 4.2 Multi-query expansion

Used to recall relevant documents for the same question from different phrasing angles.

```java
return RagQueryPlan.of(query.getQuery(), Arrays.asList(
        RagQueryVariant.multiQuery("AI4J RAG 向量检索配置"),
        RagQueryVariant.multiQuery("AI4J VectorStore search topK filter"),
        RagQueryVariant.multiQuery("AI4J 知识库问答 检索参数")
));
```

Suitable for:

- The same concept has multiple names in the docs
- A single query recalls unstably
- You need to cover Chinese and English, abbreviations, and legacy terms

### 4.3 HyDE

HyDE typically has the model first generate a "hypothetical answer / hypothetical document", then retrieves using that passage.

In AI4J, it is simply a `HYDE`-type query variant:

```java
return RagQueryPlan.single(
        query.getQuery(),
        RagQueryVariant.hyde("AI4J 的 RAG 配置通常包括 embedding model、VectorStore、topK、filter 和 context assembler。")
);
```

Suitable for:

- The original query is too short, with insufficient semantic signal
- The docs read more like answer passages than question titles
- Dense retrieval relies more on semantic context

### 4.4 Step-back query

Step-back first abstracts a higher-level question, then retrieves background knowledge.

```java
return RagQueryPlan.of(query.getQuery(), Arrays.asList(
        RagQueryVariant.rewrite("AI4J RAG 如何配置 Pinecone 检索"),
        RagQueryVariant.stepBack("AI4J RAG 检索链路包含哪些组件")
));
```

Suitable for:

- The user question is too specific; retrieving directly tends to miss background docs
- You need to find principles, architecture, or overview knowledge first
- Detail docs and concept docs are scattered across different pages

## 5. Difference from Hybrid Retrieval

These two capabilities are often conflated, but they are not the same layer:

| Capability | Input | What it does | Position |
| --- | --- | --- | --- |
| `RagQueryPlanner` | One original query + optional `history` | Produces one or more retrieval queries | Before `Retriever` |
| `HybridRetriever` | One query | Calls multiple retrievers and fuses results | The `Retriever` layer |
| `Reranker` | Original query + candidate hits | Reorders candidate results | After retrieval |

When used together, the order is:

```text
original query
  -> planner produces multiple query variants
  -> each variant calls the base retriever once
  -> planner layer fuses variant results
  -> reranker reorders with the original query
  -> assembler builds context with the original query
```

If the base retriever is itself a `HybridRetriever`, that becomes:

```text
multiple query variants × multiple retrievers
```

This increases cost and latency, and should be enabled only when recall quality truly needs it.

## 6. How to read the trace

With `includeTrace` enabled, `RagTrace` contains:

- `queryPlan`
- `planningDurationMs`
- `retrievedHits`
- `rerankedHits`

When the planner throws, retrieval is not aborted; it falls back to the original query:

```java
RagTrace trace = result.getTrace();

if (trace.getQueryPlan() != null && trace.getQueryPlan().isFallback()) {
    System.out.println(trace.getQueryPlan().getFallbackReason());
}
```

## 7. When not to use it

:::warning
Do not add a planner to every RAG by default. It adds extra LLM calls, latency, and non-determinism.
:::

Prefer enabling it in these scenarios:

- Recall is clearly affected by how the query is phrased
- Most user input is follow-up questions, omissions, or short fragments, and the caller can provide `RagQuery.history`
- Document terminology and user terminology often diverge
- You need multi-query / HyDE / step-back to improve recall

If it is just format cleanup — for example removing extra whitespace or normalizing full-width/half-width characters — a planner is usually unnecessary; a lightweight normalize at the business call site is enough.

## 8. The conclusion to remember

`RagQueryPlanner` is RAG retrieval preprocessing — not an agent planner, and not a general-purpose text rewriter.

Its correct usage is:

- Keep the original query
- Reuse `ChatMemoryItem` for conversation history; do not add a RAG-specific memory
- The planner only produces retrieval variants
- The SDK executes and fuses the variants internally
- Rerank and final context still go back to the original query

This way it supports rewrite, multi-query, HyDE, and step-back without turning the main RAG chain into an overly heavy agent runtime.
