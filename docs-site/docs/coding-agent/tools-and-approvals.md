---
sidebar_position: 6
---

# Tools 与审批机制

Coding Agent 的核心不是只会聊天，而是能通过工具读文件、改文件、跑命令和接外部系统。

这页聚焦两个问题：

1. 当前内置了哪些工具？
2. 如果我要扩展 Tool，应该从哪里接？

---

## 1. 内置 Tool

当前内置 Tool 有四个：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

它们是由 `CodingAgentBuilder` 默认装配进去的。

---

## 2. 每个 Tool 的职责

### 2.1 `read_file`

适合：

- 读取工作区文件；
- 限定行范围读取；
- 读取技能目录里的只读 `SKILL.md`。

### 2.2 `write_file`

适合：

- 创建文件；
- 覆盖文件；
- 追加文件内容。

### 2.3 `apply_patch`

适合：

- 基于结构化 patch 修改文件；
- 精准编辑已有内容；
- 保留更清晰的差异语义。

### 2.4 `bash`

适合：

- 执行非交互命令；
- 启动长期运行进程；
- 读取日志、写 stdin、停止进程。

---

## 3. Tool 如何扩展

如果只是使用 Coding Agent，默认内置 Tool 足够。

如果要扩展，有三层入口：

### 3.1 自定义 `toolRegistry`

用来决定“暴露给模型哪些工具”。

适合：

- 接企业内部 Tool 总线；
- 只暴露特定白名单；
- 给不同工作区装不同工具集。

### 3.2 自定义 `toolExecutor`

用来决定“工具如何执行”。

适合：

- 做多租户鉴权；
- 工具调用前后统一审计；
- 对接公司内部执行网关。

注意：

- 如果你传了自定义 `toolRegistry`，也必须同时提供对应的 `toolExecutor`。

### 3.3 `ToolExecutorDecorator`

这是 Coding Agent 很实用的一层。

它允许你在不改原始 Tool 实现的前提下，为某些工具统一包一层行为，例如：

- 审批；
- 限流；
- 日志；
- 统一错误包装。

CLI/TUI 的审批装饰器和 ACP 的审批装饰器，都是这一层在工作。

---

## 4. 审批模式

启动参数：

```text
--approval <auto|safe|manual>
```

### `auto`

默认自动处理，适合本地开发和低风险场景。

### `safe`

更保守，适合你希望拦一部分高风险调用的场景。

### `manual`

每次需要确认，适合：

- 宿主侧要审计；
- 工具调用代价高；
- 需要人工兜底。

---

## 5. Tool + MCP 的关系

要区分两类工具来源：

- 内置 Tool：`bash/read_file/write_file/apply_patch`
- MCP Tool：来自外部 MCP server

它们在模型看来都能成为工具，但接入路径不同：

- 内置 Tool 由 Coding Agent 自己装配；
- MCP Tool 由 `CliMcpRuntimeManager` 接入后再挂到 tool registry / executor。

---

## 6. 推荐做法

- 内置 Tool 负责本地工作区操作；
- MCP Tool 负责外部系统访问；
- 审批逻辑统一放在 decorator 层；
- 复杂工具接入时，把 registry 和 executor 一起设计。
