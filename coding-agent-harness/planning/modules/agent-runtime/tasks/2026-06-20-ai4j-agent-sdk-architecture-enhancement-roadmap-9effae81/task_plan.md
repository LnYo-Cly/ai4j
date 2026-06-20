# AI4J Agent SDK architecture enhancement roadmap

Task Contract: harness-task/v1
Task Kind: module-task
Task Preset: module
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/artifacts/preset/2026-06-20T02-01-43-327Z
Task Package Index: required

## 目标

把“AI4J Agent SDK 后续如何做得更好用”沉淀成可执行、可审查、可继续拆任务的 Harness 规划：以 `ai4j-agent` 为核心，围绕 Session/Memory、Blueprint、插件生态、Sandbox/Remote Runner、Coding CLI/TUI 和 docs-site 质量建立后续路线图。

## 范围

- 做什么：记录架构分层、模块边界、任务顺序、插件与 sandbox 策略、CLI/TUI 方向、docs-site 后续要求和验证策略。
- 不做什么：不实现代码；不新增核心 Maven 模块；不引入新的 Agent 顶层命名；不触碰用户已贴过的任何 provider token；不把规划写入 legacy `docs/plans` 或 `docs/09-PLANNING/TASKS`。
- 主要风险：现有 P0/P1/P2 多个任务处于 review/handoff/PR 状态，如果不先收口会造成路线图和实际代码漂移；sandbox/runner 容易被过度设计；CLI/TUI 容易偏离 Java 维护成本。

## 预算选择

选择预算：complex

选择理由：本规划跨 `ai4j-agent`、`ai4j-extension-api`、`ai4j-coding`、`ai4j-cli`、`docs-site`，同时涉及插件生态、sandbox、远端 runner、终端交互和后续任务队列，需要完整上下文、路线图、风险记录和审查材料。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | repo-guidance | TARGET:AGENTS.md | 仓库模块边界、Harness 流程和新任务位置的最高约束 | coordinator / reviewer |
| C-002 | module-doc | TARGET:AGENT.md | `ai4j-agent` 当前 AgentRuntime、Memory、Tool、Workflow、Trace、Subagent 设计 | coordinator / reviewer |
| C-003 | reference | TARGET:docs/11-REFERENCE/engineering-standard.md | 模块所有权、Java 8、插件和安全边界 | coordinator / reviewer |
| C-004 | reference | TARGET:docs/11-REFERENCE/execution-workflow-standard.md | 非平凡任务必须使用 Harness task package 和验证记录 | coordinator |
| C-005 | reference | TARGET:docs/11-REFERENCE/worktree-standard.md | 后续实现任务必须用 worktree 隔离 | coordinator / worker |
| C-006 | reference | TARGET:docs/11-REFERENCE/testing-standard.md | 后续每个模块的 targeted regression 入口 | coordinator / reviewer |
| C-007 | module-plan | TARGET:coding-agent-harness/planning/modules/agent-runtime/module_plan.md | 当前 P0/P1/P2 任务队列和依赖状态 | coordinator |
| C-008 | docs-roadmap | TARGET:docs-site/docs/agent/sdk-roadmap.md | 已存在 Agent SDK roadmap，避免重复和漂移 | coordinator / reviewer |
| C-009 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-architecture-enhancement-plan.md | 本任务沉淀的完整规划正文 | coordinator / reviewer / worker |
| C-010 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/final-agent-sdk-enhancement-summary.md | 用户最终确认后的可执行摘要，便于后续实现任务快速读取 | coordinator / reviewer / worker |
| C-011 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-enhancement-master-plan-2026-06-20.md | 当前实施总计划，按 R0/P0-P7 和推荐任务顺序组织后续实现队列 | coordinator / reviewer / worker |
| C-012 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-complete-enhancement-task-plan-2026-06-20.md | 本轮完整讨论规划记录，作为后续实现任务的首读材料 | coordinator / reviewer / worker |
| C-013 | task-reference | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-20-ai4j-agent-sdk-architecture-enhancement-roadmap-9effae81/references/agent-sdk-cloud-runner-cli-product-plan-2026-06-21.md | 本轮补充规划：把云端 Agent Runner、Sandbox、插件生态、CLI/TUI、安装分发和 docs-site 任务队列收敛为下一轮实现入口 | coordinator / reviewer / worker |

## 步骤

