# ai4j dynamic workflow host runtime - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:runtime-safety-captured-in-tests-docs |
| Source task | 2026-07-06-ai4j-dynamic-workflow-host-runtime-ef15599f |
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

本轮没有需要进入共享治理层的 lesson candidate。关键经验是“host-mediated script runtime 必须默认禁止 arbitrary host interop，并把宿主 bridge 缩成最小 primitive 面”，已经直接落在代码默认值、`DynamicWorkflowNashornExecutorTest` 安全回归和 docs-site runtime 边界说明里；相比新增一条共享 lesson，这些可执行门禁更能防止回归。

## Promotion Notes

- 本任务不发起 promotion。
- 若未来新增 Node/GraalJS/custom executor，应从本任务测试中复制同类安全断言，而不是依赖聊天记忆。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 不适用；没有 open candidate。 | n/a |
| Missing Materials | 不适用；no-candidate reason 已填写。 | n/a |
| Confirmed / Finalized | 若后续人工确认任务，lesson 维持 checked-none。 | n/a |
