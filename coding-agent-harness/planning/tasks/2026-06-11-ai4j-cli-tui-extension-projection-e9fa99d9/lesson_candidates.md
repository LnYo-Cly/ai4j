# AI4J CLI TUI extension projection - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | no-candidate-accepted |
| Promotion state | not-promoted |
| Closeout token | checked-none |
| Source task | 2026-06-11-ai4j-cli-tui-extension-projection-e9fa99d9 |
| Owner | coordinator |
| Last updated | 2026-06-11 |

## Schema

允许的任务级状态：

- `missing`：候选文件不存在。
- `pending-review`：候选文件存在，但人工判定还没完成。
- `no-candidate-accepted`：人工接受本任务没有可复用候选的理由。
- `needs-promotion`：至少一个候选已排队等待治理沉淀。
- `promoted`：所有接受的候选都已写入已确认的治理目标。
- `rejected`：所有候选都已带理由拒绝或归档。

允许的行级状态：

- `ready-for-review`：agent 认为这个候选可能有复用价值。
- `needs-promotion`：人工标记这个候选值得通过 dry-run promotion 或后续沉淀任务保留。
- `promoted`：维护 CLI 或已批准的后续任务已把候选写入确认的治理目标。
- `rejected`：人工带理由拒绝这个候选。

聚合规则：

- 任意 `ready-for-review` 行会让任务级状态保持 `pending-review`。
- 任意 `needs-promotion` 行会让任务级状态变成 `needs-promotion`，除非仍有 `ready-for-review` 行。
- 全部行都是 `promoted` 时，任务级状态为 `promoted`。
- 全部行都是 `rejected` 时，任务级状态为 `rejected`。
- 没有候选的任务必须使用 `no-candidate-accepted`，并填写 `No-Candidate Reason`。

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本次变更只是在 ai4j-cli 里把已有 extension CLI 做成 TUI 薄投影，没有形成足够稳定、足够独立、值得单独沉淀的通用 lesson。后续如果再做更多宿主投影，再考虑沉淀“CLI 命令复用 + TUI 捕获层”的通用模式。

## Promotion Notes

- 如果人工审查认为候选值得沉淀，把对应行标记为 `needs-promotion`，并记录目标治理位置。
- 候选标记为 `needs-promotion` 时，必须趁源任务上下文还新鲜写出完整 task-local detail artifact，并在 `Detail Artifact` 中链接。
- `Scope` 使用 `task`、`module` 或 `global`；module 级候选必须填写 `Module Key`。
- 如果人工审查拒绝候选，把对应行标记为 `rejected`，并在 review decision 中保留理由。
- `needs-promotion` 不阻止任务 closeout，但必须继续出现在维护队列和收口记录里。
- 默认 promotion 行为是先 dry-run 或创建后续沉淀任务。不要写共享 Lessons 表；被接受的候选应成为 promoted lesson 详情文档。
- 沉淀任务必须先分类 scope、检查既有 lessons 和 standards 冲突、提出目标改动，并在 apply 前报告验证证据。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 无候选。 | n/a |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认，但候选仍有延后的治理事项。 | 记录后续任务或 dry-run 决策。 |
