# Add Anthropic Messages adapter - Lesson candidates

## 检查结论

- Lesson check status：checked-none
- 结论：前置发现已沉淀为 reference/project 记忆；本轮实现细节落到单测 + live 烟测，无新的 repo-wide 共享规则需要沉淀。

## 候选评估

| Candidate | 是否沉淀 | 原因 | 后续 |
| --- | --- | --- | --- |
| 智谱双 baseUrl / coding-plan=Anthropic | 已沉淀为 reference 记忆 | 非显而易见、需实测才能确认 | 记忆 `zhipu-two-baseurls-coding-plan-anthropic` |
| 手写适配器优先于官方 Kotlin SDK | 已沉淀为 project 记忆 | 架构决策，避免重复调研 | 记忆 `evaluate-replace-handwritten-adapters-with-official-sdk` |
| 厂家生态收敛到「OpenAI + Anthropic」两套线协议 | 已沉淀（含于上述两条） | 影响后续 provider 架构方向 | 同上 |
| Anthropic SSE→OpenAI chunk 流式转译（input_json 聚合到 content_block_stop 再 emit） | 否 | 属本任务实现细节，已由 `AnthropicChatServiceTest` 固化 | 保留在测试中 |
| `ChatMessage` 无参构造为 private，统一用 builder | 否 | 代码约束，已在实现中遵守 | 若跨任务反复踩坑再沉淀 |

## 备注

本任务的真实防线已落到：`AnthropicChatServiceTest`（13 个映射单测）+ live 烟测 `AnthropicTest`（coding-plan key × open.bigmodel.cn/api/anthropic）。
