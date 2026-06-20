# Feature SSoT review queue status alignment - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### Feature SSoT 与 generated ledger 状态漂移

- 背景：Feature SSoT 的 Active Features 表仍将 F-024 到 F-037 标为 `🟡 in_progress`，但这些任务在 generated Harness Ledger 中已经进入 review queue。
- 发现：`coding-agent-harness/governance/generated/Harness-Ledger.md` 中对应任务行显示 `review | review`，Review 字段为 `agent-reviewed`，说明 agent review 已提交但人工确认未完成。
- 影响：Feature SSoT 应表达“等待人工确认”，不能继续表达“仍在实施”，也不能移动到 Completed Features。
- 后续：把这些 rows 改为 `🟣 review`，并在 residual 中保留等待 human review confirmation 的语义。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Active review rows status | `🟣 review` | 与 generated Harness Ledger 的 review queue 状态一致，同时不暗示 human confirmation 已完成。 | 移入 Completed Features；保留 `🟡 in_progress`。 | accepted |
| Feature SSoT authority wording | human-readable planning summary | Harness v2 lifecycle 的机器来源是 generated Harness Ledger，Feature SSoT 应作为人工摘要并在漂移时修复。 | 继续自称 single source of truth。 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否可以由 agent 运行 `review-confirm` | 不可以；只能提交 agent review，等待人工确认。 | human | 人工审查时 |
