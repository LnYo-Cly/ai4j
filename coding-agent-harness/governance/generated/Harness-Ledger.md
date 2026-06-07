# Harness Ledger

## Purpose

Generated canonical task lifecycle index. Humans should use the Dashboard for current status; agents should use `task-list`, `task-index`, or this generated ledger for low-cost lookup.

This file is not a hand-written work log. Do not edit lifecycle rows manually. Update task-local facts (`task_plan.md`, `progress.md`, `review.md`, `lesson_candidates.md`, closeout / walkthrough evidence), then run `harness governance rebuild --archive --apply`.

Repo Governance / CI-CD changes remain routed through their reference standards and task evidence. Regression gates, delivery sequencing, cadence rules, closeout contracts, and module ownership remain in their dedicated governance files until explicitly replaced by equivalent scanner-supported facts.

## Active Ledger

| ID | Scope | Module | Task | State | Queues | Plan | Review | Lessons Check | Closeout | Residual | Updated |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| HL-YYYY-MM-DD-001 | task | none | Short operational title | planned | none | {{paths.harnessRoot}}/planning/tasks/.../task_plan.md | pending | pending | pending | none | YYYY-MM-DD |
| HL-2026-06-04-first-wave-project-upgrades-93da333c | task | none | first wave project upgrades | closed | none | coding-agent-harness/planning/tasks/2026-06-04-first-wave-project-upgrades-93da333c/task_plan.md | pending | pending | pending | First wave project upgrades finalized after human review confirmation HRC-202606040850. Low-risk release GPG path cleanup and output Git boundary are complete; follow-up upgrades remain module-parallel harness and regression baseline/live split. | 2026-06-04 |
| HL-2026-06-04-module-parallel-harness-upgrade-d6ab88ce | task | none | module parallel harness upgrade | closed | none | coding-agent-harness/planning/tasks/2026-06-04-module-parallel-harness-upgrade-d6ab88ce/task_plan.md | pending | pending | pending | Module-parallel harness upgrade finalized after human review confirmation HRC-202606040911. Capability configured, 10 module surfaces registered, module contracts customized, and status/module-list verification passed. | 2026-06-04 |
| HL-2026-06-04-regression-baseline-live-split-b2f834db | task | none | regression baseline live split | closed | none | coding-agent-harness/planning/tasks/2026-06-04-regression-baseline-live-split-b2f834db/task_plan.md | pending | pending | pending | Regression baseline/live split finalized after human review confirmation HRC-202606041042. Regression SSoT and Cadence now separate local-required baseline from live-provider and credential-release opt-in gates; residuals route live profile/provider hygiene and FlowGram webapp CI follow-up. | 2026-06-04 |
| HL-2026-06-04-live-provider-test-hygiene-c392a468 | task | none | live provider test hygiene | closed | none | coding-agent-harness/planning/tasks/2026-06-04-live-provider-test-hygiene-c392a468/task_plan.md | pending | pending | pending | Live provider test hygiene closed after Dashboard human review confirmation; evidence, walkthrough, regression routing, and residual R-008 are recorded. | 2026-06-04 |
| HL-2026-06-04-docs-site-information-architecture-redesign-6c91ba27 | task | none | docs site information architecture redesign | closed | none | coding-agent-harness/planning/tasks/2026-06-04-docs-site-information-architecture-redesign-6c91ba27/task_plan.md | pending | pending | pending | docs-site IA redesign design task closed after human confirmation; Wave 1 implementation is authorized as a separate task. | 2026-06-04 |
| HL-2026-06-04-docs-site-wave-1-entrance-redesign-54198b78 | task | none | docs site wave 1 entrance redesign | closed | none | coding-agent-harness/planning/tasks/2026-06-04-docs-site-wave-1-entrance-redesign-54198b78/task_plan.md | pending | pending | pending | docs-site Wave 1 entrance redesign closed after human review confirmation; remaining deep-page enterprise docs work moves to docs-site enterprise program. | 2026-06-05 |
| HL-2026-06-05-docs-site-modular-positioning-pass-c8547bc0 | task | none | docs site modular positioning pass | closed | none | coding-agent-harness/planning/tasks/2026-06-05-docs-site-modular-positioning-pass-c8547bc0/task_plan.md | pending | pending | pending | docs-site modular positioning pass closed after human review confirmation; modular building-block positioning is now part of the docs-site entrance. | 2026-06-05 |
| HL-2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b | task | none | docs-site 文档重构总任务 | closed | none | coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a | task | none | ai4j sdk project skill for agent-assisted development | closed | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80 | task | none | ai4j sdk skill ab evaluation and docs install command | closed | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-05-ai4j-app-builder-user-skill-c784073b | task | none | ai4j app builder user skill | closed | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-app-builder-user-skill-c784073b/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac | task | none | remove ai4j sdk maintainer skill | closed | none | coding-agent-harness/planning/tasks/2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac/task_plan.md | pending | pending | pending | Human review confirmed; closing out skill surface cleanup and Plain Java onboarding fix. | 2026-06-06 |
| HL-2026-06-06-5-c6e2fa16 | task | none | 5 分钟首聊主路径文档 | closed | none | coding-agent-harness/planning/tasks/2026-06-06-5-c6e2fa16/task_plan.md | pending | pending | pending | Human review confirmed; closing out 5-minute first chat docs path. | 2026-06-06 |
| HL-2026-06-06-item-885d365a | task | none | 首聊可复制代码合同 | closed | none | coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-07-chatclient-d5f84742 | task | none | 轻量 ChatClient 首聊门面 | closed | none | coding-agent-harness/planning/tasks/2026-06-07-chatclient-d5f84742/task_plan.md | pending | pending | pending | 用户已确认审查通过，任务关闭。 | 2026-06-07 |
| HL-2026-06-07-feature-ssot-closeout-drift-cleanup-a8e25ef6 | task | none | Feature SSoT closeout drift cleanup | review | none | coding-agent-harness/planning/tasks/2026-06-07-feature-ssot-closeout-drift-cleanup-a8e25ef6/task_plan.md | coding-agent-harness/planning/tasks/2026-06-07-feature-ssot-closeout-drift-cleanup-a8e25ef6/review.md | pending | pending | Feature SSoT closeout drift fixed; F-022/F-023 moved to completed, F-023 repository walkthrough added, targeted governance checks passed. | 2026-06-07 |
| HL-2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 | task | none | core sdk invocation contract audit | active | none | coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763/task_plan.md | pending | pending | pending | 开始审计 Core SDK 调用主线，不改代码，只产出设计结论 | 2026-06-07 |

## Field Rules

- `Scope`: `task` for root planning tasks, `module` for module-local tasks.
- `Module`: module key, or `none`.
- `Queues`: scanner-derived lifecycle queues; query with `harness task-list --queue`.
- `Review`, `Lessons Check`, `Closeout`, and `Residual`: scanner-derived summaries and routes. Detailed evidence stays in task-local files.
- `Updated`: generation date, not a manual edit timestamp.

## Legacy Tables

`Feature-SSoT.md` and `Private-Feature-SSoT.md` are legacy task lifecycle projections. Current Harness versions archive them during `harness governance rebuild --archive --apply` and do not regenerate them.
