# Anthropic native Messages surface - 进度

## 状态：已完成

## 进度记录

### [2026-06-23 13:00] - worktree + task registration

- P0 baseline 提交（commit e793970）；建 worktree `feature/anthropic-native-surface`；登记 P1 任务包。

### [2026-06-23 14:10] - P1 implementation + verification

- 做了什么：
  - 新增 `IMessagesService`（与 IChatService/IResponsesService 并列）。
  - 新增 `AnthropicMessagesService implements IMessagesService`：原生 `messages()`（单轮）/`messagesStream(handler)`（阻塞，回调原生事件）/`toEventListener`（可复用 SSE 解析）/`appendToolResults`（工具循环静态辅助）。
  - 新增 `AnthropicStreamHandler`（onStart/onDeltaText/onThinkingDelta/onToolUseStart/Delta/Complete/onStopReason/onComplete/onError）。
  - 新增类型化 `AnthropicApiException` + 子类（rate_limit/overloaded/authentication/invalid_request）。
  - `AnthropicContentBlock` 增 `thinking` 字段；响应映射补 `thinking` block→reasoningContent、流式 `thinking_delta`→onReasoningDelta。
  - `AnthropicChatService`（统一适配器）改为委托 `AnthropicMessagesService`：非流式走 `messages()`+循环；流式经 `OpenAiChunkBridge`（AnthropicStreamHandler）桥接到 OpenAI chunk，复用 `toEventListener` 解析。
  - `AiService.getMessagesService(ANTHROPIC)`。
- 验证结果：
  - ai4j 模块 119 tests（P0 116 + 原生 2 + thinking 1）0 failures。
  - live 烟测 `AnthropicNativeLiveTest`（原生 IMessagesService 路径）：GLM glm-5.1 + MiniMax MiniMax-M3 各跑 messages+stream，4/4 通过，真实内容返回、stop_reason=end_turn。
  - `harness check` 通过。
- 证据：command:.worktrees/feature/anthropic-native-surface:ai4j 119 tests, native live 4/4 (GLM+MiniMax)

## 残余

- P1.5（agent MessagesModelClient 接入）在 agent-runtime 任务。
- 多 tool 并行流式按单 tool 链路（content_block_stop 聚合）。

### [2026-06-23 closeout] - 任务收口

- 代码已合并到 main（经 PR + CI 全绿）；状态推进到 已完成。
- 残余：见 walkthrough/residual。
