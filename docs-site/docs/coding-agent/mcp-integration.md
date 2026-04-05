---
sidebar_position: 8
---

# MCP 对接

Coding Agent 可以把外部 MCP server 接进来，作为自己的工具来源之一。

这页只讲 Coding Agent 场景下的 MCP 使用方式，不重复讲 MCP 协议本身。

---

## 1. MCP 在 Coding Agent 里的角色

在 Coding Agent 里，MCP 主要用来接外部能力，例如：

- 数据库
- 浏览器
- 搜索
- 内部平台 API

当前接入后，MCP 工具会被挂入 Coding Agent 的 `toolRegistry` 和 `toolExecutor`。

---

## 2. 配置文件

全局 MCP 配置文件：

```text
~/.ai4j/mcp.json
```

工作区启用状态保存在：

```text
<workspace>/.ai4j/workspace.json
```

其中 `enabledMcpServers` 用来声明当前仓库启用了哪些 MCP server。

---

## 3. CLI 中的 MCP 命令

当前高频命令包括：

- `/mcp`
- `/mcp add --transport <stdio|sse|http> <name> <target>`
- `/mcp enable <name>`
- `/mcp disable <name>`
- `/mcp pause <name>`
- `/mcp resume <name>`
- `/mcp retry <name>`
- `/mcp remove <name>`

可以简单理解为三层控制：

- `add/remove`：全局注册表
- `enable/disable`：工作区是否启用
- `pause/resume/retry`：当前会话里的运行时状态

---

## 4. 三种传输方式

当前支持：

- `stdio`
- `sse`
- `streamable_http`

适合场景：

- `stdio`：本地进程式 MCP server
- `sse`：事件流式远端服务
- `streamable_http`：标准 MCP HTTP endpoint

补充说明：

- CLI 当前参数仍使用 `--transport <stdio|sse|http>`
- 其中 `http` 在运行时会映射到 `streamable_http`
- 写配置文件或 ACP `mcpServers` 时，推荐直接写 `type: "streamable_http"`

---

## 5. ACP 下的 MCP

如果宿主通过 ACP 接入，还可以在 `session/new` / `session/load` 时直接传 `mcpServers`。

这适合：

- 由宿主动态注入 MCP server；
- 不依赖本地全局 `mcp.json`；
- 做宿主侧临时会话配置。

---

## 6. 推荐做法

- 长期稳定的 MCP server 放进全局 `mcp.json`
- 哪些仓库启用哪些 server，由 `workspace.json` 控制
- 当前会话的临时暂停/恢复，用 `/mcp pause|resume`
- ACP 宿主需要动态配置时，直接传 `mcpServers`

---

## 7. 和普通 MCP 文档的关系

如果你要了解：

- MCP 协议
- MCP Client/Server/Gateway
- 如何自己实现一个 MCP 服务

请继续看站点里的 [MCP](/docs/mcp/overview) 专题。
