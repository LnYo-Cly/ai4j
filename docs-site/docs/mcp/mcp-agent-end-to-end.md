---
sidebar_position: 7
---

# MCP 与 Agent 一体化实战（端到端）

这一页给你一个“从第三方 MCP 到 Agent 工具调用”的完整闭环示例，目标是做到：

1. 接入外部 MCP 服务
2. 通过 `McpGateway` 聚合工具
3. 在 Agent 中按场景暴露 MCP 工具
4. 运行并观测结果

> 这是你后续做开源示例、演示仓库、CI 回归最实用的一条链路。

## 1. 场景设定

- 外部 MCP 服务：`weather-http`（假设已提供 `query_weather` 工具）
- 模型：Doubao / OpenAI 兼容模型
- Agent：ReAct Runtime
- 工具暴露策略：只暴露 `mcpServerIds=["weather-http"]`，不自动暴露其它工具

## 2. 准备 MCP 配置

在 `mcp-servers-config.json` 中声明服务：

```json
{
  "mcpServers": {
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "enabled": true
    }
  }
}
```

> `serviceId`（这里是 `weather-http`）就是后续 `toolRegistry(..., mcpServices)` 里要传的 ID。新配置建议直接写 `streamable_http`，不要再把 `http` 当成主写法。

## 3. 启动并初始化网关

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();

List<Tool.Function> gatewayTools = gateway.getAvailableTools().join();
System.out.println("MCP tools in gateway: " + gatewayTools.size());
```

建议初始化后先打印工具名，确认服务确实注册成功。

## 4. 构建模型服务

```java
DoubaoConfig doubaoConfig = new DoubaoConfig();
doubaoConfig.setApiKey(System.getenv("ARK_API_KEY"));

Configuration configuration = new Configuration();
configuration.setDoubaoConfig(doubaoConfig);

AiService aiService = new AiService(configuration);
ResponsesModelClient modelClient = new ResponsesModelClient(
        aiService.getResponsesService(PlatformType.DOUBAO)
);
```

## 5. 构建 Agent（接入 MCP）

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是天气助手，必要时必须调用工具后再回答。")
        .instructions("使用 MCP 工具查询天气，并给出简洁建议。")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
        .options(AgentOptions.builder().maxSteps(4).build())
        .build();
```

这里关键点：

- `functionList` 传空
- `mcpServices` 只传 `weather-http`
- 达成“只暴露该 MCP 服务工具”的精确控制

## 6. 发起请求并查看输出

```java
AgentResult result = agent.run(AgentRequest.builder()
        .input("请查询北京今天天气，并给出穿衣建议")
        .build());

System.out.println("OUTPUT: " + result.getOutputText());
```

如果链路正常，模型会触发 MCP tool call，再给出最终文本。

## 7. 增加 Trace 观测（推荐默认开启）

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
        .traceExporter(new ConsoleTraceExporter())
        .traceConfig(TraceConfig.builder().build())
        .build();
```

你会看到 RUN/STEP/MODEL/TOOL 的 trace，能快速判断慢在模型还是慢在 MCP。

## 8. 常见故障与定位

## 8.1 Agent 看不到 MCP 工具

排查顺序：

1. `gateway.isInitialized()` 是否为 true
2. `gateway.getAvailableTools()` 是否有工具
3. `toolRegistry(..., Arrays.asList("weather-http"))` 的 serviceId 是否拼写一致

## 8.2 模型没有触发工具

排查顺序：

1. `systemPrompt/instructions` 是否明确“必须调用工具”
2. `maxSteps` 是否过小
3. 工具描述是否足够让模型理解用途

## 8.3 调用超时或失败

排查顺序：

1. MCP 服务端可达性（url/认证）
2. 服务端 `tools/call` 是否可用
3. 网关/反向代理超时配置

## 9. 多租户扩展（可选）

如果你做 SaaS，可用用户级 MCP 客户端：

```java
gateway.addUserMcpClient("u1001", "weather-http", userClient).join();
String output = gateway.callUserTool("u1001", "query_weather", Collections.singletonMap("location", "Beijing")).join();
```

配合 Agent 时，建议在会话层绑定 userId，并统一审计。

## 10. 与 Workflow 组合（实战升级）

推荐把 MCP 查询和结果格式化拆成两个节点：

1. 节点 A：MCP 查询 + 初步分析
2. 节点 B：JSON 格式化

这样可以明显提升稳定性和可维护性（对应你当前的天气双 Agent 模式）。

## 11. 一份可复用的最小测试模板

```java
@Test
public void test_mcp_agent_e2e() throws Exception {
    McpGateway gateway = McpGateway.getInstance();
    gateway.initialize("mcp-servers-config.json").join();

    Agent agent = Agents.react()
            .modelClient(modelClient)
            .model("doubao-seed-1-8-251228")
            .systemPrompt("你是天气助手，必须先调用工具")
            .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
            .options(AgentOptions.builder().maxSteps(4).build())
            .build();

    AgentResult result = agent.run(AgentRequest.builder().input("北京天气").build());
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.getOutputText());
    Assert.assertTrue(result.getOutputText().length() > 0);
}
```

## 12. 生产落地建议

1. Gateway 初始化失败时，提供降级回答而不是直接 500。
2. MCP 工具必须按业务场景白名单暴露。
3. Trace 默认开启，日志至少保留 `serviceId/toolName/status/latency`。
4. 关键第三方 MCP 建议配熔断和重试策略。

---

如果你接下来愿意，我可以再补一个“**MCP + StateGraph + SubAgent 三层编排**”的端到端案例页，直接对应你目前的 Agent 架构路线。
