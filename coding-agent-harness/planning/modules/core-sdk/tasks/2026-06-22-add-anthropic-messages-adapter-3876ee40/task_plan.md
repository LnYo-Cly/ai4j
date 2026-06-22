# Add Anthropic Messages adapter

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/core-sdk/tasks/2026-06-22-add-anthropic-messages-adapter-3876ee40/artifacts/preset/2026-06-22T12-50-30-000Z
Task Package Index: required

## 目标

在 `ai4j` 核心模块新增手写的 Anthropic Messages 格式适配器，使 SDK 支持 Anthropic 线协议（`/v1/messages`），覆盖 Claude 本体与合作厂家的 Anthropic 兼容入口。

## 范围

- 做什么：新增 `platform/anthropic/`（Config + Messages 实体 + ChatService）、`PlatformType.ANTHROPIC`、`Configuration.anthropicConfig`、工厂注册；实现 chat / stream / tool_use 映射；单测 + live 烟测。
- 不做什么：不替换现有 OpenAI 兼容适配器；不引入官方 Kotlin SDK；不改 docs-site；首轮不接入 agent/coding/cli 层。
- 主要风险：Anthropic 的 system/messages/tool_use/thinking 字段映射失真；流式 SSE 事件类型（message_start/content_block_delta/...）解析；coding-plan key 的 baseUrl/鉴权差异。

## 预算选择

选择预算：standard

选择理由：单模块（`ai4j`）、新增一个 provider 适配器（约 500–1000 行），需真实单测 + live 烟测 + 任务收口，但不跨 agent/coding/cli 多层。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/OpenAiChatService.java` | 参考适配器：OkHttp 请求构造、SSE 流、错误处理的范式 | coordinator / worker |
| C-002 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/service/IChatService.java` | 要实现的统一接口 | coordinator / worker |
| C-003 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/platform/zhipu/` | 紧凑 provider 范例（Config + 实体 + Service 三件套） | coordinator / worker |
| C-004 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceFactory.java` | provider 注册到工厂的位置 | coordinator / worker |
| C-005 | code | `ai4j/src/main/java/io/github/lnyocly/ai4j/listener/SseListener.java` | 流式 SSE 监听器复用 | coordinator / worker |
| C-006 | ref | `https://docs.bigmodel.cn/cn/guide/develop/claude/introduction` | 智谱 Claude API 兼容（Anthropic 格式）官方说明 | coordinator / reviewer |
| C-007 | ref | `https://platform.claude.com/docs/en/api/sdks/java` | Anthropic Messages 协议与 Java SDK 参考 | coordinator / reviewer |

## 步骤

1. 骨架：新建 `platform/anthropic/`（AnthropicConfig + 占位实体 + AnthropicChatService 空实现），`PlatformType.ANTHROPIC`、`Configuration.anthropicConfig`、工厂注册。
2. 请求映射：`ChatCompletion` → Anthropic Messages 请求（model / system / messages / max_tokens / stop / temperature / tools）。注意 Anthropic 的 `system` 是顶层字段、`messages` 里 content 可为字符串或 content-block 数组。
3. 响应映射：Anthropic 响应（`content: [{type:text|tool_use,...}]`、`stop_reason`、`usage`）→ `ChatCompletionResponse` / `Choice` / `ChatMessage`。
4. 流式映射：Anthropic SSE 事件（`message_start` / `content_block_start` / `content_block_delta` / `content_block_stop` / `message_delta` / `message_stop`）→ `SseListener`。
5. 测试：单测（请求/响应/流映射，离线）+ live 烟测（`@Category(LiveProviderTest.class)`，读 `ANTHROPIC_API_KEY` / `ANTHROPIC_BASE_URL`，默认 baseUrl 支持 `open.bigmodel.cn/api/anthropic`）。
6. 回归：`mvn -pl ai4j -DskipTests=false test`；live 烟测用 `-P live-provider-tests` + 真实 key 跑。
7. 收口：更新 progress / findings / review / walkthrough / lesson。

## 验收标准

- [ ] `platform/anthropic/` 实现并编译通过，`AnthropicChatService implements IChatService`
- [ ] `PlatformType.ANTHROPIC`、`Configuration.anthropicConfig`、工厂注册就位
- [ ] 请求/响应/流映射单测通过
- [ ] live 烟测对 `open.bigmodel.cn/api/anthropic` + coding-plan key 返回内容
- [ ] `mvn -pl ai4j -DskipTests=false test` 全绿（现有 103 测试不受影响）
- [ ] task-local review / walkthrough / findings / lesson 文件收口

## 工作树（Worktree）

- 路径：不使用单独 worktree
- 分支：当前工作分支
- Worker owner：coordinator
- Worker handoff commit required：no
- Coordinator integration branch：n/a
- 未使用 worktree 的原因：单模块新增 provider，dirty 工作区已存在，不额外分叉

## 长程任务判定

- 是否属于长程任务：否
- Stop Condition 摘要：映射失真 / 现有回归失败 / 必须引入 Kotlin 才能继续时，回到 coordinator 复核。

## 审查判定

- 是否需要对抗性审查：是
- 报告文件：`review.md`
- Reviewer：coordinator self-review（+ 可选 subagent）
- No-finding 要求：reviewer 无重要发现

## 关联

- 相关 Regression Gate：`mvn -pl ai4j -am "-Dtest=AnthropicChatServiceTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j -DskipTests=false test`
- 审查报告：`review.md`
- 前置发现：智谱双 baseUrl / coding-plan=Anthropic（见 `findings.md` 与记忆 `zhipu-two-baseurls-coding-plan-anthropic`）

## 模块关联（启用模块并行时填写）

- Module：core-sdk
- Step：CORE-02（同步上游 provider / protocol 变化）
- Module Plan：`coding-agent-harness/planning/modules/core-sdk/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：core-sdk 模块 active task 登记
- Harness Ledger update needed：`task_plan.md`、`review.md`、`walkthrough.md`，closeout 后 closed
- Closeout / Regression update needed：`docs/05-TEST-QA/Regression-SSoT.md` 视是否新增固定 gate 决定

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | core-sdk |

## Module Context Entry Points

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/core-sdk/brief.md | module purpose / scope |
| Module plan | coding-agent-harness/planning/modules/core-sdk/module_plan.md | module steps, active task links, handoff |
