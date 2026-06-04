# live provider test hygiene - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | POM profile/category、live test credential handling、Regression SSoT/Cadence sync、verification evidence |

## 审查范围

- 审查类型：regression / security
- 范围内：root and `ai4j` Surefire config、core/agent/coding live provider tests、env-only key handling、task/governance docs。
- 范围外：真实 provider 调用、release signing、`HandoffPolicyTest` 行为修复。
- 来源材料：diff、`progress.md`、`artifacts/INDEX.md`、Regression SSoT、Cadence Ledger、testing standard。

## Agent Review Submission（Agent 提交审查）

本节由 `harness task-review` 生成或补全。当前下方材料清单已人工预填，提交命令会写入 submission 元数据。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | agent |
| Task Key | 2026-06-04-live-provider-test-hygiene-c392a468 |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | Default local tests exclude live provider tests; targeted live profile tests skip cleanly without credentials; R-008 records unrelated `HandoffPolicyTest` blocker. |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md`, `artifacts/INDEX.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 未运行真实 provider 调用，因为本任务没有用户提供的凭据或 live approval。
  - RG-002 完整默认 gate 有 R-008 非本任务失败。
- Fix loop count：2
- 当前结论：本任务目标可以提交审查；live provider hygiene 已由 default exclusion、profile smoke、env-only scan 和文档同步证明。R-008 必须独立修复后才能把 RG-002 记为 fully green。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `rg` 仍会命中 `config-api-key`、`sk-test`、`jina-key` 等本地 fixture fake key；这些不是 live provider credentials。
- Maven reactor tests 不应在同一个 checkout 内并行跑会写相同 `target/` 的 `-am` 命令；本轮最终采用顺序复跑确认 R-008。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j -DskipTests=false test` passed, 98 tests. |
| E-002 | command | TARGET:. | `mvn -pl ai4j-coding -DskipTests=false test` passed, 56 tests. |
| E-003 | command | TARGET:. | `mvn -pl ai4j-coding -am -DskipTests package` passed and compiled core/agent/coding test sources. |
| E-004 | command | TARGET:. | `-P live-provider-tests` targeted smoke passed with JUnit skips for core/agent/coding when env credentials were absent. |
| E-005 | command | TARGET:. | Credential scan found only deterministic local fixture fake key values after cleanup. |
| E-006 | command | TARGET:. | `mvn -pl ai4j-agent -am -DskipTests=false test` failed in `HandoffPolicyTest`; routed as R-008. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞 live-provider hygiene 目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| R-008: agent local gate fails in `HandoffPolicyTest` | project coordinator | yes for this task | Create/fix an agent-runtime task before claiming RG-002 fully green. |
| No real provider behavior was executed | project coordinator/operator | yes | Run LV-001/LV-002 with real env credentials only when a task or release explicitly requires it. |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 审查材料包已准备，可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需文件、章节和证据已补齐。 | n/a |
| Blocked | no | R-008 不阻塞本任务目标，已路由为外部残余。 | n/a |
| Lessons | no | 本任务不提升共享 lesson；经验记录保留在 walkthrough。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认。 | dashboard 人工确认后 closeout。 |
| Soft-deleted / Superseded | no | n/a | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：见 `progress.md`
- 发现记录：见 `findings.md`
- Regression SSoT：新增 R-008；R-002/R-006 标记 resolved/closed
- Lessons：checked-none: 本任务没有需要提升到共享治理的 durable lesson
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

信心来自代码 diff、default local tests、targeted live profile skip smoke、credential scan 和 v2/legacy 回归文档同步。发布前若需要真实 provider 证据，必须由 operator 提供 env 凭据并运行 LV-001/LV-002。
