---
sidebar_position: 3
---

# MCP 配置与网关参考

这页专门回答两个工程问题：

1. `mcp-servers-config.json` 到底怎么写
2. `McpGateway` 初始化、热更新、用户隔离时，配置字段各自起什么作用

---

## 1. 配置文件顶层结构

AI4J 当前的文件配置入口是：

- `mcp-servers-config.json`

顶层结构是：

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"]
    }
  }
}
```

也就是说：

- `mcpServers` 是一个 map
- key 是 `serverId`
- value 是单个 `McpServerInfo`

`serverId` 的作用非常重要，因为它会直接参与：

- 网关服务注册
- 工具路由
- Agent 中的 `mcpServices` 白名单

---

## 2. 单个 MCP 服务配置字段

当前 `McpServerInfo` 支持的核心字段包括：

- `name`
- `description`
- `command`
- `args`
- `env`
- `cwd`
- `transport`（旧字段，已弃用）
- `type`
- `url`
- `headers`
- `enabled`
- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`
- `tags`
- `priority`
- `version`
- `createdTime`
- `lastUpdatedTime`
- `requiresAuth`
- `authTypes`

其中真正高频、最关键的字段是：

- `type`
- `command` / `args`
- `url`
- `headers`
- `enabled`
- `autoReconnect`

---

## 3. 三种最常见配置方式

### 3.1 STDIO 本地子进程

适合：

- 本地工具进程
- 命令行方式启动的 MCP Server

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_TOKEN": "your-token"
      },
      "enabled": true,
      "autoReconnect": true
    }
  }
}
```

高频字段含义：

- `command`：启动命令
- `args`：命令参数
- `env`：子进程环境变量
- `cwd`：工作目录

### 3.2 SSE 远程服务

适合：

- 已有 SSE MCP 服务
- 远程事件流式接入

```json
{
  "mcpServers": {
    "remote-sse": {
      "type": "sse",
      "url": "http://127.0.0.1:8080/sse",
      "headers": {
        "Authorization": "Bearer xxx"
      },
      "enabled": true
    }
  }
}
```

### 3.3 Streamable HTTP（推荐）/ HTTP（兼容别名）

适合：

- 标准服务化发布
- 内网或公网 HTTP 接入

```json
{
  "mcpServers": {
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "headers": {
        "Authorization": "Bearer xxx"
      },
      "enabled": true,
      "autoReconnect": true,
      "connectTimeout": 30000
    }
  }
}
```

说明：

- 推荐直接写 `type: "streamable_http"`
- `http` 仍然兼容，但只建议用于旧配置迁移

---

## 4. `type`、`transport` 和 `url` 的关系

这里最容易混乱。

当前推荐规则是：

- 优先使用 `type`
- `transport` 只保留向后兼容

推荐写法：

- `stdio`
- `sse`
- `streamable_http`

兼容别名：

- `http` -> `streamable_http`

而 `url` 只对：

- `sse`
- `streamable_http`

这几类传输有意义。

如果是 `stdio`，核心字段应该是：

- `command`
- `args`
- `env`

---

## 5. `enabled` 与热更新语义

`FileMcpConfigSource` 当前加载配置时，只会把：

- `enabled == true`
- 或 `enabled == null`

的服务纳入有效配置。

这意味着：

- 把 `enabled` 改成 `false`，重新加载后服务会被移除
- 新增一个启用配置，重新加载后会触发新增事件
- 修改配置内容，重新加载后会触发更新事件

这也是文件热更新的基础语义。

---

## 6. 自动重连相关字段

这几项主要用于远端服务治理：

- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`

推荐理解：

- `autoReconnect`：是否允许自动重连
- `reconnectInterval`：两次重连间隔
- `maxReconnectAttempts`：最多尝试次数
- `connectTimeout`：建连超时

对于本地稳定的 `stdio` 工具，这些字段通常不是第一优先级；对于远端 HTTP/SSE 服务，这几项则很重要。

---

## 7. `headers`、`requiresAuth`、`authTypes`

这三组字段不要混用概念。

### `headers`

这是实际会参与远端请求的 HTTP 头。

典型用途：

- `Authorization`
- 租户标识
- 自定义认证头

### `requiresAuth`

这是配置元信息，表达“该服务是否需要认证”。

### `authTypes`

这是配置元信息，表达“支持哪些认证方式”。

它们更适合用于：

- 配置后台
- 管理台展示
- 审计与治理

真正发请求时，还是以 `headers` 为准。

---

## 8. `tags`、`priority`、`version` 适合怎么用

这些字段更偏治理和平台化。

### `tags`

适合：

- 按类别分组
- 标记能力域，例如 `db`、`filesystem`、`devops`

### `priority`

适合：

- 同类服务排序
- 灰度切换时做优先级控制

### `version`

适合：

- 配置变更检测
- 平台后台审计
- 回滚和比对

如果只是个人项目或单机实验，可以先不依赖这三项。

---

## 9. 网关初始化的三种常见方式

### 9.1 直接读配置文件

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

适合：

- 本地开发
- 单项目静态配置

### 9.2 自定义配置源

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

适合：

- 后续切到数据库、Redis、配置中心
- 需要统一抽象配置来源

### 9.3 运行时动态增删

```java
gateway.addMcpClient("github", client).join();
gateway.removeMcpClient("github").join();
```

适合：

- 热更新
- 租户隔离
- 平台化服务治理

---

## 10. Agent 中如何引用配置好的 MCP 服务

配置文件里注册的 `serverId`，最终要进入 Agent 的工具白名单。

例如配置里有：

- `github`
- `filesystem`

那么 Agent 里应显式写：

```java
.toolRegistry(java.util.Arrays.asList("queryWeather"), java.util.Arrays.asList("github", "filesystem"))
```

这层语义非常重要：

- 配置存在，不等于默认暴露给模型
- Agent 仍然需要显式选择要开放的 MCP 服务

---

## 11. 常见错误

### 11.1 配置文件存在，但网关看不到服务

优先检查：

- 顶层是否是 `mcpServers`
- `enabled` 是否被设成 `false`
- 配置文件路径是否真的被加载

### 11.2 写了 `url`，但 `stdio` 仍然不生效

因为 `stdio` 核心靠的是：

- `command`
- `args`

不是 `url`。

### 11.3 用了旧字段 `transport`

当前仍兼容，但新文档和新配置都应优先写 `type`。

### 11.4 Agent 看不到工具

通常不是网关没加载，而是：

- `toolRegistry(..., mcpServices)` 没写对应 `serverId`

---

## 12. 推荐阅读

1. [MCP 使用路径与场景选择](/docs/mcp/use-cases-and-paths)
2. [MCP Gateway 管理](/docs/mcp/gateway-management)
3. [接入第三方 MCP（全部方式）](/docs/mcp/third-party-mcp-integration)
4. [构建并对外发布 MCP Server](/docs/mcp/build-your-mcp-server)
