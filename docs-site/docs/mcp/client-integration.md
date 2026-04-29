---
sidebar_position: 4
---

# MCP Client 接入（单服务模式）

这一页只讲“单服务模式”。

也就是说，你当前的问题是：

- 先连上一个 MCP server
- 先把会话初始化成功
- 先跑通 tool / resource / prompt 三类高层 API

如果你已经要接多个服务或做用户隔离，就应该去读 gateway，而不是继续停在这页。

## 1. `McpClient` 的真实生命周期

`McpClient` 不是创建完就能直接 `callTool()`。

它的真实生命周期是：

1. 构造 transport
2. new `McpClient(...)`
3. `connect()`
4. 初始化握手成功
5. 读取 tools / resources / prompts
6. 调用能力
7. `disconnect()`

中间少任何一步，都不能算一个稳定 client。

## 2. `connect()` 到底做了什么

从源码看，`connect()` 至少做了这几件事：

1. `transport.start()`，超时 `30s`
2. 发 `initialize`
3. 协议版本固定为 `2025-03-26`
4. 上报 client capabilities
5. 发送 `notifications/initialized`
6. 若 transport 需要心跳，则启动 heartbeat

这意味着“网络已连通”和“client 已初始化”不是一回事。

也因此 `callTool()` 会先检查：

- `isConnected()`
- `isInitialized()`

## 3. `McpClient` 声明了哪些 client capabilities

初始化时，client 默认声明的能力至少包括：

- `sampling`
- `roots`
- `tools`
- `resources`
- `prompts`

其中：

- `roots.listChanged = true`
- `tools.listChanged = true`
- `resources.listChanged = true`
- `resources.subscribe = true`
- `prompts.listChanged = true`

这说明 AI4J 的 client 不是只为了 `tools/call`，而是同时把 Resource / Prompt 也当成一等能力。

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

### SSE

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

这两条路径的区别，不在 `McpClient`，而在 transport 连接模型。

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

## 9. `callTool()` 的失败语义要分两层看

这里是一个容易写错的点。

### 9.1 连接态失败

如果 client 没连接或没初始化，`callTool()` 会直接返回 exceptional future。

### 9.2 协议层失败

如果服务端返回 MCP error response，当前实现通常会把错误压成字符串返回，而不是一定抛异常。

因此调用方不要只抓异常，也要检查返回内容是否是失败文本。

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
- 初始化握手有没有真正完成

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

- [MCP Gateway 管理](/docs/mcp/gateway-management)
- [Tool 暴露语义与安全边界](/docs/mcp/tool-exposure-semantics)
