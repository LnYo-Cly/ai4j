# plugin ecosystem hardening fixes - 教训候选

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
| Source task | 2026-07-05-plugin-ecosystem-hardening-fixes-bcef4a36 |
| Owner | coordinator |
| Last updated | 2026-07-05 |

## Schema

允许的任务级状态：`missing`、`pending-review`、`no-candidate-accepted`、`needs-promotion`、`promoted`、`rejected`。
允许的行级状态：`ready-for-review`、`needs-promotion`、`promoted`、`rejected`。

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本轮修复沿用了既有 ai4j-sdk harness/worktree/docs-site 规则；fresh worktree 缺 `docs-site/node_modules` 需 `npm ci` 的经验已存在于历史记忆/多条 Cadence 记录中，不再新增 lesson candidate。

## Promotion Notes

- checked-none：不创建 promoted lesson，不阻塞 closeout。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不适用，本任务无候选。 | checked-none 已记录。 |
| Missing Materials | 不适用，文件存在且 no-candidate reason 已填写。 | 不适用。 |
| Confirmed / Finalized | closeout 完成后进入。 | 记录 walkthrough 和最终提交。 |
