---
title: RAG Evaluation
description: 讲清 AI4J 两套评估器的边界：离线 RagEvaluator 用人工标注的相关 id 算 precision/recall/F1/MRR/NDCG 衡量检索质量，在线 RagOnlineEvaluator 在生成回答后用 judge 模型打 faithfulness 分，两者不混用、不替代。
tags: [concept]
---

# RAG Evaluation

在 AI4J 里，“评估 RAG” 不是一件事，而是两件边界完全不同的事：

- **离线检索质量评估**：`RagEvaluator`，衡量“召回准不准”
- **在线回答质量评估**：`RagOnlineEvaluator`，衡量“最终回答忠不忠实”

这页主轴是前者——`RagEvaluator`。它在换 chunking 策略、换 embedding 模型、换融合算法（RRF/RSF/DBSF）时最有用：你拿一批已经标注好“正确答案 id”的 query 跑一遍，看 precision、recall、MRR、NDCG 动没动。

理解这一页的前提是：**离线评估只看检索结果，不调模型、不算 token、不判断回答好坏。**

## 1. 核心源码入口

- `rag/RagEvaluator.java` —— 离线指标计算器
- `rag/RagEvaluation.java` —— 评估结果对象
- `rag/RagHitSupport.java` —— `stableKey(...)` 用来把命中和标注对齐
- `rag/RagHit.java` —— 被评估的命中

如果你找的是“生成完答案之后用大模型打分”，那对应的类是 `rag/RagOnlineEvaluator.java` 与 `rag/RagJudge.java`，本页最后一节会简要点出区别。

## 2. 它到底算什么

`RagEvaluator.evaluate(hits, relevantIds)` 当前一次性给出 5 个标准检索指标：

| 指标 | 含义 | 关注的问题 |
| --- | --- | --- |
| `precisionAtK` | 前 K 个命中里相关的比例 | 排在前面的准不准 |
| `recallAtK` | 相关文档被召回的比例 | 该回来的有没有漏 |
| `f1AtK` | precision 与 recall 的调和平均 | 两者的综合 |
| `mrr` | 第一个相关命中的倒数排名 | 第一个对的来得快不快 |
| `ndcg` | 按 rank 折扣的累积增益 | 整体排序质量 |

外加几个计数：

- `evaluatedAtK` —— 实际评估到第几条
- `retrievedCount` —— 检索回来的总数
- `relevantCount` —— 你标注的相关 id 总数
- `truePositiveCount` —— 命中且相关的数量

## 3. “命中” 和 “标注” 是怎么对齐的

这是这一层最容易出理解偏差的地方。

`RagEvaluator` 判断某个 `RagHit` 算不算“相关”，靠的是：

```java
String key = RagHitSupport.stableKey(hit, i);
```

也就是和 hybrid 去重、rerank 标识用的**同一套稳定 key 逻辑**，优先级大致是：

1. `hit.id`
2. `documentId + chunkIndex`
3. `sourcePath + chunkIndex`
4. `sourceUri + chunkIndex`
5. `sourceName + sectionTitle + chunkIndex`
6. `content`
7. fallback index

而 `relevantIds` 是你给的标注集合。两边能对上，才算一次 true positive。

:::note
这意味着：你标注时用的 id，必须和 `RagHit` 能算出来的稳定 key 是同一种东西。如果你的命中没有稳定 `id`、`documentId` 又漂移，离线指标会静默地算错——不是因为公式错，而是因为对齐失败。
:::

## 4. 最短怎么用

`RagEvaluator` 是纯计算，不依赖任何平台、不调任何模型，拿到 `List<RagHit>` 就能跑：

```java
import io.github.lnyocly.ai4j.rag.RagEvaluator;
import io.github.lnyocly.ai4j.rag.RagEvaluation;
import io.github.lnyocly.ai4j.rag.RagHit;
import io.github.lnyocly.ai4j.rag.RagResult;

import java.util.Arrays;
import java.util.List;

// 1. 先跑一次真实检索，拿到 hits
RagResult result = rag.search(RagQuery.builder()
        .query("员工医疗报销多久内提交")
        .dataset("hr-docs")
        .embeddingModel("text-embedding-3-small")
        .topK(8)
        .build());
List<RagHit> hits = result.getHits();

// 2. 给出人工标注的相关 id（来自评测集）
List<String> relevantIds = Arrays.asList("doc-1#chunk-0", "doc-7#chunk-3");

// 3. 算指标
RagEvaluation evaluation = new RagEvaluator().evaluate(hits, relevantIds);

System.out.println("precision@K = " + evaluation.getPrecisionAtK());
System.out.println("recall@K    = " + evaluation.getRecallAtK());
System.out.println("f1@K        = " + evaluation.getF1AtK());
System.out.println("mrr         = " + evaluation.getMrr());
System.out.println("ndcg        = " + evaluation.getNdcg());
```

`evaluate(hits, relevantIds)` 不传第三个参数时，`K` 默认取 `hits.size()`——也就是评估全部召回结果。如果你想只看前几条，用带 `topK` 的重载：

```java
// 只评估前 4 条命中的指标
RagEvaluation evaluation = new RagEvaluator().evaluate(hits, relevantIds, 4);
```

`topK <= 0` 时回退成“评估全部”，`topK` 超过 `hits.size()` 时取实际命中数，不会越界。

## 5. 指标是怎么算出来的（以一个例子过一遍）

假设召回 4 条，标注相关的是 `b` 和 `d`：

```java
List<RagHit> hits = Arrays.asList(
        RagHit.builder().id("a").content("A").build(),  // rank 1
        RagHit.builder().id("b").content("B").build(),  // rank 2 相关
        RagHit.builder().id("c").content("C").build(),  // rank 3
        RagHit.builder().id("d").content("D").build()   // rank 4 相关
);

RagEvaluation eval = new RagEvaluator().evaluate(hits, Arrays.asList("b", "d"), 4);
```

