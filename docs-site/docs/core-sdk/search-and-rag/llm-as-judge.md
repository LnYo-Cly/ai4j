---
sidebar_position: 12
title: LLM-as-Judge (Answer Quality)
description: "讲透 ai4j 的 RAG 回答质量评判层：RagJudge SPI、内置 ChatRagJudge 的三维评分协议（faithfulness/contextRelevance/answerRelevance，temperature 0、json_object、容错解析、分数钳制 [0,1]）、RagOnlineEvaluator 如何把评判写进 RagTrace，以及它与离线 RagEvaluator 检索指标的分工。"
tags: [reference]
---

# LLM-as-Judge（回答质量评判）

[RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation) 里的 `RagEvaluator` 算的是**检索质量**——召回的相关 id 对不对。但召回准不代表回答好:一个 recall@K = 1.0 的系统,生成阶段仍可能跑题或幻觉。回答质量要用**评判层**(LLM-as-judge)来估。

这一层很小,但和检索指标不可互替。这页讲清它的 SPI、内置实现的固定协议、评判结果去哪了、以及它和离线指标的边界。

## 1. 核心源码入口

| 类 | 角色 |
| --- | --- |
| `RagJudge` | **SPI 接口**,一个方法 `judge(RagJudgeRequest) -> RagJudgeEvaluation`。你想换评判逻辑就实现它。|
| `ChatRagJudge` | 内置实现:用 `IChatService` 调模型,按固定 rubric 出三维分数。|
| `RagJudgeRequest` | 评判输入:`query` / `answer` / `context` / `hits`。|
| `RagJudgeEvaluation` | 评判输出:三个分数 + `reason` + `rawOutput`。|
| `RagOnlineEvaluator` | 把 judge 包成"对一次 RAG 结果 + 回答做在线评判",并把结果写进 `RagTrace`。|

## 2. 三个评分维度

`ChatRagJudge` 的 system prompt 固定要求模型返回三个维度的分数(各 0 到 1):

| 维度 | 评什么(来自 user prompt 的 rubric) |
| --- | --- |
| `faithfulnessScore` | 回答是否**被检索到的上下文支持**(不编造) |
| `contextRelevanceScore` | 检索到的上下文是否**与问题相关** |
| `answerRelevanceScore` | 回答是否**直接切中问题** |

faithfulness 管"幻觉",contextRelevance 管"召回质量",answerRelevance 管"答非所问"。三个维度一起才描述清楚一次 RAG 回答的质量——单看任一个都不够。

## 3. `ChatRagJudge` 的固定协议

内置 judge 的评判调用是**完全确定性的协议**,不是让你自由发挥 prompt:

- **system prompt 固定**:`"You are a strict RAG evaluator. Return only JSON with faithfulnessScore, contextRelevanceScore, answerRelevanceScore, reason. Scores must be numbers from 0 to 1."`
- **temperature = 0.0**:尽可能压低评判的随机性。
- **response_format = json_object**:强制模型出 JSON。
- **user prompt 固定结构**:Question / Answer / Retrieved context 三段 + 每个维度的判分说明。

```java
ChatRagJudge judge = new ChatRagJudge(chatService, "glm-4.6");   // 任意 IChatService + 模型名
RagJudgeEvaluation result = judge.judge(RagJudgeRequest.builder()
        .query("AI4J 支持哪些向量库?")
        .answer(answerText)
        .context(retrievedContext)
        .hits(hits)
        .build());

Double faithfulness = result.getFaithfulnessScore();    // 0..1,可能为 null
String reason = result.getReason();                      // 模型给的理由
```

### 3.1 容错解析与分数钳制

模型不是 schema 保证的 API,judge 对输出做了两层防御:

- **JSON 提取**:不要求模型只回 JSON——取输出里第一个 `{` 到最后一个 `}` 之间的子串再解析。模型在 JSON 前后加解释也认。
- **分数钳制**:每个分数 `score(...)` 读出来后钳制到 `[0, 1]` 区间(小于 0 记为 0,大于 1 记为 1);键缺失则该维度为 `null`,**不是 0**——null 和真实的 0 区分开。

