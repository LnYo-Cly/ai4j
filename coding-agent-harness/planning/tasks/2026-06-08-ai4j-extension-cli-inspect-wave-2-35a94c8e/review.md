# AI4J extension CLI inspect wave 2 - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | CLI extension list/inspect implementation, extension API runtime inspection snapshot, CLI fixture tests, regression/task governance |

## 审查范围

- 审查类型：architecture / regression / security
- 范围内：`ai4j-cli/**` top-level extension command、`ai4j-extension-api` inspection snapshot、ServiceLoader test fixture、targeted Maven evidence、Regression/Cadence/harness task materials。
- 范围外：`extension install`、持久化 enable、Spring Boot binding、Agent/Coding runtime adapter、marketplace、runtime jar download、真实第三方插件发布验证。
- 来源材料：`task_plan.md`、当前 diff、`findings.md`、`progress.md`、targeted Maven output、package smoke、RG-004 failed gate output。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending task-review |
| Submitted At | pending task-review |
| Submitted By | Codex coordinator |
| Task Key | 2026-06-08-ai4j-extension-cli-inspect-wave-2-35a94c8e |
| Materials Checklist Hash | pending lifecycle scanner |
| Evidence Summary | `ai4j-cli extension list/inspect` is implemented with manifest-only default inspect, opt-in runtime inspection snapshot, CLI fixture tests, extension API tests, package smoke, and routed RG-004 upstream residual. |
| Open Findings Count | 0 |
| Scanner Version | pending lifecycle scanner |

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

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：无阻塞缺口；完整 RG-004 的上游 agent residual 已按 R-008 路由，不影响本轮新增 CLI 行为的 targeted 证据。
- Fix loop count：2
- 当前结论：实现范围集中，默认 inspect 不执行 `apply()`，runtime inspect 仅返回 read-only snapshot；CLI/API targeted tests 和 package smoke 均通过，可以提交人工确认。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- 完整 `mvn -pl ai4j-cli -am -DskipTests=false test` 仍会在 `ai4j-agent` 的既有 `HandoffPolicyTest` 残余处失败；这不是本轮新增代码引起，且已记录在 R-008。
- 本轮 CLI 输出是人可读格式；后续若要 marketplace/脚本消费，应单独加 `--json`。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `git diff --check` passed with CRLF warnings only. |
| E-002 | command | TARGET:. | `mvn -pl ai4j-extension-api -DskipTests=false test` passed: 8 tests, 0 failures. |
| E-003 | command | TARGET:. | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed: `Ai4jCliTest` 8 tests, 0 failures. |
| E-004 | command | TARGET:. | `mvn -DskipTests package` passed across 10 reactor modules. |
| E-005 | command | TARGET:. | `mvn -pl ai4j-cli -am -DskipTests=false test` failed before `ai4j-cli`, in existing `ai4j-agent` `HandoffPolicyTest` R-008. |
| E-006 | diff | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | CLI list/inspect implementation with manifest-only default and opt-in runtime inspection. |
| E-007 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionInspectionSnapshot.java | Read-only runtime inspection model without executors or command handlers. |
| E-008 | fixture | TARGET:ai4j-cli/src/test/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension | ServiceLoader fixture validates classpath discovery from CLI tests. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 完整 RG-004 被既有 R-008 阻塞，CLI 模块未执行到 | coordinator | yes | 独立 agent runtime 任务修复 `HandoffPolicyTest` 后重跑完整 RG-004。 |
| `--runtime` 会临时执行第三方 extension `apply()` | coordinator | yes | CLI help 和 default inspect 明确提示；后续可在 plugin sandbox/签名策略任务中增强。 |
| 无脚本稳定 JSON 输出 | coordinator | yes | 后续 marketplace/install 任务前评估 `--json`。 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料包已补齐，可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节和证据已补齐。 | n/a |
| Blocked | no | 无 open blocking finding；RG-004 上游失败已路由为既有残余。 | n/a |
| Lessons | no | 本任务无需要 promotion 的 lesson 候选。 | n/a |
| Confirmed / Finalized | no | 尚无人工确认。 | 人工确认后 closeout / ledger finalized。 |
| Soft-deleted / Superseded | no | 任务仍为 active review path。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新验收标准，路径 `task_plan.md`
- Progress：`progress.md` 已记录 implementation、targeted pass、package pass、RG-004 R-008 residual
- 发现记录：`findings.md` 已记录默认 manifest-only、inspection snapshot 和 RG-004 residual
- Regression SSoT：已同步 RG-010/RG-004 最近验证和 SRB 行
- Lessons：checked-none: cli-extension-inspect-local-contract-no-new-governance-lesson
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自 CLI/API targeted tests、package smoke、self-review 对默认不执行第三方代码的确认、read-only inspection snapshot 边界、以及回归残余 R-008 的明确路由。人工确认仍是任务关闭前的必需门禁。
