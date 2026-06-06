# 轻量 ChatClient 首聊门面 - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | core SDK facade、单元测试、docs-site 首聊入口、ai4j-app-builder recipe、回归证据 |

## 审查范围

- 审查类型：regression / architecture / docs
- 范围内：`ai4j` 新增 `ChatClient`、`ChatClientTest`、docs-site start-here 页面、root/docs README、`ai4j-app-builder` skill recipe、Regression SSoT 与 Cadence Ledger。
- 范围外：不审查 live provider 质量、真实 OpenAI 额度/网络、agent runtime、FlowGram 行为、Spring Boot starter 代码。
- 来源材料：`task_plan.md`、当前 diff、RG-001/RG-007/RG-008 命令输出、`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md`。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606070230 |
| Submitted At | 2026-06-07 02:30 |
| Submitted By | Codex coordinator |
| Task Key | 2026-06-07-chatclient-d5f84742 |
| Materials Checklist Hash | pending lifecycle scanner |
| Evidence Summary | `ChatClient` facade and tests added; docs-site and app-builder first-chat paths now prefer `ChatClient`; RG-001/RG-007/RG-008 passed. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |

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

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：无。
- Fix loop count：1
- 当前结论：实现保持为薄 facade，未改变 `AiService` / `IChatService` 合同；MockWebServer 覆盖请求路径、鉴权和文本抽取；core/full package/docs build 均通过，可以提交人工审查。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `ChatClient.openAi(...)` 当前仅作为 OpenAI-compatible 首聊门面；其他 provider 仍建议走完整对象链，避免一次性包装出半成品 API。
- Live provider 未运行；本任务默认证据是本地 deterministic tests/build，真实 key、额度和网络仍属于用户运行时外部条件。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j "-Dtest=ChatClientTest,FirstChatCopyableCodeTest,ConfigurationTest" -DskipTests=false test` passed: 10 tests, 0 failures. |
| E-002 | command | TARGET:. | `mvn -pl ai4j -am -DskipTests=false test` passed: 108 tests, 0 failures. |
| E-003 | command | TARGET:. | `mvn -DskipTests package` passed across 9 reactor modules. |
| E-004 | command | TARGET:docs-site | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` exited 0. |
| E-005 | command | TARGET:docs-site | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` passed and generated `docs-site/build`. |
| E-006 | command | TARGET:. | `git diff --check` found no whitespace errors; only Windows LF/CRLF warnings. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 真实 provider 质量、网络、key 权限和 quota 未在本任务验证 | user / release operator | yes | 只有 provider 协议或 release 任务需要时再按 LV-001 opt-in live gate 执行。 |
| `ChatClient` 目前只覆盖 OpenAI-compatible 首聊 facade | coordinator | yes | 后续如扩展多 provider，应单独设计 provider-neutral facade，避免本任务扩大范围。 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 审查材料包已提交，且可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节、证据已补齐。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | 本任务无可复用治理 lesson 候选。 | n/a |
| Confirmed / Finalized | no | 尚无人工确认。 | 人工确认后 closeout / ledger finalized。 |
| Soft-deleted / Superseded | no | 任务仍为 active review path。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：无需再改，路径 `task_plan.md`
- Progress：`progress.md` 已记录 scope、implementation、verification
- 发现记录：`findings.md` 已记录 FND/DEC
- Regression SSoT：已更新 RG-001/RG-007/RG-008
- Lessons：checked-none: chatclient-first-chat-facade-local-api-no-new-governance-lesson
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自 core facade 的本地 MockWebServer 测试、core 全量测试、9 模块 package、docs-site typecheck/build、回归 SSoT/Cadence 记录，以及无 open material finding 的 self-review。人工确认仍是任务最终关闭前的必需门禁。
