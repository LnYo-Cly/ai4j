# AI4J Extension Recipe and Plugin Composition UX - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none: docs-site recipe documentation only |
| Source task | 2026-06-10-ai4j-extension-recipe-and-plugin-composition-ux-5d2320fc |
| Owner | coordinator |
| Last updated | 2026-06-10 |

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

本任务只把既有插件 API、CLI、Spring 配置和 docs-site 内容整理成使用者 recipe，没有形成新的跨任务治理规则或可复用工程教训；不需要 promoted lesson。

## Promotion Notes

- 无 promotion。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不进入；无候选。 | 不适用 |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认且无 lesson 后续事项。 | 记录 closeout。 |
