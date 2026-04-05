---
sidebar_position: 3
---

# ModelClient 选型：Responses vs Chat

你问过一个非常关键的问题：

> 为什么 Chat 的 stream 看起来是 token-by-token，而 Responses 的 stream 是 event-by-event？

这页专门回答这个问题，并给出选型建议。

## 1. 协议层差异（本质原因）

### Chat 接口

- 典型流式形态是“文本增量块”（delta）。
- 你通常看到的是接近 token 级别的追加，但不保证“一个 event = 一个 token”。

### Responses 接口

- 流式是 **事件流**，不仅有文本，还包含：
  - response.created
  - response.in_progress
  - output_item.added
  - reasoning_summary_text.delta
  - output_text.delta
  - response.completed
- 所以它不是“只按 token 推文本”，而是“按事件类型推状态与内容”。

## 2. 在 AI4J 中怎么选

### 选 ResponsesModelClient 的场景

- 你需要结构化事件（reasoning、message、tool call）
- 你要做可观测、审计、复杂 agent
- 你重视 system/instructions 字段语义分离

### 选 ChatModelClient 的场景

- 你只需要经典聊天流式输出
- 你已有大量 chat-completion 兼容代码
- 你希望最小改动接入

## 3. 是否可以混用

可以，而且推荐在复杂系统中混用：

- 路由/规划/格式化节点：Responses（结构化更强）
- 简单问答节点：Chat（轻量稳定）

`WeatherAgentWorkflowTest` 就演示了这种混用方式。

## 4. 代码示例

```java
Agent weatherAgent = Agents.react()
        .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .toolRegistry(Arrays.asList("queryWeather"), null)
        .build();

Agent formatAgent = Agents.react()
        .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .instructions("输出严格 JSON")
        .build();
```

## 5. 流式观测建议

- Chat：主要观测文本增量和完成事件
- Responses：按事件类型分层观测（状态事件、文本事件、工具事件）

如果你的目标是构建“可追踪的 Agent 平台”，建议以 Responses 为主。

## 5.1 ChatModelClient 的 tool-call 边界

`ChatModelClient` 和直接调用 `IChatService`，在工具执行语义上是两条不同路径：

- 直接用 `IChatService.chatCompletion(...)`
  - 默认保持经典 Chat SDK 行为
  - provider 可以自己执行 `ToolUtil.invoke(...)`
  - tool result 会由 provider 适配层回填，再继续下一轮
- 通过 `ChatModelClient` 进入 Agent / Coding Agent
  - `ChatModelClient` 会在有 tools 时自动开启 `passThroughToolCalls`
  - chat provider 会把 `tool_calls` 原样交回 agent runtime
  - 真正执行工具的是 `toolExecutor`，不是 provider 适配层

这也是为什么 coding agent 里的 `read_file`、`write_file`、`apply_patch` 这类 runtime 工具，应该走 `ChatModelClient` 路径，而不是直接让 provider 自己执行。

如果你本来就是在写 Agent，一般不需要手动设置 `passThroughToolCalls`，`ChatModelClient` 已经代为处理。

## 6. 常见误解

1. “Responses 不支持实时输出” —— 错。它是事件化实时输出。
2. “Chat 一定更快” —— 不一定，取决于模型和服务端实现。
3. “必须二选一” —— 错，AI4J 支持在同一 workflow 中混用。
