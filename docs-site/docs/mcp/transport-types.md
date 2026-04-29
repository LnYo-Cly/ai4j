---
sidebar_position: 3
---

# MCP 传输类型详解（STDIO / SSE / Streamable HTTP）

transport 在 MCP 里不是“连通细节”，而是会直接改变部署形态、稳定性模型和治理方式的第一层选择。

在 AI4J 里，这层抽象的核心是：

- `McpTransport`
- `TransportConfig`
- `McpTransportFactory`

## 1. 先看 `McpTransport` 抽象到底承诺了什么

所有 transport 都要实现：

- `start()`
- `stop()`
- `sendMessage(...)`
- `setMessageHandler(...)`
- `isConnected()`
- `needsHeartbeat()`
- `getTransportType()`

这意味着 transport 不只是连接对象，而是一个完整的消息层适配器。

其中最关键的一个方法是：

- `needsHeartbeat()`

因为它直接决定 `McpClient` 是否会启动应用层 heartbeat 检查。

## 2. `TransportConfig` 是统一配置平面

AI4J 没把各种 transport 的参数各写一套，而是统一收进了 `TransportConfig`。

### 2.1 通用字段

- `type`
- `connectTimeout`
- `readTimeout`
- `writeTimeout`
- `enableRetry`
- `maxRetries`
- `retryDelay`
- `enableHeartbeat`
- `heartbeatInterval`

### 2.2 HTTP / SSE 字段

- `url`
- `headers`

### 2.3 STDIO 字段

- `command`
- `args`
- `env`

### 2.4 默认值

当前默认值包括：

- `connectTimeout = 30`
- `readTimeout = 60`
- `writeTimeout = 60`
- `enableRetry = true`
- `maxRetries = 3`
- `retryDelay = 1000ms`
- `enableHeartbeat = false`
- `heartbeatInterval = 30000ms`

因此 transport 选型不仅决定协议，还决定默认连接行为。

## 3. STDIO：本地进程模式

### 3.1 它真正适合什么

STDIO 最适合：

- 本地子进程工具
- CLI / IDE 宿主拉起外部 MCP server
- 不想额外开放服务端端口的场景

### 3.2 它的优点

- 部署简单
- 不需要远程网络暴露
- 进程边界清晰

### 3.3 它的代价

- 宿主要负责进程生命周期
- 子进程 stdout / stderr / 环境变量问题会直接影响可用性
- 扩容和远程治理不如 HTTP 型 transport 自然

### 3.4 AI4J 里的典型写法

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);
```

### 3.5 什么时候优先选它

- 本地开发
- 单机工具链
- Coding / IDE 宿主环境

## 4. SSE：长连接事件流模式

### 4.1 它真正适合什么

SSE 适合：

- 对方 MCP server 已经按 SSE 方式提供服务
- 你接受长连接模型
- 你所在环境对 SSE 代理和超时配置比较友好

### 4.2 它的优点

- 长连接事件模型直观
- 适合服务端主动推送消息

### 4.3 它的代价

- 更依赖网络和代理稳定性
- 断线重连策略更重要
- 网关、LB、反向代理可能成为问题源

### 4.4 AI4J 里的典型写法

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

### 4.5 在 AI4J 里的额外含义

网络型 transport 通常会让：

- `needsHeartbeat() = true`

因此 `McpClient` 会启动应用层 heartbeat 检查。

## 5. Streamable HTTP：服务化优先模式

### 5.1 它真正适合什么

如果你想把 MCP 当成一个可治理、可部署、可网关化的服务接口，Streamable HTTP 往往是最自然的选择。

适合：

- 内网 / 公网服务发布
- 平台侧统一接入
- 云原生部署

### 5.2 它的优点

- HTTP 基础设施成熟
- 更容易接鉴权、网关、观测、流量治理
- 部署习惯更接近普通服务

### 5.3 它的代价

- 你需要更认真地处理认证头、超时和网络问题
- “只是本地跑一个脚本”时，它反而显得更重

### 5.4 AI4J 里的典型写法

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer xxx"));

McpTransport transport = new StreamableHttpTransport(config);
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

### 5.5 为什么它通常是生产优先选项

因为它最容易和：

- 认证
- 反向代理
- 网关治理
- 服务发现

这些平台能力对齐。

## 6. `McpTransportFactory` 为什么重要

如果每个上层模块都自己 new 具体 transport，transport 选择就会散落在业务代码里。

AI4J 用：

- `McpTransportFactory.createTransport(type, config)`

来统一收口创建逻辑。

支持的类型别名至少包括：

- `stdio`
- `sse`
- `streamable_http`
- `http`

其中：

- `http` 会归一化到 `streamable_http`

这让配置层可以更自由，但运行时仍有统一归一化语义。

## 7. `TransportConfig.fromServerInfo(...)` 的意义

当你走配置驱动或 gateway 模式时，通常不会手写 transport 创建代码。

`TransportConfig.fromServerInfo(...)` 会根据 `McpServerConfig.McpServerInfo` 自动归一化成：

- stdio 配置
- sse 配置
- streamable http 配置

这说明 transport 选型已经可以被提升到配置层，而不是只能写死在 Java 代码里。

## 8. 选型时最应该看的不是“哪个高级”，而是“谁负责什么”

### 当宿主负责进程生命周期

优先：

- STDIO

### 当平台负责服务治理

优先：

- Streamable HTTP

### 当对方已经提供 SSE 能力

优先：

- SSE

transport 选型本质上是在选“连接治理责任落在哪一层”。

## 9. 常见问题

### 9.1 `connect()` 卡住

常见原因：

- URL 不通
- 子进程没启动
- initialize 没走完

### 9.2 SSE 经常断

优先检查：

- 代理层超时
- 长连接支持
- 自动重连是否启用

### 9.3 STDIO 启动失败

优先检查：

- `command`
- `args`
- `env`
- 子进程本地运行是否本来就失败

## 10. 最后的选型原则

如果你只想先连通，选最贴近目标部署形态的 transport，不要为了“以后可能会扩展”而过早上复杂模式。

通常：

- 本地工具链：STDIO
- 已有 SSE 服务：SSE
- 平台服务化接入：Streamable HTTP
