# CLI memory compact command UX - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | 规划范围、命令边界、测试计划和安全边界 |
| read-only reviewer | subagent | 实现完成后可检查 CLI/TUI/ACP/docs 一致性 |

## 审查范围

- 审查类型：architecture / regression / security
- 范围内：`/memory` 与已有 `/compact`、`/compacts`、`/checkpoint` 的职责分工；CLI/TUI/ACP/docs/test 计划；raw memory 不泄露边界。
- 范围外：compact 算法正确性、provider live 调用、真实模型上下文质量。
- 来源材料：`task_plan.md`、`references/cli-memory-compact-command-ux-plan.md`、`findings.md`、现有 CLI command 代码巡检结果。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending |
| Submitted By | pending |
| Task Key | 2026-06-20-cli-memory-compact-command-ux-d56c15fd |
| Materials Checklist Hash | pending |
| Evidence Summary | pending implementation and tests |
| Open Findings Count | pending |
| Scanner Version | pending |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | partial | `progress.md`; implementation evidence pending |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | pending | `lesson_candidates.md`; closeout decision pending implementation |
| Walkthrough or closeout link | yes | pending | `walkthrough.md`; closeout pending implementation |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 实现尚未开始，尚未证明 snapshot 是否有 auto-compact breaker 字段。
  - CLI/TUI/ACP/docs 的最终一致性需要 diff 和 tests 证明。
- Fix loop count：0
- 当前结论：规划可以作为实现输入继续；不能进入 review confirmation，直到实现和验证完成。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- 当前任务先记录规划；实现完成后需要重写 Evidence Checked、Confidence Challenge 和 residual。
- 如果 implementation 不扩大到 `ai4j-coding`，Regression SSoT 可能只需扩展 CLI command surface gate。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java | 已有 `/compact`、`/compacts`、`/checkpoint`，未见 `/memory` root command。 |
| E-002 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | 已有 compact/checkpoint dispatch 和 status/session memory 字段，可作为 `/memory` 输出来源。 |
| E-003 | code | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpSlashCommandSupport.java | ACP 已暴露 `compacts` 与 `checkpoint`，需补 `memory`。 |
| E-004 | docs | TARGET:docs-site/docs/coding-agent/command-reference.md | 文档已有 compact/checkpoint 命令说明，需补 `/memory` 分工。 |

## 无重要发现声明

本轮只审查规划材料和现有命令面，未发现阻塞“按本计划进入实现”的重要发现；实现是否可发布必须等待 diff、测试和 docs build 证据。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| snapshot 可能没有 auto-compact breaker open/closed 字段 | coordinator | yes | 实现时降级为 `unknown` 或省略，不强改 public API。 |
| `/memory` 输出过多可能泄露 raw memory | coordinator | no | 实现和 reviewer 必须检查仅输出摘要字段。 |
| CLI/TUI/ACP command surface 可能漂移 | coordinator | no | targeted tests + docs reference 对齐。 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- |
| Review | no | 尚未实现和提交审查材料包。 | 实现、验证、review.md 更新后执行 `task-review`。 |
| Missing Materials | no | 当前规划材料已补齐；实现证据仍是 active task 的正常待办。 | not applicable |
| Blocked | no | 当前没有 open blocking finding。 | not applicable |
| Lessons | no | 当前暂无可复用 lesson 候选；closeout 时再判定。 | `lesson_candidates.md` 写 no-candidate 或候选。 |
| Confirmed / Finalized | no | 尚未人工确认。 | review-confirm + closeout。 |
| Soft-deleted / Superseded | no | active task。 | not applicable |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`。
- Progress：规划记录写入 `progress.md`。
- 发现记录：已更新 `findings.md`。
- Regression SSoT：实现时判断是否新增/扩展 CLI slash command parity gate。
- Lessons：pending，预计 closeout 时 `checked-none`，除非实现中出现可复用流程教训。
- 收口记录：实现完成后更新 `walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

当前只是规划信心，依据来自现有代码巡检和 Harness task package。发布前最终信心必须来自 targeted tests、CLI module tests、docs build、harness status 和 review no-finding。
