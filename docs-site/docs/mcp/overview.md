---
sidebar_position: 1
---

# MCP 总览

在 AI4J 里，MCP 不是“一个能接工具的选项”，而是一整套独立的能力连接层。

如果只从概念上看，它当然是 Model Context Protocol；但从这套代码的组织方式看，它至少同时承担了 4 类职责：

- 连接单个远端或本地 MCP server
- 管理多个 MCP client 和工具映射
- 把 Java 侧能力发布成 MCP server
- 把 MCP 能力桥接回 Agent / Tool 体系

所以这一章真正讨论的不是“什么是 MCP”，而是“AI4J 怎么把 MCP 做成可连接、可治理、可发布、可复用的工程层”。

## 1. 先把 MCP 放回整体架构里

MCP 在仓库里的位置，不属于某个工具实现细节，而属于 AI 能力的连接层。

如果按职责拆分，可以这样理解：

- `ai-basics`
  解决模型服务、请求协议、service registry
- `mcp`
  解决标准化外部能力接入与发布
- `agent`
  解决推理循环、handoff、team、memory
- `coding-agent`
  解决工程化宿主、workspace、回归和审批链

一句话说：

- `MCP` 负责“能力怎么连进来 / 发出去”
- `Agent` 负责“模型怎么用这些能力”

## 2. 这条子系统其实分成 4 个平面

理解 AI4J 的 MCP，先不要盯着单个类名，先把 4 个平面分开。

### 2.1 单服务 client 平面

核心对象：

- `McpClient`
- `McpTransport`

它负责连接一个具体 MCP server，并提供高层调用：

- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`

### 2.2 transport 平面

核心对象：

- `TransportConfig`
- `McpTransportFactory`
- `StdioTransport`
- `SseTransport`
- `StreamableHttpTransport`

它负责把“连接一个 MCP server”这件事具体化成：

- 本地进程
- SSE 长连接
- Streamable HTTP

### 2.3 gateway 治理平面

核心对象：

- `McpGateway`
- `McpGatewayToolRegistry`
- `McpGatewayConfigSourceBinding`
- `McpGatewayKeySupport`

它负责多服务管理，而不只是“多建几个 client”。

这里的重点能力包括：

- 全局和用户级客户端隔离
- 工具名到 client 的映射
- 动态配置源加载
- 统一工具调用入口

### 2.4 server 发布平面

核心入口：

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpServerFactory`

它解决的是反方向的问题：

- 不是“连别人的 MCP”
- 而是“把自己的 Java 能力发布成 MCP”

## 3. `McpClient` 不是一个薄 HTTP wrapper

看源码时最容易低估 `McpClient`。它不只是把消息发出去。

### 3.1 `connect()` 做了完整握手

`connect()` 当前会：

1. 启动 transport，超时 `30s`
2. 发送 `initialize`
3. 协议版本固定为 `2025-03-26`
4. 声明 client capabilities
5. 再发送 `notifications/initialized`

也就是说，`connect()` 不是“底层 socket 通了”就算完成，而是 MCP 初始化真正走完才算完成。

### 3.2 它默认会做缓存

`McpClient` 会缓存：

- `availableTools`
- `availableResources`
- `availablePrompts`

这解释了为什么它更像一个会话级 client，而不是每次都完全无状态请求。

### 3.3 它默认会做心跳和重连

当前实现里：

- 网络型 transport 通常 `needsHeartbeat() = true`
- client 会启动一个 10 分钟一次的低频 heartbeat 检查
- 默认 `autoReconnect = true`
- 断线后会在 5 秒后尝试重连

这说明 AI4J 里的 MCP client 已经带有基础连接治理，而不是把稳定性全部甩给上层调用方。

## 4. Gateway 才是“多服务 MCP”的真实中心

很多系统说自己支持多个 MCP，实际只是维护了一个 client list。AI4J 这套实现不是这样。

`McpGateway` 的意义在于：它把多服务管理提升成一个独立运行时。

### 4.1 它管理的不只是 client，还管理 key 规则

当前源码里，至少有两类 key：

- 全局 client：`serviceId`
- 用户 client：`user_{userId}_service_{serviceId}`

工具映射也有两类 key：

- 全局工具：`toolName`
- 用户工具：`user_{userId}_tool_{toolName}`

这说明用户隔离不是靠额外备注，而是直接进了 key 规则。

### 4.2 它有独立 tool registry

`McpGatewayToolRegistry` 会：

- 向所有已连接 client 拉工具清单
- 建立 tool -> client 映射
- 缓存可用工具列表

这让“工具来自哪个 MCP 服务”不再只能靠人工记忆。

### 4.3 它支持配置源而不只是配置文件

`McpGateway` 既能从默认 `mcp-servers-config.json` 初始化，也支持：

- `McpConfigSource`

这意味着它可以进一步演进成动态配置管理，而不是只适合本地静态 JSON。

## 5. Tool 暴露层不是“全开”，而是显式收敛

这是这一章最重要的安全边界之一。

`ToolUtil` 当前同时桥接了：

- built-in tool
- 传统 `Function` 工具
- 本地 MCP 工具
- 远程 MCP 服务

但暴露语义不是“一股脑全给模型”。

### 5.1 `getAllTools(functionList, mcpServerIds)` 的真实语义

它只会合并：

- 你显式传入的 function 列表
- 你显式传入的 MCP server 列表

也就是说，普通 Agent 场景下不是默认全量暴露所有本地 MCP 工具。

### 5.2 `getLocalMcpTools()` 是另一条语义

这个方法会返回扫描到的本地 `@McpService` / `@McpTool` 能力，更适合服务发布或本地暴露场景。

所以：

- `getAllTools(...)` 更偏消费侧白名单
- `getLocalMcpTools()` 更偏本地 MCP 能力枚举

### 5.3 调用优先级也不是一回事

`ToolUtil.invoke(...)` 当前优先级大致是：

1. built-in tool
2. 用户级 MCP 工具
3. 本地 MCP 工具
4. 传统 Function 工具
5. 全局 MCP gateway 工具

这说明“暴露面选择”和“真正调用时的优先级”是两套逻辑，不能混写成一句话。

## 6. 这一章最适合哪几类人

### 6.1 你只想接一个第三方 MCP

重点看：

- `McpClient`
- transport

### 6.2 你要接多个 MCP 并做治理

重点看：

- `McpGateway`
- config source
- tool registry

### 6.3 你要把自己的 Java 能力发布出去

重点看：

- MCP annotations
- server factory

### 6.4 你在做 Agent / Coding Agent 接入

重点看：

- Tool 暴露边界
- Gateway 与 ToolUtil 的桥接语义

## 7. 推荐阅读顺序

1. [MCP 使用路径与场景选择](/docs/mcp/use-cases-and-paths)
2. [MCP 传输类型详解](/docs/mcp/transport-types)
3. [MCP Client 接入（单服务模式）](/docs/mcp/client-integration)
4. [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)
5. [MCP Gateway 管理](/docs/mcp/gateway-management)
6. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)

如果你只记一句话：

AI4J 里的 MCP 不是“工具列表协议”，而是一个覆盖 client、transport、gateway、server 四个平面的能力连接子系统。
