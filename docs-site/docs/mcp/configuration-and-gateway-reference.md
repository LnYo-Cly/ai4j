---
sidebar_position: 3
---

# MCP 配置与网关参考

这页不是再重复“配置文件怎么写”，而是要把一个经常被写错的点讲清楚：

- 哪些字段只是配置元数据
- 哪些字段真的会进入 transport / client / gateway 运行时

如果这层不分清，文档就会把“字段存在”误写成“功能已经实现”。

## 1. 先分清两个 `McpServerInfo`

仓库里有两个名字非常像的类型：

- `io.github.lnyocly.ai4j.mcp.config.McpServerConfig.McpServerInfo`
  这是运行时配置对象，来自 `mcp-servers-config.json`
- `io.github.lnyocly.ai4j.mcp.entity.McpServerInfo`
  这是协议/展示层的服务元信息对象，只包含 `name/version/description/...`

这一页讨论的是前者，也就是配置文件真正反序列化出来的运行时配置对象。

## 2. 顶层文件结构

配置文件入口默认是：

- `mcp-servers-config.json`

结构是：

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "enabled": true
    }
  }
}
```

几个关键点：

- 顶层 key 必须是 `mcpServers`
- 每个子 key 就是 `serverId`
- `serverId` 会直接进入 gateway 注册、路由、Agent 白名单

所以 `serverId` 不是备注字段，而是实际运行时主键。

## 3. 哪些字段真的会影响运行时

当前 `McpGatewayClientFactory` 的创建链是：

1. `McpTypeSupport.resolveType(serverInfo)`
2. `TransportConfig.fromServerInfo(serverInfo)`
3. `McpTransportFactory.validateConfig(...)`
4. `McpTransportFactory.createTransport(...)`
5. `new McpClient(serverId, clientVersion, transport)`

顺着这条链看，真正会进入 transport/client 的核心字段只有：

- `type`
- `transport`
  旧字段，仅用于兼容
- `command`
- `args`
- `env`
- `url`
- `headers`

以及在配置源层生效的：

- `enabled`

这是最关键的事实。

## 4. 字段分层表

| 字段 | 当前是否真正参与运行时 | 说明 |
| --- | --- | --- |
| `type` | 是 | 决定 `stdio / sse / streamable_http` |
| `transport` | 是，但仅兼容 | 会被 `McpTypeSupport.resolveType(...)` 作为旧字段读取 |
| `command` | 是 | `stdio` 必填 |
| `args` | 是 | `stdio` 参数 |
| `env` | 是 | `stdio` 子进程环境变量 |
| `url` | 是 | `sse / streamable_http` 必填 |
| `headers` | 是 | 远程 HTTP/SSE 请求头 |
| `enabled` | 是 | 配置源提取有效服务时使用 |
| `cwd` | 否 | 配置里有，但 `TransportConfig.fromServerInfo(...)` 没有传下去 |
| `autoReconnect` | 否 | gateway 创建 `McpClient` 时没把它传入构造器 |
| `reconnectInterval` | 否 | 当前 `McpClient` 重连逻辑固定 5 秒，不读此字段 |
| `maxReconnectAttempts` | 否 | 当前没有接入重连次数限制 |
| `connectTimeout` | 否 | 配置对象里有，但未映射到 `TransportConfig` |
| `tags` | 否 | 当前不参与路由或过滤 |
| `priority` | 否 | 当前不参与冲突仲裁或排序 |
| `version` | 间接 | 会参与 `FileMcpConfigSource` 的 JSON 比较，从而触发更新 |
| `createdTime` / `lastUpdatedTime` | 间接 | 同上，偏治理元数据 |
| `requiresAuth` | 否 | 元信息，不会自动注入认证 |
| `authTypes` | 否 | 元信息，不会自动改变 client 行为 |

如果你只记一件事，就记这一张表。

## 5. `type` 和 `transport` 的真实关系

推荐写法永远是：

- `type: "stdio"`
- `type: "sse"`
- `type: "streamable_http"`

`transport` 只是兼容旧配置的遗留字段。

`McpTypeSupport.normalizeType(...)` 还接受一些别名：

- `process`
- `local`
- `server-sent-events`
- `event-stream`
- `http`
- `mcp`
- `streamable-http`
- `http-streamable`

但要注意：

- 未知值会被归一化回 `stdio`

这意味着如果你把类型写错了，不一定立刻显式报错，而可能被当成 `stdio` 继续走下去。生产环境里不要依赖这种容错。

## 6. 三种常见配置的正确写法

### `stdio`

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "${GITHUB_TOKEN}"
      },
      "enabled": true
    }
  }
}
```

