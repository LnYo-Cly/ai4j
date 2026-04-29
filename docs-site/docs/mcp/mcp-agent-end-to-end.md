---
sidebar_position: 7
---

# MCP 与 Agent 一体化实战（端到端）

这一页不是“天气助手 demo 文案”，而是把 MCP 真正进入 Agent 运行时后的执行链拆开。

要回答的问题只有一个：

> 一个第三方 MCP 服务，是怎样从配置文件一路进入 Agent 推理循环，并最终被模型调用的？

## 1. 先看完整执行链

在 AI4J 里，MCP 接入 Agent 至少会经过 7 层：

1. `mcp-servers-config.json`
2. `McpGateway.initialize(...)`
3. `McpClient.connect()`
4. `McpGatewayToolRegistry.refresh(...)`
5. `ToolUtil.getAllTools(functionList, mcpServerIds)`
6. Agent runtime 把工具 schema 暴露给模型
7. `ToolUtil.invoke(...)` 再把调用路由回 gateway/client

如果不把这 7 层拆开，很多文档都会把“Agent 能调用 MCP”写成一句空话。

## 2. 场景设定

假设我们接一个第三方天气服务：

- serviceId: `weather-http`
- transport: `streamable_http`
- tool: `query_weather`
- Agent runtime: ReAct
- 目标：模型根据用户问题决定是否调用天气工具

配置如下：

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

这里的 `weather-http` 不是备注，而是后续 Agent 白名单要用到的真实 `serviceId`。

## 3. 第 1 段：把第三方服务接进宿主

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();
```

这一步之后，真正发生的不是“网关准备好了”这么简单，而是：

1. gateway 读入配置
2. `McpGatewayClientFactory` 根据 `type` 创建 transport
3. 为 `weather-http` 创建 `McpClient`
4. `client.connect()` 执行 MCP 握手
5. `toolRegistry.refresh(...)` 拉取工具清单

建议你在这一步就先做一次显式检查：

```java
List<Tool.Function> gatewayTools = gateway.getAvailableTools().join();
System.out.println(gatewayTools);
```

如果这里拿不到工具，后面 Agent 再怎么调也不会成功。

## 4. 第 2 段：把 MCP 工具投影到 Agent 可见面

Agent 不是直接读取 gateway 内部状态，而是通过 `ToolUtil.getAllTools(...)` 获取本次请求的工具集合。

构建 Agent 时通常是：

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是天气助手，必要时必须调用工具后再回答。")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
        .options(AgentOptions.builder().maxSteps(4).build())
        .build();
```

这句最重要的不是语法，而是白名单语义：

- `functionList` 为空，说明本次不暴露本地 Function 工具
- `mcpServices = ["weather-http"]`，说明只暴露这一个 MCP 服务的工具

当前 AI4J 不会因为 gateway 里接了很多服务，就自动把它们全部给模型看见。

## 5. 第 3 段：模型发起 tool call 时真正走哪条路

模型在推理中决定调用工具后，不是直接碰 `McpClient`，而是先回到 `ToolUtil.invoke(...)`。

在 MCP 场景里，关键分支是：

1. `ToolUtil.invoke(functionName, argument)`
2. 如果命中用户工具前缀，先尝试 `gateway.callUserTool(...)`
3. 否则进入本地 MCP / Function / 全局 gateway 工具分发
4. 远程第三方 MCP 最终落到 `gateway.callTool(...)`
5. gateway 根据 `tool -> client` 映射找到 `weather-http`
6. `McpClient.callTool(...)` 发起 `tools/call`

因此，Agent 看到的是工具 schema，但真正执行时依然由 Core SDK 的 MCP 运行时兜底。

## 6. 第 4 段：结果怎样回到模型

`McpClient.callTool(...)` 拿到 MCP 响应后，会：

1. 解析 `tools/call` 的返回内容
2. 转成字符串结果
3. 返回给 `ToolUtil`
4. 再交回 Agent runtime
5. Agent 把 tool result 放回模型上下文
6. 模型继续生成最终回答

