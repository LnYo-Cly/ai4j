---
sidebar_position: 3
---

# MCP Client 接入（单服务模式）

本页聚焦“你只接 1 个第三方 MCP 服务”的最短路径。

## 1. 最小流程

1. 创建 `McpTransport`
2. 创建 `McpClient`
3. `connect()` 初始化会话
4. `getAvailableTools()` 拉取工具
5. `callTool()` 执行工具
6. `disconnect()` 释放连接

## 2. 方式 A：STDIO 接本地第三方 MCP

```java
McpTransport transport = new StdioTransport(
        "npx",
        Arrays.asList("-y", "@modelcontextprotocol/server-filesystem", "D:/workspace"),
        null
);

McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();

List<McpToolDefinition> tools = client.getAvailableTools().join();
System.out.println("TOOLS=" + tools.size());

String result = client.callTool("read_file", Collections.singletonMap("path", "README.md")).join();
System.out.println(result);

client.disconnect().join();
```

## 3. 方式 B：SSE/HTTP 接远程第三方 MCP

```java
TransportConfig config = TransportConfig.streamableHttp("https://example.com/mcp");
config.setHeaders(Collections.singletonMap("Authorization", "Bearer your-token"));

McpTransport transport = McpTransportFactory.createTransport("streamable_http", config);
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

如果是 SSE：

```java
McpTransport transport = new SseTransport("https://example.com/sse");
McpClient client = new McpClient("demo-client", "1.0.0", transport);
client.connect().join();
```

## 4. Resource / Prompt 高层 API

除了 Tool，现在 `McpClient` 也已经提供了 Resource / Prompt 的便捷方法：

- `getAvailableResources()` -> `resources/list`
- `readResource(uri)` -> `resources/read`
- `getAvailablePrompts()` -> `prompts/list`
- `getPrompt(name, arguments)` -> `prompts/get`

示例：

```java
List<McpResource> resources = client.getAvailableResources().join();
McpResourceContent resource = client.readResource("file://docs/README.md").join();

List<McpPrompt> prompts = client.getAvailablePrompts().join();
McpPromptResult prompt = client.getPrompt(
        "code_review_prompt",
        Collections.<String, Object>singletonMap("language", "java")
).join();
```

适合场景：

- Resource：读配置、模板、文件、知识片段
- Prompt：拿第三方 MCP 服务提供的提示模板

## 5. `McpClient` 当前能力边界

当前高层 API 重点封装了：

- `getAvailableTools()` -> `tools/list`
- `callTool(name, args)` -> `tools/call`
- `getAvailableResources()` -> `resources/list`
- `readResource(uri)` -> `resources/read`
- `getAvailablePrompts()` -> `prompts/list`
- `getPrompt(name, args)` -> `prompts/get`

当前返回类型仍然保持“轻量高层对象”风格：

- Tool -> `McpToolDefinition`
- Resource -> `McpResource` / `McpResourceContent`
- Prompt -> `McpPrompt` / `McpPromptResult`

## 6. 与 Agent 的桥接方式

单服务模式下，你通常有两种桥接：

1. 自己在工具执行层调用 `McpClient.callTool(...)`
2. 通过 `McpGateway` 聚合后，再交给 `ToolUtil` / `toolRegistry`

如果你后续会接多个服务，建议直接进阶到 Gateway 模式。

## 7. 稳定性建议

- 连接前检查 transport 配置完整性
- `connect()` 和 `callTool()` 设置外层超时
- 对 `callTool()` 做错误分层（网络错误/业务错误）
- 在 finally 中 `disconnect()`

对于 Resource / Prompt 也建议做：

- URI 白名单
- Prompt 名称白名单
- 外层超时和降级

## 8. 安全建议

- 工具名白名单
- 参数 schema 校验
- 认证信息只走 header/env，不写死在代码

## 9. 常见排障

1. `not connected or not initialized`
   - 先确认 `connect().join()` 成功。
2. `tool not found`
   - 先 `getAvailableTools()` 看服务端暴露名。
3. `resource not found` / `prompt not found`
   - 先分别用 `getAvailableResources()` / `getAvailablePrompts()` 看暴露清单。
3. HTTP 401/403
   - 检查 `TransportConfig.headers` 认证配置。

## 10. 下一步阅读

- 《接入第三方 MCP（全部方式）》
- 《MCP Gateway 管理》
