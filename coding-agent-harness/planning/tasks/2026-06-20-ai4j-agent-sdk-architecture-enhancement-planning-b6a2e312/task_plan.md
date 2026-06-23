# AI4J Agent SDK architecture enhancement planning

Task Contract: harness-task/v1
Task Package Index: required

## 目标

将本轮关于 `ai4j-agent` 的架构增强讨论沉淀为 Harness 任务包，形成可被后续实施任务引用的路线图。

## 范围

- 做什么：记录 `ai4j-agent` 的定位、Session/Memory/Compact/Plugin/Sandbox/Runner/Blueprint 增强方向、分阶段优先级和后续任务拆分。
- 不做什么：不修改生产 Java 代码，不新增 Maven 模块，不实现 CLI `/sandbox`，不实现 YAML loader，不接入真实沙箱。
- 主要风险：规划可能过大，后续实施必须拆成小任务；Sandbox/Runner 属于产品化远期能力，不应阻塞 P0 SDK 内核增强。

## 预算选择

选择预算：standard

选择理由：本任务是架构规划和任务材料记录，不涉及生产代码实现；但覆盖多个模块和后续路线，需要完整任务包而非 simple。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | code | TARGET:ai4j-agent/src/main/java | 当前 Agent SDK 基础能力和包结构 | coordinator / reviewer |
| C-002 | code | TARGET:ai4j-extension-api/src/main/java | 插件合同现状，决定 hook/sandbox provider 是否放入 extension contract | coordinator / reviewer |
| C-003 | code | TARGET:ai4j-coding/src/main/java | Coding tools 后续接入 sandbox 的目标层 | coordinator / reviewer |
| C-004 | code | TARGET:ai4j-cli/src/main/java | `/sandbox`、TUI、远端 runner 控制端的未来入口 | coordinator / reviewer |
| C-005 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-enhancement-plan.md | 本轮架构规划主文档 | coordinator / reviewer / future worker |

## 步骤

1. 诊断当前 `ai4j-agent`、extension、coding、cli 模块边界。
2. 将本轮讨论沉淀为 `references/ai4j-agent-sdk-enhancement-plan.md`。
3. 更新 brief、task_plan、visual_map、findings、review、lesson_candidates。
4. 运行 Harness 状态检查，记录证据。
5. 提交任务材料，供后续 human review 或 implementation task 引用。

## 验收标准

- [ ] 主规划文档存在，且覆盖 `ai4j-agent` 定位与 P0-P5 路线。
- [ ] 任务包明确本任务只做规划，不做代码实现。
- [ ] Review 文件给出 no material finding 或明确 residual。
- [ ] `npx --yes coding-agent-harness status --json .` 无 failure。

## 工作树（Worktree）

- 路径：不适用
- 分支：main
- Worker owner：coordinator
- Worker handoff commit required：不适用
- Coordinator integration branch：main
- 未使用 worktree 的原因：仅记录 Harness 规划材料，不改生产代码；同 checkout 足够。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：如果转入代码实现或新增 Maven 模块，停止并另开实施任务。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self
- No-finding 要求：本轮为规划任务，self-review 覆盖材料完整性、边界和后续 residual。

## 关联

- 相关 Regression Gate：不适用；本任务不改生产代码。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：无

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：architecture-planning
- Module Plan：TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：不适用，本任务不改变模块状态
- Harness Ledger update needed：由 lifecycle CLI 自动同步
- Closeout / Regression update needed：不适用；仅规划记录

## 规划刷新补充

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-006 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-complete-planning-refresh.md | 最新完整规划刷新稿，补充插件生态、YAML Blueprint、真实 sandbox、远端 Runner、CLI/TUI 和 Harness 轻量桥接原则 | coordinator / reviewer / future worker |
| C-007 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-execution-roadmap-and-research-gates.md | 执行级路线图与调研门禁，明确 P0-P5 拆分、source-backed research gates、docs-site 同步路线和当前实际下一步 | coordinator / reviewer / future worker |

新增验收补充：

