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
