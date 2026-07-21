# Citations and Trace

AI4J 这一章里，`citation` 和 `trace` 很容易被一起写成“可解释性能力”。
这个说法不算错，但太粗。源码里这两者其实解决的是两件不同的事：

- `citation` 解决“最终上下文和回答能引用到哪里”
- `trace` 解决“RAG 管线里命中是怎么变化的”

这页把两条链分开讲。

## 1. 源码入口在哪里

关键类很少，但非常集中：

- `rag/DefaultRagService.java`
- `rag/DefaultRagContextAssembler.java`
- `rag/RagContext.java`
- `rag/RagCitation.java`
- `rag/RagTrace.java`
- `rag/RagResult.java`

从这组类就能看出，AI4J 当前的 citation/trace 都属于 **RAG 结果组装层**，不是 provider 层能力。

## 2. citation 是怎么生成出来的

真正生成 citation 的不是 retriever，也不是 reranker，而是：

```java
DefaultRagContextAssembler.assemble(query, hits)
```

它会按最终 `hits` 顺序逐条处理：

1. 为每个 hit 分配 `citationId`，格式是 `S1`、`S2`、`S3`
2. 从 hit 里提取：
   - `sourceName`
   - `sourcePath`
   - `sourceUri`
   - `pageNumber`
   - `sectionTitle`
   - `content`
3. 生成 `RagCitation`
4. 同时拼接给模型的 `context.text`

所以 citation 不是单独的一张表，它和最终给模型的上下文文本，是在同一个 assembler 里一起产生的。

## 3. `RagCitation` 里到底保存什么

当前 `RagCitation` 很轻：

- `citationId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `snippet`

这里没有：

- 精确字符 offset
- chunkId
- dataset
- 命中分数

这说明 AI4J 当前 citation 的设计目标更偏：

- 给模型和用户一个可读来源标识
- 给排障提供基本来源线索

而不是做细粒度审计级引用定位。

## 4. `context` 文本里的引用前缀是怎么拼的

`DefaultRagContextAssembler` 默认会在每段命中内容前面加类似：

```text
[S1] source label
```

其中 `source label` 的拼接优先级大致是：

1. `sourceName`
2. `sourcePath`
3. 否则退化成 `source`

如果有：

- `pageNumber`
- `sectionTitle`

也会继续附加到 label 上。

然后才把 `hit.content` 接到后面。

这说明 citation 在 AI4J 当前实现里，不只是返回结构化字段，而是 **直接写进了给模型看的上下文文本**。

## 5. `includeCitations` 控制的到底是什么

`RagQuery.includeCitations` 默认是 `true`。

但从 `DefaultRagContextAssembler` 的实现看，它控制的是：

- 是否把 `[S1] source label` 这种前缀写进 `context.text`

它**不控制** `RagCitation` 列表是否生成。

也就是说，即使你把 `includeCitations = false`：

- `RagResult.citations` 仍然会有内容
- 只是给模型的上下文不再内嵌 citation 标签

这个细节很容易被误解成“关掉后就没有 citations 了”，实际上不是。

### 5.1 需要控制 context token budget 时怎么办

默认 `DefaultRagContextAssembler` 不做 token 截断，它只按最终 hits 顺序拼上下文。
如果你的 RAG 结果会直接进入模型 prompt，应该显式替换成 `TokenAwareRagContextAssembler`：

```java
RagService ragService = new DefaultRagService(
        retriever,
        reranker,
        new TokenAwareRagContextAssembler("gpt-4o-mini", 3000)
);
```

`TokenAwareRagContextAssembler` 的 token 计数是 context budget guard，不是精确计费器。
推荐优先传你实际使用的模型名；如果底层 tokenizer 还不认识这个模型名，会自动退回到默认
`cl100k_base` 估算，保证 RAG 不因为新模型名无法解析而失败。

如果你明确知道模型使用的 tokenizer，也可以显式覆盖 encoding：

```java
RagContextAssembler assembler = TokenAwareRagContextAssembler.withEncoding(
        EncodingType.O200K_BASE,
        3000
);
```

如果模型名和 encoding 都不确定，使用 `new TokenAwareRagContextAssembler(3000)` 即可，
并把 budget 设置得保守一点。

它只做三件事：

- 按现有 hit 顺序加入上下文，直到达到 token budget；
- 第一个 hit 自己就超长时，截断该 hit 的 content；
- `RagCitation` 只返回真正进入 context 的来源。

没有配置时仍然走 `DefaultRagContextAssembler`，不会改变默认行为。

## 6. trace 是怎么生成出来的

`trace` 由 `DefaultRagService.search(...)` 生成。

只有当：

```java
query != null && query.isIncludeTrace()
```

时，结果里才会带：

```java
RagTrace.builder()
    .retrievedHits(hits)
    .rerankedHits(reranked)
    .build()
```

也就是说，`DefaultRagService.search(...)` 默认只记录检索链路：

- 检索后命中列表
- rerank 后命中列表

它不负责记录：

- 最终模型回答
- prompt 拼装细节
- provider reasoning

`RagTrace` 预留了 `generationUsage` 字段。上层 ask/plugin/demo 在调用模型生成答案后，
可以把 response usage 和按业务价格表算出的 cost 回填进去；core RAG 不内置价格表，
也不在 `search(...)` 里发起最终回答生成。

所以别把它误写成“自动全链路 trace”。

## 7. 在线 LLM judge 怎么接

`RagService.search(...)` 只做检索和 context 组装，不生成最终回答。
所以在线评估不是自动跑在 `search(...)` 里，而是在你拿到最终回答后显式调用：

```java
RagResult rag = ragService.search(RagQuery.builder()
        .query("PTO 是什么？")
        .includeTrace(true)
        .build());