得到的结果是：

| 指标 | 值 | 推导 |
| --- | --- | --- |
| `evaluatedAtK` | 4 | 全部评估 |
| `truePositiveCount` | 2 | b、d 命中 |
| `precisionAtK` | 0.5 | 2 / 4 |
| `recallAtK` | 1.0 | 2 / 2（全部相关都被召回） |
| `f1AtK` | 0.6667 | 2·0.5·1.0 / (0.5+1.0) |
| `mrr` | 0.5 | 1 / 2（第一个相关排在第 2 位） |
| `ndcg` | 0.6509 | DCG / IDCG |

注意 `mrr` 取的是**第一个**相关命中的倒数排名；`ndcg` 用的是 rank-based 增益 `1 / log2(rank+1)`，并按理想排序做归一。这些都是标准公式，AI4J 没有自造变体。

## 6. 边界情况怎么处理

`RagEvaluator` 对几个退化场景有明确行为：

- **`hits` 为空**：所有指标为 0，`truePositiveCount = 0`。
- **没有任何相关命中**：precision/recall/F1/MRR/NDCG 全为 0。
- **`relevantIds` 为空或全为空白**：`relevantCount = 0`，recall 被守卫成 0，不会除零。
- **`relevantIds` 含空白串或 null**：会被 `normalize(...)` 过滤掉。
- **`topK` 越界或非正**：安全裁剪到实际命中数，或回退成评估全部。

所以它不会因为“标注集是空的”“这次一条没召回”这种正常退化而抛异常。

## 7. 典型用法：做 A/B 对比

`RagEvaluator` 真正的价值在于做**可控变量对比**，而不是单次报一个绝对分数。常见场景：

| 你改了什么 | 该盯哪些指标 |
| --- | --- |
| 换 embedding 模型 | recall@K、ndcg |
| 调 chunk size / overlap | recall@K、precision@K |
| 把 RRF 换成 RSF/DBSF 融合 | ndcg、mrr |
| 接入 rerank | ndcg、mrr（排序修正收益） |
| 改 query planning 召回更多变体 | recall@K（关注是否召回更多，但 precision 可能下降） |

:::tip
跑离线评估时，建议固定一批 query 和标注集，每次只改一个变量。否则 precision 从 0.5 涨到 0.6，你根本说不清是 embedding 的功劳还是 chunking 的功劳。
:::

## 8. 离线 `RagEvaluator` vs 在线 `RagOnlineEvaluator`

这两个名字很像，但做的事完全不同，不要混用：

| 维度 | `RagEvaluator`（离线） | `RagOnlineEvaluator`（在线） |
| --- | --- | --- |
| 评估对象 | 检索结果 `List<RagHit>` | 最终回答 + 上下文 |
| 判定来源 | 你给的相关 id 标注 | `RagJudge`（通常是 judge 大模型） |
| 指标 | precision/recall/F1/MRR/NDCG | faithfulness、relevance 等主观分 |
| 是否调模型 | 否，纯本地计算 | 是，走 judge |
| 典型时机 | 换策略前的离线回归 | 生成回答之后的在线/批量评估 |
| 写入 trace | 否 | 是，写入 `trace.judgeEvaluation` |

一句话：`RagEvaluator` 回答“召回集准不准”，`RagOnlineEvaluator` 回答“模型有没有基于召回集胡编”。前者衡量检索链路，后者衡量生成忠实度，互不替代。

在线评判层（`RagJudge` SPI、`ChatRagJudge` 三维评分协议、评判如何写进 `RagTrace`）的完整说明见 [LLM-as-Judge](/docs/core-sdk/search-and-rag/llm-as-judge)。

如果你要看在线 judge 怎么用，参考 [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)。

## 9. 当前这一层没有替你做什么

- 不提供标注集管理或评测集存储
- 不自动跑批量 query（你自己在循环里调 `evaluate(...)`）
- 不做交叉编码器重排后的指标差异归因
- 不计算生成端指标（faithfulness/answer-relevance，那是 `RagOnlineEvaluator` 的范畴）
- 不输出报告或图表

它只做一件事：**给定 hits 和相关 id，算出标准检索指标。** 把批量编排、报告渲染留给上层应用，是合理的边界。

## 10. 最容易踩坑的 3 个点

### 10.1 标注 id 和命中 key 对不上

这是离线指标失真的头号原因。标注用 `doc-1`，但命中算出来的稳定 key 是 `doc-1#chunk-0`，结果全部相关被当成 false negative，recall 归零。

### 10.2 用绝对分数判断“好不好”

precision@K = 0.6 这个数字本身没意义，只有和“换策略前的 0.5”对比才有意义。离线评估的核心是**对比**，不是单点。

### 10.3 把离线指标当成回答质量

检索准不代表回答好。一个 recall@K = 1.0 的系统，生成阶段仍然可能跑题或幻觉。回答质量要靠 `RagOnlineEvaluator`。

## 11. 这页最该记住的结论

- `RagEvaluator` 是纯本地、无模型依赖的离线检索质量评估器
- 一次调用给出 precision/recall/F1@K、MRR、NDCG
- 命中和标注靠 `RagHitSupport.stableKey(...)` 对齐，**对齐失败会让指标静默失真**
- 它和 `RagOnlineEvaluator` 一个管召回、一个管回答，不可互相替代

## 12. 继续阅读

- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
- [Rerank](/docs/core-sdk/search-and-rag/rerank)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- [Query Planning](/docs/core-sdk/search-and-rag/query-planning)
