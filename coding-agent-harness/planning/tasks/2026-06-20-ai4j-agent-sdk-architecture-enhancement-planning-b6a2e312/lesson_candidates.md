# AI4J Agent SDK architecture enhancement planning - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | pending-review |
| Review gate | candidate-file-present |
| Review decision | pending-human-review |
| Promotion state | not-promoted |
| Closeout token | pending |
| Source task | 2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312 |
| Owner | coordinator |
| Last updated | 2026-06-20 |

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
| LC-20260620-agent-sdk-planning-scope | ready-for-review | 先收敛主概念再拆实施任务 | module | agent-runtime | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-enhancement-plan.md | 本任务澄清不新增 `AgentHost` / `ai4j-runtime`，而是增强现有 `ai4j-agent`；并把 Sandbox/Runner/Blueprint 拆成后续阶段。 | 后续 agent 容易把 architecture brainstorming 直接膨胀成新模块或一次性大改；该候选可提醒先固定主心智和任务边界。 | pending-human-review | module plan or engineering standard update | pending | maybe update agent-runtime module plan / engineering standard after approval | create follow-up lesson sedimentation task if accepted |

## No-Candidate Reason

不适用：本任务已有一个候选等待人工判定。

## Promotion Notes

- 如果人工审查认为候选值得沉淀，把对应行标记为 `needs-promotion`，并记录目标治理位置。
- 默认 promotion 行为是先 dry-run 或创建后续沉淀任务。不要在本任务中直接写共享 Lessons 表。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | 任意候选是 `ready-for-review` 或 `needs-promotion`。 | 人工拒绝、保留在任务内、创建沉淀任务或批准 promotion。 |
| Missing Materials | 文件缺失、状态非法，或缺少必需的 no-candidate reason。 | Agent 修复候选文件。 |
| Confirmed / Finalized | 已人工确认，但候选仍有延后的治理事项。 | 记录后续任务或 dry-run 决策。 |