String answer = chatWithContext(rag.getContext());

RagOnlineEvaluator evaluator = aiService.getRagOnlineEvaluator(
        PlatformType.OPENAI,
        "gpt-4o-mini"
);

RagJudgeEvaluation judge = evaluator.evaluate(rag, answer);

Double faithfulness = judge.getFaithfulnessScore();
Double contextRelevance = judge.getContextRelevanceScore();
Double answerRelevance = judge.getAnswerRelevanceScore();
```

内置 `ChatRagJudge` 只做一件事：把 question / answer / retrieved context 发给一个
chat model，要求返回 JSON 分数。返回结果会写回：

```java
rag.getTrace().getJudgeEvaluation()
```

如果你不想用内置 prompt 或 chat provider，直接实现：

```java
class MyJudge implements RagJudge {
    public RagJudgeEvaluation judge(RagJudgeRequest request) {
        // call your evaluator / policy / judge model
    }
}
```

这不是离线 Recall/MRR 的替代品。它更适合线上抽样、调试和质量回放，常看的三个分数是：

- `faithfulnessScore`：回答是否忠于 retrieved context
- `contextRelevanceScore`：召回上下文是否和问题相关
- `answerRelevanceScore`：回答是否正面回答问题

## 8. 为什么 trace 不能只靠最终回答替代

最终回答告诉你的只是模型输出。
但 RAG 排障真正常见的问题是：

- 是没召回到？
- 还是召回到了但排序不对？
- 还是排序对了但 context 太长被噪音稀释？

当前 `RagTrace` 虽然很轻，但至少能帮你区分前两类问题：

- `retrievedHits` 看召回集合
- `rerankedHits` 看排序变化

这对于调：

- dense topK
- hybrid 融合
- rerank topN
- finalTopK

都非常重要。

## 9. citation 和 trace 的数据来源为什么不同

这两者的来源阶段并不相同。

`citation` 来自：

- 最终传给 `contextAssembler` 的 `finalHits`

`trace` 来自：

- `retrievedHits`
- `rerankedHits`

这意味着 citation 是 **后状态**，trace 更偏 **中间状态记录**。

一个直接后果是：

- 被 `finalTopK` 裁掉的 hit 可能出现在 trace 里
- 但不会出现在 citations 里

所以看到某条命中“trace 有，citation 没有”，不一定是 bug，很可能只是因为它没进最终上下文。

## 10. citation 质量真正受什么影响

从源码看，citation 的内容几乎直接取自 `RagHit`。
因此 citation 质量首先受前面几层影响：

- chunking 是否合理
- metadata 是否完整
- retriever 是否把 `sourceName/sourcePath/pageNumber/sectionTitle` 带回来了
- rerank 后是否替换过 content

尤其 `DenseRetriever`，它会尽量从 metadata 恢复：

- `documentId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `chunkIndex`

如果 ingest 时这些字段没有入库，citation 再怎么写也只能很弱。

## 11. 当前设计最真实的边界

AI4J 这层 citation/trace 很有用，但边界要说透。

它当前没有直接提供：

- chunk 级精确偏移定位
- 最终回答中的 span 到 citation 的自动绑定
- prompt 版本记录
- 模型使用了哪条 citation 的因果证明
- provider 级可追踪性

LLM judge 能给线上质量一个可观察分数，但它本身仍然是模型判断，不是强证明。

所以它更像是：

- 一个可读引用机制
- 一个轻量 RAG 过程快照

而不是法务级、审计级、科研级引用系统。

## 12. 最容易踩坑的 6 个点

### 12.1 以为 citation 来自原始文档

当前 citation 实际来自最终 `RagHit`，不是直接来自原始文档对象。

### 12.2 以为关闭 `includeCitations` 就没有 citation 结构

关掉的只是上下文文本里的标签前缀，不是 `RagCitation` 列表本身。

### 12.3 以为 trace 是完整调用链

当前 trace 默认覆盖 retrieval 和 rerank；如果上层生成答案后回填 usage/cost，
才会出现 `generationUsage`；如果你显式调用 `RagOnlineEvaluator`，才会额外写入 judge 分数。

### 12.4 忽略 metadata 质量

没有 `sourceName`、`pageNumber`、`sectionTitle` 的 hit，citation 可读性会明显下降。

### 12.5 把 citation 和 answer grounding 画等号

AI4J 能提供引用材料，不等于模型最终一定严格按这些引用作答。

### 12.6 把 LLM judge 分数当强事实

LLM judge 是线上质量信号，不是审计证明。高风险场景仍应保留人工抽检或规则校验。

## 13. 最稳的扩展位置在哪里

如果你想增强 citation/trace，当前最稳的扩展位点是：

- 自定义 `RagContextAssembler`
- 改进 ingest metadata
- 在上层 runtime 生成答案后回填 `generationUsage`
- 在回答生成后调用 `RagOnlineEvaluator`

不要把所有事情都压到 retriever 上。
retriever 负责找回 chunk，不负责决定最终 citation 呈现格式。

## 14. 这页最该记住的结论

AI4J 当前的 citation 和 trace 不是同一件事：

- citation 由 `DefaultRagContextAssembler` 基于最终 hits 生成
- trace 由 `DefaultRagService` 记录 retrieval / rerank 两个阶段
- generation usage 由上层生成答案后显式回填到 trace
- judge evaluation 由 `RagOnlineEvaluator` 在最终回答之后显式写入 trace

前者面向“最终引用与上下文呈现”，后者面向“中间过程排障”。
把这两层分清，RAG 的可解释性分析才不会混。

## 15. 继续阅读

- [Chunking Strategies](/docs/core-sdk/search-and-rag/chunking-strategies)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Rerank](/docs/core-sdk/search-and-rag/rerank)
