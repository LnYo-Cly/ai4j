---
sidebar_position: 2
title: "MCP Usage Paths and Scenario Selection"
description: "Splits MCP into three paths by the problem you want to solve: integrating an existing MCP, governing multiple MCPs, and publishing your own Java capabilities as an MCP; gives the key objects and reading order for each path."
tags: [concept]
---

# MCP Usage Paths and Scenario Selection

When you first read the MCP docs, it is easy to conflate three completely different problems into one:

- I want to connect to someone else's MCP
- I want to manage many MCPs at the same time
- I want to publish my own capabilities as an MCP

The goal of this page is to pull these three threads apart and tell you what you should actually look at first for each one.

## 1. Pick a path by "the problem you want to solve" first

Do not read in table-of-contents order; route by problem type first.

### Path A: You only want to integrate an existing MCP

This is the most common starting point.

Typical goals:

- Connect to a remote or local MCP server
- List its tools
- Make one `tools/call`
- Then decide whether to wire it into an Agent or a Coding Agent

The key objects on this path are:

- `McpTransport`
- `McpClient`
- `TransportConfig`

Further reading:

1. [MCP Transport Types In Depth](/docs/capabilities/mcp/transport-types)
2. [MCP Client Integration (Single-Service Mode)](/docs/capabilities/mcp/client-integration)
3. [Integrating Third-Party MCP (All Approaches)](/docs/capabilities/mcp/third-party-mcp-integration)

### Path B: You already have more than one MCP and need governance

When you start connecting to, at the same time:

- GitHub
- a browser
- a database
- internal APIs

and you also have to think about:

- user isolation
- dynamic start/stop
- configuration reload
- tool-source governance

you should no longer stay in the "just build a few more `McpClient`s" mindset.

The key objects on this path are:

- `McpGateway`
- `McpGatewayToolRegistry`
- `McpConfigSource`

Further reading:

1. [MCP Gateway Management](/docs/capabilities/mcp/gateway-management)
2. [Tool Exposure Semantics and Security Boundaries](/docs/capabilities/mcp/tool-exposure-semantics)
3. [Integrating Third-Party MCP (All Approaches)](/docs/capabilities/mcp/third-party-mcp-integration)

### Path C: You want to publish your own Java capabilities as an MCP

This is the opposite direction from "connecting to someone else's MCP".

Typical goals:

- Publish internal service methods as Tools
- Publish business data as Resources
- Publish templates as Prompts
- Decide whether to serve them over stdio, SSE, or streamable HTTP

The key objects on this path are:

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpServerFactory`

Further reading:

1. [Build and Publish an MCP Server](/docs/capabilities/mcp/build-your-mcp-server)
2. [MCP Transport Types In Depth](/docs/capabilities/mcp/transport-types)
3. [Tool Exposure Semantics and Security Boundaries](/docs/capabilities/mcp/tool-exposure-semantics)

## 2. Each path has a completely different system boundary

This is the most important decision point.

### Single-service client path

You mainly care about:

- whether the transport can connect
- whether the target peer uses modern stateless HTTP or a legacy initialization handshake
- whether tools/resources/prompts can be fetched correctly

### Gateway path

You mainly care about:

- which tool belongs to which client
- how user-level and global-level isolation works
- how configuration changes take effect dynamically

### Server path

You mainly care about:

- which local capabilities can be published externally
- what the published contract looks like
- how local annotations map to externally exposed MCP capabilities

If you do not separate these three kinds of questions first, both the docs and the implementation will mix layers.

## 3. When you should not rush into a Gateway

As long as you are currently in any of the stages below, do not adopt a gateway first:

- you are only integrating one MCP
- you have not yet completed a single `connect -> getAvailableTools -> callTool` end to end
- you are still unclear on the transport differences

The reason is straightforward:

- single-service problems should solve transport and protocol profile first
- multi-service problems should then solve governance and mapping

Adopting a gateway too early only stacks connection problems on top of governance problems.

## 4. When you should adopt a Gateway immediately

As soon as any of the following applies, you should no longer stay in the single-client mindset:

- there is more than one service
- you need to isolate tools per user
- you need to add/remove services dynamically
- you need a unified view of tool sources

Because, looking at the source, `McpGateway` is no longer a "client list"; it is:

- an independent tool registry
- a key scheme
- a binding to a configuration source
- a multi-client dispatch entry point

## 5. When to consider Tool exposure semantics instead of connectivity alone

Many people, once they have an MCP connected, wire it straight into an Agent. This is not stable.

In AI4J, you should confirm first:

- whether `getAllTools(functionList, mcpServerIds)` exposes only the tools you explicitly want
- whether you have mistakenly used `getLocalMcpTools()` in a normal Agent consumption scenario
- whether local MCP tools, Function tools, and remote gateway tools have naming conflicts

In other words, the second question after connectivity is not "can it be called", but rather "should it be exposed to the model".

## 6. The relationship between MCP and Agent / Coding Agent

They often appear together, but they are not the same layer.

More precisely:

- `MCP` is responsible for capability connection and publishing
- `Agent` is responsible for how these capabilities are consumed at reasoning time
- `Coding Agent` is responsible for how the host environment loads and governs these capabilities

Therefore:

- get MCP itself working first
- then wire in Agent / Coding Agent

is the more stable path.

## 7. A minimal decision table

| Your problem | Which path to take first |
| --- | --- |
| I only want to connect to one existing MCP | Single-service Client |
| I need to manage multiple MCPs at the same time | Gateway |
| I want to publish Java capabilities to others | MCP Server |
| I am worried tools are exposed too broadly | Tool Exposure |

## 8. Recommended starting order

If you are completely unsure how to begin right now, the recommended order is:

1. Connect to one MCP in single-service mode first
2. Then understand transport selection
3. Then move into tool exposure semantics
4. Finally do gateway or server publishing

The benefit of this order: you solve "can connect" first, then "can govern", and finally "can publish".
