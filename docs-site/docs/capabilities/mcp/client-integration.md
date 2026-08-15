---
sidebar_position: 4
title: MCP Client 接入（单服务模式）
description: 聚焦单服务模式：McpClient 的真实生命周期、connect() 与 AUTO/legacy profile 的差异、缓存与心跳重连语义，以及 callTool 的失败语义和常见排障路径。
tags: [how-to]
---

# MCP Client 接入（单服务模式）

这一页只讲“单服务模式”。

也就是说，你当前的问题是：

- 先连上一个 MCP server
- 先确定它使用现代无状态 HTTP 还是 session-era 协议
- 先跑通 tool / resource / prompt 三类高层 API

如果你已经要接多个服务或做用户隔离，就应该去读 gateway，而不是继续停在这页。

## 1. `McpClient` 的真实生命周期

`McpClient` 不是创建完就能直接 `callTool()`。

它的真实生命周期是：

1. 构造 transport
2. new `McpClient(...)`
3. `connect()`
4. 按 transport/profile 完成就绪：默认 AUTO 先做有限探测；现代 HTTP 不握手；legacy transport 完成初始化握手
5. 读取 tools / resources / prompts
6. 调用能力
7. `disconnect()`

中间少任何一步，都不能算一个稳定 client。

## 2. `connect()` 到底做了什么

`connect()` 总会先执行 `transport.start()`，超时 `30s`，然后按 transport/profile 进入不同路径：

- 默认的 `StreamableHttpTransport` 使用 `AUTO`。它只先发送现代 `server/discover`；有效的现代发现结果会选择 `2026-07-28`，不发送 `initialize` 或 `notifications/initialized`。
- AUTO 只在该探测收到未识别的 HTTP `400`、`404` 或 `405` 时回退到 initialization-era Streamable HTTP，并执行 `initialize -> notifications/initialized`。认证失败、可识别的现代 JSON-RPC error、以及无效发现响应都不会触发降级。
- STDIO、显式 `type: "sse"` 的 HTTP+SSE，以及显式选择 legacy Streamable HTTP profile 的 client，保留初始化时代流程。
- 若 transport 需要心跳，client 会在准备就绪后启动 heartbeat。

因此 `isConnected()` 和 `isInitialized()` 仍是调用前检查，但现代 HTTP 的 `isInitialized()` 表示 client ready，不表示发生过协议握手。

## 3. `McpClient` capability 边界

AI4J 的高层 API 覆盖 Tool、Resource 和 Prompt。现代 HTTP 每次请求声明的 capabilities 是保守的：它不会声称支持 sampling、roots、elicitation、MRTR 或 subscriptions，直到这些多轮协议能力真正可用。

不要根据旧 session-era `initialize` 示例推断现代 peer 也支持这些可选 capability。应以目标 server 的协议 profile 和实际能力目录为准。

## 4. 最小接入示例：STDIO

如果你连接的是本地子进程 MCP server，最短路径通常是 stdio。

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);

McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String result = client.callTool("read_file", Collections.singletonMap("path", "README.md")).join();

