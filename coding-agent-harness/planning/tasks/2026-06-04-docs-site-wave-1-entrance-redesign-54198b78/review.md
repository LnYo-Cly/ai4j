# docs site wave 1 entrance redesign - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator self-review | self | docs-site Wave 1 entrance diff, task package, build evidence |

## 审查范围

- 审查类型：regression / docs architecture / release readiness
- 范围内：`intro.md`、`why-ai4j.md`、`feature-map.md`、`sidebars.ts`、任务包证据。
- 范围外：全站旧页面质量、Docusaurus 主题视觉、Java SDK 代码、README。
- 来源材料：本轮 diff、`npm run build`、`git diff --check`、前置 IA 设计任务参考资料。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending task-review lifecycle command |
| Submitted At | pending task-review lifecycle command |
| Submitted By | coordinator |
| Task Key | 2026-06-04-docs-site-wave-1-entrance-redesign-54198b78 |
| Materials Checklist Hash | pending task-review lifecycle command |
| Evidence Summary | docs-site build passed; git diff check passed; docs entrance diff complete |
| Open Findings Count | 0 |
| Scanner Version | pending task-review lifecycle command |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` has `no-candidate-accepted` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 本轮只修正入口和功能地图，未逐页重写 Core SDK / Agent / FlowGram 深页。
  - 没有做浏览器视觉截图；本轮是 Markdown 内容和 sidebar 变更，Docusaurus build 已覆盖主要断链和编译风险。
- Fix loop count：1
- 当前结论：本轮目标可以进入人工审查；剩余问题属于后续 docs-site Wave 2/3 深页质量提升，不阻塞 Wave 1 入口修正。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `docs-site/build` 由构建生成，但未出现在 `git status --short`，不需要纳入 commit。
- `git diff --check` 输出 LF/CRLF 工作区转换 warning，这是 Windows 工作区常见 warning，不是 diff check 失败。
- 旧深页质量仍需要后续分波次提升；本轮不处理全站迁移。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | `docs-site/docs/intro.md` | 首页已改成普通 Java、Spring Boot、Feature Map 三条入口，并保留能力层级和模块地图。 |
| E-002 | diff | `docs-site/docs/start-here/why-ai4j.md` | 定位页已说明 AI4J 与 Spring AI、LangChain4j、AgentScope Java 的边界和适合/不适合场景。 |
| E-003 | diff | `docs-site/docs/start-here/feature-map.md` | 新增功能地图，覆盖主要能力和成熟度标签。 |
| E-004 | diff | `docs-site/sidebars.ts` | Start Here sidebar 已挂入 `start-here/feature-map`。 |
| E-005 | command | `docs-site/` | `npm run build` 成功，生成静态文件到 `build`，未报告断链或编译错误。 |
| E-006 | command | repo root | `git diff --check` 成功，仅有 LF/CRLF warning。 |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| docs-site 深页仍可能存在“功能讲不清楚”的旧问题 | coordinator / user | yes | 后续 Wave 2/3 针对 Core SDK、RAG、MCP、Agent、FlowGram 分页补强 |
| Feature Map 的成熟度标记需要随实现变化维护 | coordinator | yes | 后续新增或稳定能力时同步更新 `feature-map.md` |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 材料已齐，执行 `task-review` 后可等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | brief、plan、progress、visual map、lesson decision、walkthrough 和 evidence 均已准备。 | n/a |
| Blocked | no | 无 open blocking finding，构建和 diff check 均通过。 | n/a |
| Lessons | no | 本轮已检查无可复用 lesson candidate。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认。 | 人工确认后进入 closeout。 |
| Soft-deleted / Superseded | no | 任务仍是当前有效任务。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：无需更新，已在 `task_plan.md` 标记构建和 diff check 通过。
- Progress：`progress.md` 的 `2026-06-04 20:12` 和 `2026-06-04 20:17` 条目。
- 发现记录：已写入 `findings.md`。
- Regression SSoT：无新增固定回归面；docs-site 现有构建命令足够覆盖本轮。
- Lessons：checked-none: 本轮是具体 docs-site 入口执行切片，没有新增可复用 harness 治理规则。
- 收口记录：`walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

最终信心来自：小范围 diff、Docusaurus production build 成功、`git diff --check` 成功、任务包材料齐全、无 open P0/P1/P2 finding。残余风险已限定为后续 docs-site 深页质量提升，不影响 Wave 1 入口修正。
