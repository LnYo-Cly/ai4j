# docs site wave 1 real onboarding recipes - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex | self | docs-site Wave 1 onboarding / recipe content |

## 审查范围

- 审查类型：docs / regression
- 范围内：`docs-site/docs` Wave 1 页面、sidebar、新 recipe、build 输出
- 范围外：Java API、i18n 全量同步、站点视觉重设计
- 来源材料：task plan、diff、文本扫描、`npm run build`

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | ARS-pending |
| Submitted At | pending |
| Submitted By | agent |
| Task Key | 2026-06-07-docs-site-wave-1-real-onboarding-recipes-f62730fb |
| Materials Checklist Hash | pending |
| Evidence Summary | docs-site Wave 1 onboarding recipes updated; OpenAI-compatible/TroveBox page added and docs-site build passed. |
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

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes
- 如果不是 100%，剩余漏洞或证据缺口：
  - 无
- Fix loop count：1
- 当前结论：docs-site build 通过，页面只使用真实对象链和现有配置能力，可提交人工确认。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- 中文 i18n 旧路径未同步，保留为后续任务。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:docs-site/docs/start-here/openai-compatible-and-trovebox.md | 新增 OpenAI-compatible / TroveBox recipe |
| E-002 | diff | TARGET:docs-site/docs/start-here | first chat / Java / Spring Boot onboarding updated |
| E-003 | diff | TARGET:docs-site/docs/spring-boot/configuration-reference.md | starter profile and TroveBox config added |
| E-004 | diff | TARGET:docs-site/docs/core-sdk/service-entry-and-registry.md | registry 主入口和中转平台 profile 说明 |
| E-005 | diff | TARGET:docs-site/sidebars.ts | 新 recipe 挂入 Start Here |
| E-006 | command | TARGET:docs-site | `npm run build` passed |
| E-007 | command | TARGET:. | text scan for rejected Chat facade references |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 中文 i18n 未同步 | coordinator | yes | 后续 i18n 同步任务 |

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
- Regression SSoT：无新增；docs-site build 证据记录在本任务
- Lessons：checked-none: docs-wave-local-scope
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

最终信心来自 diff 审查、文本扫描和 `npm run build`。本任务不发布 Java API。
