# module parallel harness upgrade - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。本轮没有阻塞性发现。

## 研究发现

### module-parallel 触发条件

- 背景：当前 harness 只有 `core,dashboard`，但仓库是多模块 monorepo。
- 发现：项目有 8 个 Maven 模块和 2 个 docs/demo frontend surface，可独立分配 owner、范围和验证命令。
- 影响：启用 `module-parallel` 是符合 skill 选择规则的最小增量能力。
- 后续：如果要让可写 worker 并行改代码，再单独启用 `subagent-worker`。

### 批量注册命令风险

- 背景：一次 PowerShell 批量循环调用 `npx` 返回成功但只输出 Windows banner。
- 发现：只有首个 `core-sdk` 实际登记；后续改为逐条 `module register` 后可复查每个 JSON 输出和提交。
- 影响：涉及 harness lifecycle / registry 的命令应优先保留逐条可审计输出。
- 后续：无；本轮已修正。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Capability | 启用 `module-parallel` | 仓库有 2 个以上可独立演进模块且需要 owner/registry/sync 规则。 | 继续只用 core/dashboard。 | accepted |
| 模块范围 | 8 个 Maven 模块加 `docs-site`、`flowgram-webapp-demo` | docs/demo surface 也有独立构建和验证边界。 | 只登记 Maven modules。 | accepted |
| 依赖关系 | 只登记稳定一阶协调依赖 | 避免 registry 变成完整 Maven dependency graph。 | 记录所有 transitive dependency。 | accepted |
| Worker 能力 | 本轮不启用 `subagent-worker` | 未授权可写 worker，且当前只做治理文档。 | 同时启用 worker/worktree 协议。 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否启用 `subagent-worker` | 暂不启用；需要用户明确授权。 | user / coordinator | 第一次需要可写 worker 并行开发前 |
| 是否做 regression baseline/live split | 尚未实施；作为下一波升级任务。 | coordinator | 下一轮升级前 |
