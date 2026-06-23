# P4 CLI sandbox commands and status UX - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | P4 CLI `/sandbox` implementation, parser/resolver, runtime rebind, tests, governance evidence |
| Pascal | subagent | Read-only review request queued against current working tree; result incorporated if returned before final commit |

## 审查范围

- 审查类型：adversarial / security / regression / architecture
- 范围内：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`、`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/*.java`、`SlashCommandController` completion、factory overload、new CLI tests、RG-004/SRB-059 evidence。
- 范围外：Daytona provider HTTP implementation、file tools / apply_patch / MCP/browser / background process sandbox 化、docs-site 内容变更。
- 来源材料：task plan、working-tree diff、targeted Maven output、broad CLI Maven output、env-presence-only live skip evidence。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | coordinator |
| Task Key | MODULES/cli-host/2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | Targeted 61 tests passed; broad `ai4j-cli -am` passed with CLI 298 tests; RG-004 and SRB-059 updated; env presence check recorded live skip without secrets |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` uses `no-candidate-accepted` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 未在当前 shell 重新运行 Daytona live smoke，因为 env 中没有 `DAYTONA_API_KEY`；本轮只声称 CLI host binding 本地回归通过，不重新声称 provider live 可用。
  - 本任务未覆盖 file tools、MCP/browser、apply_patch、后台 process lifecycle 的远端化，这是明确范围边界。
- Fix loop count：2（实现 -> targeted/broad tests -> self-review/governance closeout）
- 当前结论：可以进入审查/提交；本地 RG-004 证据充分，live provider 仅保持 opt-in residual。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- `/sandbox attach daytona <id-or-name>` 的 id/name 自由文本不做 completion，这是刻意避免枚举/泄露远端 sandbox 列表；用户手动输入 id 或 name。
- Review 发现同一远端 sandbox reattach 可能被旧 handle close 误删的边界，已通过 `sameSandboxSession(...)` 和新增 regression 修复。
- `safeMessage(ex)` 仍可能包含 provider 返回的普通错误消息；本轮 parser/resolver 不把 API key 放进异常文本，后续 provider 层也应继续避免把 Authorization/header 放进 exception message。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | `/sandbox` dispatch, output, enable/disable runtime rebind, rollback, close-on-exit, TUI palette/help/status added |
| E-002 | diff | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox | parser/binding/resolver added; resolver supports Daytona only and reads credentials from env/config |
| E-003 | diff | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/factory | optional `SandboxSession` overload added without breaking existing factory implementations |
| E-004 | command | TARGET:ai4j-cli | targeted Maven command passed with 61 tests |
| E-005 | command | TARGET:ai4j-cli | broad `mvn -pl ai4j-cli -am -DskipTests=false test` passed with CLI 298 tests |
| E-006 | command | TARGET:. | env presence check reported missing Daytona credentials without printing values |
| E-007 | report | TARGET:docs/05-TEST-QA/Regression-SSoT.md | RG-004 updated to P4 pass |
| E-008 | report | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | SRB-059 added |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 当前 shell 无 Daytona env credential，未重复 live smoke | coordinator | yes | 保留 LV-004 既有 live pass；后续 release 或 provider task 可在已配置 env 的机器上重跑 |
| 只有 shell `exec` 进入 sandbox，file/MCP/browser/process lifecycle 仍不远端化 | product/coordinator | yes | 后续“remote agent runner / cloud sandbox productization”任务单独设计 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料齐全，self-review 无 P0/P1/P2 blocking finding，等待需要时的人工确认。 | 人工确认或退回。 |
| Missing Materials | no | brief/task_plan/progress/review/lesson/walkthrough/evidence 已补齐。 | 不适用。 |
| Blocked | no | 无 open blocking finding；live rerun 缺凭证是 opt-in residual，不阻断本地 baseline。 | 不适用。 |
| Lessons | no | 本任务无新的可复用治理 lesson，`lesson_candidates.md` 已标记 no-candidate-accepted。 | 不适用。 |
| Confirmed / Finalized | no | 尚未在本任务中记录人工确认。 | 人工确认后执行最终 lifecycle closeout。 |
| Soft-deleted / Superseded | no | 任务仍是当前 P4 交付。 | 不适用。 |

## 后续路由（Follow-Up Routing）

- 任务计划：无需更新，当前 `task_plan.md` 覆盖范围边界。
- Progress：见 `progress.md` 的 2026-06-22 实现、测试、live skip 和治理记录。
- 发现记录：`findings.md` 已补 F-006/F-007。
- Regression SSoT：调整 RG-004。
- Lessons：checked-none: 本任务复用既有 sandbox/CLI 边界原则，没有新增跨任务治理 lesson。
- 收口记录：`walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

最终信心来自 targeted parser/completion/runtime tests、broad CLI `-am` 回归、显式 no-secret slash 参数边界、enable/disable rollback 代码审查、以及 RG-004/SRB-059 治理记录。发布前最终审查不只依赖 self-only；已向现有 review 子代理提交只读审查请求，若其返回 P0/P1/P2 发现则必须先修复再完成。


## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606220049 |
| Submitted At | 2026-06-22 00:49 |
| Submitted By | agent |
| Task Key | MODULES/cli-host/2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 |
| Materials Checklist Hash | 9e7e51c6p4cli000 |
| Evidence Summary | P4 CLI sandbox UX ready for review: /sandbox status/enable/attach/disable implemented, targeted 61 tests passed, broad ai4j-cli -am passed with CLI 298 tests, RG-004/SRB-059 updated, live Daytona rerun skipped because env credentials are absent. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/modules/cli-host/tasks/2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 |
