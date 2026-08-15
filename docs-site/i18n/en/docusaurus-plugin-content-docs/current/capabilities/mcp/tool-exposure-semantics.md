---
sidebar_position: 5
title: "Tool Exposure Semantics and Security Boundaries"
description: "Clarifies the security boundaries of MCP tool exposure: getAllTools explicit allowlist and getLocalMcpTools local enumeration are two distinct semantics, and invocation priority and exposure surface are two separate concerns, with a code review checklist attached."
tags: [concept]
---

# Tool Exposure Semantics and Security Boundaries

This page covers the most commonly misunderstood aspect of MCP in AI4J:

> "A tool can be invoked" and "a tool should be exposed to the model" are not the same thing.

If this boundary is not clarified first, MCP integration easily drifts from "capability augmentation" into "uncontrolled exposure surface".

## 1. First, separate the three tool sources

In the current AI4J implementation, there are at least three sources of MCP-related tools:

### 1.1 Local MCP tools

Sourced from:

- `@McpService`
- `@McpTool`

Scanned via reflection by `ToolUtil.scanMcpTools()` and registered into a local cache.

### 1.2 Remote / gateway MCP tools

Sourced from:

- `McpGateway`
- a connected `McpClient`

### 1.3 Legacy Function tools

Sourced from:

- `@FunctionCall`

This shows that the AI4J tool plane is not MCP-only; MCP and legacy Function tools coexist.

## 2. What `getAllTools(...)` actually means

The most important entry point right now is:

- `ToolUtil.getAllTools(functionList, mcpServerIds)`

Its real semantics are unambiguous:

- It only merges the function list you explicitly pass in
- It only merges the MCP server list you explicitly pass in

That is, it is not "automatically opening up every tool available in the system".

```java
// Only expose the function and MCP server you explicitly name to the model — allowlist semantics
List<Tool> exposed = ToolUtil.getAllTools(
        List.of("queryWeather"),        // local function tool
        List.of("weather-service"));    // MCP server id
```

This is a very important security design decision.

## 3. `getLocalMcpTools()` is not the same semantics

Another entry point that is easy to conflate with it is:

- `ToolUtil.getLocalMcpTools()`

It enumerates every `@McpService / @McpTool` capability found by the local scan.

It is better suited for:

- Publishing local MCP capabilities
- Enumerating local capabilities
- Organizing the capabilities exposed by an MCP server

```java
// Enumerate every @McpService/@McpTool capability found by the local scan — not an allowlist, an inventory
List<Tool> local = ToolUtil.getLocalMcpTools();
```

It should not be treated as equivalent to:

- What a regular agent should open up for the current request

So:

- `getAllTools(...)` is effectively a call-site allowlist
- `getLocalMcpTools()` is effectively a local capability enumeration surface

## 4. Why "explicit pass-in" is more sensible than "open everything by default"

If everything were injected by default, it would cause at least three classes of problems.

### 4.1 The permission surface grows silently

The caller thinks they have only opened up a few tools, while the model actually receives more capabilities.

### 4.2 Debugging becomes blurry

Once a tool conflict or mis-invocation occurs, it is hard to tell immediately whether it came from:

- A local MCP tool
- A remote gateway tool
- Or a legacy Function tool

### 4.3 Approval and audit cannot be stably closed

Only when the exposure surface is explicitly declared at the call site do subsequent allowlists, approvals, traces, and regression audits have a stable foundation.

## 5. Invocation priority and exposure surface are two separate concerns

This point is critical; many documents conflate the two.

From the real logic of `ToolUtil.invoke(...)`, the invocation priority is roughly:

1. built-in tool
2. user-level MCP tool
3. local MCP tool
4. legacy Function tool
5. global MCP gateway tool

This means:

- Which tool actually gets executed depends on invocation priority
- Whether a tool should be exposed to the model depends on the allowlist choice in `getAllTools(...)`

These are two different questions.

## 6. How local MCP tools get scanned out

`ToolUtil.scanMcpTools()` will:

1. Reflectively scan classes annotated with `@McpService`
2. Find methods annotated with `@McpTool` within them
3. Generate API-friendly tool ids
4. Put them into the local `mcpToolCache`

So local MCP tools are not hand-registered in some fixed list; they enter the cache through annotation scanning.

### What this implies

:::warning High-risk @McpTool is visible in the local enumeration surface by default
If you mark a high-risk capability as `@McpTool`, it is naturally visible within the local capability enumeration surface.

This is exactly why "whether to expose it to the model" must continue to be controlled by the upper-layer allowlist.
:::

## 7. Why gateway tool exposure also introduces a serviceId

Remote MCP tools are not flattened directly.

The reason `getAllTools(functionList, mcpServerIds)` requires `mcpServerIds` is:

- It needs to make explicit which MCP services you allow tools to be pulled from in this round

This turns the tool exposure surface from "by global default" into "by service allowlist".

### This is more obvious in user-level scenarios

The current gateway key rules include:

- `user_{userId}_service_{serviceId}`
- `user_{userId}_tool_{toolName}`

This shows that user isolation is not an ad-hoc decision either; it is baked into the keys and mapping rules.

## 8. When to use which exposure approach

### Regular agent / chat scenarios

Prefer:

- `getAllTools(functionList, mcpServerIds)`

Because what you want is a request-level allowlist.

### MCP server publishing scenarios

You will more often care about:

- `getLocalMcpTools()`

Because what you want to organize is the locally publishable capabilities, not what to open up for the current conversation.

### Gateway governance scenarios

You should care more about:

- serviceId selection
- user-level vs global-level mapping
- tool conflicts and source governance

## 9. What to actually check during code review

The most practical part of this page is really the review checklist.

### 9.1 Is the exposure surface explicit

Check:

- Whether the call site explicitly passes `functionList`
- Whether it explicitly passes `mcpServerIds`

### 9.2 Is the local enumeration entry point being misused

Check:

- Whether `getLocalMcpTools()` is being used directly as the default tool set for a regular agent

### 9.3 Are there naming conflicts

Check whether the following may collide, causing invocation priority to behave unexpectedly:

- Local MCP tool id
- Function tool name
- Remote gateway tool name

### 9.4 Are high-risk tools subject to secondary control

Check whether the following carry extra approval or allowlist gating:

- File system write operations
- Browser automation operations
- External service mutation-style calls

## 10. The final boundary judgment

If you remember only one sentence:

- `getAllTools(...)` answers "what to open up this time"
- `getLocalMcpTools()` answers "what local MCP capabilities exist"
- `ToolUtil.invoke(...)` answers "who actually executes it in the end"

Keep these three layers separate, and MCP tool governance will not descend into confusion.
