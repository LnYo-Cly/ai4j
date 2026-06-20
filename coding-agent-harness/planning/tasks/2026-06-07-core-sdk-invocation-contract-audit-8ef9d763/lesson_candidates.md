# core sdk invocation contract audit - 教训候选

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none: design-audit-local-decision |
| Source task | 2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 |
| Owner | coordinator |
| Last updated | 2026-06-07 |

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务是一次 task-local 设计审计，结论已经写入 `design.md` 和 `findings.md`。是否把“不要新增隐藏式 Chat facade”沉淀为全局工程标准，需要后续人工确认后单独执行，不在本任务内直接 promotion。

## Promotion Notes

- 若人工审查认为该结论应成为长期标准，可创建后续 lessons / reference 更新任务。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不进入；本任务 no-candidate accepted。 | n/a |
| Missing Materials | 文件缺失或状态非法。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认但仍有后续治理事项。 | 记录后续任务或 dry-run 决策。 |
