# CubeSandbox live install and coding sandbox routing - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | checked-none |
| Promotion state | not-promoted |
| Closeout token | checked-none |
| Source task | 2026-06-21-cubesandbox-live-install-and-coding-sandbox-rout-fd63343a |
| Owner | coordinator |
| Last updated | 2026-06-21 |

## Schema

允许的任务级状态：`missing`、`pending-review`、`no-candidate-accepted`、`needs-promotion`、`promoted`、`rejected`。
允许的行级状态：`ready-for-review`、`needs-promotion`、`promoted`、`rejected`。

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本轮没有新增可复用治理 lesson。关键规则（不伪造 live pass、缺 env 记 pending-env、docs 和 Regression/Cadence 同步、metadata-only 不本地回退）已经存在于 AGENTS.md、Regression SSoT、testing standard 和既有 harness 约定中；本任务只是按这些规则执行。

## Promotion Notes

无。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | no | 已判定 checked-none，无需 promotion。 |
| Missing Materials | no | 文件存在且 no-candidate reason 已填写。 |
| Confirmed / Finalized | yes after review confirmation | closeout 完成后进入 finalized。 |
