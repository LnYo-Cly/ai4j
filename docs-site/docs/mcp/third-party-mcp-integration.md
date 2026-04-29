---
sidebar_position: 4
---

# 接入第三方 MCP（全部方式）

这一页不只列“几种接法”，而是把第三方 MCP 在 AI4J 里的接入层级拆开讲清楚。

你真正可选的不是一个 API，而是 5 种集成深度：

1. 单 client 直连
2. 配置驱动的多服务 gateway
3. 运行时动态增删
4. 用户级隔离
5. 挂到 Agent 请求链

不同深度解决的是不同问题，别把它们混成“都是接个 MCP”。

## 1. 先选集成层级，不要先写代码

| 集成方式 | 解决的问题 | 适合场景 |
| --- | --- | --- |
| `McpClient` 直连 | 跑通一个服务 | 验证、原型、小工具 |
| `McpGateway.initialize(...)` | 多服务统一接入 | 服务数量开始增长 |
| `addMcpClient/removeMcpClient` | 运行时热插拔 | 平台化治理 |
| `addUserMcpClient(...)` | 用户或租户隔离 | SaaS、多账号 |
| `toolRegistry(..., mcpServices)` | 让 Agent 消费指定服务 | 推理链路接入 |

推荐顺序永远是：

1. 先用单 client 验证连通性
2. 再决定是否引入 gateway
3. 最后再接 Agent

## 2. 方式一：单 `McpClient` 直连

这是最小闭环，也是所有问题排查的起点。

```java
McpTransport transport = McpTransportFactory.createTransport(
        "stdio",
        TransportConfig.stdio("npx", Arrays.asList("-y", "@modelcontextprotocol/server-github"))
);

McpClient client = new McpClient("github-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String result = client.callTool("search_repositories", Collections.singletonMap("q", "ai4j")).join();

client.disconnect().join();
```

这条链真正跑的是：

1. transport 启动
2. `initialize`
3. `notifications/initialized`
4. `tools/list`
5. `tools/call`

只要这条链还没稳，就不要急着上 `McpGateway`。

## 3. 方式二：配置驱动接入多个第三方服务

当服务数量超过一个，就不该在业务代码里手写多个 `McpClient`。

配置示例：

```json
{
  "mcpServers": {
    "github": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "enabled": true
    },
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "enabled": true
    }
  }
}
```

初始化：

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();

List<Tool.Function> tools = gateway.getAvailableTools().join();
```

这里要理解两个真实边界：

- gateway 负责把多个第三方服务聚成一个目录
- 它不会自动替你解决同名 tool 冲突

如果两个第三方 server 都导出了 `search` 之类的名字，当前映射会覆盖。

## 4. 方式三：运行时动态增删

这是“平台模式”才需要的集成深度。

```java
McpClient githubClient = new McpClient("github", "1.0.0", githubTransport);
gateway.addMcpClient("github", githubClient).join();

gateway.removeMcpClient("github").join();
```

适合：

- 控制台开关服务
- 动态试用新 MCP
- 故障摘流

真实语义不是“往 map 里 put 一下”：

- add 会先连上 client，再整表 refresh 工具目录
- remove 会先断开，再整表 refresh

所以这类集成要提前考虑目录刷新成本和冲突治理。

## 5. 方式四：用户级隔离接入

如果第三方 MCP 不是全局共享，而是“每个用户自己的一组凭证或服务”，就要进入用户级接入。

```java
McpClient userClient = new McpClient("user-github", "1.0.0", transport);
gateway.addUserMcpClient("u123", "github", userClient).join();

String result = gateway.callUserTool(
        "u123",
        "search_repositories",
        Collections.singletonMap("q", "ai4j")
).join();
```

内部 key 规则是：

- 用户 client：`user_{userId}_service_{serviceId}`
- 用户 tool：`user_{userId}_tool_{toolName}`

当前默认行为是：

- 先查用户级工具
- 查不到再回退全局工具

如果你的权限模型要求“用户没配就绝不允许调全局共享服务”，你要在业务层自己把回退禁掉。

## 6. 方式五：把第三方 MCP 暴露给 Agent

这一步的关键不是“能连上 gateway”，而是“只把该次任务真正需要的服务暴露给模型”。

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("github"))
        .build();
```

这条链路的核心语义是：

- `McpGateway` 只是服务目录
- `toolRegistry(..., mcpServices)` 才决定本次请求的可见服务

当前 `ToolUtil.getAllTools(functionList, mcpServerIds)` 只会合并：

- 显式传入的本地 function 列表
- 显式传入的 MCP 服务列表

不会把所有远程 MCP 自动暴露给模型。

## 7. 第三方 MCP 在 AI4J 里的完整运行链

如果把整条链画成一条线，就是：

1. `McpServerConfig` 或自定义配置源定义服务
2. `McpGatewayClientFactory` 依据 type 创建 transport + client
3. `McpClient.connect()` 完成 MCP 握手
4. `McpGatewayToolRegistry` 收集工具清单
5. Agent 通过 `mcpServices` 选择本次暴露面
6. 模型触发 tool call
7. `ToolUtil.invoke(...)` 把调用路由到 gateway
8. gateway 找到对应 client
9. client 发起 `tools/call`
10. 结果返回给模型

只要你知道问题卡在第几步，排查就不会乱。

## 8. 第三方服务接入时最容易踩的 4 个坑

### 8.1 服务同名 tool 冲突

当前网关映射是：

- 全局工具：`toolName -> clientKey`

不是：

- `serviceId + toolName -> clientKey`

所以不同服务导出同名工具时，冲突会直接落到运行时。

### 8.2 把网关当成权限系统

gateway 负责连接和路由，不负责：

- 是否允许某个会话使用某个服务
- 谁能接入哪个租户的第三方凭证

权限控制仍然应该由业务层或会话层做。

### 8.3 以为所有配置字段都已生效

当前真正进入 transport/client 的核心字段主要是：

- `type`
- `command`
- `args`
- `env`
- `url`
- `headers`

像 `priority`、`tags`、`requiresAuth` 这类字段，更接近治理元数据，不会自动改变调用行为。

### 8.4 还没验证连通性就先接 Agent

正确顺序应该是：

1. `McpClient` 单独打通
2. 再进入 gateway
3. 最后再进入 Agent

## 9. 推荐落地策略

### 小项目

- 先直连 `McpClient`
- 服务少时不要过早上复杂治理

### 中型项目

- 统一走 `McpGateway`
- 明确 serviceId 和 toolName 规范

### 平台项目

- `McpGateway + McpConfigSource`
- 动态增删
- 审计日志
- 用户级隔离
- 显式暴露白名单

## 10. 这页最该记住的结论

第三方 MCP 接入在 AI4J 里不是单一 API，而是一条从连接、治理、隔离到 Agent 暴露的分层链路。

先把“连通性问题”和“治理问题”拆开，再把“治理问题”和“模型可见性问题”拆开，接入方案就会清楚很多。
