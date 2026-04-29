# MCP 总览

在 AI4J 里，`MCP` 不是“远程 Tool 的别名”，也不是 `Tools` 章节下的一个补充选项。它是一整套独立的协议接入面，覆盖了：

- 外部服务如何被连接
- 服务能力如何被缓存、刷新和投影
- 哪些服务在本次请求里可见
- 服务端如何把本地 Java 能力发布成标准 MCP server

这一章真正对应的源码主线是：

- `mcp/client/McpClient.java`
- `mcp/gateway/McpGateway.java`
- `mcp/transport/*`
- `mcp/server/McpServerEngine.java`
- `tool/ToolUtil.java`
- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/response/entity/ResponseRequest.java`

## 1. AI4J 里 MCP 的真实位置

MCP 属于 `Core SDK` 基座层，而不是某个上层 Agent 专属特性。

上层模块当然会复用它：

- `ai4j-agent` 可以把 MCP 作为外部能力面
- `ai4j-coding` 可以把 MCP 作为远程工具接入面
- CLI/TUI 可以通过运行时继续消费这些能力

但 MCP 自己先把下面几层问题解决了：

1. `transport` 怎么连
2. `client` 怎么握手、缓存和重连
3. `gateway` 怎么管理多服务
4. `tool / resource / prompt` 怎么进入协议能力面
5. 本次请求到底开放哪些服务

如果把它只写成“模型可调用外部工具”，会把最关键的运行时层全部抹掉。

## 2. 一条真实请求链到底怎么走

在 AI4J 当前实现里，一次 MCP 能力进入模型调用链，大致是下面这条顺序：

1. `McpGateway.initialize(...)` 从 `mcp-servers-config.json` 或 `McpConfigSource` 加载服务配置。
2. `McpGateway` 为每个服务创建 `McpClient`，并调用 `client.connect()`。
3. `McpClient.connect()` 先启动 transport，再发送 `initialize` 请求，然后发送 `notifications/initialized`。
4. 请求对象通过 `ChatCompletion.mcpServices(...)` 或 `ResponseRequest.mcpServices(...)` 声明本次允许使用哪些 `serviceId`。
5. provider 适配层调用 `ToolUtil.getAllTools(functions, mcpServices)`。
6. `ToolUtil` 再通过 `McpGateway.getAvailableTools(...)` 或 `getUserAvailableTools(...)` 把这些服务的 MCP tool 投影成 provider `Tool` schema。
7. 模型返回 tool call 后，执行层最终走 `ToolUtil.invoke(...)`，再转发到本地函数、本地 MCP tool，或 `McpGateway.callTool(...)`。

也就是说，MCP 在 AI4J 里不是“请求里带个 URL 就能用了”，而是一个完整的宿主运行链。

## 3. 它为什么和本地 Tools 平级

`Tools` 与 `MCP` 在最终 provider 侧都会长成 `tool schema`，但来源完全不同。

本地 `Tools`：

- 直接来自 JVM 内部函数声明
- 由 `@FunctionCall` 或本地 MCP 扫描生成
- 执行点在当前进程内

`MCP`：

- 来自独立服务的能力目录
- 先经过协议握手、缓存和 transport
- 最后才被投影成 provider 可见的 tool schema

所以 “最终都长得像 tool” 只是表现层相似，不代表它们是同一种能力。

## 4. 这一章最重要的几个对象

### `McpClient`

负责单服务连接生命周期。它会：

- 启动具体 transport
- 发送 `initialize`
- 维护 `availableTools / availableResources / availablePrompts` 缓存
- 处理 `notifications/*/list_changed` 后的缓存失效
- 在断连时清理 pending request，并可按 5 秒延迟调度重连

### `McpGateway`

负责多服务管理。它会：

- 保存 `serviceId -> McpClient`
- 刷新 `tool -> client` 映射
- 提供全局实例 `getInstance()`
- 支持配置源重绑和动态增删服务
- 区分全局服务与用户级服务

### `ToolUtil`

负责把 MCP 工具真正并入请求与执行链。它会：

- 在 `getAllTools(...)` 里把本地函数和 MCP tools 合并
- 从 `mcpServices` 判断本次请求使用哪些服务
- 在执行时根据函数名决定是本地函数、本地 MCP tool，还是远端 MCP tool

### `McpServerEngine`

负责服务端协议处理。它当前直接处理：

- `initialize`
- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`
- `ping`

这意味着 AI4J 的 MCP server 不是只做 tool call。

## 5. 当前实现里必须记住的边界

### 边界一：gateway 管“已接入”，请求白名单管“本次可见”

`McpGateway` 里连着哪些服务，不等于模型都能看见。请求侧仍然要显式传：

- `ChatCompletion.mcpServices(...)`
- `ResponseRequest.mcpServices(...)`

### 边界二：MCP capability 不等于 tool projection

协议上有：

- Tool
- Resource
- Prompt

其中只有 Tool 一定会直接进入 provider 的 tool 调用链。Resource 和 Prompt 有独立协议语义，不应该被文档混写成“另外两种 Tool”。

### 边界三：远端工具命名当前不会自动带服务前缀

`McpGatewayToolRegistry` 目前把全局工具映射成：

- `toolName -> clientId`

而 `McpToolConversionSupport.convertToOpenAiTool(...)` 也直接保留远端 `tool.getName()`。

这意味着如果两个远端服务都暴露同名工具，最后的映射键会发生覆盖。AI4J 已经为本地 `@McpService + @McpTool` 做了 `serviceName_toolName` 规范化，但远端多服务重名工具当前并没有同等隔离。

这不是文档概念问题，而是设计和接入时必须知道的实际行为。

## 6. 什么时候应该优先想到 MCP

以下情况更适合直接进入 MCP 心智：

- 能力不在本地 JVM 进程里
- 需要独立 transport 和连接生命周期
- 会接多个外部能力服务
- 想同时暴露 `tool / resource / prompt`
- 希望自己的 Java 能力以标准 MCP server 形式发布给别的客户端

反过来，如果只是本地函数调用，优先看 `Tools`；如果只是给模型一套方法论和 SOP，优先看 `Skills`。

## 7. 阅读这一章的正确顺序

推荐按下面顺序读：

1. [Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
2. [Client Integration](/docs/core-sdk/mcp/client-integration)
3. [Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
4. [Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)
5. [Transport Types](/docs/core-sdk/mcp/transport-types)
6. [Protocol Capabilities](/docs/core-sdk/mcp/protocol-capabilities)
7. [Publish Your MCP Server](/docs/core-sdk/mcp/publish-your-mcp-server)

## 8. 这一页的结论

> AI4J 里的 MCP 是一条完整的协议接入链：`transport -> client -> gateway -> request whitelist -> tool projection -> execution`。它和本地 Tool 最终都能进入模型 tool 调用，但来源、缓存、错误面、权限边界和多服务治理完全不是一回事。
