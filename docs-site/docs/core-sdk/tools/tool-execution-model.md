# Tool Execution Model

这页专门解释工具“怎么跑”，而不只是“怎么声明”。

## 1. 基座层

在基础 `IChatService` 场景下，`Chat` 通常能提供更直接的自动工具循环体验。

## 2. Responses 层

`Responses` 更偏底层事件解析，本身不等于“自动把整套工具循环都包完”。

## 3. Agent 层

一旦进入 `Agent`，重点就从“函数声明”升级到：

- `ToolRegistry`
- `ToolExecutor`
- 路由与治理

## 4. Coding Agent 层

再往上，工具会变成 workspace-aware 的运行时能力：

- `read_file`
- `write_file`
- `apply_patch`
- `bash`
