# ai4j dynamic workflow plugin - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:task-local-ci-note |
| Source task | 2026-07-06-ai4j-dynamic-workflow-plugin-d652ef2e |
| Owner | coordinator |
| Last updated | 2026-07-06 |

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

本任务发现的可复用事项是“独立插件仓库在 extension-api 未发布前需要先安装 ai4j parent POM + extension-api”，已经直接写入独立仓库 README 与 GitHub Actions；暂不需要提升为共享 harness lesson。

## Promotion Notes

- 无。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | no | 本轮没有需要提升到共享治理层的 lesson candidate。 | 不适用 |
| Missing Materials | no | 候选文件存在且 no-candidate reason 已填写。 | 不适用 |
| Confirmed / Finalized | no | 尚未进行人工确认。 | 人工确认或任务 closeout 后刷新 generated ledger |
