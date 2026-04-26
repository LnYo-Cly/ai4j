---
sidebar_position: 3
---

# 基于向量知识库的法律助手 RAG

历史主题来源：法律助手场景的 RAG 落地实践。

## 1. 目标

- 将法规、政策、案例等私域材料纳入知识库
- 回答时引用可追溯证据，降低幻觉

## 2. 数据流程

### 2.1 入库

1. 文档解析（PDF/Word/网页）
2. 文本分块（按语义或固定长度）
3. 向量化
4. 通过 `IngestionPipeline` 写入 `VectorStore`

### 2.2 查询

1. 问题向量化
2. `RagService` 做 Top-K 召回
3. 重排与过滤
4. 拼接证据上下文
5. 让模型基于证据生成答复

## 3. 元数据建议

最低建议字段：

- `source`
- `title`
- `section`
- `updatedAt`
- `version`

有了这些字段，才能做后续追溯与版本管理。

## 4. 最小伪代码

```java
RagService ragService = aiService.getRagService(
        PlatformType.OPENAI,
        vectorStore
);

RagResult ragResult = ragService.search(RagQuery.builder()
        .query(question)
        .dataset("legal_kb_v202603")
        .embeddingModel("text-embedding-3-small")
        .topK(5)
        .build());

String context = ragResult.getContext();

ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withSystem("请仅基于提供材料回答"))
        .message(ChatMessage.withUser("问题:" + question + "\n上下文:\n" + context))
        .build();
```

## 5. 生产建议

### 5.1 证据优先

要求模型输出时显式给出证据来源（文档名/条款号）。

### 5.2 时效治理

法规有版本更新时，必须支持“按版本召回”或“失效材料剔除”。

### 5.3 人工校验

法律场景高风险，建议对关键输出增加人工复核流程。

## 6. 指标体系

- 检索命中率
- 证据覆盖率
- 人工纠错率
- 单次回答成本

## 7. 常见坑

- 分块过大导致召回噪声高
- 元数据缺失导致无法回溯
- prompt 未限制“必须基于证据”导致幻觉上升

建议先把“检索质量”和“证据引用”做稳，再优化文案表达。
