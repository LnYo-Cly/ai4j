# 2026-04-02 Coding Agent Compaction Alignment Design

## 背景

对照 Claude Code 的 compaction 体系，`ai4j-coding` 在这次改动前已经具备：

- 结构化 checkpoint compaction
- split-turn 处理
- auto compact 阈值触发
- compact 后 outer loop 继续执行

但仍缺少几个更接近产品级稳定性的层次：

- full compact 之前的轻量级 microcompact
- auto compact 连续失败后的熔断
- 更明确的 compact strategy 元数据，便于 loop / CLI / ACP 诊断

## Claude Code 对照

Claude Code 的 compaction 不是单一动作，而是分层管线：

- `microCompact`: 优先清理高成本旧 tool result
- `sessionMemoryCompact`: 优先用已有 session memory 作为摘要源
- `compactConversation`: 常规 full compact
- `postCompactCleanup`: compact 后清理缓存与跟踪状态
- `autoCompact` circuit breaker: 连续失败后停止重复尝试

AI4J 当前最适合直接对齐的是其中 3 项：

1. tool-result microcompact
2. auto-compact failure circuit breaker
3. compact strategy / diagnostics

`sessionMemoryCompact` 与 Claude 的附件恢复、boundary relink、外置 session memory 文件体系耦合较深，现阶段不宜直接照搬到 `ai4j-agent` / `ai4j-coding`。

## 本轮实现

### 1. Tool Result Microcompact

在 `ai4j-coding` 增加轻量级 `tool-result microcompact`：

- 只处理旧的 `function_call_output`
- 保留最近若干个 tool result 不动
- 对超大的旧 tool result 用短 stub 替换
- 仅当 microcompact 本身足以把上下文压回安全阈值时才落盘
- 若仍超预算，则放弃 microcompact，继续走 full checkpoint compact

这保证了：

- 不破坏 assistant tool call / tool result 对应关系
- 不因为“先微压缩再 full compact”而降低 full summary 质量

### 2. Auto Compact Circuit Breaker

在 `CodingSession` 增加 auto compact 连续失败跟踪：

- 记录 consecutive failure count
- 达到阈值后打开 circuit breaker
- breaker 打开后不再重复尝试 auto compact
- 向 outer loop 暴露明确错误，避免继续自转
- 成功 compact 或手动 compact 后重置 breaker

### 3. Compact Metadata

为 `CodingSessionCompactResult` / `CodingSessionSnapshot` 增加：

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`
- `autoCompactFailureCount`
- `autoCompactCircuitBreakerOpen`

其中：

- `strategy` 会明确区分 `tool-result-micro`、`checkpoint`、`checkpoint-delta`、`aggressive-checkpoint`、`aggressive-checkpoint-delta`
- `deltaItemCount` 用于表示本次 compact 实际总结了多少增量 item
- `checkpointReused` 用于表示本次 compact 是否复用了已有 checkpoint 再追加 delta

用于 CLI / headless runtime / ACP 观察 compact 行为，也让 session-memory-first 方向的“checkpoint + delta”语义变成可观测状态。

### 4. Prompt-Too-Long Retry Chain

在 `CodingSessionCompactor` 的 summary 路径增加：

- 识别 compact summary 自身的 `prompt-too-long / context-too-large` 类错误
- 自动裁掉最老的一段待总结消息后重试
- 最多重试固定次数
- 若重试仍失败，则回退到本地 fallback checkpoint，而不是直接让 compact 整体失败

这使得 compaction 不再因为“总结请求自己过长”而把会话卡死。

### 5. Post-Compact Re-Anchoring And State Persistence

在现有 compact 结果之上，再补两层会话级语义：

- `CodingSessionState` 持久化 `latestCompactResult`
- `CodingSessionState` 持久化 `autoCompactFailureCount` / `autoCompactCircuitBreakerOpen`
- session save / resume / fork 后继续保留最近 compact 诊断与 breaker 状态
- compact 后的 hidden continuation prompt 会显式带入 checkpoint goal / constraints / blocked / next steps / critical context / process snapshots
- manual compact 成功后会清理旧的 loop artifacts，避免 compact 之后还残留上轮决策痕迹

这让 compact 不再只是“把旧上下文压短”，而是把 compact 之后的继续执行语义也稳定下来。

### 6. Session-Memory-First Fallback

在已有 checkpoint 的前提下，如果增量 compact 的 summary 模型不可用：

- 不再直接把 compact 整体判成失败
- 改为优先复用已有 checkpoint
- 将最近 delta items 本地合并回 `criticalContext / blocked / nextSteps`
- 结果显式标记 `fallbackSummary=true`

这让“已有稳定 checkpoint 的长会话”在 summary 模型短时失败时，仍然能维持较高质量的继续执行上下文。

## 保持不变的边界

- 不修改 `ai4j-agent` 的 `BaseAgentRuntime` 语义
- 不引入 Claude Code 式外置 session memory 文件系统
- 不在 `ai4j-agent` 下沉新的通用 compaction 抽象
- 不重做 attachment / boundary relink / transcript rewrite 体系

## 剩余差距

相对 Claude Code，仍有以下缺口：

- session-memory-first compact
- post-compact attachment / plan / skill reinjection
- 更完整的 post-compact cleanup / cache invalidation

这些更适合作为下一阶段在 `ai4j-coding` 继续补，而不是立即下沉到 `ai4j-agent`。

## 结论

本轮把 AI4J 的 compaction 从“单层 checkpoint compact”推进到了“microcompact + checkpoint compact + prompt-too-long retry + failure breaker”的分层形态。

这已经明显更接近 Claude Code 的 compaction 设计方向，同时保持了当前 SDK 的兼容边界和实现复杂度可控。
