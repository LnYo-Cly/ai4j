# 模块会话提示词包

coordinator 为已注册模块启动工作时使用这份全局提示词包。开始前用 `harness.yaml`、
生成的 `Module-Registry.md` 和模块 `module_plan.md` 中的真实信息替换占位内容。

## 上下文包

- 项目：[project]
- 模块 Key：[module]
- 任务目录：[path]
- 模块计划：[path]
- 分配的 worktree：[path]
- 分配的分支：[branch]

## 目标

[写一个具体的模块结果。]

## 写入范围

只允许编辑：

- [path]

除非 coordinator 明确分配范围，不要编辑共享 SSoT、coordinator 负责的集成文件或无关模块。

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
- 使用当前任务 `visual_map.md` 阶段表作为生命周期地图。切片结束时检查当前 gate phase；只有 `Actor` 为 `agent` 的 `Exit Command` 才由 Agent 执行。
- 需要审查时更新 `review.md`。人工审查完成必须通过本地 dashboard workbench，或由 coordinator 执行 `harness review-confirm`；存在开放 P0/P1/P2 finding 时不得确认。

## 共享同步规则

除非 coordinator 分配共享锁，worker 会话不得更新 Module Registry、Harness Ledger、
Closeout Index、Regression SSoT 或 Cadence Ledger。

## 暂停规则

如果请求需要超出已分配范围、处理无关 dirty 文件、做产品决策或改变共享合同，暂停并汇报。
