# module parallel harness upgrade

## Task ID

`2026-06-04-module-parallel-harness-upgrade-d6ab88ce`

## 创建日期

2026-06-04

## 一句话结果

为 `ai4j-sdk` 启用 `module-parallel` harness，并把 8 个 Maven 模块加 2 个 docs/demo surface 登记为可独立分工的模块。

## 完成后能得到什么

本任务完成后，下一轮 agent 能按模块 key 路由任务，而不是把整个 monorepo 当成单一代码区。`harness.yaml` 持有模块 registry，`Module-Registry.md` 提供只读视图，每个模块都有真实 `brief.md` 和 `module_plan.md`，说明写入范围、共享面、验证命令和交接规则。后续做代码、starter、FlowGram、CLI、docs 或 web demo 变更时，可以直接创建 module task 并降低跨模块误改风险。

## 交付物

- 可见产物：`module-parallel` capability、10 个模块登记、模块 brief/plan、模块会话提示词包。
- 修改位置：`coding-agent-harness/harness.yaml`、`coding-agent-harness/planning/modules/**`、`coding-agent-harness/governance/generated/**`。
- 验证证据：`npx --yes coding-agent-harness status --json .` 通过，`module list --json` 返回 10 个模块。

## 第一眼应该看什么

先读 `coding-agent-harness/planning/modules/Module-Registry.md`，再读目标模块的 `module_plan.md`。需要启动模块 worker 时读 `Session-Prompt-Pack.md`。

## 边界

- 范围内：启用 module-parallel、登记模块、补齐模块合同、验证 harness/module 状态、记录任务 review 包。
- 范围外：不启用 subagent-worker，不创建独立 worktree，不修改业务代码，不重构 Regression SSoT。
- 停止条件：需要人工确认、启用可写 worker、变更 shared SSoT 或继续做 regression baseline/live split 时停止并等待明确授权。

## 完成判断

- `harness.yaml` capabilities 包含 `module-parallel`。
- `module list --json` 返回 10 个模块。
- 每个模块有项目真实 `brief.md` 和 `module_plan.md`。
- Harness `status --json` 通过且无 warning。
- 当前任务材料进入 review，等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中，待 `task-phase` 和 `task-review`
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据记录到 `progress.md`，agent review submission 生成，人工确认通过 dashboard workbench 完成。

## 当前下一步

推进 `EXEC-01` 到 done，然后提交 Agent Review Submission。