当前真正生效的是：

- `command`
- `args`
- `env`

`cwd` 虽然有字段，但当前不会进入 `StdioTransport`。

### `sse`

```json
{
  "mcpServers": {
    "remote-sse": {
      "type": "sse",
      "url": "http://127.0.0.1:8080/sse",
      "headers": {
        "Authorization": "Bearer ${TOKEN}"
      },
      "enabled": true
    }
  }
}
```

当前真正生效的是：

- `url`
- `headers`

### `streamable_http`

```json
{
  "mcpServers": {
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "headers": {
        "Authorization": "Bearer ${TOKEN}"
      },
      "enabled": true
    }
  }
}
```

`http` 仍兼容，但语义上已经被归一化到 `streamable_http`。

## 7. `enabled` 是真正的开关字段

`FileMcpConfigSource.loadConfigs()` 会调用 `McpConfigIO.extractEnabledConfigs(serverConfig)`，也就是：

- 只有启用的配置才会进入有效配置集
- `reloadConfigs()` 后，新增/删除/更新事件都是围绕“启用配置集”计算的

这意味着把一个服务改成 `enabled: false`，其效果不是“保留配置但标记停用”，而是：

- 从当前有效服务集合里移除
- gateway 收到删除事件后把对应 client 下线

## 8. `autoReconnect` 等字段为什么现在不能乱宣传

这几个字段最容易被文档写过头：

- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`

它们在配置类里确实存在，但当前 gateway 创建 client 的时候：

- 没把 `autoReconnect` 传给 `McpClient`
- 没把 `reconnectInterval` / `maxReconnectAttempts` 用到重连调度
- 没把 `connectTimeout` 从配置映射到 `TransportConfig`

实际运行时的行为是：

- `McpClient` 默认 `autoReconnect = true`
- 断线后固定 5 秒后尝试重连
- `connect()` 内部 transport 启动和初始化超时固定 30 秒

所以如果你在配置文件里写了这些字段，当前更应该把它理解成：

- 平台治理元数据
- 未来扩展预留

而不是“已经完全接线的运行参数”。

## 9. `headers` 和认证元数据不要混为一谈

### `headers`

这是现在真正会参与远程请求的认证注入点。

用途包括：

- `Authorization`
- API key
- 租户标识

### `requiresAuth` / `authTypes`

这两个字段当前更适合用于：

- 后台界面展示
- 配置校验
- 审计标记

它们本身不会自动生成请求头，也不会自动触发登录流程。

## 10. 网关初始化的 3 种常见方式

### 直接读配置文件

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

### 先绑定配置源

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

### 运行时动态注入

```java
gateway.addMcpClient("github", client).join();
gateway.removeMcpClient("github").join();
```

这 3 种方式不是重复功能，而是 3 种治理层级：

- 静态配置
- 抽象配置源
- 完全平台化热插拔

## 11. Agent 侧怎么引用这些配置

配置文件中注册的 `serverId`，最终要进入 Agent 的 `mcpServices` 白名单：

```java
.toolRegistry(Collections.<String>emptyList(), Arrays.asList("github", "filesystem"))
```

要点只有一句：

- “服务已被 gateway 注册”不等于“本次请求默认可见”

## 12. 这页最该记住的结论

在 AI4J 当前实现里，配置字段分成两层：

- 一层是真正进入 transport/client/gateway 的运行字段
- 一层是为治理、审计、未来扩展准备的元数据字段

文档写法必须忠于这条边界，否则就会把“对象字段存在”错写成“运行能力已经生效”。
