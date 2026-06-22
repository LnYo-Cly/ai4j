# Anthropic MessagesModelClient for agent

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-23-messages-model-client-0f5bad51/artifacts/preset/2026-06-23T14-30-00-000Z
Task Package Index: required

## 目标

agent 层新增 `MessagesModelClient`，委托 P1 的 `IMessagesService`，以原生 Anthropic 线协议跑 agent。

## 范围

- 做什么：`MessagesModelClient implements AgentModelClient`（镜像 `ChatModelClient`）；`AgentPrompt`↔`AnthropicChatCompletion` 映射；`StreamBridge` 流式桥；单测。
- 不做什么：不改 agent 核心/循环/工具调度（编程到 AgentModelClient，注入即用）；不改既有 Chat/Responses model client。
- 风险：item envelope→AnthropicMessage 映射；thinking→reasoningText。

## 预算

simple。单类 + 单测，委托既有 IMessagesService。

## 步骤

1. `MessagesModelClient`：`toAnthropicRequest(AgentPrompt)`（system+instructions→system、items→AnthropicMessage[]、tools→AnthropicTool、reasoning→thinking via extraBody）。
2. `create()`→`messagesService.messages()`→`toModelResult`（text/thinking/tool_use→AgentModelResult）。
3. `createStream()`→`StreamBridge`（AnthropicStreamHandler）→`AgentModelStreamListener` 回调 + 聚合。
4. 单测（stub IMessagesService）。

## 验收标准

- [ ] `MessagesModelClient` 委托 `IMessagesService`，编译通过
- [ ] create/stream 映射单测通过
- [ ] `mvn -pl ai4j-agent -am -DskipTests=false test` 全绿

## 工作树

- 路径：`.worktrees/feature/anthropic-native-surface`；分支：`feature/anthropic-native-surface`

## 模块关联

- Module：agent-runtime
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`
