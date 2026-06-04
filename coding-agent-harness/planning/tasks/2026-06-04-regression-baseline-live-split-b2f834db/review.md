# regression baseline live split - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator self-review | self | 回归 SSoT、Cadence、task materials、验证边界 |
| dashboard reviewer | human | Agent Review Submission 后的人工确认 |

## 审查范围

- 审查类型：regression / governance
- 范围内：`coding-agent-harness/governance/regression/*`、legacy docs projection、任务材料、harness status 输出。
- 范围外：业务测试代码、Maven profile/category 实现、真实 provider、发布签名、CI required checks。
- 来源材料：task plan、diff、CI/package script inspection、provider/env test scan、harness status。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending task-review CLI |
| Submitted At | pending task-review CLI |
| Submitted By | coordinator |
| Task Key | 2026-06-04-regression-baseline-live-split-b2f834db |
| Materials Checklist Hash | pending task-review CLI |
| Evidence Summary | Regression SSoT 和 Cadence Ledger 已拆分 local-required 与 live/credential opt-in gate；harness status 和关键字段扫描作为治理验证证据。 |
| Open Findings Count | 0 |
| Scanner Version | pending task-review CLI |

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
  - 本轮没有验证所有 Java module tests 是否已经完全不依赖真实 provider；该问题已作为 R-006 路由。
- Fix loop count：1
- 当前结论：可以提交审查，因为本轮目标是治理分层，不是关闭 live test profile/hygiene 残余。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- legacy `docs/` 目录仍被 `.gitignore` 忽略；本轮只显式跟踪三份 projection 文件，dashboard/status 的事实源仍是 tracked v2 harness 文件。
- RG-009 仍缺 dedicated CI workflow，已作为 R-007 路由。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:coding-agent-harness/governance/regression/Regression-SSoT.md | 新增三层回归分层、LV/CR opt-in gate、R-006/R-007 残余。 |
| E-002 | diff | TARGET:coding-agent-harness/governance/regression/Cadence-Ledger.md | 触发表新增必跑 gate、opt-in gate、节奏和最低证据深度。 |
| E-003 | command | TARGET:.github/workflows/java-regression.yml | Java PR workflow 确认覆盖 package smoke 和 6 模块 test matrix。 |
| E-004 | command | TARGET:. | harness status 期望 pass / 0 failures / 0 warnings。 |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| live-provider tests 仍缺统一 Maven profile/category | project coordinator | yes | R-002 / R-006 |
| FlowGram webapp demo baseline 尚未进入 dedicated CI | project coordinator | yes | R-007 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料包补齐后提交人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件已补齐。 | 不适用。 |
| Blocked | no | 无 open blocking finding。 | 不适用。 |
| Lessons | no | 本轮无可复用 lesson candidate。 | 不适用。 |
| Confirmed / Finalized | no | 尚待人工确认。 | Closeout、ledger 和 lesson routing 都完成。 |
| Soft-deleted / Superseded | no | 任务仍为 active。 | 不适用。 |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：由 `task-log` 和 `task-phase` 写入
- 发现记录：已更新 `findings.md`
- Regression SSoT：新增 / 调整
- Lessons：checked-none: 本轮是项目特定 gate 分层，没有可直接提升为全局 lesson 的新模式
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自 tracked v2 SSoT/Cadence diff、CI/package script 检查、provider/env 测试扫描、harness status，以及人工 dashboard confirmation。live profile 与 CI 扩展残余已路由，不阻塞本轮治理切片。
