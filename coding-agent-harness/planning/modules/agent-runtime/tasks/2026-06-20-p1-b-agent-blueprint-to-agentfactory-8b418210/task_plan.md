# P1-B Agent Blueprint to AgentFactory

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-p1-b-agent-blueprint-to-agentfactory-8b418210/artifacts/preset/2026-06-19T22-38-15-254Z
Task Package Index: required

## 目标

[用一句话说明本任务完成后应达到的状态。]

## 范围

- 做什么：[本轮允许修改或交付的内容]
- 不做什么：[明确排除的内容，避免执行中扩大范围]
- 主要风险：[当前已知的技术、产品、协作或验证风险]

## 预算选择

选择预算：complex

选择理由：[为什么本任务适合这个预算]

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc / private-plan / external / code | PUBLIC:path 或 PRIVATE:path 或 TARGET:path 或 EXTERNAL:path 或 URL:https://example.com | [说明这份上下文如何影响任务] | coordinator / reviewer / worker |

## 步骤

1. [步骤 1]
2. [步骤 2]
3. [步骤 3]

## 验收标准

- [ ] [标准 1]
- [ ] [标准 2]
- [ ] [标准 3]

## 工作树（Worktree）

- 路径：[worktree 路径，例如 `.worktrees/feat/xxx`]
- 分支：[分支名]
- Worker owner：[coordinator / subagent id / 不适用]
- Worker handoff commit required：[yes / no / 不适用]
- Coordinator integration branch：[分支名 / 不适用]
- 未使用 worktree 的原因：[说明]

## 长程任务判定

- 是否属于长程任务：[是 / 否]
- 若是，合同文件：`long-running-task-contract.md`
- 连续执行权限：[已授权 / 未授权 / 不适用]
- Stop Condition 摘要：[一句话说明什么时候必须停]

## 审查判定

- 是否需要对抗性审查：[是 / 否]
- 若是，报告文件：`review.md`
- Reviewer：[self / subagent / external / human / 不适用]
- No-finding 要求：[例如 reviewer 无重要发现 / 不适用]

## 关联

- 相关 Regression Gate：[引用]
- 审查报告：[路径 / 不适用]
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：[引用；如无写“无”]

## 模块关联（启用模块并行时填写）

- Module：[module key，例如 reader / graph / 不适用]
- Step：[step ID，例如 RDR-02 / 不适用]
- Module Plan：[link to module_plan.md / 不适用]

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator / 不适用
- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- Closeout / Regression update needed：[路径或 n/a]

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
