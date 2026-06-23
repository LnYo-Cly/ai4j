# 发现记录

## 关键发现

### agent 规范模型已协议中立 → P1.5 零松耦合成本

- `AgentModelClient`/`AgentPrompt`/`AgentModelResult`/`AgentToolCall`/`AgentModelStreamListener` 均无 OpenAI 类型耦合；已有 `ChatModelClient`/`ResponsesModelClient`。
- 结论：第三个 `MessagesModelClient` 纯增量，agent 核心/循环/工具调度零改动。
- `AgentModelResult.reasoningText` 与 `onReasoningDelta` 正好是 Anthropic thinking 的家 → thinking 无损贯通。

### item envelope → AnthropicMessage

- `AgentPrompt.items` 是 `List<Object>`（ChatMessage 或 Map{type:"message"/"function_call_output"}）。
- `convertItem` 覆盖两种 → AnthropicMessage（含 tool_use / tool_result block），与 ChatModelClient 同构。
