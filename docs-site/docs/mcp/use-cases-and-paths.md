---
sidebar_position: 2
---

# MCP 使用路径与场景选择

很多人第一次看 MCP 文档会把三件事混在一起：

- 接别人家的 MCP
- 管多个 MCP
- 发布自己的 MCP

这页就是把这三条线彻底拆开。

---

## 1. 场景一：接入第三方 MCP

这是最常见的起点。

你手上已经有一个现成的 MCP 服务，比如：

- 文件系统
- GitHub
- 浏览器
- 数据库
- 企业内部 API

你的目标通常是：

- 连上服务
- 列出可用工具
- 调用一个工具
- 再决定是否接入 Agent / Coding Agent

### 你该看什么

1. [MCP 传输类型详解](/docs/mcp/transport-types)
2. [MCP Client 接入（单服务模式）](/docs/mcp/client-integration)
3. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)

### 你会用到哪些对象

- `McpTransport`
- `McpClient`
- `TransportConfig`

### 什么时候先别上 Gateway

如果你现在只接一个服务，先别急着上 `McpGateway`。

先把单服务连通、工具可见、调用成功这三步做完，排障会简单很多。

---

## 2. 场景二：管理多个 MCP

当你开始同时接多个服务，或者需要按用户、租户、项目维度控制 MCP 时，就不要继续手写多个 `McpClient` 了。

这时应该切到网关模式。

### 典型需求

- 一个项目里同时接 GitHub、浏览器、数据库和内部 API
- 不同用户只允许看到不同工具
- 希望动态加服务、删服务、重载配置

### 你该看什么

1. [MCP Gateway 管理](/docs/mcp/gateway-management)
2. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)
3. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)

### 你会用到哪些对象

- `McpGateway`
- `McpConfigSource`
- `McpClient`

### 网关模式最适合什么时候上

只要出现下面任一情况，就应该上网关：

- 服务数量超过 1 个
- 需要动态启停
- 需要按用户隔离
- 需要做可观测性和治理

---

## 3. 场景三：发布自己的 MCP 服务

这是“把 Java 能力产品化”的入口。

你不是去消费别人家的 MCP，而是要把自己系统里的能力发布出去，让外部 MCP Client、Agent 或 IDE 来调用。

### 典型需求

- 把内部业务方法暴露成 Tool
- 把业务数据暴露成 Resource
- 把提示模板暴露成 Prompt
- 发布成 `stdio`、`sse` 或 `streamable_http`

### 你该看什么

1. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)
2. [MCP 传输类型详解](/docs/mcp/transport-types)
3. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)

### 你会用到哪些对象

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpServerFactory`

### 最推荐的发布方式

如果你要对公网或内网稳定发布，优先 `streamable_http`。

如果你只是给本地宿主进程用，`stdio` 会更简单。

---

## 4. 一个简单判断表

| 你的问题 | 你该走哪条线 |
| --- | --- |
| 我只想连一个现成 MCP 服务 | 单服务 Client |
| 我想同时接多个 MCP 并治理它们 | Gateway |
| 我要把自己的能力开放给别人 | MCP Server |

---

## 5. 和 Agent / Coding Agent 的关系

MCP 经常会和 Agent、Coding Agent 一起出现，但不要把它们混成同一层。

推荐理解：

- MCP 解决“工具能力从哪里来”
- Agent 解决“模型如何选择和调用这些工具”
- Coding Agent 解决“宿主如何加载和管理这些工具能力”

先把 MCP 自身打通，再接 Agent / Coding Agent，路径会稳很多。

---

## 6. 推荐起步方式

如果你现在还不确定怎么开始，最稳的顺序是：

1. 先用单服务 Client 接一个现成 MCP
2. 再升级到 Gateway 管多个 MCP
3. 最后再发布自己的 MCP Server

因为这三步的工程复杂度是逐步增加的。

---

## 7. 下一步阅读

1. [MCP Client 接入（单服务模式）](/docs/mcp/client-integration)
2. [MCP Gateway 管理](/docs/mcp/gateway-management)
3. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)
