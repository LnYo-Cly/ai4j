---
sidebar_position: 1
---

# MCP 总览

MCP 在 AI4J 里不是一页协议说明，而是一整套“模型如何接外部能力”的工程体系。

这一章主要解决三件事：

- 你如何把第三方 MCP 接进自己的系统
- 你如何管理多个 MCP 服务
- 你如何把自己的 Java 能力发布成 MCP 给别人调用

---

## 1. MCP 在 AI4J 里的位置

MCP（Model Context Protocol）可以理解成模型连接外部能力的标准协议层。

它统一了：

- Tool
- Resource
- Prompt
- 初始化与通知流程

在 AI4J 的整体结构中，它和基础模型接入层、Agent 层、Coding Agent 层的关系可以简单理解为：

- `ai-basics` 解决“模型请求怎么发”
- `MCP` 解决“外部能力怎么接”
- `Agent` 解决“推理循环和编排怎么做”
- `Coding Agent` 解决“工程化宿主如何消费这些能力”

---

## 2. 这一章最适合谁

### 2.1 你只想接别人家的 MCP

比如：

- 文件系统工具
- GitHub 工具
- 浏览器工具
- 内部平台 API

你的主路径是：

1. `McpClient`
2. `Transport`
3. `McpGateway`（如果不止一个服务）

### 2.2 你想统一管理多个 MCP

比如：

- 一个项目里同时接 GitHub、浏览器、数据库和内部 API
- 希望按用户、按租户或按工作区动态启停服务

你的主路径是：

1. `McpGateway`
2. `McpConfigSource`
3. 动态 client / service 管理

### 2.3 你想把自己的能力发布给别人

比如：

- 把内部 Java 服务封装成标准 Tool
- 把业务资源暴露成 URI 资源
- 把提示模板对外参数化开放

你的主路径是：

1. `@McpService`
2. `@McpTool` / `@McpResource` / `@McpPrompt`
3. `McpServerFactory`

---

## 3. AI4J 的 MCP 模块地图

源码根路径：

`ai4j/src/main/java/io/github/lnyocly/ai4j/mcp`

- Client：`mcp.client.McpClient`
- Transport：`mcp.transport.*`
- Gateway：`mcp.gateway.McpGateway`
- Config：`mcp.config.*`
- Server：`mcp.server.*`
- 注解与适配器：`mcp.annotation.*`、`mcp.util.*Adapter`

更细一点看，各子包的职责是：

| 包 | 作用 |
| --- | --- |
| `mcp.client` | 直接连接单个 MCP server 的 client 抽象 |
| `mcp.transport` | `STDIO / SSE / Streamable HTTP` 等底层传输接线 |
| `mcp.gateway` | 多服务聚合、统一调度与工具调用入口 |
| `mcp.config` | 配置源、注册表和配置管理 |
| `mcp.server` | 将 Java 能力发布成 MCP server 的工厂与服务端实现 |
| `mcp.annotation` | `@McpService / @McpTool / @McpResource / @McpPrompt` 注解入口 |
| `mcp.util` | Prompt/Tool/Resource 适配器，把 Java 侧定义转换成协议对象 |

这意味着 AI4J 不只是“能连一个 MCP”，而是同时覆盖：

- 单服务 client 接入
- 多服务网关治理
- Java 服务端对外发布

---

## 4. 三种传输类型

- `STDIO`
  适合本地子进程 MCP 工具
- `SSE`
  适合事件流式远端服务
- `Streamable HTTP`
  适合公网/内网标准 HTTP MCP 发布

如果你只想先选一种：

- 本地工具进程：先用 `STDIO`
- 服务化发布：优先 `Streamable HTTP`

---

## 5. 工具暴露边界

这一章还有一个很重要的边界：MCP 工具不是默认“全暴露”。

当前语义是：

- `ToolUtil.getAllTools(functionList, mcpServerIds)`：只使用你显式传入的 Function / MCP 服务
- `ToolUtil.getLocalMcpTools()`：用于 MCP Server 对外暴露本地 MCP 工具

这样做的目的很明确：

- 普通 Agent 不会意外暴露全部本地 MCP 工具
- 服务端发布与客户端消费的语义分层更清楚

这里再强调一层分工：

- `MCP` 负责“工具/资源/提示如何进出协议”
- `ToolUtil` / `AgentToolRegistry` / `Coding tool runtime` 负责“这些能力在各上层如何被消费”

也就是说，MCP 是能力连接层，不直接等于 Agent runtime。

---

## 6. 推荐阅读顺序

1. [MCP 使用路径与场景选择](/docs/mcp/use-cases-and-paths)
2. [MCP 配置与网关参考](/docs/mcp/configuration-and-gateway-reference)
3. [MCP 传输类型详解](/docs/mcp/transport-types)
4. [MCP Client 接入（单服务模式）](/docs/mcp/client-integration)
5. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)
6. [MCP Gateway 管理](/docs/mcp/gateway-management)
7. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)
8. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)
9. [Memory 与 Tool 分层边界](/docs/ai-basics/memory-and-tool-boundaries)

---

## 7. 下一步

如果你现在最关心的是“我到底该看哪条线”，先看 [MCP 使用路径与场景选择](/docs/mcp/use-cases-and-paths)。
