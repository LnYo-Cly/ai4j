---
sidebar_position: 6
---

# 构建并对外发布 MCP Server

你问到“如何把自己的能力暴露成 MCP 给别人用”，这页给完整步骤。

## 1. 目标流程

1. 用注解定义本地 MCP 能力（Tool/Resource/Prompt）
2. 启动 MCP Server（STDIO/SSE/Streamable HTTP）
3. 外部 MCP Client 连接并调用

## 2. 能力定义：注解体系

- `@McpService`：定义服务元信息
- `@McpTool` + `@McpParameter`：定义工具
- `@McpResource` + `@McpResourceParameter`：定义资源
- `@McpPrompt` + `@McpPromptParameter`：定义提示模板

## 3. 示例：定义一个可发布的服务

```java
@McpService(name = "WeatherService", description = "Weather MCP service", transport = "streamable_http", port = 8081)
public class WeatherMcpService {

    @McpTool(name = "query_weather", description = "Query weather by city")
    public String queryWeather(@McpParameter(name = "city", description = "City name") String city) {
        return "Weather(" + city + ")";
    }

    @McpResource(uri = "weather://city/{city}", name = "city-weather", description = "Weather resource")
    public String weatherResource(@McpResourceParameter(name = "city") String city) {
        return "Resource(" + city + ")";
    }

    @McpPrompt(name = "weather-summary", description = "Generate weather summary")
    public String weatherPrompt(@McpPromptParameter(name = "city") String city) {
        return "Please summarize weather for " + city;
    }
}
```

## 4. Server 启动方式

AI4J 提供 `McpServerFactory`：

```java
McpServer server = McpServerFactory.createServer("streamable_http", "weather-server", "1.0.0", 8081);
server.start().join();

// 关闭
server.stop().join();
```

支持：

- `stdio`
- `sse`
- `streamable_http`（`http` 兼容映射）

## 5. 三种 Server 类型怎么选

- `StdioMcpServer`
  - 适合作为本地子进程服务
- `SseMcpServer`
  - 适合已有 SSE 消费方
- `StreamableHttpMcpServer`
  - 最推荐用于公网/内网服务发布

## 6. 暴露内容来源机制

Server 在运行时会扫描并暴露本地 MCP 注解能力：

- Tool：来自本地 MCP 工具缓存
- Resource：来自 `McpResourceAdapter`
- Prompt：来自 `McpPromptAdapter`

其中 Tool 列表对外暴露使用 `ToolUtil.getLocalMcpTools()`。

## 7. 对外发布时的工程建议

1. **版本标识**：`@McpService.version` + 发布日志。
2. **命名规范**：tool/resource/prompt 名称稳定，避免频繁破坏性变更。
3. **参数兼容**：新增参数尽量 optional，避免影响老客户端。
4. **超时与限流**：工具方法要有超时保护。
5. **审计**：记录请求来源、方法、耗时、错误。

## 8. 让第三方调用你的 MCP

第三方可用任意兼容 MCP 的客户端接入；AI4J 客户端示例：

```java
McpTransport transport = new StreamableHttpTransport("http://127.0.0.1:8081/mcp");
McpClient client = new McpClient("consumer", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
String result = client.callTool("query_weather", Collections.singletonMap("city", "Beijing")).join();
```

## 9. 常见问题

1. 客户端连上但 `tools/list` 为空
   - 检查注解扫描范围与工具命名。
2. 资源/提示不可见
   - 检查是否使用了 `@McpResource/@McpPrompt`，以及参数注解是否齐全。
3. HTTP 发布后 404
   - 确认端点路径（通常 `/mcp` 或 SSE 对应路径）和反向代理配置。

## 10. 参考源码

- `McpServer`
- `McpServerFactory`
- `StdioMcpServer`
- `SseMcpServer`
- `StreamableHttpMcpServer`
- `McpToolAdapter`
- `McpResourceAdapter`
- `McpPromptAdapter`
