---
sidebar_position: 5
---

# MCP Gateway 管理（多服务聚合与治理）

`McpGateway` 是 AI4J 中 MCP 平台化的核心：

- 统一管理多个 `McpClient`
- 聚合工具列表
- 按 service/user 路由调用
- 支持动态增删与状态查询

## 1. 关键 API

初始化与生命周期：

- `initialize()` / `initialize(configFile)`
- `shutdown()`
- `isInitialized()`

客户端管理：

- `addMcpClient(serviceId, client)`
- `removeMcpClient(serviceId)`
- `addUserMcpClient(userId, serviceId, client)`
- `removeUserMcpClient(userId, serviceId)`
- `clearUserMcpClients(userId)`

工具与调用：

- `getAvailableTools()`
- `getAvailableTools(serviceIds)`
- `getUserAvailableTools(serviceIds, userId)`
- `callTool(toolName, arguments)`
- `callUserTool(userId, toolName, arguments)`

观测与诊断：

- `getGatewayStatus()`
- `getToolToClientMap()`

## 2. 网关初始化模式

## 模式 A：配置文件初始化

```java
McpGateway gateway = new McpGateway();
gateway.initialize("mcp-servers-config.json").join();
```

## 模式 B：自定义配置源

```java
McpConfigSource source = new FileMcpConfigSource("mcp-servers-config.json");
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

`McpConfigSource` 支持监听配置增删改，适合做热更新平台。

## 3. 路由语义

### 全局客户端

- clientKey: `serviceId`
- toolKey: `toolName`

### 用户客户端

- clientKey: `user_{userId}_service_{serviceId}`
- toolKey: `user_{userId}_tool_{toolName}`

调用优先级（用户模式）：

1. 先查用户专属工具
2. 未命中再回退全局工具

## 4. 动态管理示例

```java
McpClient weatherClient = new McpClient("weather", "1.0.0", weatherTransport);
gateway.addMcpClient("weather", weatherClient).join();

Map<String, Object> status = gateway.getGatewayStatus();
System.out.println(status);

gateway.removeMcpClient("weather").join();
```

## 5. 与 Agent 集成建议

推荐结构：

1. 启动期初始化 gateway
2. Agent 构建时显式传入 `mcpServices`
3. 工具执行统一走 `ToolUtil` -> `McpGateway`

```java
.toolRegistry(Arrays.asList("queryWeather"), Arrays.asList("github", "filesystem"))
```

## 6. 高可用建议

1. 服务分级：核心/非核心分层
2. 超时策略：按服务差异化设置
3. 熔断降级：非核心失败不阻断主链路
4. 版本治理：配置有版本号与回滚
5. 指标监控：按 service/tool 统计成功率与耗时

## 7. 安全建议

- 工具白名单优先
- 多租户场景强制 userId 校验
- 高风险工具单独审计
- Token/Key 不落日志

## 8. 关联文档

- 《接入第三方 MCP（全部方式）》
- 《构建并对外发布 MCP Server》
- 《Tool 暴露语义与安全边界》
- 《MySQL 动态 MCP 服务管理》
