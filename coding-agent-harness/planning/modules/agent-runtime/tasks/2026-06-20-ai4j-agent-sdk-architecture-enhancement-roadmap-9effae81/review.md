# AI4J Agent SDK architecture enhancement roadmap - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | 架构规划、模块边界、后续任务队列、Harness 材料完整性 |

## 审查范围

- 审查类型：architecture / regression / planning
- 范围内：`ai4j-agent` 后续增强方向、插件生态、sandbox/remote runner、coding CLI/TUI、docs-site 后续要求、task package 材料。
- 范围外：Java 代码实现、真实 provider 调用、真实 sandbox provider、CLI/TUI 手动运行。
- 来源材料：`AGENTS.md`、`AGENT.md`、`docs/11-REFERENCE/*`、`docs-site/docs/agent/sdk-roadmap.md`、`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`、本任务 `references/agent-sdk-architecture-enhancement-plan.md`。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | coordinator |
| Task Key | 2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | Planning package prepared; `git diff --check` passed; harness status failures=0 and task materials ready, with dirty-state warning before commit. |
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

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - P2-B 仍在独立 worktree 中等待材料修复、PR/CI/merge；本规划必须以“先收口已有任务”为前置。
  - One-command install 的最终方案尚未调研 native binary、jbang、npm wrapper、zip script 的维护成本。
  - TUI 是否开放 render plugin 仍需等 JLine 交互稳定后再判断。
- Fix loop count：1
- 当前结论：规划可以作为后续任务队列，但不能替代任何实现任务的单独设计、测试和 review。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

## 非阻塞备注（Non-Material Notes）

- 当前规划明确避免新增核心 Maven 拆分，符合用户维护成本偏好。
- Sandbox/remote runner 规划必须在后续任务中继续保持“抽象先行、真实 provider opt-in”。
- docs-site 后续必须执行真实 API 对齐，不应继续使用未实现的 fluent API 示例。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-architecture-enhancement-plan.md | 完整规划覆盖模块边界、插件生态、sandbox/runner、CLI/TUI、docs-site 和任务队列。 |
| E-002 | diff | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 | Task package 已替换模板占位内容。 |
| E-003 | command | TARGET:. | `git diff --check` passed. |
| E-004 | command | TARGET:. | `npx --yes coding-agent-harness status --json .` reported failures=0; current task materialsReady=true and lessonCandidateDecisionComplete=true; dirty warning expected before commit. |
| E-005 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-complete-enhancement-task-plan-2026-06-20.md | 完整任务规划覆盖产品定位、模块边界、Session/Memory/Compact、YAML Blueprint、插件生态、Sandbox/Remote Runner、CLI/TUI、docs-site 和实施队列。 |
| E-006 | report | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-cloud-runner-cli-product-plan-2026-06-21.md | 补充规划覆盖云端 Agent Runner、Sandbox 运行形态、插件生态、Coding Agent CLI/TUI、安装分发和 docs-site 产品化任务队列。 |

## 无重要发现声明

本轮已检查上述规划证据，未发现阻塞“规划落盘”目标的重要发现。剩余风险均属于后续实现任务的范围控制和选型验证问题。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| P2-B 尚未合并，路线图第一步必须先完成它 | coordinator | yes | 修复 `.wt/p2b` brief 材料、PR、CI、merge |
| CLI one-command install 方案未定 | coordinator | yes | 后续 `ai4j-cli` packaging task |
| 真实 sandbox provider 不在本规划验证 | coordinator | yes | 后续 sandbox provider/plugin task |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- |
| Review | yes | 规划材料准备后提交给用户确认。 | 人工确认或退回。 |
| Missing Materials | no | task package 必需材料已补齐；最终以 harness status 为准。 | n/a |
| Blocked | no | 当前无 open blocking finding。 | n/a |
| Lessons | no | 本任务无 lesson candidate。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认和 closeout。 | 人工确认后 closeout。 |
| Soft-deleted / Superseded | no | 当前任务有效。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：对应 `progress.md` 的“规划落盘”记录
- 发现记录：已更新 `findings.md`
- Regression SSoT：无；规划本身不新增固定 regression gate
- Lessons：checked-none: planning-record-only
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

当前信心来自仓库标准、现有 `AGENT.md` 模块说明、agent roadmap、module plan 和本 task-local 规划材料。正式实现前仍应按每个切片开启独立 task、worktree、targeted regression 和 review。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606200221 |
| Submitted At | 2026-06-20 02:21 |
| Submitted By | agent |
| Task Key | MODULES/agent-runtime/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 |
| Materials Checklist Hash | 3d317d608349d56d |
| Evidence Summary | AI4J Agent SDK architecture enhancement roadmap ready for review: module boundaries, plugin ecosystem, sandbox/runner, CLI/TUI, docs-site API alignment, and next task queue recorded. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81 |
