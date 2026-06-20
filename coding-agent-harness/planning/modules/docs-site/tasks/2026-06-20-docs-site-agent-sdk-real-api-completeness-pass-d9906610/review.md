# docs site agent sdk real api completeness pass - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | docs-site Agent SDK real API matrix, links, stale AgentSession reference, verification evidence |

## 审查范围

- 审查类型：adversarial / regression / docs accuracy
- 范围内：`docs-site/docs/agent/real-api-matrix.md`、Agent overview/quickstart/sidebar links、`reference-core-classes.md` 中 `AgentSession` 描述、task package 材料。
- 范围外：Java API 实现、真实 provider/sandbox/runner、全站 IA 重构、缺失 numbered reference 文件修复。
- 来源材料：`AGENTS.md`、`docs/11-REFERENCE/testing-standard.md`、`ai4j-agent/**`、`ai4j-extension-api/**`、`ai4j-cli/SlashCommandController.java`、docs-site diff、typecheck/build 输出。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | coordinator |
| Task Key | 2026-06-20-docs-site-agent-sdk-real-api-completeness-pass-d9906610 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | Added Agent SDK real API matrix, updated Agent doc entry links and stale AgentSession reference; docs-site typecheck/build passed; diff check passed. |
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
  - 新矩阵覆盖的是 Agent SDK 主能力，不是全站 Core SDK / FlowGram / Solutions 能力矩阵。
  - `docs/11-REFERENCE/engineering-standard.md` 与 `execution-workflow-standard.md` 在最新 `origin/dev` 缺失，但这不是本 docs-site 切片范围。
  - GitHub PR CI 尚未运行，需 PR 后继续观察。
- Fix loop count：1
- 当前结论：本轮 docs-site 切片可提交；剩余风险均非本任务发布阻塞项。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |


## 非阻塞备注（Non-Material Notes）

- 新增页面被 `.gitignore` 的 `docs/` 规则忽略，提交必须使用 `git add -f docs-site/docs/agent/real-api-matrix.md`。
- `npm ci` 报 50 个既有 npm audit vulnerabilities；本任务未升级依赖，记录为环境/依赖既有状态，不作为本 PR 阻塞。
- build 第一次因 120s wrapper timeout 未完成，300s timeout 重跑通过。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:docs-site/docs/agent/real-api-matrix.md | 新增 Agent SDK 真实 API 能力矩阵，按能力状态、源码入口、文档入口组织。 |
| E-002 | diff | TARGET:docs-site/docs/agent/reference-core-classes.md | 修正 `AgentSession` 过期描述，覆盖 metadata/event log/snapshot/store/compact/sandbox binding。 |
| E-003 | command | TARGET:docs-site | `npm run typecheck` passed. |
| E-004 | command | TARGET:docs-site | `npm run build` passed with 300s timeout. |
| E-005 | command | TARGET:. | `git diff --check` passed. |
| E-006 | command | TARGET:. | `rg` secret / fake API scan found no provider token material; fake API hits are intentional anti-pattern references. |
| E-007 | command | TARGET:. | `npx --yes coding-agent-harness status --json .` reported failures=0; only dirty warning before commit. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞 docs-site Agent SDK 真实 API 完整性切片的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 全站其他章节仍可能缺少类似能力矩阵 | coordinator | yes | 后续 docs-site completeness pass |
| AGENTS.md 引用的部分 numbered reference 文件在 `origin/dev` 缺失 | coordinator | yes | 后续 harness/reference repair task |
| PR CI 未运行 | coordinator | no | PR 后 watch checks |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料、diff 和本地验证准备后提交给人工确认。 | 人工确认或退回。 |
| Missing Materials | no | task package 必需材料已补齐；最终以 harness status 为准。 | n/a |
| Blocked | no | 当前无 open blocking finding。 | n/a |
| Lessons | no | 本任务不提升全局 lesson。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认和 closeout。 | 人工确认后 closeout。 |
| Soft-deleted / Superseded | no | 当前任务有效。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：对应 `progress.md` 的 worktree/audit/verification 记录
- 发现记录：已更新 `findings.md`
- Regression SSoT：无；本任务未新增固定 regression gate
- Lessons：checked-none: docs-task-local
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

当前信心来自源码对照、diff 审查、typecheck/build、diff check 和 secret/fake API 扫描。PR 后仍需 GitHub checks 作为合并前证据。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606201229 |
| Submitted At | 2026-06-20 12:29 |
| Submitted By | agent |
| Task Key | MODULES/docs-site/2026-06-20-docs-site-agent-sdk-real-api-completeness-pass-d9906610 |
| Materials Checklist Hash | 8cf9216ba3c37560 |
| Evidence Summary | docs-site Agent SDK real API matrix ready: added source-backed capability/status matrix, linked it from Agent docs, fixed AgentSession reference, docs typecheck/build passed. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/modules/docs-site/tasks/2026-06-20-docs-site-agent-sdk-real-api-completeness-pass-d9906610 |
