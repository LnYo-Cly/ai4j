# 收口记录：Add Anthropic Messages adapter

## 摘要

在 `ai4j` 核心模块新增手写的 Anthropic Messages（`/v1/messages`）格式适配器，使 SDK 支持 Anthropic 线协议，覆盖 Claude 本体与合作厂家的 Anthropic 兼容入口（智谱 Coding Plan、Minimax-M3 等）。与现有 12 家 OpenAI 兼容 provider 同构（OkHttp + Jackson，零 Kotlin 依赖），把统一 OpenAI 格式双向转换到 Anthropic Messages，含 chat / stream / tool_use。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j` |
| 新增文件 | `config/AnthropicConfig.java`、`platform/anthropic/chat/entity/{AnthropicChatCompletion,AnthropicChatCompletionResponse,AnthropicMessage,AnthropicContentBlock,AnthropicTool,AnthropicUsage}.java`、`platform/anthropic/chat/AnthropicChatService.java`、测试 `AnthropicChatServiceTest`、`AnthropicTest`（live） |
| 修改文件 | `service/PlatformType.java`（+ANTHROPIC）、`service/Configuration.java`（+anthropicConfig）、`service/factory/AiService.java`（+case） |
| 删除文件 | 无 |
| 不在范围内 | docs-site、agent/coding/cli 层接入（后续任务） |

## 关键修复/交付

| 问题 | 实现 | 结果 |
| --- | --- | --- |
| SDK 零 Anthropic 支持 | 新增 `platform/anthropic/` 全套适配器 | Claude 本体 + 各家 Anthropic 兼容入口可用 |
| coding-plan key 走不通 | 适配器经 `x-api-key` + 可配 apiHost 打 Anthropic 端点 | 同一 key 从「余额不足」→秒通 |
| 协议映射 | system 顶层、tool_use/tool_result block、stop_reason↔finish_reason、usage | 单测 13 个覆盖请求/响应/流 |
| 流式协议差异 | Anthropic SSE 事件→OpenAI chat.completion.chunk 转译 | 流式 finishReason=stop、usage 正常 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| 映射单测 | `mvn -pl ai4j -am "-Dtest=AnthropicChatServiceTest" -DskipTests=false test` | PASS | 13 tests, 0 failures |
| 模块回归 | `mvn -pl ai4j -DskipTests=false test` | PASS | 116 tests（103 既有 + 13 新增） |
| 下游编译 | `mvn -pl ai4j-agent,ai4j-coding,ai4j-cli -am -DskipTests compile` | PASS | BUILD SUCCESS |
| live 烟测 | `ANTHROPIC_API_KEY=… ANTHROPIC_BASE_URL=open.bigmodel.cn/api/anthropic/ ANTHROPIC_MODEL=glm-5.1 mvn -P live-provider-tests -Dtest=AnthropicTest test` | PASS | common + stream 2 tests，内容返回、finishReason=stop |
| Diff hygiene | `git diff --check` | PASS | 无 whitespace error |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review + real regression + live smoke | 无阻塞发现 | 通过 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 首轮未接入 agent/coding/cli 层 | coordinator | yes | 后续任务把 Anthropic 作为 agent 可选 platform |
| 多 tool 并行流式为单 tool 链路实现 | coordinator | yes | content_block_stop 逐个 emit 已可聚合；如需 token 级 tool arg 流式再增强 |
| 未做 docs-site | coordinator | yes | 单独文档任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否沉淀共享 lesson？ | 否；前置发现已沉淀为 reference 记忆，实现细节保留在测试中 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 发现记录 | `findings.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
