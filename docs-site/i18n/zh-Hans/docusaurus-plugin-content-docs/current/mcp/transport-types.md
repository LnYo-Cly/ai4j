---
sidebar_position: 2
---

# MCP 传输类型详解（STDIO / SSE / Streamable HTTP）

这页专门回答“各类 MCP transport 有什么区别，怎么选”。

## 1. 三种 transport 对照表

| 类型 | 典型场景 | 连接方式 | AI4J 实现 |
| --- | --- | --- | --- |
| STDIO | 本地进程工具 | 启动子进程 + stdin/stdout | `StdioTransport` |
| SSE | 远程事件流服务 | `GET /sse` + `POST /message` | `SseTransport` |
| Streamable HTTP | 标准 HTTP MCP 端点 | `POST /mcp`（可返回 JSON 或 SSE） | `StreamableHttpTransport` |

## 2. STDIO

### 特点

- 适合本机或同宿主机工具
- 无需额外开放端口
- 由客户端负责拉起 MCP 进程

### AI4J 用法

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);

McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

### 选型建议

- 本地开发优先
- 对隔离要求高时，配合独立用户/容器运行进程

## 3. SSE

### 特点

- 服务端主动推送事件，适合长连接
- 通常拆为事件端点与消息端点
- 网络抖动时要处理重连

### AI4J 用法

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

### 选型建议

- 已有 SSE MCP 服务时使用
- 需要关注代理层对长连接的支持

## 4. Streamable HTTP

### 特点

- 统一 HTTP 端点（通常 `/mcp`）
- 兼容返回单次 JSON 或事件流
- 对云原生部署友好

### AI4J 用法

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer xxx"));

McpTransport transport = new StreamableHttpTransport(config);
McpClient client = new McpClient("my-client", "1.0.0", transport);
client.connect().join();
```

## 5. 用 `McpTransportFactory` 统一创建

```java
TransportConfig config = TransportConfig.stdio("npx", Arrays.asList("-y", "12306-mcp"));
McpTransport transport = McpTransportFactory.createTransport("stdio", config);
```

支持类型别名解析：

- `stdio`
- `sse`
- `streamable_http`
- `http`（兼容别名，最终会归一化为 `streamable_http`）

## 6. 心跳与连接稳定性

`McpTransport` 接口有 `needsHeartbeat()`：

- 网络型 transport（SSE/HTTP）通常需要心跳
- 进程型 transport（STDIO）通常不需要

`McpClient` 会在需要时启动心跳与重连逻辑。

## 7. 生产选型建议

- **单机工具链**：STDIO
- **已有 SSE 服务**：SSE
- **标准化平台与网关治理**：Streamable HTTP

## 8. 常见问题

1. `connect()` 卡住：多为端点不通或初始化未完成。
2. SSE 频繁断开：检查网关/反向代理超时设置。
3. STDIO 启动失败：优先看 command/args/env 是否正确。

## 9. 关联源码

- `McpTransport`
- `TransportConfig`
- `StdioTransport`
- `SseTransport`
- `StreamableHttpTransport`
- `McpTransportFactory`