`rawOutput` 原样保留模型输出,排查用。

## 4. `RagOnlineEvaluator`:把评判接进 RAG 流

直接用 `ChatRagJudge.judge(...)` 要自己组装 request。`RagOnlineEvaluator` 替你做这步:吃一个 `RagResult`(一次检索的结果)+ 生成的回答,自动组装 request 并评判。

```java
RagOnlineEvaluator evaluator = new RagOnlineEvaluator(judge);

RagResult ragResult = ragTool.retrieve(...);   // 一次检索,带 query/context/hits
String answer = ...;                            // 生成阶段产出的回答

RagJudgeEvaluation evaluation = evaluator.evaluate(ragResult, answer);
```

`evaluate` 从 `RagResult` 取 `query` / `context` / `hits`,配上 `answer` 交给 judge。

## 5. 评判结果去哪了:写进 `RagTrace`

`RagOnlineEvaluator.evaluate(...)` 不只返回评判——它还**把评判塞进这次检索的 `RagTrace`**:

```
result.getTrace().setJudgeEvaluation(evaluation);
```

所以回答质量的分数跟着 trace 走,后续在 [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace) 里能取到。这让"检索的引用"和"回答质量的评判"挂在同一条 trace 上,审计和 UI 展示能一起拿。

## 6. 自定义 judge:实现 `RagJudge` SPI

不想用 `ChatRagJudge` 的固定 rubric?实现 `RagJudge` 接口即可:

```java
public class MyPairwiseJudge implements RagJudge {
    @Override
    public RagJudgeEvaluation judge(RagJudgeRequest request) throws Exception {
        // 你的评判逻辑:成对比较、自定 rubric、调不同模型……
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

`RagOnlineEvaluator` 只依赖 `RagJudge` 接口,不绑 `ChatRagJudge`。换 judge 不用改 evaluator。

## 7. 与离线 `RagEvaluator` 的分工(别混)

| | `RagEvaluator`(离线) | `RagOnlineEvaluator` + `RagJudge`(在线) |
| --- | --- | --- |
| 算什么 | **检索**质量(召回准不准) | **回答**质量(回答支不支持、切不切题) |
| 要模型吗 | **不要**(纯本地,比对 hits 和标注 id) | **要**(judge 是 LLM 调用) |
| 确定性 | 完全确定(同一 hits/标注 → 同一分数) | 非确定(LLM,尽管 temperature=0) |
| 指标 | precision/recall/F1@K、MRR、NDCG | faithfulness/contextRelevance/answerRelevance |
| 用法 | 离线批量评测检索策略(A/B) | 在线评判单次回答质量,入 trace |

一个管"召回",一个管"回答",**不可互相替代**。recall@K = 1.0 的系统,faithfulness 仍可能低(召回准但生成跑题);faithfulness = 1.0 的系统,recall 仍可能低(回答忠实但漏召回)。

## 8. 边界与踩坑

- **judge 是 LLM,分数是"意见"不是真值**。temperature=0 压低随机性但不消除——同一输入跨模型/跨时间可能略变。别把 0.83 当成精确读数,要**对比**用(换策略/换 prompt 前后)。
- **需要一个能跑的 IChatService + 模型**。judge 的成本是一次额外的 LLM 调用,批量评测要算清调用次数。
- **维度缺失是 `null` 不是 `0`**。模型没给某个分数时该维度为 null——聚合统计时显式处理 null,别默认成 0 拉低均值。
- **contextRelevance 与离线 recall 视角不同**:recall 看的是"相关 id 有没有进 hits"(标注级),contextRelevance 看的是"检索文本对不对题"(语义级)。两者通常正相关但不对齐。
- **judge 不替你管评测集**。批量编排(对 N 个 query 跑 judge、汇总均值/分布)由上层应用做,本层只做"评判一次"。

## 9. 继续阅读

- **离线检索指标**(recall/NDCG,与 judge 分工):[RAG Evaluation](/docs/core-sdk/search-and-rag/evaluation)
- **评判结果随 trace 走**:[Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)
- **检索本身怎么做**:[Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)
