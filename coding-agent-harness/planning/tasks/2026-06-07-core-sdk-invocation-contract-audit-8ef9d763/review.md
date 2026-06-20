# core sdk invocation contract audit - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex | self | Core SDK invocation contract audit package |

## 审查范围

- 审查类型：architecture / governance
- 范围内：当前 task package、`design.md`、`findings.md`、源码证据链
- 范围外：业务代码实现、docs-site 正文修改、Java executable behavior
- 来源材料：task plan、design、findings、progress、targeted source scan

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | ARS-pending |
| Submitted At | pending |
| Submitted By | agent |
| Task Key | 2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 |
| Materials Checklist Hash | pending |
| Evidence Summary | Core SDK invocation contract audited; design concludes the object chain remains the main contract and hidden Chat facade should not be reintroduced. |
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

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：
  - 无
- Fix loop count：1
- 当前结论：本任务只做设计审计，结论直接来自源码入口和既有文档，不发布新 API。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- 后续如要把“不要新增隐藏式 Chat facade”沉淀为长期标准，需要单独 reference/lesson 任务。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java | 配置聚合入口 |
| E-002 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java | 单实例能力工厂 |
| E-003 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java | 多实例能力入口 |
| E-004 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/platform/openai/chat/OpenAiChatService.java | Chat tool loop 与 stream 执行边界 |
| E-005 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java | Tool/MCP 调度中心 |
| E-006 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/rag/DefaultRagService.java | RAG 独立服务合同 |
| E-007 | report | TARGET:ai4j/src/main/java/io/github/lnyocly/ai4j/memory/ChatMemory.java | Memory 投影到 Chat/Responses |
| E-008 | diff | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 | 设计审计材料已写入 |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 本任务不执行 API 实现，只给设计结论 | coordinator | yes | 后续 API 任务需要单独人工确认 |

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
- Lessons：checked-none: design-audit-local-decision
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自源码入口审计、设计文档、findings 决策表和 task package self-review。本任务不发布业务代码。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606071125 |
| Submitted At | 2026-06-07 11:25 |
| Submitted By | agent |
| Task Key | TASKS/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 |
| Materials Checklist Hash | 23d23ef34ee637d3 |
| Evidence Summary | Core SDK invocation contract audited; design keeps object-chain main contract and rejects hidden Chat facade. |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/tasks/2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 |
