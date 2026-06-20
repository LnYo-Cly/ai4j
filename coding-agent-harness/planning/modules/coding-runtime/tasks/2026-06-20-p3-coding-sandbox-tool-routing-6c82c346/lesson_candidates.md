# P3 Coding sandbox tool routing - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:p3-routing-slice-task-local |
| Source task | 2026-06-20-p3-coding-sandbox-tool-routing-6c82c346 |
| Owner | coordinator |
| Last updated | 2026-06-20 |

## Schema

允许的任务级状态：`missing`、`pending-review`、`no-candidate-accepted`、`needs-promotion`、`promoted`、`rejected`。

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务没有新增可复用的全局 Harness lesson。发现均为当前 P3 sandbox routing 切片的任务局部边界，并已记录在 `findings.md`、docs-site 和 Regression SSoT/Cadence Ledger 中。后续 file/patch/browser/git routing 应通过新的实现任务继续沉淀，不在本轮提升为共享 lesson。

## Promotion Notes

- 无待 promotion 候选。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不适用；本任务已判定 no-candidate-accepted。 | 无 |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认且 closeout 完成。 | 记录 closeout。 |
