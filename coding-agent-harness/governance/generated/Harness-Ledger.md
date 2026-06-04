# Harness Ledger

## Purpose

Generated canonical task lifecycle index. Humans should use the Dashboard for current status; agents should use `task-list`, `task-index`, or this generated ledger for low-cost lookup.

This file is not a hand-written work log. Do not edit lifecycle rows manually. Update task-local facts (`task_plan.md`, `progress.md`, `review.md`, `lesson_candidates.md`, closeout / walkthrough evidence), then run `harness governance rebuild --archive --apply`.

Repo Governance / CI-CD changes remain routed through their reference standards and task evidence. Regression gates, delivery sequencing, cadence rules, closeout contracts, and module ownership remain in their dedicated governance files until explicitly replaced by equivalent scanner-supported facts.

## Active Ledger

| ID | Scope | Module | Task | State | Queues | Plan | Review | Lessons Check | Closeout | Residual | Updated |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| HL-YYYY-MM-DD-001 | task | none | Short operational title | planned | none | {{paths.harnessRoot}}/planning/tasks/.../task_plan.md | pending | pending | pending | none | YYYY-MM-DD |
| HL-2026-06-04-first-wave-project-upgrades-93da333c | task | none | first wave project upgrades | review | none | coding-agent-harness/planning/tasks/2026-06-04-first-wave-project-upgrades-93da333c/task_plan.md | coding-agent-harness/planning/tasks/2026-06-04-first-wave-project-upgrades-93da333c/review.md | pending | pending | 已完成第一波低风险升级切片：移除 release POM 中本机 GPG 绝对路径，改用可覆盖的 gpg.executable；补充 output/ 忽略规则；验证 mvn -DskipTests package 通过，harness status 无失败无警告。剩余 module-parallel 与 regression baseline/live split 作为后续切片。 | 2026-06-04 |

## Field Rules

- `Scope`: `task` for root planning tasks, `module` for module-local tasks.
- `Module`: module key, or `none`.
- `Queues`: scanner-derived lifecycle queues; query with `harness task-list --queue`.
- `Review`, `Lessons Check`, `Closeout`, and `Residual`: scanner-derived summaries and routes. Detailed evidence stays in task-local files.
- `Updated`: generation date, not a manual edit timestamp.

## Legacy Tables

`Feature-SSoT.md` and `Private-Feature-SSoT.md` are legacy task lifecycle projections. Current Harness versions archive them during `harness governance rebuild --archive --apply` and do not regenerate them.
