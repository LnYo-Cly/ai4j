---
sidebar_position: 1
---

# MCP 总览（概念、角色、能力边界）

你希望把 MCP 文档补全为“可直接落地”的版本，这一节先给完整地图。

## 1. MCP 是什么

MCP（Model Context Protocol）可以理解成“模型连接外部能力的标准协议层”，它把工具、资源、提示等能力统一成可发现、可调用、可扩展的接口。

在 AI4J 里，MCP 覆盖了两条主线：

1. **你作为 MCP Client**：连接第三方 MCP 服务并把工具暴露给模型
2. **你作为 MCP Server**：把本地 Java 能力发布成 MCP 给别人调用

## 2. MCP 核心对象（协议视角）

- **Tool**：可执行动作（例如查询天气、创建 Issue）
- **Resource**：可读取的数据对象（按 URI 访问）
- **Prompt**：可参数化提示模板
- **Initialize/Notification**：会话初始化与状态通知

AI4J 当前“客户端高层 API”重点封装了 Tool（`tools/list`、`tools/call`）；Server 端支持 Tool/Resource/Prompt 的标准端点。

## 3. AI4J 的 MCP 模块地图

- Client：`mcp.client.McpClient`
- Transport：`mcp.transport.*`
  - `StdioTransport`
  - `SseTransport`
  - `StreamableHttpTransport`
- Gateway：`mcp.gateway.McpGateway`
- Config：`mcp.config.*`
- Server：`mcp.server.*`
- 注解与适配器：`mcp.annotation.*` + `mcp.util.*Adapter`

## 4. 三种传输类型

- `STDIO`：本地子进程通信（常用于本地 MCP 工具进程）
- `SSE`：Server-Sent Events（双端点：SSE + message）
- `Streamable HTTP`：HTTP MCP 端点（通常 `/mcp`）

详细差异见《MCP 传输类型详解》。

## 5. 你最常用的四种接入方式

1. 直接 `McpClient + Transport` 连接单个第三方 MCP
2. `McpGateway.initialize(config)` 管理多个 MCP
3. `McpGateway.addMcpClient/addUserMcpClient` 动态增删
4. 在 Agent 里通过 `toolRegistry(functions, mcpServices)` 场景化暴露

详细步骤见《接入第三方 MCP（全部方式）》。

## 6. 暴露你自己的 MCP 服务

AI4J 支持你把本地注解能力暴露成 MCP Server：

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`

再配合 `McpServerFactory` 选择 `stdio/sse/streamable_http` 启动。

完整流程见《构建并对外发布 MCP Server》。

## 7. 工具暴露语义（与你最近调整一致）

当前工具语义：

- `ToolUtil.getAllTools(functionList, mcpServerIds)`：只使用你显式传入的 Function/MCP 服务
- `ToolUtil.getLocalMcpTools()`：用于 MCP Server 对外暴露本地 MCP 工具

这保证普通 Agent 不会意外暴露全部本地 MCP 工具。

## 8. 推荐阅读路径

1. 本页
2. 《MCP 传输类型详解》
3. 《MCP Client 接入》
4. 《接入第三方 MCP（全部方式）》
5. 《MCP Gateway 管理》
6. 《构建并对外发布 MCP Server》
7. 《Tool 暴露语义与安全边界》
8. 《MySQL 动态 MCP 服务管理》
