# Add Anthropic Messages adapter - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。阻塞性问题请写入 `review.md`。

## 研究发现

### 智谱双 baseUrl：coding-plan key 只在 Anthropic 端点有效

- 背景：用户提供的 GLM coding-plan key 在 SDK（OpenAI 格式 `api/paas/v4`）上调 `glm-5.1` 报「余额不足或无可用资源包」。
- 发现（2026-06-22 实测，同一 key）：打 `api/paas/v4/chat/completions`（OpenAI 格式）→ `{"code":"1113","message":"余额不足或无可用资源包,请充值。"}`；打 `api/anthropic/v1/messages`（Anthropic 格式）→ 正常返回 `{"type":"message","model":"glm-5.2","content":[{"type":"text","text":"Hi!"}],"stop_reason":"end_turn",...}`。
- 结论：智谱有两个互不通账的 baseUrl：
  - `https://open.bigmodel.cn/api/paas/v4` — OpenAI 格式，普通资源包/充值余额通道。
  - `https://open.bigmodel.cn/api/anthropic` — Anthropic 格式，GLM Coding Plan 专属通道（Claude Code 等编程工具走这条）。
- 影响：当前 SDK 只有 OpenAI 格式适配器，**无法**用 coding-plan key 的正道。这不是 key 坏了也不是真没钱，是端点/协议选错。

### 厂家生态收敛到「OpenAI 格式 + Anthropic 格式」两套线协议

- 发现：Minimax-M3 文档首推「Anthropic SDK / Anthropic API」入口；智谱提供 Claude API 兼容；越来越多厂家为接入 Claude Code / coding-agent 生态而暴露 Anthropic Messages 端点。
- 影响：正确的设计是「2 套通用格式适配器 × 可配 baseUrl/key」，而非「每家一个适配器」。SDK 已有 OpenAI 格式那半（12 家），缺 Anthropic 格式这半。

### 当前 SDK 零 Anthropic 支持

- 发现：全仓库 grep `anthropic|claude|x-api-key|/v1/messages` 在 main 代码零命中；`platform/` 下无 `anthropic` 包；`PlatformType` 12 个枚举无 ANTHROPIC。
- 影响：补一个手写 Anthropic 适配器是覆盖另外半边生态的唯一动作；不必引入官方 Kotlin SDK（见记忆 `evaluate-replace-handwritten-adapters-with-official-sdk`）。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 实现路线 | 手写适配器（纯 Java + fastjson2） | 与现有 12 家同构、零 Kotlin/Jackson 依赖、可即时改源码 | 包官方 anthropic-java（Kotlin+Jackson） | accepted |
| baseUrl | AnthropicConfig 可配 apiHost，默认 `https://api.anthropic.com/` | 一套适配器覆盖 Claude 本体 + 各家兼容入口 | 写死或每家一个包 | accepted |
| 鉴权头 | `x-api-key` + `anthropic-version: 2023-06-01` | Anthropic 线协议标准；coding-plan 端点同样接受 | Authorization: Bearer（备用，如某些网关需要） | accepted |
| 协议范围（首轮） | chat / stream / tool_use / system | 覆盖 coding-agent 主路径；thinking 作为可选透传 | 首轮就做完整 thinking/reasoning_content | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| ~~coding-plan 端点鉴权用 `x-api-key` 还是 `Authorization: Bearer`~~ | **已确认**：`x-api-key` 实测通过（live 烟测） | coordinator | 已闭环 |
| ~~tool_use 映射是否首轮必须~~ | **已实现**：非流式/流式 tool_use 均支持（单测 + 循环） | coordinator | 已闭环 |
| ~~是否同时支持 `thinking` 字段~~ | **已支持**：经 extraBody 透传（`shouldPassthroughExtraBodyForThinking` 单测） | coordinator | 已闭环 |

## 实现阶段发现

- 编译期修正：`ChatMessage` 无参构造为 private（Lombok `@NoArgsConstructor(access=PRIVATE)`），统一改用 `builder()`；流式转译器内 `eventSource` 闭包用 `currentEventSource` 字段持有（onOpen/onEvent 赋值），避免在 helper 方法里引用越界。
- 流式 tool_use 聚合策略：`input_json_delta` 累积到 content_block_stop 再以完整 toolCall（id+name+完整 arguments）一次性 emit，规避 OpenAI 分片 tool_call 在无 index 字段时的路由歧义，且 SseListener 的 `addCompleteToolCalls` 路径可正确消费（content.text="" + name + 合法 JSON object）。
- stop_reason 映射：`tool_use→tool_calls`、`max_tokens→length`、`end_turn/stop_sequence→stop`，与 OpenAI finish_reason 语义对齐，保证上层 agent/coding 的循环判断不被破坏。
- live 佐证：coding-plan key 经 Anthropic 端点 `open.bigmodel.cn/api/anthropic/` + glm-5.1，非流式返回内容、流式 finishReason=stop——直接印证「coding-plan key 走 Anthropic 格式」的判断，也反向证明了早先 glm-5.1「余额不足」是端点选错。

