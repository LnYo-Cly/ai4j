# Agent Blueprint schema export and docs hardening - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:task-specific-blueprint-schema |
| Source task | 2026-06-20-agent-blueprint-schema-export-and-docs-hardening-4741edc1 |
| Owner | coordinator |
| Last updated | 2026-06-20 |

## Schema

本任务没有新增可跨任务复用的 Harness 经验；主要是 Agent Blueprint 产品能力切片。

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务的经验属于当前 Blueprint/schema 功能自身，不需要沉淀为通用 Harness lesson；后续如果反复出现“authoring schema 与 runtime validator 漂移”的问题，再单独创建 module-level lesson。

## Promotion Notes

无。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不适用，已接受无候选。 | n/a |
| Missing Materials | 文件缺失、状态非法。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认但 closeout 未完成。 | 完成 closeout。 |
