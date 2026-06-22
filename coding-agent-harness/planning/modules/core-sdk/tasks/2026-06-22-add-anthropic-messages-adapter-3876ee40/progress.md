# Add Anthropic Messages adapter - 进度

## 状态：审查中

## 进度记录

### [2026-06-22 20:40] - task registration

- 做了什么：登记 core-sdk 模块任务，创建任务包；前置发现（智谱双 baseUrl、coding-plan=Anthropic、SDK 零 Anthropic 支持）固化到 findings 与项目记忆。
- 验证结果：`harness check` 通过。
- 证据：command:G:\My_Project\java\ai4j-sdk:task package created + harness check passed

### [2026-06-22 21:30] - EXEC-01/02 implementation

- 做了什么：
  - 新增 `platform/anthropic/`：`AnthropicConfig`、Messages 实体（`AnthropicChatCompletion`/`AnthropicChatCompletionResponse`/`AnthropicMessage`/`AnthropicContentBlock`/`AnthropicTool`/`AnthropicUsage`）、`AnthropicChatService`（实现 `IChatService` + `ParameterConvert` + `ResultConvert`）。
  - 请求映射：system 抽顶层、user/assistant/tool→Anthropic messages（含 tool_use/tool_result block）、OpenAI Tool→AnthropicTool（input_schema）、max_tokens/temperature/top_p/stop_sequences/stream/extraBody（透传 thinking 等）。
  - 响应映射：content blocks→ChatMessage（text + toolCalls）、stop_reason→finish_reason（tool_use→tool_calls、max_tokens→length、end_turn→stop）、usage→Usage。
  - 流式映射：Anthropic SSE 事件（message_start/content_block_delta(text_delta|input_json_delta)/content_block_stop/message_delta/message_stop）→ OpenAI chat.completion.chunk；tool_use 在 content_block_stop 聚合为完整 toolCall。
  - 工具调用循环：复用 `requiresFollowUp` 模式，非流式/流式均支持自动 invoke + tool_result 回传。
  - 鉴权：`x-api-key` + `anthropic-version` 头（非 Bearer）。
  - 注册：`PlatformType.ANTHROPIC`、`Configuration.anthropicConfig`、`AiService.createChatService` case。
- 验证结果：
  - 编译 BUILD SUCCESS；ai4j 模块 116 tests（103 既有 + 13 新增）0 failures。
  - 下游 agent/coding/cli 编译 BUILD SUCCESS（纯增量改动，无回归）。
- 证据：command:G:\My_Project\java\ai4j-sdk:ai4j 116 tests pass, downstream compile SUCCESS

### [2026-06-22 21:35] - live smoke test

- 做了什么：用智谱 coding-plan key 经 Anthropic 端点 `open.bigmodel.cn/api/anthropic/` 跑 `AnthropicTest`（common + stream，model=glm-5.1）。
- 验证结果：2 tests 通过，BUILD SUCCESS。非流式返回内容；流式 `finishReason=stop`、usage 统计正常。
- 关键证明：同一 key 之前在 OpenAI 格式端点 `api/paas/v4` 报「余额不足」，现在经 Anthropic 适配器打到正确端点秒通——印证了「coding-plan key 走 Anthropic 格式」的判断。
- 证据：command:G:\My_Project\java\ai4j-sdk:AnthropicTest 2 tests pass against zhipu api/anthropic

### [2026-06-22 21:40] - diff hygiene

- 做了什么：`git diff --check`。
- 验证结果：无 whitespace error（仅 Windows LF→CRLF 提示）。
- 证据：command:G:\My_Project\java\ai4j-sdk:diff check clean

## 残余

- 首轮未做 agent/coding/cli 层接入（只交付 provider 适配器 + 单测/live 烟测）；后续任务接入。
- 多 tool 并行流式（同回合多个 tool_use block）按单 tool 链路实现，已能聚合；多 block 顺序在 content_block_stop 逐个 emit，SseListener 可累积。
- 未做 docs-site 文档；Minimax 新网关修复是独立小任务（见记忆）。

## 协调者交接（Coordinator）

- Global sync status：synced
- Registry update needed：core-sdk 模块 active task 状态更新
- Harness Ledger update needed：closeout 后 `harness governance rebuild --apply`
- 负责人：coordinator
