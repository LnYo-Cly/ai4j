# 设计草案：Agent Observability Enhancement

## 目标
把 agent / session / CLI / ACP 的观测信息收成同一套事实源，让每一次运行都能稳定追踪 runId / sessionId / turnId / eventId，并且把 memory / compact / sandbox / tool / handoff / team task 的关键事件统一进入 trace、session log 和 CLI 输出。

## 方案
1. **统一信封**
   - 给 `AgentEvent`、`AgentSessionEvent`、`SessionEvent` 增加稳定的 correlation 字段。
   - 由 `BaseAgentRuntime` / `AgentSession` / `CodingCliSessionRunner` 在边界上补齐这些字段。
2. **观测融合**
   - `AgentTraceListener` 消化新增字段，保留现有 span 结构，同时把 sandbox / memory / compact 事件补入 trace。
   - CLI/ACP 的 session 更新和 `/status` 输出也带同一套字段。
3. **输出收敛**
   - CLI 保留人类可读输出，但补一个机器可读摘要入口。
   - ACP 与 CLI 的状态字段对齐，避免“同一会话在不同 surface 看见不同事实”。

## 取舍
- 不新建第二套 tracing 系统，避免重复维护。
- 不把敏感 prompt/tool data 默认全量落盘，默认做截断/脱敏。
- 不重构 memory 算法本身，只暴露观测点。

## 预计改动面
- `ai4j-agent`: `AgentEvent`、`AgentSession*`、`trace/*`、`runtime/BaseAgentRuntime.java`
- `ai4j-cli`: `runtime/*`、`acp/*`、`session/*`、`SlashCommandController.java`
- 文档：`docs-site/docs/agent/*` 中 trace/session/memory/overview 相关页面

## 验证
- 最小单测：event / snapshot / trace listener / session log 字段回归
- CLI 侧 smoke：`/status`、`/events`、`/sandbox`、ACP session update
- 文档：构建 docs-site 或至少检查相关页面一致性
