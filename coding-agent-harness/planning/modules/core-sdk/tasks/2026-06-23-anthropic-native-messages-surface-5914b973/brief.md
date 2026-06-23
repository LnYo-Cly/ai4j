# Anthropic native Messages surface

## Task ID

`2026-06-23-anthropic-native-messages-surface-5914b973`

## 创建日期

2026-06-23

## 一句话结果

把 P0 里熔在 `AnthropicChatService`（统一 IChatService 适配器）中的原生能力抽出来，提升为与 `IChatService`/`IResponsesService` 并列的一等公民 `IMessagesService`，让原生 Anthropic 调用方零 OpenAI 转换、零字段丢失。

## 为什么要做

P0 的 `AnthropicChatService` 同时承担了「统一 OpenAI 适配」和「原生 Anthropic 传输」两件事——原生类型存在但只能透过 OpenAI 入口够到。对系统本就说 Anthropic 方言的调用方，这是有损来回转换。拆出原生 `IMessagesService` 后：原生 in (`AnthropicChatCompletion`) → 原生 out (`AnthropicChatCompletionResponse`)，零转换；统一适配器瘦身为纯翻译器，委托原生。

## 交付物

- `service/IMessagesService.java`（与 `IChatService`/`IResponsesService` 并列）
- `platform/anthropic/chat/AnthropicMessagesService implements IMessagesService`（原生 create/stream + 工具循环，从 P0 抽出传输层）
- `platform/anthropic/stream/AnthropicStreamHandler`（原生事件回调：onStart/onContentBlockDelta(text_delta|thinking_delta|input_json_delta)/onMessageDelta/onComplete/onError）
- `platform/anthropic/errors/AnthropicApiException` + 类型化子类（rate_limit/overloaded/invalid_request）
- `AnthropicChatService`（统一适配器）改为委托 `AnthropicMessagesService`，只留 OpenAI↔Anthropic 翻译
- 响应映射补 `thinking` block → reasoningContent / 流式 thinking_delta → onReasoningDelta
- `AiService.getMessagesService(PlatformType.ANTHROPIC)`
- 单测（原生 create/stream/tool 循环）+ live 烟测

## 边界

- 范围内：`ai4j` 模块；原生表面抽取 + thinking 处理 + 类型化异常。
- 范围外：agent 层接入（P1.5，agent-runtime 任务）；docs-site；不引入官方 Kotlin SDK。
- 停止条件：抽取后统一路径回归失败；或必须引入 Kotlin 才能继续。

## 完成判断

1. `IMessagesService` 接口 + `AnthropicMessagesService` 实现就位；`AnthropicChatService` 委托它。
2. 原生 create/stream 单测通过；thinking block/delta 正确映射。
3. `mvn -pl ai4j -DskipTests=false test` 全绿（P0 的 116 测试不受影响 + 新增）。
4. `harness check` 通过。
