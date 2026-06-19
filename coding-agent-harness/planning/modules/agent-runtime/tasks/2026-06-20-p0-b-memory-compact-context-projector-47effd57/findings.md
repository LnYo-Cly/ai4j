# Findings - P0-B Memory Compact Context Projector

## 发现记录

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P1 | Prompt projection 不能只接入 ReAct；CodeActRuntime 也覆盖 `buildPrompt`。 | `CodeActRuntime.buildPrompt(...)` 原先直接使用 `memory.getItems()`。 | 让 CodeActRuntime 调用 Base 的 `projectItems(...)`。 | fixed |
| F-002 | P2 | `MEMORY_COMPRESS` report 如果只发 publisher 不传 listener，stream 调用方看不到投影事件。 | `BaseAgentRuntime.projectItems(...)` 原先 `publish(context, null, ...)`。 | 将 step/listener 传入 projection publish。 | fixed |
| F-003 | P2 | Compact 结果不能只是一段 summary 字符串，需要预留结构化字段。 | 总规划刷新稿与 task plan 验收。 | `CompactResult` 保留 completed/pending/decisions/artifacts/failed commands/test results/user confirmations/sandbox/open questions/context report。 | fixed |
| F-004 | P3 | 内置 compact policy 只是确定性基础策略，不是语义级 LLM 总结。 | `StructuredSummaryCompactPolicy` 只基于 snapshot 和 projection 构造 summary。 | docs-site 明确限制，避免过度承诺。 | fixed |

## 残余问题

| Residual | Owner | Status | Follow-up |
| --- | --- | --- | --- |
| 模型驱动 semantic compact 尚未实现。 | future owner | accepted | 可作为 plugin lifecycle / custom CompactPolicy 示例后续实现。 |
| token 级预算尚未实现。 | future owner | accepted | 后续可增加 tokenizer-backed `ContextBudgetEstimator`。 |
| event log/artifact 到 structured compact fields 的提炼尚未实现。 | future owner | accepted | Coding Agent / Runner 阶段结合 artifact 和 sandbox state 设计。 |
