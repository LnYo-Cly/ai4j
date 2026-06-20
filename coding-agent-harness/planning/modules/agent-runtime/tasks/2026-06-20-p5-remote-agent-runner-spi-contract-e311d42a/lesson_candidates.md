# P5 Remote Agent Runner SPI contract - Lesson Candidates

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | accepted-no-candidate |
| Promotion state | not-promoted |
| Closeout token | checked-none:runner-spi-contract-task-local |
| Source task | MODULES/agent-runtime/2026-06-20-p5-remote-agent-runner-spi-contract-e311d42a |
| Owner | coordinator |
| Last updated | 2026-06-20 |

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务新增 provider-neutral Remote Agent Runner SPI contract、fake runner tests 和 docs-site 页面，不改变 repo-wide Harness 标准。`Runner contract-first，不接真实云 provider` 已记录在 task-local plan、docs-site 和 regression evidence 中；是否提升为共享工程标准，需要等后续 provider contribution / CLI UX / product guide 任务验证后再判断。

## Promotion Notes

- 当前无 lesson 候选需要 promotion。
- 如果后续 Runner provider、CLI/TUI、docs-site product guide 都沿用同一 contract-first 规则，再另开 lesson sedimentation 或 engineering-standard 更新任务。