client.disconnect().join();
```

这一条路径验证的是：

- 子进程是否能启动
- stdio transport 是否能握手
- tools/list 和 tools/call 是否能通

## 5. 最小接入示例：Streamable HTTP / SSE

如果你接的是服务化 MCP，通常会用 HTTP 或 SSE。

### Streamable HTTP

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer your-token"));

McpTransport transport = McpTransportFactory.createTransport("streamable_http", config);
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

这个示例默认使用 `AUTO`。对未知或迁移中的 Streamable HTTP peer，它只通过 `server/discover` 做有限兼容探测；它不会自动选择 HTTP+SSE。对已知的 session-handshake server，显式设置：

```java
config.withProtocolProfile(McpProtocolProfile.LEGACY_2025_03_26);
```

完整请求头、server profile 和升级路线见 [Streamable HTTP 传输](/docs/capabilities/mcp/streamable-http)。

### SSE

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

这两条路径的区别，不在 `McpClient`，而在 transport 连接模型。HTTP+SSE 是 `sse` 的显式 transport；不要把它当作 Streamable HTTP AUTO 的回退目标。

## 6. `McpClient` 提供的高层 API 不止 Tool

当前高层 API 至少覆盖：

- `getAvailableTools()` -> `tools/list`
- `callTool(name, args)` -> `tools/call`
- `getAvailableResources()` -> `resources/list`
- `readResource(uri)` -> `resources/read`
- `getAvailablePrompts()` -> `prompts/list`
- `getPrompt(name, arguments)` -> `prompts/get`

### 6.1 Resource 示例

```java
List<McpResource> resources = client.getAvailableResources().join();
McpResourceContent resource = client.readResource("file://docs/README.md").join();
```

### 6.2 Prompt 示例

```java
List<McpPrompt> prompts = client.getAvailablePrompts().join();
McpPromptResult prompt = client.getPrompt(
        "code_review_prompt",
        Collections.<String, Object>singletonMap("language", "java")
).join();
```

这说明 MCP 在 AI4J 里不是单纯“远程 function call 协议”，而是覆盖 tool / resource / prompt 三类能力。

## 7. 缓存语义要先理解

`McpClient` 会缓存：

- `availableTools`
- `availableResources`
- `availablePrompts`

这有两个直接后果：

### 7.1 好处

- 不必每次都重复 list
- 会话级调用开销更低

### 7.2 边界

断线或重连后，缓存可能失效，因此 client 在断开时会清掉这些缓存。

这也是为什么 `disconnect()` 不只是“关连接”，还会清空状态。

## 8. 心跳和自动重连不是可有可无的细节

### 8.1 心跳

若 transport `needsHeartbeat()` 返回 `true`，client 会启动低频 heartbeat 检查。

当前实现是：

- 每 10 分钟做一次 `getAvailableTools()` 检查

这更像保底存活检测，而不是高频 keepalive。

### 8.2 自动重连

`McpClient` 默认：

- `autoReconnect = true`

断线后会：

- 清缓存
- 停 heartbeat
- 停 transport
- 取消 pending requests
- 5 秒后尝试重连

这意味着它已经具备基础会话恢复能力，但不是复杂的连接池。

### 8.3 关闭自动重连

默认的 3 参构造器把 `autoReconnect` 固定为 `true`：

```java
new McpClient("demo-client", "1.0.0", transport)
// 等价于 new McpClient("demo-client", "1.0.0", transport, true)
```

如果你希望自己掌控连接生命周期（例如由上层 gateway、编排器统一调度重连，或短生命周期一次性调用），用 4 参构造器显式关闭：

```java
McpClient client = new McpClient("demo-client", "1.0.0", transport, false);
```

关闭后的行为差异：

- 断线时仍会清缓存、停 heartbeat、停 transport、取消 pending requests
- 但**不再调度重连**（日志会打印 `自动重连已禁用，跳过MCP重连`）
- 后续是否重连完全由调用方决定，通常做法是重新 `new McpClient(...)` 再 `connect()`

:::note Gateway 创建的 client 默认开启重连
`McpGateway` 经由 `McpGatewayClientFactory` 创建 client 时使用默认的 3 参构造器，即 `autoReconnect = true`。当前没有配置项把 `autoReconnect` 从配置文件注入到 gateway 创建链路（见 [配置与网关参考 - autoReconnect 字段](/docs/capabilities/mcp/configuration-and-gateway-reference)）。需要关闭时，应在 gateway 外自行构造 client 再用 `addMcpClient(...)` 接入。
:::

## 9. `callTool()` 的失败语义要分两层看

这里是一个容易写错的点。

### 9.1 连接态失败

如果 client 没连接或没初始化，`callTool()` 会直接返回 exceptional future。

### 9.2 协议层失败

如果服务端返回 MCP error response，当前实现通常会把错误压成字符串返回，而不是一定抛异常。

:::warning 协议层失败不一定抛异常
因此调用方不要只抓异常，也要检查返回内容是否是失败文本。
:::

## 10. 推荐的接入姿势

单服务模式最稳的使用方式是：

1. 构造 transport
2. `connect().join()`
3. 先 `getAvailableTools()` 看真实暴露名
4. 再 `callTool(...)`
5. finally 中 `disconnect().join()`

这样可以显著降低排障成本，因为工具名、权限、连接问题会在更靠前的步骤暴露出来。

## 11. 常见排障路径

### 11.1 `not connected or not initialized`

先检查：

- 有没有先 `connect()`
- transport/profile 是否与目标 peer 匹配；AUTO 的 `server/discover` 是否收到允许回退的响应
- 只有 legacy profile 才检查初始化握手是否真正完成；现代 HTTP 则检查请求 metadata 和 HTTP headers 是否被代理保留

### 11.2 `tool not found`

先检查：

- `getAvailableTools()` 是否能看到这个名字
- 你调用的是 MCP 暴露名，而不是自己的别名

### 11.3 `resource not found` / `prompt not found`

先检查：

- `getAvailableResources()`
- `getAvailablePrompts()`

### 11.4 HTTP 401 / 403

先检查：

- `TransportConfig.headers`
- token 是否真的打进请求

## 12. 什么时候应该离开这页

一旦你已经：

- 接了不止一个 MCP
- 需要用户级隔离
- 需要工具来源治理

就不该继续停留在单服务模式，应切到：

- [MCP Gateway 管理](/docs/capabilities/mcp/gateway-management)
- [Tool 暴露语义与安全边界](/docs/capabilities/mcp/tool-exposure-semantics)
