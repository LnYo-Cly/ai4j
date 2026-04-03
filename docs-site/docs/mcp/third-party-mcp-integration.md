---
sidebar_position: 4
---

# 接入第三方 MCP（全部方式）

你要求“外部第三方 MCP 的所有使用方式”，这里按工程复杂度给出完整路径。

## 1. 方式总览

### 方式 1：单连接直连（`McpClient`）

- 适合：1~2 个服务、快速验证
- 优点：最简单
- 缺点：服务多了后连接与路由逻辑分散

### 方式 2：网关配置加载（`McpGateway.initialize(...)`）

- 适合：多服务统一管理
- 优点：工具聚合、统一调用入口
- 缺点：需要维护配置文件/配置源

### 方式 3：运行时动态增删（`addMcpClient/removeMcpClient`）

- 适合：平台化、热更新
- 优点：不停机增删服务
- 缺点：需要更严格治理与审计

### 方式 4：用户级隔离（`addUserMcpClient`）

- 适合：SaaS 多租户
- 优点：租户工具可隔离
- 缺点：命名与权限策略要严格

### 方式 5：接入 Agent（`toolRegistry(..., mcpServices)`）

- 适合：模型任务执行
- 优点：按场景显式暴露工具
- 缺点：前置要求是 gateway 已可用

## 2. 方式 1：单连接直连

```java
McpTransport transport = McpTransportFactory.createTransport(
        "stdio",
        TransportConfig.stdio("npx", Arrays.asList("-y", "12306-mcp"))
);

McpClient client = new McpClient("ticket-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String output = client.callTool("search_train", Collections.singletonMap("from", "北京")).join();

client.disconnect().join();
```

## 3. 方式 2：网关配置加载

`ai4j/src/main/resources/mcp-servers-config.json` 示例：

```json
{
  "mcpServers": {
    "test_weather_http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp"
    }
  }
}
```

推荐在新配置里直接写 `streamable_http`，`http` 只保留兼容旧配置。

初始化：

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();

List<Tool.Function> tools = gateway.getAvailableTools().join();
String result = gateway.callTool("query_weather", Collections.singletonMap("location", "Beijing")).join();
```

## 4. 方式 3：动态增删服务（热更新）

```java
McpClient githubClient = new McpClient("github", "1.0.0", githubTransport);
gateway.addMcpClient("github", githubClient).join();

// ... later
gateway.removeMcpClient("github").join();
```

也可通过 `McpConfigSource` + 监听器实现配置驱动热更新（文件、MySQL、Redis 都可扩展）。

## 5. 方式 4：用户级第三方 MCP（多租户）

```java
McpClient userClient = new McpClient("user-github", "1.0.0", transport);
gateway.addUserMcpClient("u123", "github", userClient).join();

String output = gateway.callUserTool("u123", "search_repositories", Collections.singletonMap("q", "ai4j")).join();
```

路由命名规则：

- 用户服务键：`user_{userId}_service_{serviceId}`
- 用户工具键：`user_{userId}_tool_{toolName}`

## 6. 方式 5：把第三方 MCP 暴露给 Agent

### 6.1 先初始化网关

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();
```

### 6.2 Agent 只暴露指定 MCP 服务

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(Arrays.asList("queryWeather"), Arrays.asList("github"))
        .build();
```

当前语义：

- 只暴露 `functionList + mcpServerIds` 中显式传入内容
- 不会自动注入全部本地 MCP 工具

## 7. 第三方 MCP 接入治理清单

1. 服务注册：serviceId 命名规范（避免冲突）
2. 认证管理：token/key 走 header/env
3. 限流熔断：慢服务隔离
4. 审计：记录 toolName、serviceId、耗时、状态
5. 升级策略：先灰度后全量

## 8. 常见错误与修复

1. 网关拿不到工具
   - 检查 `gateway.isInitialized()` 与配置路径。
2. Agent 能看到的工具不全
   - 检查 `toolRegistry(..., mcpServices)` 是否传了正确 `mcpServerIds`。
3. 用户工具调用失败
   - 检查 userId/serviceId 前缀和 `callUserTool` 参数。

## 9. 进阶建议

- 小项目：直连 `McpClient`
- 中大型项目：统一 `McpGateway`
- 平台项目：`McpGateway + 动态配置源 + 审计` 标准化落地
