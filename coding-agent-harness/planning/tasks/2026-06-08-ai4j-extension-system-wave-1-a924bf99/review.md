# AI4J extension system wave 1 - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| [name] | self / subagent / external / human | [审查范围] |

## 审查范围

- 审查类型：adversarial / security / regression / architecture / release / other
- 范围内：[文件、模块、行为、运行目标]
- 范围外：[明确不审查的内容；如无写“无”]
- 来源材料：[task plan、diff、commit、PR、测试输出、运行证据]

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | [由 task-review 生成] |
| Submitted At | [timestamp] |
| Submitted By | [agent 或 coordinator 身份] |
| Task Key | 2026-06-08-ai4j-extension-system-wave-1-a924bf99 |
| Materials Checklist Hash | [由 task-review 生成；只作信息记录，不作为手工门禁] |
| Evidence Summary | [测试、diff、运行和审查材料证据] |
| Open Findings Count | [数字] |
| Scanner Version | [生成时的 scanner 版本] |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes / no | present / missing / incomplete | [路径或原因] |
| Task plan | yes / no | present / missing / incomplete | [路径或原因] |
| Progress and evidence | yes / no | present / missing / incomplete | [路径或原因] |
| Visual map | yes / no | present / missing / incomplete | [路径或原因] |
| Lesson candidate decision | yes / no | present / missing / incomplete | [路径或原因] |
| Walkthrough or closeout link | yes / no | present / missing / incomplete | [路径或原因] |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes / no
- 如果不是 100%，剩余漏洞或证据缺口：
  - [风险 / 漏洞 / 未验证假设；如无写“无”]
- Fix loop count：[已经执行几轮 review -> fix -> evidence -> review]
- 当前结论：[为什么现在可以继续、暂停或收口]

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- [不阻塞本轮目标但值得记录的问题；如无写“无”]

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command / diff / fixture / screenshot / review / report | PUBLIC:path 或 PRIVATE:path 或 TARGET:path 或 EXTERNAL:path 或 URL:https://example.com | [检查了什么，结论是什么] |

## 无重要发现声明

[如果没有重要发现，明确写：本轮已检查上述证据，未发现阻塞目标的重要发现。]

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| [风险] | [负责人] | yes / no | [后续路径或“无”] |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes / no | 已提交审查材料包，且可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | yes / no | 必需文件、章节、证据或 review submission 缺失 / 不完整。 | Agent 补齐材料并重新提交审查。 |
| Blocked | yes / no | 存在 open blocking finding、非法状态转换、审计失败或需要人工 waiver。 | blocker 被修复、关闭或明确豁免。 |
| Lessons | yes / no | Lesson candidate 需要拒绝、留在任务内、dry-run promotion 或创建沉淀任务。 | 人工决定候选路由；除非明确批准，promotion 仍是单独维护任务。 |
| Confirmed / Finalized | yes / no | 已有人工确认；可能仍待结项或治理收口。 | Closeout、ledger 和 lesson routing 都完成。 |
| Soft-deleted / Superseded | yes / no | 任务有 tombstone、superseded-by 或 archive 状态；duplicate / abandoned 等语义写在 `Reason`。 | reopen 或作为只读审计历史保留。 |

## 后续路由（Follow-Up Routing）

- 任务计划：[是否需要更新，路径或“无”]
- Progress：[对应 `progress.md` 条目]
- 发现记录：[是否需要写入 `findings.md`]
- Regression SSoT：[新增 / 调整 / 无]
- Lessons：[checked-created: L-YYYY-MM-DD-NNN / checked-candidate: LC-YYYYMMDD-NNN / queued-promotion: LC-YYYYMMDD-NNN / checked-none: 一句话原因]
- 收口记录：[收口时引用路径]

## 最终信心依据（Final Confidence Basis）

[说明最终信心来自哪些证据、审查层级和已关闭发现。发布前最终审查不能只依赖 self-only。]
