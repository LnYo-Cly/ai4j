# Anthropic MessagesModelClient for agent

## Task ID

`2026-06-23-messages-model-client-0f5bad51`

## 创建日期

2026-06-23

## 一句话结果

新增 agent 层第三个 `AgentModelClient` 实现 `MessagesModelClient`，委托 P1 的 `IMessagesService`，让 agent 能以原生 Anthropic 线协议跑（零 OpenAI 转换），并把 thinking 贯通到 `AgentModelResult.reasoningText`。

## 为什么要做

agent 规范模型（`AgentPrompt`/`AgentModelResult`/`AgentToolCall`/`AgentModelStreamListener`）已协议中立，且已有 `ChatModelClient`/`ResponsesModelClient` 两个实现。P1 把 `IMessagesService` 提为一等公民后，agent 自然可加第三个 model client 走 Anthropic 原生线协议——无松耦合成本。

## 交付物

- `ai4j-agent/.../agent/model/MessagesModelClient implements AgentModelClient`：`AgentPrompt`→`AnthropicChatCompletion`、委托 `IMessagesService.messages()`/`messagesStream()`、`AnthropicChatCompletionResponse`→`AgentModelResult`（text→outputText、thinking→reasoningText、tool_use→AgentToolCall）。
- 流式桥 `StreamBridge implements AnthropicStreamHandler`：原生事件→`AgentModelStreamListener`（onDeltaText/onReasoningDelta/onToolCall/onComplete）。
- 单测 `MessagesModelClientTest`（stub IMessagesService，验证 create/stream 映射）。

## 完成判断

1. `MessagesModelClient` 编译通过、委托 `IMessagesService`。
2. 单测 3 个通过（create 映射 + stream 桥）。
3. `mvn -pl ai4j-agent -am -DskipTests=false test` 全绿。

## 模块关联

- Module：agent-runtime
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`
