# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | optional | read-only | harness task policy | 2026-06-23 | current task review | n/a | allowed within this task |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 单类镜像 ChatModelClient。 |
| 工作树 | `.worktrees/feature/anthropic-native-surface` | 与 P1 同分支（紧耦合）。 |
| thinking 落点 | `AgentModelResult.reasoningText` / `onReasoningDelta` | 复用 agent 既有槽，零新增字段。 |
| 工具循环 | 不在此 client（agent runtime 自驱） | 与 ChatModelClient 一致（passThroughToolCalls 由上层）。 |

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |
