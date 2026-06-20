# CubeSandbox sandbox provider adapter - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:task-specific-cubesandbox-provider-adapter |
| Source task | 2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb |
| Owner | coordinator |
| Last updated | 2026-06-21 |

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

本任务形成的是 CubeSandbox adapter 的任务内工程决策：端口兼容、header safety、metadata 收紧、live-provider pending-env 记录，都已写入 `findings.md`、`review.md`、docs-site 和 tests。它们不需要提升为全局 Harness lesson：现有 `docs/11-REFERENCE/testing-standard.md` 已规定 live-provider opt-in 与 secret 边界；AGENTS.md 已规定新增/调整固定回归面要同步 Regression SSoT / Cadence Ledger。本轮无新增跨任务治理规则。

## Promotion Notes

- No promotion requested.
- 后续如果多个 provider adapter 重复出现“secret 不得从 spec 读取 / live test 必须 pending-env 诚实记录 / raw socket header safety”问题，可以单独创建 provider-adapter-standard lesson；本任务不直接写共享 Lessons。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不进入；本任务已接受无可复用候选。 | n/a |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | 当前已满足。 |
| Confirmed / Finalized | 已人工确认但仍有延后的治理事项。 | 当前无 lesson 延后事项。 |
