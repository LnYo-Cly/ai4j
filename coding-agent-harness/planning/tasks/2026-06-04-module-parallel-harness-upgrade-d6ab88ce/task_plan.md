# module parallel harness upgrade

Task Contract: harness-task/v1
Task Kind: standard-task
Task Preset: standard-task
Preset Version: 1
Evidence Bundle: coding-agent-harness/planning/tasks/2026-06-04-module-parallel-harness-upgrade-d6ab88ce/artifacts/preset/2026-06-04T08-54-34-188Z
Task Package Index: required

## 目标

启用 module-parallel harness，并把 `ai4j-sdk` 的 Maven 模块、docs site 和 FlowGram web demo surface 登记成可审计的模块边界。

## 范围

- 做什么：运行 `add-capability module-parallel`；注册 10 个 module key；生成并定制模块 `brief.md` / `module_plan.md`；验证 `status` 和 `module list`。
- 不做什么：不修改 Java、前端或 docs-site 业务代码；不启用 `subagent-worker`；不创建 worktree；不做 regression baseline/live-provider 分层。
- 主要风险：module dependency 只记录稳定的一阶协调关系，不能替代完整 Maven dependency graph；后续真实模块任务仍需按任务范围确认共享文件锁。

## 预算选择

选择预算：complex

选择理由：本任务改变 harness capability、全局 registry、10 个模块的运行合同和 worker prompt 边界，属于 monorepo 级治理升级，需要完整任务包和 review 证据。

## 上下文包（Context Packet）

| ID | 类型 | 路径 | 为什么需要 | 使用者 |
| --- | --- | --- | --- | --- |
| C-001 | public-doc | TARGET:AGENTS.md | 定义 8 个 Maven 模块和 docs/demo surface 的 repo 范围。 | coordinator |
| C-002 | code | TARGET:pom.xml | Maven reactor 和模块边界的源事实。 | coordinator |
| C-003 | harness | TARGET:coding-agent-harness/harness.yaml | capability 与 modules.items 的 SSoT。 | coordinator, reviewer |
| C-004 | harness | TARGET:coding-agent-harness/planning/modules/Module-Registry.md | 生成的模块只读视图。 | coordinator, reviewer, worker |
| C-005 | harness | TARGET:coding-agent-harness/planning/modules/Session-Prompt-Pack.md | 后续模块 worker 会话的全局提示词包。 | coordinator, worker |

## 步骤

1. 诊断当前 harness capability 和空 module registry。
2. dry-run 并执行 `add-capability module-parallel --locale zh-CN`。
3. 注册模块：`core-sdk`、`agent-runtime`、`coding-runtime`、`cli-host`、`spring-starter`、`flowgram-starter`、`flowgram-demo`、`bom`、`docs-site`、`flowgram-webapp-demo`。
4. 用项目事实替换生成的模块 brief/plan 和 session prompt pack。
5. 运行 `status --json`、`module list --json` 和占位扫描。
6. 提交 review 包并等待人工确认。

## 验收标准

- [x] `module-parallel` 出现在 `harness.yaml` capability 列表中。
- [x] `module list --json` 返回 10 个模块，scope 和依赖符合 repo 事实。
- [x] 模块 `brief.md` / `module_plan.md` 不再保留通用模板占位。
- [x] `Session-Prompt-Pack.md` 已针对 `ai4j-sdk` 模块 worker 协作边界定制。
- [x] `npx --yes coding-agent-harness status --json .` 通过且无 warning。

## 工作树（Worktree）

- 路径：same checkout
- 分支：`main`
- Worker owner：不适用
- Worker handoff commit required：不适用
- Coordinator integration branch：不适用
- 未使用 worktree 的原因：本轮只改 harness governance 文档和 manifest，由 coordinator 顺序执行最稳妥。

## 长程任务判定

- 是否属于长程任务：否
- 若是，合同文件：不适用
- 连续执行权限：不适用
- Stop Condition 摘要：需要人工确认、启用可写 worker、变更业务代码或进入回归分层任务时停止。

## 审查判定

- 是否需要对抗性审查：否
- 若是，报告文件：不适用
- Reviewer：self，然后 human dashboard confirmation
- No-finding 要求：self review 无 P0/P1/P2 阻塞发现；人工确认另行完成。

## 关联

- 相关 Regression Gate：无新增固定 regression gate；本轮为 harness governance 变更。
- 审查报告：`review.md`
- Generated Ledger：由 lifecycle CLI / `harness governance rebuild` 重建。
- 前置任务：`2026-06-04-first-wave-project-upgrades-93da333c`

## 模块关联（启用模块并行时填写）

- Module：base / global harness governance
- Step：MP-01
- Module Plan：`coding-agent-harness/planning/modules/Module-Registry.md`

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync owner：coordinator
- Global sync status：synced
- Registry update needed：10 modules registered and module contracts updated
- Harness Ledger update needed：已由 lifecycle CLI 同步 generated Harness Ledger
- Closeout / Regression update needed：`walkthrough.md`；Regression SSoT 无新增 gate

## Standard Task Preset

This task was created through the declarative `standard-task` preset.

| Field | Value |
| --- | --- |
| Preset Title | module parallel harness upgrade |
