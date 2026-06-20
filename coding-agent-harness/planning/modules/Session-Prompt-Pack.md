# 模块会话提示词包

coordinator 为 `ai4j-sdk` 已注册模块启动 worker 或模块专属会话时使用这份全局提示词包。开始前从 `harness.yaml`、生成的 `Module-Registry.md` 和对应 `module_plan.md` 复制真实模块信息。

## 上下文包

- 项目：`ai4j-sdk`
- 模块 Key：使用 `Module-Registry.md` 中的真实 key，例如 `core-sdk`、`agent-runtime`、`flowgram-webapp-demo`
- 任务目录：使用当前 harness task path
- 模块计划：`coding-agent-harness/planning/modules/<module-key>/module_plan.md`
- 分配的 worktree：按任务计划填写；未使用 worktree 时写 same checkout
- 分配的分支：遵循 `feature/<name>`、`fix/<name>`、`docs/<name>` 或当前 coordinator 指定分支

## 目标

交付当前模块任务的明确结果，并保持写入范围、验证命令和 handoff 证据可复查。

## 写入范围

只允许编辑：

- 当前模块 `module_plan.md` 中列出的写入范围
- 当前任务计划明确授权的共享文件

除非 coordinator 明确分配共享锁，不要编辑共享 SSoT、Module Registry、Harness Ledger、Regression SSoT、Cadence Ledger 或无关模块。

## 必需输出

- 分支名
- Commit SHA
- 变更文件
- 已运行检查及结果
- 残余风险
- 需要 coordinator 同步的事项

## 审查与状态规则

- 汇报时区分 `task.state`、`lifecycleState`、`reviewStatus` 和 `closeoutStatus`。
- `done` 只表示实现步骤完成；有 closeout 证据后才是 `closed`。
- 使用当前任务 `visual_map.md` 阶段表作为生命周期地图。
- 只有 `Actor` 为 `agent` 的 `Exit Command` 才由 Agent 执行。
- 需要人工审查时必须通过本地 dashboard workbench；agent 不能代写人工 confirmation。
- 存在开放 P0/P1/P2 finding 时不得确认。

## 共享同步规则

worker 会话不得直接更新 Module Registry、Harness Ledger、Closeout Index、Regression SSoT 或 Cadence Ledger，除非 coordinator 在任务计划中写明共享锁和允许路径。

## 暂停规则

如果请求需要超出已分配范围、处理无关 dirty 文件、做产品决策、改变共享合同、接触 secrets 或运行 live-provider 验证，暂停并汇报。
