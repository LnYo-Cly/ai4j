# P4 CLI sandbox commands - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | P4 planning, implementation diff, CLI runtime boundary, docs and regression evidence |

## 审查范围

- 审查类型：architecture / regression / release-readiness
- 范围内：`ai4j-cli` `/sandbox` 命令族、runtime rebind、completion/palette/help/status、docs-site 与 regression 证据。
- 范围外：真实 sandbox provider、远端 runner、云端凭据、外部容器/VM 后端。
- 来源材料：`task_plan.md`、`execution_strategy.md`、`references/cli-sandbox-command-plan.md`、后续 implementation diff、Maven/docs/Harness evidence。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending |
| Submitted At | pending |
| Submitted By | pending |
| Task Key | MODULES/cli-host/2026-06-20-p4-cli-sandbox-commands-72f40aa0 |
| Materials Checklist Hash | pending |
| Evidence Summary | pending implementation and verification |
| Open Findings Count | pending |
| Scanner Version | pending |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | partial | `progress.md`; implementation evidence pending |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | pending | `lesson_candidates.md` still pending closeout decision |
| Walkthrough or closeout link | yes | pending | `walkthrough.md` will be updated at closeout |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no，计划阶段信心足够，但实现和验证尚未完成。
- 如果不是 100%，剩余漏洞或证据缺口：
  - attach 后无真实 provider bridge 的运行时行为需要实现阶段用测试钉住，不能只靠文档声明。
  - factory overload 和 runtime rebind 需要证明不破坏现有 provider/model/stream/mcp 切换。
- Fix loop count：0，implementation 尚未开始。
- 当前结论：可以进入 EXEC-01；不能提交 review 或 closeout。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- P4 计划刻意不实现真实 provider/runner；这是范围控制，不是遗漏。
- docs-site 更新必须避免不存在 API 示例。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | report | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-p4-cli-sandbox-commands-72f40aa0/references/cli-sandbox-command-plan.md | 已记录 P4 命令合同、实现接缝、测试矩阵和 out-of-scope。 |
| E-002 | code | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingAgentBuilder.java | 已确认 P3 `.sandbox(SandboxSession)` 接入点存在。 |
| E-003 | code | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | 已确认当前 SPI 没有通用 attach/resume，P4 不应 overclaim。 |

## 无重要发现声明

计划阶段未发现阻塞进入实现的重要发现；最终无重要发现声明必须在实现 diff 和回归证据完成后重写。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| attach 无真实 provider bridge 的行为需要用测试和文档约束 | coordinator | no | EXEC-01 实现时关闭 |
| CLI runtime rebind 可能影响 stream/mcp/provider 状态 | coordinator | no | targeted + broad CLI tests |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | no | 还没有提交 agent review，implementation/verification 未完成。 | 执行 `task-review`。 |
| Missing Materials | no | 计划材料已补齐；实现证据待后续阶段，不属于当前 planning 缺材料。 | n/a |
| Blocked | no | 当前无 blocker。 | n/a |
| Lessons | yes | `lesson_candidates.md` 仍待 closeout 前决定。 | 人工/agent 在 closeout 阶段判定。 |
| Confirmed / Finalized | no | 未完成 review-confirm / closeout。 | 后续 PR/CI/review/closeout。 |
| Soft-deleted / Superseded | no | 任务仍 active。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`。
- Progress：见 `progress.md` 2026-06-20 12:02 planning entry。
- 发现记录：已更新 `findings.md`。
- Regression SSoT：实现阶段根据 RG-004/RG-008 证据决定是否更新。
- Lessons：pending，closeout 时写 `checked-none:<reason>` 或候选。
- 收口记录：实现验证后更新 `walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

pending implementation, tests, docs build, Harness status, review, PR/CI evidence.