- [ ] 下一轮实施者能从 `references/INDEX.md` 找到最新完整规划刷新稿。
- [ ] 规划明确“不把 Harness 完整内化到 CLI”，只做轻量识别和桥接。
- [ ] 规划明确“OpenAI-compatible”是通用概念，不把任何中转平台名称写成 SDK 概念。
- [ ] 规划明确 P1-C 已合并，当前下一步是 P2 Sandbox SPI，而不是重复规划或一次性实现 P0-P5。
- [ ] 执行级路线图明确 Pi/Codex/Claude/OpenCode/Java SDK/Sandbox 调研必须 source-backed，不能凭印象复刻。
- [ ] 执行级路线图明确当前仓库现实：P1-B/P1-C 已合并，下一步推进 P2 Sandbox SPI，再进入 P3/P4/P5。

## 执行级路线图补充

| Track | 状态 | 后续任务 |
| --- | --- | --- |
| R0 source-backed research | planned | Pi 插件/TUI、Codex/Claude/OpenCode CLI 模式、Spring AI/LangChain4j/AgentScope Java、Sandbox provider API。 |
| P0 Agent core | active | P0-A/P0-B/P0-C/P0-D 按 Harness 生命周期补 closeout；P1-C CLI run YAML 已合并。 |
| P1 Blueprint YAML | planned | schema/model/loader/validator -> AgentFactory -> CLI run。 |
| P2 Sandbox SPI | planned | fake provider、session binding、extension provider contribution。 |
| P3 Coding sandbox routing | planned | shell/file/git/browser/project run/test runner routing。 |
| P4 CLI/TUI | planned | JLine renderer abstraction、slash commands、provider/model/session/plugin/sandbox UX、Harness bridge。 |
| P5 Remote Runner | deferred | 先写协议合同；满足 P0-P4 后再决定是否新增 Maven module。 |

当前实际下一步：P1-C 已通过 PR #110 合并到 `origin/main`；本规划任务不应继续扩散范围，后续实现应转入 P2-A Sandbox SPI model。

## 最终集成实施规划补充

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-008 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-integrated-implementation-plan-2026-06-20.md | 当前最终实施入口，合并 ai4j-agent 增强路线、插件生态、Blueprint、Sandbox、CLI/TUI、Runner、R0 调研门禁和 P2 Sandbox SPI 下一步 | coordinator / reviewer / future worker |

新增验收补充：

- [ ] 后续实施者能从 `references/INDEX.md` 优先找到集成实施规划。
- [ ] 规划明确 P1-B/P1-C 均已合并，下一步是启动 P2-A Sandbox SPI model，而不是重复开总规划。
- [ ] 规划明确 P2 Sandbox SPI、P3 coding routing、P4 CLI/TUI、P5 Runner 的门禁关系。
- [ ] 规划明确 R0 调研门禁，后续对标 Pi / Codex / Claude Code / OpenCode / Java SDK / Sandbox provider 必须 source-backed。

当前实际下一步：启动 P2-A Sandbox SPI model 任务；P1-C 已通过 PR #110 合并，merge commit `384edd11424884e308c047f7e2a4b20997e95e49`。

## 2026-06-20 最终总规划与当前状态校正补充

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-009 | report | TARGET:coding-agent-harness/planning/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-planning-b6a2e312/references/ai4j-agent-sdk-final-roadmap-and-task-plan-2026-06-20.md | 当前最终入口；把本轮讨论完整沉淀为任务级规划，并校正 root `main` / `dev` 的真实状态与后续优先级。 | coordinator / reviewer / future worker |

新增验收补充：

- [ ] 后续实施者能从 `references/INDEX.md` 第一时间找到最终总规划。
- [ ] 规划明确早期“下一步 P2-A”在当前 root `main` 上已过期：P2 Sandbox SPI、P2-B binding、P3 coding sandbox routing 已有基础提交。
- [ ] 规划明确 `dev` worktree 已包含 P4 `/sandbox status/attach/disable` metadata-only 命令，且 metadata-only attach 不会静默回退本地执行。
- [ ] 规划明确下一批可执行切片：R0 source-backed research、Agent Runtime backlog reconciliation、Remote Agent Runner SPI contract、one-command install、CLI/TUI polish、docs-site completeness。
- [ ] 规划继续保持 planning-only，不修改 Java 生产代码、不新增 Maven 模块、不提交 token。
