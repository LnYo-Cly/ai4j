# docs site wave 1 entrance redesign - 教训候选

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
| Source task | 2026-06-04-docs-site-wave-1-entrance-redesign-54198b78 |
| Owner | coordinator |
| Last updated | 2026-06-04 |

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

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务是前置 docs-site 信息架构设计的 Wave 1 执行切片，产出是具体页面文案、功能地图和 sidebar 挂载。没有新增可复用 harness 流程、仓库治理规则、回归策略或跨任务标准；后续若多次出现“文档入口需要成熟度标签”的模式，再通过独立沉淀任务提升为 docs-site 标准。

## Promotion Notes

- 本轮不写共享 lessons 表。
- 本轮不创建 promoted lesson 详情文档。
- 后续 Wave 2/3 如果固化出可复用 docs-site 页面合同，再单独创建 lesson sedimentation 任务。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不进入；无候选已接受。 | n/a |
| Missing Materials | 文件缺失、状态非法，或缺少 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认且 closeout 完成。 | n/a |
