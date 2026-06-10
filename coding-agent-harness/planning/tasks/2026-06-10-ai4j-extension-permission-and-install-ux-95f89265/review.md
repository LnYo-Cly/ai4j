# AI4J Extension Permission and Install UX - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | extension API activation contract, CLI plan/run/resource UX, Spring Boot binding, docs-site wording, regression governance |
| Hypatia | subagent | read-only review of uncommitted F-039 diff across API, CLI, Spring, docs-site, regression/task materials |

## 审查范围

- 审查类型：adversarial / regression / architecture / docs-contract / security-boundary
- 范围内：`ai4j-extension-api` explicit resource activation API and activation plan, `ai4j-cli` plan/run/resource behavior, `ai4j-spring-boot-starter` property binding, docs-site extension pages, Regression SSoT/Cadence updates, task materials。
- 范围外：远程 marketplace、CLI 自动修改 Maven/Gradle 依赖、运行时热加载 jar、provider 自动注册、Agent 自动创建。
- 来源材料：task plan、working diff、targeted and broad regression outputs、docs-site typecheck/build、subagent review result、harness status。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | agent-review-f039-2026-06-10 |
| Submitted At | 2026-06-10 local |
| Submitted By | Codex coordinator |
| Task Key | 2026-06-10-ai4j-extension-permission-and-install-ux-95f89265 |
| Materials Checklist Hash | lifecycle-cli-pending |
| Evidence Summary | Extension API 19 tests, CLI targeted 25 tests, Spring starter targeted 6 tests, Ask User plugin tests, monorepo package smoke, docs-site typecheck/build, diff check, and harness status are recorded in `progress.md`. |
| Open Findings Count | 0 |
| Scanner Version | manual-review-v1 |

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

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 未执行 live-provider 或真实第三方插件包验证；本任务只要求本地 deterministic gates。
  - 人工 Review Confirmation 尚未执行；agent 不能代办。
- Fix loop count：2
- 当前结论：可以提交 Agent Review Submission；subagent 的 P2 材料/治理缺口已补齐，P3 prompt/guardrail 正向测试缺口已修复并重跑。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-039-RF-001 | P2 | Regression SSoT / Cadence Ledger and task closeout materials were not yet synchronized. | Subagent review of `docs/05-TEST-QA/*` and task package. | Update RG-010/RG-004/RG-005/RG-011/RG-007/RG-008, add SRB-047, and fill progress/review/walkthrough/lesson decision. | no | closed | no | This review packet, `progress.md`, Regression SSoT, Cadence Ledger |
| F-039-RF-002 | P3 | Prompt and Guardrail allowlist positive paths lacked direct assertions. | Subagent review of `ExtensionRegistryTest`, `Ai4jCliTest`, `ExtensionAutoConfigurationTest`. | Add API/Spring/CLI positive assertions and rerun affected gates. | no | closed | no | `ExtensionRegistryTest`, `Ai4jCliTest`, `ExtensionAutoConfigurationTest` |

## 非阻塞备注（Non-Material Notes）

- `enable(...)` keeps compatibility semantics for command, Skill, Prompt, and Guardrail resources unless strict mode or an allowlist is used.
- `extension plan` is an activation preview; it does not execute commands or expose tools to a model.
- CLI `run/resource` strict allow parameters are host/manual paths and still do not imply Agent tool exposure.

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionRegistry.java | Added explicit resource activation API, allowlists, and activation plan generation. |
| E-002 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/runtime/ExtensionRuntimeState.java | Snapshot now filters non-tool resources in strict mode and fail-fast rejects unknown allowed resources. |
| E-003 | diff | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java | Added `extension plan`, strict allow args for run/resource, and scoped help wording. |
| E-004 | diff | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiExtensionProperties.java | Added explicit-resource-activation and commands/skills/prompts/guardrails allow binding model. |
| E-005 | diff | TARGET:docs-site/docs/core-sdk/extension | Docs now cover plan, strict Java/Spring examples, Ask User strict examples, and non-marketplace boundaries. |
| E-006 | review | TARGET:subagent:Hypatia | Read-only subagent found no blocker; two material cleanup findings were addressed. |
| E-007 | command | TARGET:. | `mvn -pl ai4j-extension-api -DskipTests=false test` passed with 19 tests. |
| E-008 | command | TARGET:. | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 25 tests. |
| E-009 | command | TARGET:. | `mvn -pl ai4j-spring-boot-starter -am -Dtest=ExtensionAutoConfigurationTest -DfailIfNoTests=false -DskipTests=false test` passed with 6 tests. |
| E-010 | command | TARGET:. | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` passed with extension API 19 tests and Ask User plugin 6 tests. |
| E-011 | command | TARGET:. | `mvn -DskipTests package` passed across 11 reactor projects. |
| E-012 | command | TARGET:docs-site | `npm run typecheck` and `npm run build` passed. |
| E-013 | command | TARGET:. | `git diff --check` passed with CRLF warnings only. |
| E-014 | command | TARGET:. | `npx.cmd --yes coding-agent-harness status --json .` returned 0 failures and 1 dirty-state warning. |

## 无重要发现声明

本轮已检查上述证据，所有 material findings 已关闭，未发现阻塞 F-039 目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 未执行 live-provider 或真实外部插件验证 | maintainer | yes | 本任务本地合同足够；真实第三方插件属于后续生态验证 |
| 人工 Review Confirmation 尚未执行 | human | yes | 由用户通过 dashboard/workbench 或 lifecycle CLI 确认或退回 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | Agent review packet 已准备，且无 open material finding。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节和证据已补齐。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | `lesson_candidates.md` 记录 no-candidate。 | 人工覆盖 no-candidate 判断时重新路由。 |
| Confirmed / Finalized | no | 尚无人工确认。 | Human Review Confirmation 后再完成。 |
| Soft-deleted / Superseded | no | 本任务仍为当前 active task。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已执行，路径 `task_plan.md`。
- Progress：验证和 review fix 已记录，路径 `progress.md`。
- 发现记录：重要发现记录在本 review 表中，均已关闭。
- Regression SSoT：更新 RG-010/RG-011/RG-004/RG-005/RG-007/RG-008；Cadence Ledger 新增 SRB-047。
- Lessons：checked-none: 本任务没有新增可复用 harness 流程规则。
- 收口记录：`walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

最终信心来自 API/CLI/Spring/official plugin targeted tests、monorepo package smoke、docs-site typecheck/build、diff check、harness status，以及 self + subagent review。人工确认仍是用户侧门禁，不由 agent 代办。
