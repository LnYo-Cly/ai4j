# Extension plugin contract hardening - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | agent-no-candidate; human may override |
| Promotion state | not-promoted |
| Closeout token | no-candidate |
| Source task | 2026-06-10-extension-plugin-contract-hardening-272a10c4 |
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

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务没有新增可复用的 harness 流程规则；它按现有任务生命周期、Regression SSoT/Cadence、review packet 和 walkthrough 协议执行。新增内容属于 AI4J extension plugin 产品契约、测试覆盖和 docs-site 文档，不需要提升为全局 harness lesson。人工审查仍可覆盖此判断并创建后续沉淀任务。

## Promotion Notes

- 默认 promotion 行为是先 dry-run 或创建后续沉淀任务。
- 不要在普通 closeout 中直接写共享 Lessons 表。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 本任务当前没有 ready-for-review 或 needs-promotion 候选。 | 人工审查覆盖 no-candidate 判断时重新路由。 |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认，但候选仍有延后的治理事项。 | 记录后续任务或 dry-run 决策。 |
