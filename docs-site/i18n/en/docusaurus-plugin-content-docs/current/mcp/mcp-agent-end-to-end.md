---
sidebar_position: 7
title: "MCP and Agent Integration in Practice (End-to-End)"
description: "Break down the 7-layer execution chain a third-party MCP travels from config file into the Agent reasoning loop until it is finally invoked by the model, covering projection, call dispatch, multi-tenant fallback, and trace diagnostic points."
tags: [how-to]
---

# MCP and Agent Integration in Practice (End-to-End)

This page is not "weather assistant demo copy"; it breaks open the execution chain after MCP actually enters the Agent runtime.

There is only one question to answer:

> How does a third-party MCP service travel from the config file into the Agent reasoning loop and ultimately get called by the model?

## 1. First, the full execution chain

In AI4J, wiring MCP into an Agent passes through at least 7 layers:

1. `mcp-servers-config.json`
2. `McpGateway.initialize(...)`
3. `McpClient.connect()`
4. `McpGatewayToolRegistry.refresh(...)`
5. `ToolUtil.getAllTools(functionList, mcpServerIds)`
6. The Agent runtime exposes the tool schema to the model
7. `ToolUtil.invoke(...)` then routes the call back to the gateway/client

Without breaking these 7 layers apart, many docs reduce "the Agent can call MCP" to an empty phrase.

## 2. Scenario setup

Suppose we wire in a third-party weather service:

- serviceId: `weather-http`
- transport: `streamable_http`
- tool: `query_weather`
- Agent runtime: ReAct
- Goal: the model decides whether to call the weather tool based on the user's question

Configuration:

```json
{
  "mcpServers": {
    "weather-http": {
      "type": "streamable_http",
      "url": "http://127.0.0.1:8000/mcp",
      "protocolProfile": "AUTO",
      "enabled": true
    }
  }
}
```

Here `weather-http` is not a comment but the real `serviceId` the Agent allowlist will use later. `protocolProfile` is read by the Gateway configuration model; the default `AUTO` only performs a limited compatibility probe via `server/discover` against Streamable HTTP and will not automatically switch the endpoint to HTTP+SSE.

## 3. Segment 1: Wiring the third-party service into the host

```java
McpGateway gateway = McpGateway.getInstance();
gateway.initialize("mcp-servers-config.json").join();
```

After this step, what actually happens is not as simple as "the gateway is ready"; rather:

1. The gateway reads the configuration
2. `McpGatewayClientFactory` creates the transport based on `type`
3. An `McpClient` is created for `weather-http`
4. `client.connect()` runs the lifecycle corresponding to the profile: AUTO discovers a modern peer or enters the legacy initialization handshake
5. `toolRegistry.refresh(...)` pulls the tool list

It is recommended to perform an explicit check right at this step:

```java
List<Tool.Function> gatewayTools = gateway.getAvailableTools().join();
System.out.println(gatewayTools);
```

If no tools are obtained here, no amount of calling by the Agent later will succeed.

## 4. Segment 2: Projecting MCP tools onto the Agent's visible surface

The Agent does not read the gateway's internal state directly; it obtains the tool set for the current request via `ToolUtil.getAllTools(...)`.

Building an Agent typically looks like:

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是天气助手，必要时必须调用工具后再回答。")
        .toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
        .options(AgentOptions.builder().maxSteps(4).build())
        .build();
```

The most important thing here is not the syntax but the allowlist semantics:

- `functionList` is empty, meaning no local Function tools are exposed this time
- `mcpServices = ["weather-http"]`, meaning only this one MCP service's tools are exposed

AI4J does not automatically expose every service registered in the gateway to the model just because many are connected.

## 5. Segment 3: The actual path when the model issues a tool call

After the model decides to call a tool during reasoning, it does not touch `McpClient` directly; it first returns to `ToolUtil.invoke(...)`.

In the MCP scenario, the key branches are:

1. `ToolUtil.invoke(functionName, argument)`
2. If it matches a user tool prefix, try `gateway.callUserTool(...)` first
3. Otherwise, enter local MCP / Function / global gateway tool dispatch
4. Remote third-party MCP eventually lands at `gateway.callTool(...)`
5. The gateway finds `weather-http` via the `tool -> client` mapping
6. `McpClient.callTool(...)` issues `tools/call`

Therefore, what the Agent sees is the tool schema, but actual execution is still backed by the Core SDK's MCP runtime.

## 6. Segment 4: How results return to the model

After `McpClient.callTool(...)` receives the MCP response, it will:

1. Parse the return content of `tools/call`
2. Convert it to a string result
3. Return it to `ToolUtil`
4. Hand it back to the Agent runtime
5. The Agent puts the tool result back into the model context
6. The model continues generating the final answer

In other words, the MCP tool plays two roles inside the Agent:

- First, exposed to the model as a schema
- Then, fed back to the model as a tool result

This aligns with the role of local Function tools inside the Agent, except there is an extra MCP protocol link in between.

## 7. A minimal runnable example

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

This code validates the entire closed loop, not some local API.

## 8. Why the gateway sometimes has tools but the Agent can't see them

This is the most common integration misjudgment.

The troubleshooting order should be:

1. whether `gateway.isInitialized()` is `true`
2. whether `gateway.getAvailableTools()` can list tools
3. whether `toolRegistry(..., mcpServices)` was passed the correct `serviceId`
4. whether serviceId and toolName were conflated

:::note mcpServices takes service IDs
`mcpServices` takes service IDs, not tool names.
:::

## 9. Why the model sometimes doesn't trigger a tool

If gateway and Agent registration are both fine but the model still doesn't use the tool, the troubleshooting order should be:

1. whether the tool description lets the model understand what it can do
2. whether the `systemPrompt` explicitly requires calling the tool when necessary
3. whether `maxSteps` is too small
4. whether the user's question actually needs the tool

This is no longer an MCP connection issue but a runtime prompt and reasoning strategy issue.

## 10. How the multi-tenant scenario enters this chain

If the same Agent host must serve multiple users, and each user binds a different third-party MCP, wire it like this:

```java
gateway.addUserMcpClient("u1001", "weather-http", userClient).join();
```

The call chain then becomes:

1. The Agent session binds a `userId`
2. `ToolUtil` tries user-level tools first
3. The gateway looks up `user_{userId}_tool_{toolName}`
4. On a hit, it goes through the user's dedicated client
5. On a miss, it falls back to global tools

You must think through the permission boundary yourself here:

:::warning Multi-tenant falls back to global tools by default
- The default implementation allows fallback
- Strong-isolation scenarios usually should not fall back
:::

## 11. Where to add tracing and diagnostics

MCP + Agent problems are often not "wrong" but "don't know where it's stuck".

It is recommended to observe at least these points:

- whether gateway initialization succeeded
- whether the tool appears in the Agent's exposed list
- whether the model actually issued a tool call
- whether the tool call returned successfully

If the Agent runtime has tracing enabled, focus on:

- RUN
- MODEL
- TOOL

This lets you quickly distinguish:

- whether the model didn't decide to call
- or the MCP tool execution failed

## 12. A minimal assertion that resembles a regression test

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

The point is not to assert specific output text, but to assert:

- the gateway has tools
- the Agent can execute
- the final answer is non-empty

## 13. The takeaway to remember from this page

MCP entering the Agent is not "sticking a third-party tool name at the model".

It is in fact a chain spanning 3 layers:

- The Core SDK's MCP connection and governance layer
- The ToolUtil tool projection and call dispatch layer
- The Agent runtime's reasoning and tool consumption layer

Understanding these 3 layers separately makes end-to-end issues much easier to locate.
