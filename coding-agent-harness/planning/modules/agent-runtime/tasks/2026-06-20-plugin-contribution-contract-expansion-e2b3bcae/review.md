# Plugin contribution contract expansion - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| coordinator | self | 插件公共 API、manifest/inspection/activation/validator 契约、ask-user 示例、docs-site 文档、回归证据 |

## 审查范围

- 审查类型：architecture / regression / security / docs
- 范围内：`ai4j-extension-api` contribution contract、`ai4j-plugin-ask-user` 示例、`ai4j-agent` extension bridge 回归、docs-site 技术文档。
- 范围外：真实 sandbox provider、真实 runner provider、插件市场/安装、CLI TUI UX、live provider 调用。
- 来源材料：diff、`task_plan.md`、`findings.md`、Java targeted tests、docs-site build、`git diff --check`。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | coordinator |
| Task Key | MODULES/agent-runtime/2026-06-20-plugin-contribution-contract-expansion-e2b3bcae |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | Plugin contribution contract implementation prepared: extension-api tests, ask-user plugin tests, agent extension targeted tests, docs-site build, and diff check passed. |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

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
  - provider-style contribution 只是 manifest metadata，不能证明真实 sandbox/runner provider 已可用；这是范围内明确不做事项。
  - `engineering-standard.md` 在此 worktree 缺失，未能按阅读矩阵读取；但 AGENTS、testing-standard 和现有代码已覆盖本轮决策。
  - 需要远端 PR checks 补充 CI 证据。
- Fix loop count：1
- 当前结论：本实现可提交 review；剩余风险均为 P3 / out-of-scope，不阻塞本轮 contribution contract。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

不要保留示例 finding。若没有重要发现，只保留表头，并补全下面的无重要发现声明。

## 非阻塞备注（Non-Material Notes）

- `ExtensionContribution` 是 metadata-only，不会改变现有 tool `enable + exposeTool` 安全语义。
- provider-style contribution 默认 `requiresExplicitActivation=true`，activation plan 显示 `requires host binding`。
- validator 对 metadata-only capability 使用 warning，不破坏旧插件兼容。
- docs-site build 首次失败是 worktree 缺少 `node_modules`，安装依赖后通过。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionContribution.java | 新增 manifest-level contribution metadata DTO。 |
| E-002 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionContributionType.java | 新增 tool/command/skill/prompt/guardrail/lifecycle/memory/compact/context/sandbox/runner/CLI/UI contribution 类型。 |
| E-003 | command | TARGET:. | `mvn -pl ai4j-extension-api -DskipTests=false test` passed. |
| E-004 | command | TARGET:. | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` passed. |
| E-005 | command | TARGET:. | `mvn -pl ai4j-agent -am "-Dtest=ExtensionAgentToolsTest,AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` passed. |
| E-006 | command | TARGET:docs-site | `npm --prefix docs-site run build` passed after local `npm --prefix docs-site install`. |
| E-007 | command | TARGET:. | `git diff --check` passed. |
| E-008 | docs | TARGET:docs-site/docs/agent/plugin-contribution-contract.md | 新增技术文档解释真实 API、边界和安全默认值。 |
| E-009 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionManifest.java | Manifest 拒绝重复 `type:name` contribution，避免宿主展示歧义。 |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。剩余风险为 provider 真正绑定、CLI 安装/市场、远端 runner 产品化等后续任务范围。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| provider-style contribution 只是 metadata，不提供真实 provider runtime | coordinator | yes | Sandbox routing / Remote runner provider task |
| 当前 worktree 缺少 `engineering-standard.md` | coordinator | yes | 标准同步或 dev rebase 时处理，不阻塞本任务 |
| npm audit 报既有依赖风险 | docs-site owner | yes | 依赖升级任务单独处理 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 实现和验证材料准备后提交给人工/PR review。 | 人工确认或 PR 合并。 |
| Missing Materials | no | 必需 task 文件已补齐。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | 本任务无可推广 lesson candidate。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认和 closeout。 | review-confirm / task-complete 后退出。 |
| Soft-deleted / Superseded | no | 当前任务有效。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新 `task_plan.md`
- Progress：对应 `progress.md` 的 16:36 / 16:44 / 16:49 记录
- 发现记录：已更新 `findings.md`
- Regression SSoT：本轮未新增固定 gate；沿用 extension-api / ask-user / agent extension / docs build targeted gates
- Lessons：checked-none: task-specific API slice
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

当前信心来自公共 API 测试、官方插件示例测试、agent extension bridge targeted regression、docs-site build 和 self adversarial review。PR 后仍需远端 CI/维护者审查补充最终合并证据。
