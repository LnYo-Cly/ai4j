# Anthropic native Messages surface

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-06-23-anthropic-native-messages-surface-5914b973/artifacts/preset/2026-06-23T13-00-00-000Z
Task Package Index: required

## 目标

把 P0 熔在统一适配器里的原生 Anthropic 能力抽成一等公民 `IMessagesService`，原生 in/out 零转换；统一适配器瘦身为翻译器。

## 范围

- 做什么：新增 `IMessagesService` + `AnthropicMessagesService`（原生 create/stream + 工具循环）、`AnthropicStreamHandler`、类型化 `AnthropicApiException`；`AnthropicChatService` 改委托；补 thinking block/delta 映射；`AiService.getMessagesService`。
- 不做什么：agent 层接入（P1.5）；不引入官方 SDK。
- 风险：抽取后统一路径回归；thinking 字段映射失真。

## 预算

standard。单模块、抽取重构 + 新增表面 + 单测/live 烟测。

## 上下文包

| ID | 路径 | 为什么需要 |
| --- | --- | --- |
| C-001 | `platform/anthropic/chat/AnthropicChatService.java` | P0 熔合实现，要从中抽出原生层 |
| C-002 | `service/IChatService.java` / `service/IResponsesService.java` | 协议族接口范式 |
| C-003 | `service/factory/AiService.java` | provider 注册位置 |

## 步骤

1. 新增 `IMessagesService`（create/messagesStream，原生类型 in/out + `AnthropicStreamHandler`）。
2. 抽 `AnthropicMessagesService`：把 P0 的传输（executeChatCompletionRequest/buildChatCompletionRequest/SSE 解析）移入；原生 create/stream + 工具循环。
3. 新增 `AnthropicStreamHandler` 回调 + thinking_delta 路由。
4. 新增类型化 `AnthropicApiException`；传输层错误映射。
5. 响应映射补 thinking block → reasoningContent。
6. `AnthropicChatService` 改委托 `AnthropicMessagesService`，仅留 OpenAI↔Anthropic 翻译。
7. `AiService.getMessagesService(ANTHROPIC)`。
8. 单测（原生 create/stream/tool/thinking）+ live 烟测；回归。

## 验收标准

- [ ] `IMessagesService` + `AnthropicMessagesService` 就位，`AnthropicChatService` 委托
- [ ] thinking block/delta 映射有单测
- [ ] `mvn -pl ai4j -DskipTests=false test` 全绿
- [ ] `harness check` 通过

## 工作树

- 路径：`.worktrees/feature/anthropic-native-surface`
- 分支：`feature/anthropic-native-surface`
- Worker owner：coordinator
- 未使用 worktree 的原因：不适用（已用 worktree；主工作区有 observability 等无关未提交改动，按 worktree-standard 需隔离）

## 模块关联

- Module：core-sdk
- Step：CORE-02（同步上游 provider/protocol 变化）
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`