1. 诊断现有 harness、模块注册和 agent roadmap，确认本任务挂在 `agent-runtime` 下。
2. 记录核心判断：不新增核心 Maven 拆分；`ai4j-agent` 是 Agent SDK 核心；插件生态走 `ai4j-extension-api` + runtime hooks；sandbox 作为可选 SPI；CLI/TUI 在 `ai4j-cli`。
3. 写入完整规划 reference，覆盖 Session/Memory、Blueprint、插件、Sandbox/Remote Runner、Coding CLI/TUI、Harness 关系和任务队列。
4. 写入用户最终确认后的摘要版 reference，明确 memory/compact 参考优秀公开设计但不照搬泄露源码、支持 YAML declarative agent、插件生态和 sandbox/runner 的落地边界。
5. 更新 task-local brief、plan、strategy、findings、visual map、progress、review、walkthrough，使下一轮 agent 可直接接续。
6. 追加完整任务规划记录，覆盖产品定位、模块边界、Session/Memory/Compact、YAML Blueprint、插件生态、Sandbox/Remote Runner、CLI/TUI、docs-site 和实施队列。
7. 追加云端 Agent Runner / Sandbox / Coding Agent CLI 产品化补充规划，明确运行形态、隔离策略、插件贡献点、安装分发和任务队列。
8. 运行 `git diff --check` 和 `npx --yes coding-agent-harness status --json .`，确认规划材料无模板残留。
9. 提交 Harness 规划记录；后续实现任务再单独使用 worktree。

## 验收标准

- [x] 规划明确 `ai4j-agent` 不再拆出新的核心 SDK Maven。
- [x] 规划覆盖 memory/compact/session、YAML Agent、插件生态、sandbox/remote runner、coding CLI/TUI、harness 关系。
- [x] 规划明确后续任务队列和依赖顺序，先收口 P2-B 与已有任务。
- [x] 规划记录“不写不存在 API 示例”和 docs-site 质量要求。
- [x] 最终摘要单独记录到 `references/final-agent-sdk-enhancement-summary.md`，便于下一轮实现任务读取。
- [x] 实施总计划单独记录到 `references/agent-sdk-enhancement-master-plan-2026-06-20.md`，便于后续按队列切分任务。
- [x] 完整任务规划单独记录到
eferences/agent-sdk-complete-enhancement-task-plan-2026-06-20.md，便于后续 agent 从单一入口读取本轮全部设计结论。
- [x] `git diff --check` 通过。
- [x] `npx --yes coding-agent-harness status --json .` 通过或只剩已解释 residual。

## 工作树（Worktree）

- 路径：当前 checkout `G:\My_Project\java\ai4j-sdk`
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本任务只写 Harness 规划材料，不改生产代码；后续实现任务必须按模块和范围创建 dedicated worktree。

## 长程任务判定

- 是否属于长程任务：否，本任务是规划记录；后续实现队列属于长程 program，需要逐任务建 task package。
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：一旦要实现代码、改 public API、改 Maven 模块边界或接入真实 sandbox provider，必须新建实现任务并使用 worktree。

## 审查判定

- 是否需要对抗性审查：是，作为架构规划进行 self adversarial review；正式实现前建议再开 reviewer pass。
- 若是，报告文件：`review.md`
- Reviewer：self；实现任务可升级 subagent / human review
- No-finding 要求：本规划不得存在会导致模块边界错误、过度拆分或 docs 与 API 脱节的 P0/P1/P2 open finding。

## 关联

- 相关 Regression Gate：规划本身不新增固定 regression gate；后续实现按 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md` 增补。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建
- 前置任务：P0-A/P0-B/P0-C/P0-D、P1-A/P1-B/P1-C、P2-A/P2-B 相关任务；本规划不替代这些任务的 review/merge/closeout。

## 模块关联（启用模块并行时填写）

- Module：agent-runtime
- Step：AGENT-ROADMAP-01
- Module Plan：`coding-agent-harness/planning/modules/agent-runtime/module_plan.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime module plan 已由 Harness CLI 同步新增任务行；状态后续随 review/closeout 更新
- Harness Ledger update needed：本 task package 路径、review 状态和 closeout 状态由 lifecycle CLI 同步
- Closeout / Regression update needed：规划本身无需 Regression SSoT；如后续实现新增固定 gate，再同步 `docs/05-TEST-QA/`

## Module Preset

This module task was created through the `module` preset.

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |

## Module Context Entry Points

Read these module-level entry points before changing shared module behavior. Continue into narrower context only when the task surface requires it.

| Reference | Path | Why / When |
| --- | --- | --- |
| Module brief | coding-agent-harness/planning/modules/agent-runtime/brief.md | Start here for the module purpose and current scope. |
| Module plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md | Use this for module steps, active task links, and handoff state. |
| Module visual map | coding-agent-harness/planning/modules/agent-runtime/visual_map.md | Inspect when the change affects module sequencing or dependencies. |
