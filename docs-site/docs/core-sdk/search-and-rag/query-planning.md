# Query Planning

`Query Planning` 是 RAG 检索前处理层。它只回答一个问题：

> 原始用户问题进入 `Retriever` 之前，要不要先变成一条或多条更适合检索的 query？

这一层不负责多路召回、不负责 rerank、不负责回答生成。它在 AI4J 里的位置是：

```text
RagQuery(original)
  -> RagQueryPlanner(optional)
  -> Retriever
  -> Reranker
  -> RagContextAssembler
```

默认不启用 planner；如果你不配置，`rag.search(query)` 会保持原行为。

## 1. 为什么不是 `QueryTransformer`

这一版故意放在 RAG 里，并命名为 `RagQueryPlanner`：

- rewrite、multi-query、HyDE、step-back 都是检索前策略，不是普通文本润色。
- 有些策略会产出多条 query，不是 `String -> String` 的单次转换。
- 最终仍要保留原 query，给 rerank、上下文组装和回答生成使用。

所以它的产物不是一个新字符串，而是一个检索计划：

```java
public interface RagQueryPlanner {
    RagQueryPlan plan(RagQuery query) throws Exception;
}
```

`RagQueryPlan` 里可以有一条或多条 `RagQueryVariant`。

## 2. 默认执行语义

当 `DefaultRagService` 配了 planner 后：

1. 先把原始 `RagQuery` 交给 `RagQueryPlanner`。
2. planner 返回一个 `RagQueryPlan`。
3. 对每个 `RagQueryVariant`，SDK 复制原 `RagQuery`，只替换 `query` 字段，然后执行底层 `Retriever`。
4. 多个 variant 的命中用 RRF 风格的 rank fusion 去重融合。
5. `Reranker` 仍然使用原始 query。
6. `RagResult.query`、`RagContextAssembler` 仍然使用原始 query。
7. 如果 planner 抛异常或没有返回可用 query，SDK 回退到原始 query，并在 `RagTrace.queryPlan` 里标记 fallback。

也就是说，调用方仍然只写：

```java
RagResult result = rag.search(RagQuery.builder()
        .query("原始用户问题")
        .dataset("knowledge-base")
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .finalTopK(4)
        .build());
```

不会要求业务侧手动套 `PlanningRetriever`。

## 3. 最小使用方式

如果你想开箱即用地用模型生成 rewrite / multi-query / HyDE / step-back，可以先创建 `ModelRagQueryPlanner`：

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

如果你想完全控制策略，也可以自己实现 `RagQueryPlanner`，然后配进 `DefaultRagService`：

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

如果你走 `AiService` 默认工厂，也可以只用重载入口：

```java
RagService rag = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore,
        planner
);
```

更细粒度的模型 planner 可以限制策略和返回条数：

```java
RagQueryPlanner planner = aiService.getModelRagQueryPlanner(
        PlatformType.OPENAI,
        "gpt-4o-mini",
        Arrays.asList(RagQueryVariantType.REWRITE, RagQueryVariantType.STEP_BACK),
        3,
        true
);
```

## 4. 四种常见策略怎么表达

### 4.1 Rewrite

用于把省略、追问、口语化问题改成独立检索 query。

```java
return RagQueryPlan.single(
        query.getQuery(),
        RagQueryVariant.rewrite("AI4J RAG 如何配置向量检索")
);
```

适合：

- 多轮对话里的“那怎么接？”
- 用户输入很口语化
- query 中有代词、省略和上下文依赖

### 4.2 Multi-query expansion

用于从不同表达角度召回同一问题的相关文档。

```java
return RagQueryPlan.of(query.getQuery(), Arrays.asList(
        RagQueryVariant.multiQuery("AI4J RAG 向量检索配置"),
        RagQueryVariant.multiQuery("AI4J VectorStore search topK filter"),
        RagQueryVariant.multiQuery("AI4J 知识库问答 检索参数")
));
```

适合：

- 文档里同一概念有多种叫法
- 只靠一条 query 召回不稳定
- 需要覆盖中英文、缩写、旧术语

### 4.3 HyDE

HyDE 通常让模型先生成一段“假想答案/假想文档”，再拿这段文本去检索。

在 AI4J 里，它就是一个 `HYDE` 类型的 query variant：

```java
return RagQueryPlan.single(
        query.getQuery(),
        RagQueryVariant.hyde("AI4J 的 RAG 配置通常包括 embedding model、VectorStore、topK、filter 和 context assembler。")
);
```

适合：

- 原 query 太短，语义信号不足
- 文档更像答案段落，而不是问题标题
- dense retrieval 更吃语义上下文

### 4.4 Step-back query

Step-back 先抽一个更高层的问题，再检索背景知识。

```java
return RagQueryPlan.of(query.getQuery(), Arrays.asList(
        RagQueryVariant.rewrite("AI4J RAG 如何配置 Pinecone 检索"),
        RagQueryVariant.stepBack("AI4J RAG 检索链路包含哪些组件")
));
```

适合：

- 用户问题太具体，直接检索容易漏掉背景文档
- 需要先找原则、架构、总览类知识
- 细节文档和概念文档分散在不同页面

## 5. 和 Hybrid Retrieval 的区别

这两个能力经常被混在一起，但它们不是同一层：

| 能力 | 输入 | 做什么 | 位置 |
| --- | --- | --- | --- |
| `RagQueryPlanner` | 一条原始 query | 产出一条或多条检索 query | `Retriever` 之前 |
| `HybridRetriever` | 一条 query | 调多个 retriever 并融合结果 | `Retriever` 层 |
| `Reranker` | 原 query + 候选 hits | 对候选结果重新排序 | 检索之后 |

如果同时使用，顺序是：

```text
原 query
  -> planner 生成多个 query variants
  -> 每个 variant 调一次 base retriever
  -> planner 层融合 variant 结果
  -> reranker 用原 query 重排
  -> assembler 用原 query 组装上下文
```

如果 base retriever 本身是 `HybridRetriever`，那就是：

```text
多个 query variants × 多个 retrievers
```

这会增加成本和延迟，应该只在召回质量确实需要时启用。

## 6. Trace 怎么看

开启 `includeTrace` 后，`RagTrace` 会包含：

- `queryPlan`
- `planningDurationMs`
- `retrievedHits`
- `rerankedHits`

planner 异常时不会中断检索，会回退原 query：

```java
RagTrace trace = result.getTrace();

if (trace.getQueryPlan() != null && trace.getQueryPlan().isFallback()) {
    System.out.println(trace.getQueryPlan().getFallbackReason());
}
```

## 7. 什么时候不要用

不要默认给每个 RAG 都加 planner。它会增加额外 LLM 调用、延迟和不确定性。

优先在这些场景启用：

- 召回明显受 query 表达影响
- 用户输入大量是追问、省略、短句
- 文档术语和用户术语经常不一致
- 需要 multi-query / HyDE / step-back 改善召回

如果只是格式清理，例如去多余空格、全角半角归一，通常没必要上 planner；在业务入参处做轻量 normalize 就够了。

## 8. 最该记住的结论

`RagQueryPlanner` 是 RAG 检索前处理，不是 agent planner，也不是通用文本改写器。

它的正确使用方式是：

- 原 query 保留
- planner 只产出 retrieval variants
- SDK 内部执行并融合 variants
- rerank 和最终上下文仍回到原 query

这样既能支持 rewrite、multi-query、HyDE、step-back，又不会把 RAG 主链路变成一个过重的 agent runtime。
