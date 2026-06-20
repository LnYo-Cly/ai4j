# P3 Coding sandbox tool routing - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | P3 first slice implementation, tests, docs, and regression governance |

## 审查范围

- 审查类型：architecture / security / regression / docs
- 范围内：`ai4j-coding` sandbox routing for `bash action=exec`、session binding、fake sandbox tests、docs-site、Regression SSoT/Cadence Ledger。
- 范围外：真实 sandbox provider、file/patch/browser/git/project-run routing、CLI `/sandbox` UX、live provider/model tests。
- 来源材料：task plan、diff、Maven test output、docs build output、Regression SSoT/Cadence Ledger。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | agent |
| Task Key | MODULES/coding-runtime/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | P3 first slice routes `bash action=exec` through `SandboxSession.execute(...)`, binds non-sensitive sandbox summary to coding sessions, keeps local fallback, updates docs-site and regression governance; targeted/broad coding tests and docs build passed. |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes, for the scoped first slice
- 如果不是 100%，剩余漏洞或证据缺口：无阻塞缺口；后续未实现能力已明确排除并记录为 residual/follow-up。
- Fix loop count：1
- 当前结论：可以提交 review；证据覆盖 direct executor、agent loop、broad coding regression、docs build 和 governance update。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `read_file`、`write_file`、`apply_patch`、browser、git/project-run/test-runner 尚未 sandbox routing；docs-site 已明确列为后续切片。
- `bash start/status/logs/write/stop/list` 仍是本地 `SessionProcessRegistry`，后续需要 provider-side process lifecycle 设计。
- docs build 第一次失败是 worktree 缺 ignored `docs-site/node_modules`，不是文档内容失败；`npm --prefix docs-site install` 后 build passed。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` passed with 14 coding tests |
| E-002 | command | TARGET:. | `mvn -pl ai4j-coding -am -DskipTests=false test` passed with extension API 25, core 103, agent 119, coding 61 tests |
| E-003 | command | TARGET:docs-site | `npm --prefix docs-site run build` passed after restoring ignored local dependencies |
| E-004 | diff | TARGET:ai4j-coding/src/main/java | `CodingSandboxRuntime`, `SandboxShellCommandExecutor`, builder/session wiring, and `ShellCommandResult` execution metadata |
| E-005 | diff | TARGET:docs-site/docs/coding-agent/sandbox-routing.md | New technical docs explain implemented API and unimplemented boundaries |
| E-006 | diff | TARGET:docs/05-TEST-QA | RG-003/RG-008/SRB-057 updated |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 文件/patch/browser/git/project-run 尚未 sandbox routing | coordinator | yes | 后续 P3 切片 |
| 后台 process lifecycle 尚未 provider-side 映射 | coordinator | yes | 后续 bash process routing task |
| CLI `/sandbox` 状态展示未实现 | coordinator | yes | P4 CLI sandbox commands |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料包和本地证据已准备好，提交后等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节、证据和 lesson decision 已补齐。 | 不适用。 |
| Blocked | no | 无 open blocking finding。 | 不适用。 |
| Lessons | no | 本任务 no-candidate-accepted。 | 不适用。 |
| Confirmed / Finalized | no | 尚未人工确认。 | closeout 后完成。 |
| Soft-deleted / Superseded | no | 任务 active。 | 不适用。 |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：见 `progress.md`
- 发现记录：已更新 `findings.md`
- Regression SSoT：已更新 RG-003/RG-008
- Lessons：checked-none:p3-routing-slice-task-local
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自 targeted + broad coding tests、docs-site build、fake sandbox deterministic tests、明确的非目标边界和回归治理记录。PR 合并前仍需 CI 与人工确认。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606200337 |
| Submitted At | 2026-06-20 03:37 |
| Submitted By | agent |
| Task Key | MODULES/coding-runtime/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346 |
| Materials Checklist Hash | 4350cf683ba4357e |
| Evidence Summary | P3 coding sandbox routing first slice ready for review: bash exec routes through SandboxSession, local fallback preserved, docs and regression governance updated, targeted coding regression and docs build evidence recorded. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/modules/coding-runtime/tasks/2026-06-20-p3-coding-sandbox-tool-routing-6c82c346 |
