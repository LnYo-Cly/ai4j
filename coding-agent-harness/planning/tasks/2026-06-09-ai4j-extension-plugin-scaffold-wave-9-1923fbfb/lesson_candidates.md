# AI4J extension plugin scaffold wave 9 - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | agent-recommends-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none |
| Source task | 2026-06-09-ai4j-extension-plugin-scaffold-wave-9-1923fbfb |
| Owner | coordinator |
| Last updated | 2026-06-09 |

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

本轮是产品功能实现和文档补强，没有形成新的可复用 harness 流程规则。已知残余是版本常量同步问题，更适合作为后续工程优化，不需要提升为全局 lesson。

## Promotion Notes

- 无候选需要 dry-run promotion。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不进入；本轮无候选。 | n/a |
| Missing Materials | 不进入；候选文件存在且有 no-candidate reason。 | n/a |
| Confirmed / Finalized | 人工确认后进入 closeout / finalized。 | review confirmation + closeout |
