# Tools and Registry

到 Agent 层后，重点已经从“函数怎么声明”升级到：

- 暴露哪些工具
- 如何执行
- 如何治理

## 1. 核心抽象

- `AgentToolRegistry`
- `ToolExecutor`

## 2. 和基座的关系

基座层的 `ToolUtil`、`Skill`、`MCP` 都可以成为 Agent 的输入来源，但 Agent 会再加一层治理抽象。
