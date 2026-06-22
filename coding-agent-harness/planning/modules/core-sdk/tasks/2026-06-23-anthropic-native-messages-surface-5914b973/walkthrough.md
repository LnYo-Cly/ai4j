# 收口记录：Anthropic native Messages surface

## 摘要

把 P0 熔在统一适配器里的原生 Anthropic 能力抽成与 `IChatService`/`IResponsesService` 并列的一等公民 `IMessagesService`。原生 in/out 零 OpenAI 转换；统一适配器瘦身为纯翻译器并委托原生。补 thinking block/delta 映射 + 类型化异常。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` |
| 新增文件 | `service/IMessagesService`、`platform/anthropic/chat/AnthropicMessagesService`、`platform/anthropic/stream/AnthropicStreamHandler`、`platform/anthropic/errors/AnthropicApiException`、测试 `AnthropicMessagesServiceTest`、`AnthropicNativeLiveTest` |
| 修改文件 | `AnthropicContentBlock`(+thinking)、`AnthropicChatService`(委托)、`AiService`(+getMessagesService) |
| 不在范围内 | agent 层接入（P1.5）、docs-site |

## 关键交付

| 问题 | 实现 | 结果 |
| --- | --- | --- |
| 原生能力熔在统一适配器 | 抽出 `AnthropicMessagesService`，统一适配器委托 | 原生调用方零 OpenAI 转换 |
| thinking 无家 | content block +thinking 字段；响应→reasoningContent，流式→onReasoningDelta | thinking 贯通到 agent AgentModelResult.reasoningText |
| 流式协议差异 | `toEventListener` 复用解析；`OpenAiChunkBridge` 桥接到 OpenAI chunk | 统一与原生共用解析，零重复 |
| 错误全塌 CommonException | 类型化 `AnthropicApiException` | 原生调用方可精确 catch |

## 验证

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 原生单测 | PASS | `AnthropicMessagesServiceTest` 2 tests + thinking 1 |
| ai4j 回归 | PASS | 119 tests 0 failures |
| live 烟测（GLM coding plan × api/anthropic, glm-5.1） | PASS | 原生 messages 返回 "I am GLM..."，stream end_turn |
| live 烟测（MiniMax coding plan × api.minimaxi.com/anthropic, MiniMax-M3） | PASS | 原生 messages 返回 "I'm MiniMax-M3..."，stream end_turn |

## 审查结论

self review + 真实回归 + live 烟测：无阻塞发现。

## 残余风险

| 风险 | 接受 | 跟进 |
| --- | --- | --- |
| 多 tool 并行流式为单 tool 链路 | yes | content_block_stop 聚合已可用；需 token 级再增强 |
| 首轮未接入 agent 层 | yes | P1.5 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度 | `progress.md` |
