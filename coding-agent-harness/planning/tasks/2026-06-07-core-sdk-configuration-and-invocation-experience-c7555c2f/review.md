# Core SDK configuration and invocation experience upgrade design - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex | self | Core SDK configuration and invocation experience design package |

## 审查范围

- 审查类型：architecture / governance
- 范围内：当前 task package、`design.md`、`findings.md`、源码和 docs 示例证据
- 范围外：Java API 实现、docs-site 正文修改、发布行为
- 来源材料：task plan、前置调用合同审计、源码扫描、docs-site/README 示例扫描

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | ARS-pending |
| Submitted At | pending |
| Submitted By | agent |
| Task Key | 2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f |
| Materials Checklist Hash | pending |
| Evidence Summary | Core SDK configuration and invocation experience design completed; recommends docs/recipe first, configuration helpers second, registry/starter default profile third. |
| Open Findings Count | 0 |
| Scanner Version | pending |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |
| Design artifact | yes | present | `design.md` |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：
  - 无
- Fix loop count：1
- 当前结论：本任务只做设计，不发布 API；结论来自源码和 docs 证据，可提交人工确认。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- Wave 2 的 `Configuration.openAi(...)` helper 必须单独 API 评审，不能在本任务直接实现。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763/design.md | 前置调用合同审计 |
| E-002 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java | Plain Java 配置样板来源 |
| E-003 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java | profile/registry 正式入口 |
| E-004 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/DefaultAiServiceRegistry.java | scoped Configuration 和 platform copy |
| E-005 | report | TARGET:ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigProperties.java | `ai.platforms` 配置绑定 |
| E-006 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/config/OpenAiConfig.java | OpenAI-compatible apiHost 基础 |
| E-007 | report | TARGET:docs-site | docs-site 已有真实对象链和 profile 示例 |
| E-008 | diff | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f | 设计包已写入 |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 本任务不执行 Java API，只给升级设计 | coordinator | yes | 后续 Wave 1/Wave 2 分任务 |
| `Configuration.openAi(...)` helper 是否采用仍未定 | user / coordinator | yes | Wave 2 API 评审 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料准备完成后提交审查，等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件均存在。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | no-candidate accepted。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认。 | 人工确认后 closeout。 |
| Soft-deleted / Superseded | no | 无 tombstone 或 superseded-by。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：见 `progress.md`
- 发现记录：见 `findings.md`
- Regression SSoT：无；design-only
- Lessons：checked-none: design-wave-routing-local
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自前置合同审计、源码入口审计、docs 示例扫描、设计文档和 findings 决策表。本任务不发布业务代码。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606071540 |
| Submitted At | 2026-06-07 15:40 |
| Submitted By | agent |
| Task Key | TASKS/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f |
| Materials Checklist Hash | fdd60d47aaed85ec |
| Evidence Summary | Core SDK configuration and invocation experience design completed; recommends docs/recipe first, Configuration helper API second, registry/starter default profile third. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-configuration-and-invocation-experience-c7555c2f |