也就是说，MCP 工具在 Agent 里的角色是：

- 先作为 schema 暴露给模型
- 再作为 tool result 回填给模型

这与本地 Function 工具在 Agent 里的角色是对齐的，只是中间多了一段 MCP 协议链。

## 7. 一份最小可运行示例

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是天气助手，必须先调用工具再回答。")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
        .options(AgentOptions.builder().maxSteps(4).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请查询北京今天天气，并给出穿衣建议")
        .build());

System.out.println(result.getOutputText());
```

这段代码验证的是整条闭环，而不是某个局部 API。

## 8. 为什么有时 gateway 有工具，但 Agent 看不到

这是最常见的集成误判。

排查顺序应该是：

1. `gateway.isInitialized()` 是否为 `true`
2. `gateway.getAvailableTools()` 是否能列出工具
3. `toolRegistry(..., mcpServices)` 是否传了正确的 `serviceId`
4. 是否把 serviceId 和 toolName 混用了

`mcpServices` 传的是服务 ID，不是工具名。

## 9. 为什么模型有时不触发工具

如果 gateway 和 Agent 注册都没问题，但模型仍然不用工具，排查顺序应该是：

1. tool description 是否让模型看懂它能做什么
2. `systemPrompt` 是否明确要求必要时调用工具
3. `maxSteps` 是否过小
4. 用户问题是否真的需要该工具

这已经不是 MCP 连接问题，而是 runtime 提示与推理策略问题。

## 10. 多租户场景如何进入这条链

如果同一个 Agent 宿主要面向多个用户，而每个用户绑定不同第三方 MCP，可以这样接：

```java
gateway.addUserMcpClient("u1001", "weather-http", userClient).join();
```

之后调用链变成：

1. Agent 会话绑定 `userId`
2. `ToolUtil` 优先尝试用户级工具
3. gateway 查 `user_{userId}_tool_{toolName}`
4. 命中则走用户专属 client
5. 未命中再回退全局工具

这里要自己想清楚权限边界：

- 默认实现允许回退
- 强隔离场景通常不应该回退

## 11. Trace 和诊断应该加在哪里

MCP + Agent 问题很多时候不是“错”，而是“不知道卡在哪”。

推荐至少观察这几个点：

- gateway 初始化是否成功
- 工具是否出现在 Agent 暴露列表
- 模型是否真的发出 tool call
- tool call 是否成功返回

如果 Agent runtime 已启用 trace，重点看：

- RUN
- MODEL
- TOOL

这样能很快分清：

- 是模型没决定调用
- 还是 MCP 工具执行失败

## 12. 一份更像回归测试的最小断言

```java
@Test
public void test_mcp_agent_e2e() {
    McpGateway gateway = McpGateway.getInstance();
    gateway.initialize("mcp-servers-config.json").join();

    List<Tool.Function> tools = gateway.getAvailableTools().join();
    Assert.assertFalse(tools.isEmpty());

    Agent agent = Agents.react()
            .modelClient(modelClient)
            .model("doubao-seed-1-8-251228")
            .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
            .options(AgentOptions.builder().maxSteps(4).build())
            .build();

    AgentResult result = agent.run(AgentRequest.builder().input("北京天气").build());
    Assert.assertNotNull(result);
    Assert.assertNotNull(result.getOutputText());
}
```

重点不是断言输出具体文案，而是断言：

- gateway 有工具
- Agent 能执行
- 最终回答非空

## 13. 这页最该记住的结论

MCP 进入 Agent 不是“把一个第三方工具名塞给模型”。

它实际是一条跨 3 层的链：

- Core SDK 的 MCP 连接与治理层
- ToolUtil 的工具投影与调用分发层
- Agent runtime 的推理与工具消费层

把这 3 层分开理解，端到端问题就会容易定位很多。
