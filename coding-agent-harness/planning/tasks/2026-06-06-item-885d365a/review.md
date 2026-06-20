# 首聊可复制代码合同 - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | diff、首聊合同测试、docs/skill 同步、RG-001/RG-005/RG-007/RG-008 证据 |

## 审查范围

- 审查类型：regression
- 范围内：`ai4j` 首聊 smoke test、`ai4j-spring-boot-starter` starter smoke test、docs-site 首聊页面、`ai4j-app-builder` recipe、回归台账。
- 范围外：真实 provider 质量、live API Key、RAG/MCP/Agent/FlowGram 示例扩写、公共 API 重构。
- 来源材料：task plan、diff、Maven 输出、docs-site build/typecheck 输出、Regression SSoT、Cadence Ledger。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending task-review |
| Submitted At | pending task-review |
| Submitted By | coordinator |
| Task Key | 2026-06-06-item-885d365a |
| Materials Checklist Hash | pending task-review |
| Evidence Summary | First-chat copyable code contract tests and docs guards are implemented; RG-001, RG-005, RG-007, and RG-008 passed. |
| Open Findings Count | 0 |
| Scanner Version | pending task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` and `docs/10-WALKTHROUGH/2026-06-06-first-chat-copyable-code-contract.md` |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：
  - 无
- Fix loop count：2
- 当前结论：core targeted test 先后修复 PowerShell 命令写法和 primitive long 断言问题；之后 RG-001/RG-005/RG-007/RG-008 全部通过，可提交人工确认。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- `mvn -Dtest=FirstChatCopyableCodeTest,ConfigurationTest` 在 PowerShell 中需要给 `-Dtest=...` 加引号，否则逗号会被解析成参数列表。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j "-Dtest=FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` passed with 5 tests |
| E-002 | command | TARGET:. | `mvn -pl ai4j -am -DskipTests=false test` passed with 103 tests |
| E-003 | command | TARGET:. | `mvn -pl ai4j-spring-boot-starter -Dtest=AiServiceFirstChatAutoConfigurationTest -DskipTests=false test` passed with 1 test |
| E-004 | command | TARGET:. | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` passed with core 103 tests and starter 3 tests |
| E-005 | command | TARGET:docs-site | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `npm run build` passed |
| E-006 | command | TARGET:. | `mvn -DskipTests package` passed across 9 reactor modules |
| E-007 | diff | TARGET:docs/05-TEST-QA | Regression SSoT and Cadence Ledger updated for first-chat copyable code contract |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| live provider behavior is not proven by this task | coordinator | yes | out of scope; real provider validation remains LV-001 opt-in |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 已提交审查材料包，且可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节、证据和 lesson decision 已补齐。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | 已接受 no-candidate，结论投影到回归台账。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认。 | 人工确认后 task-complete。 |
| Soft-deleted / Superseded | no | 任务仍为 active。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新，`task_plan.md`
- Progress：`progress.md` verification entry
- 发现记录：已写入 `findings.md`
- Regression SSoT：已调整 RG-001/RG-005/RG-007/RG-008
- Lessons：checked-none: first-chat-contract-recorded-in-regression-ssot
- 收口记录：`walkthrough.md` and `docs/10-WALKTHROUGH/2026-06-06-first-chat-copyable-code-contract.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自新增本地 smoke tests、core/starter 全量模块门禁、docs-site build/typecheck、monorepo package smoke 和回归台账同步。该任务不做发布或 live provider 验证；人工确认仍是最终门禁。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606061053 |
| Submitted At | 2026-06-06 10:53 |
| Submitted By | agent |
| Task Key | TASKS/2026-06-06-item-885d365a |
| Materials Checklist Hash | 72b721d0ad872c7c |
| Evidence Summary | First-chat copyable code contract is implemented and verified: core/starter smoke tests added, docs-site and ai4j-app-builder guards updated, RG-001/RG-005/RG-007/RG-008 passed. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/tasks/2026-06-06-item-885d365a |
