# 发现记录

## 研究发现

### P0 的熔合结构

- 背景：P0 `AnthropicChatService` 同时是统一 IChatService 适配器 + 原生传输持有者（executeChatCompletionRequest / buildChatCompletionRequest / SSE 解析都在里面）。
- 抽取点：传输方法本身就是原生签名（吃 AnthropicChatCompletion，吐 AnthropicChatCompletionResponse），只需去掉 OpenAI 转换包装即得原生服务。

### agent 层已协议中立（决定 P1.5 成本）

- 发现：`AgentModelClient`/`AgentPrompt`/`AgentModelResult`/`AgentToolCall`/`AgentModelStreamListener` 均无 OpenAI 类型耦合；已有 `ChatModelClient`/`ResponsesModelClient` 两个 model client。
- 影响：P1.5（agent 接入）= 新增第三个 `MessagesModelClient`，无松耦合成本。AgentModelResult.reasoningText 正好是 thinking 的家。

## 技术决策

| 决策 | 选择 | 原因 |
| --- | --- | --- |
| 原生表面形态 | 接口 IMessagesService | 与 IChatService/IResponsesService 一致；未来 Bedrock/Vertex 不同实现 |
| thinking 落点 | reasoningContent(响应) / onReasoningDelta(流) | 与 agent AgentModelResult.reasoningText 对齐 |
| 异常 | 类型化 AnthropicApiException | 原生调用方精确 catch；统一路径映射 CommonException |
