---
sidebar_position: 4
---

# 引用、Trace 与前端展示

做 RAG 时，最容易混淆的其实不是检索算法，而是结果应该怎么给下游消费。

要先明确区分三类输出：

- 给模型看的上下文：`RagResult.context`
- 给用户看的引用：`RagResult.citations`
- 给工程排障看的检索轨迹：`RagResult.trace`

这三层不要混在一起。

如果混在一起，通常会出现两类问题：

- UI 上把一堆 `score/rank/retrieverSource` 暴露给最终用户，体验很差
- 为了界面简化只保留 `context`，后面又没法解释“答案来自哪”

## 1. 三层输出各自解决什么问题

### 1.1 `context`

这是给模型消费的文本块。

它通常长这样：

```text
[S1] employee-handbook.pdf / 员工请假
员工请假需至少提前 3 个工作日提交申请，紧急病假除外。

[S2] employee-handbook.pdf / 医疗报销
补充医疗报销需在费用发生后 30 日内提交单据。
```

作用：

- 直接注入 `Chat` / `Responses`
- 作为 Agent 的外部证据上下文
- 作为 Flowgram 里 `KnowledgeRetrieve -> LLM` 的桥接文本

### 1.2 `citations`

这是给用户和前端展示的引用结构。

它来自：

- `RagResult.getCitations()`
- `RagResult.getSources()`

每条 `RagCitation` 当前包含：

- `citationId`
- `sourceName`
- `sourcePath`
- `sourceUri`
- `pageNumber`
- `sectionTitle`
- `snippet`

作用：

- 在回答区展示来源
- 把 `[S1]`、`[S2]` 变成可点击引用
- 支持文件预览、页码跳转、原文片段展开

### 1.3 `trace`

这是给工程调试和运营分析看的，不是给普通用户看的。

它来自：

- `RagResult.getTrace()`

里面会有：

- `retrievedHits`
- `rerankedHits`

每条 `RagHit` 还可能带：

- `rank`
- `retrieverSource`
- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

作用：

- 排查为什么这条被召回
- 排查为什么它排第一
- 比较 dense / bm25 / hybrid / rerank 的效果

## 2. 推荐的消费方式

标准做法不是只传一个字符串，而是把三层结果一起保存：

```java
RagResult ragResult = ragService.search(ragQuery);

String modelContext = ragResult.getContext();
List<RagCitation> citations = ragResult.getCitations();
RagTrace trace = ragResult.getTrace();
```

推荐的职责划分：

- `context` 只负责给模型
- `citations` 只负责给用户界面
- `trace` 只负责给调试、审计、后台分析

## 3. 前端怎么展示引用

### 3.1 最稳的 UI 结构

建议拆成两块：

1. 回答正文
2. 来源列表

回答正文里保留：

- `[S1]`
- `[S2]`

来源列表里展示：

- 文件名
- 小节标题
- 页码
- 摘要片段

这样用户既能看到回答中的引用编号，也能在下方快速核对来源。

### 3.2 前端映射方式

最简单的映射关系就是：

- 正文里的 `[S1]`
- 对应 `citations` 中 `citationId = "S1"`

前端数据结构示意：

```json
{
  "answer": "员工年假审批通过前不能先离岗。[S1] 医疗报销需在 30 日内提交。[S2]",
  "citations": [
    {
      "citationId": "S1",
      "sourceName": "employee-handbook.pdf",
      "sectionTitle": "员工请假",
      "snippet": "年假审批通过后方可离岗，未审批擅自离岗按旷工处理。"
    },
    {
      "citationId": "S2",
      "sourceName": "employee-handbook.pdf",
      "sectionTitle": "医疗报销",
      "snippet": "补充医疗报销需在费用发生后 30 日内提交单据。"
    }
  ]
}
```

### 3.3 不建议直接给用户看的字段

这些字段更适合后台调试，而不是直接展示给终端用户：

- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`
- `retrieverSource`

除非你的产品本身就是检索分析平台，否则这些字段应该隐藏到调试面板里。

## 4. Agent 里应该怎么用

Agent 场景里，最容易犯的错是把整个 `RagResult` 原样塞进 prompt。

更推荐这样分层：

### 4.1 给模型的部分

只给：

- `ragResult.getContext()`

例如：

```java
String context = ragResult.getContext();
String prompt = "请仅基于以下资料回答，并尽量保留引用编号：\n\n" + context;
```

### 4.2 给运行时状态的部分

单独保存：

- `ragResult.getCitations()`
- `ragResult.getTrace()`

这样做的意义：

- 模型上下文更干净
- 最终回答后还能把引用回填到 UI
- 出问题时还能做后台排查

### 4.3 给最终输出的部分

Agent 最终返回建议不是只给字符串，而是给结构化结果：

```json
{
  "answer": "员工年假审批通过前不能先离岗。[S1]",
  "citations": [...],
  "trace": {...}
}
```

如果终端用户不需要 `trace`，就只在内部保存，不必对外暴露。

## 5. Flowgram 里怎么接

`KnowledgeRetrieve` 节点当前已经输出了完整可消费结构，不只是 `context`。

从执行器可以直接确认，当前输出包括：

- `matches`
- `hits`
- `context`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`
- `count`

这意味着在 Flowgram 里，你至少有两种常见接法。

### 5.1 给 LLM 节点

把：

- `knowledge.context`

传给下游 `LLM` 节点，作为回答证据。

这是最常见的：

```text
KnowledgeRetrieve -> LLM -> End
```

### 5.2 给前端结果面板

把：

- `knowledge.citations`
- `knowledge.trace`

作为任务结果的一部分返回前端。

这样前端就能同时展示：

- 回答正文
- 来源列表
- 调试面板里的召回/精排细节

## 6. Flowgram 前端建议怎么展示

推荐拆成三块面板：

### 6.1 回答区

- 展示最终回答文本
- 保留 `[S1]`、`[S2]`

### 6.2 来源区

- 读取 `citations`
- 展示 `sourceName / pageNumber / sectionTitle / snippet`

### 6.3 调试区

- 读取 `trace`、`retrievedHits`、`rerankedHits`
- 展示 `rank/retrieverSource/retrievalScore/fusionScore/rerankScore`

这样产品层的语义会很清楚：

- 普通用户看回答和引用
- 开发/运营看检索轨迹

## 7. 一个更合理的后端返回结构

如果你要自己封一层 API，不建议只返回：

```json
{
  "answer": "...",
  "context": "..."
}
```

更推荐：

```json
{
  "answer": "...",
  "knowledge": {
    "context": "...",
    "citations": [...],
    "trace": {
      "retrievedHits": [...],
      "rerankedHits": [...]
    }
  }
}
```

这样以后要加：

- 文件预览
- 页码跳转
- 来源高亮
- 调试面板

都不用再改整体返回形态。

## 8. 什么时候应该隐藏 trace

默认建议：

- 终端用户：隐藏
- 管理后台：可选展示
- 开发环境：建议展示
- 评测/调优环境：必须展示

因为 `trace` 的价值主要在：

- 调参
- 诊断
- 评估
- 解释排序过程

不是普通用户阅读答案时的主信息。

## 9. 推荐的落地原则

可以直接照这三个原则做：

1. 不要只保留 `context`，至少同时保留 `citations`
2. 不要把 `trace` 直接暴露给普通用户
3. 不要让模型自己“编来源”，来源必须来自 `RagCitation`

## 10. 继续阅读

1. [混合检索与 Rerank 实战工作流](/docs/ai-basics/rag/hybrid-retrieval-and-rerank-workflow)
2. [RAG 与知识库增强总览](/docs/ai-basics/rag/overview)
3. [Flowgram 内置节点](/docs/flowgram/built-in-nodes)
4. [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)
