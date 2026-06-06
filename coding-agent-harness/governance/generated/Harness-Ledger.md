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
| HL-2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b | task | none | docs-site 文档重构总任务 | review | none | coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/task_plan.md | coding-agent-harness/planning/tasks/2026-06-05-docs-site-enterprise-documentation-program-1fdb4d8b/review.md | pending | pending | docs-site 文档重构首批提交待审：canonical map、生产接入辅助页、主入口总览重写、sidebar/include 更新；docs-site npm run build 通过。 | 2026-06-05 |
| HL-2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a | task | none | ai4j sdk project skill for agent-assisted development | review | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a/task_plan.md | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-project-skill-for-agent-assisted-develo-6ba2130a/review.md | pending | pending | AI4J SDK project skill is ready for review: distributable skill folder, OpenAI UI metadata, repo map, development workflow, validation command passed, and implementation commit 3b8af61 created. | 2026-06-05 |
| HL-2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80 | task | none | ai4j sdk skill ab evaluation and docs install command | review | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80/task_plan.md | coding-agent-harness/planning/tasks/2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80/review.md | pending | pending | AI4J SDK skill A/B evaluation and docs-site README install command are ready for review: offline rubric shows 7/30 vs 28/30, install command added, docs-site build passed, and skill validation passed. | 2026-06-05 |
| HL-2026-06-05-ai4j-app-builder-user-skill-c784073b | task | none | ai4j app builder user skill | review | none | coding-agent-harness/planning/tasks/2026-06-05-ai4j-app-builder-user-skill-c784073b/task_plan.md | coding-agent-harness/planning/tasks/2026-06-05-ai4j-app-builder-user-skill-c784073b/review.md | pending | pending | Ready for human review: ai4j-app-builder Skill added, docs-site README updated, skill validation and docs-site build passed. | 2026-06-05 |
| HL-2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac | task | none | remove ai4j sdk maintainer skill | closed | none | coding-agent-harness/planning/tasks/2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac/task_plan.md | pending | pending | pending | Human review confirmed; closing out skill surface cleanup and Plain Java onboarding fix. | 2026-06-06 |
| HL-2026-06-06-5-c6e2fa16 | task | none | 5 分钟首聊主路径文档 | closed | none | coding-agent-harness/planning/tasks/2026-06-06-5-c6e2fa16/task_plan.md | pending | pending | pending | Human review confirmed; closing out 5-minute first chat docs path. | 2026-06-06 |
| HL-2026-06-06-item-885d365a | task | none | 首聊可复制代码合同 | active | none | coding-agent-harness/planning/tasks/2026-06-06-item-885d365a/task_plan.md | pending | pending | pending | 开始为首聊 public docs 和 ai4j-app-builder 示例建立可复制代码回归合同。 | 2026-06-06 |

## Field Rules

- `Scope`: `task` for root planning tasks, `module` for module-local tasks.
- `Module`: module key, or `none`.
- `Queues`: scanner-derived lifecycle queues; query with `harness task-list --queue`.
- `Review`, `Lessons Check`, `Closeout`, and `Residual`: scanner-derived summaries and routes. Detailed evidence stays in task-local files.
- `Updated`: generation date, not a manual edit timestamp.

## Legacy Tables

`Feature-SSoT.md` and `Private-Feature-SSoT.md` are legacy task lifecycle projections. Current Harness versions archive them during `harness governance rebuild --archive --apply` and do not regenerate them.
