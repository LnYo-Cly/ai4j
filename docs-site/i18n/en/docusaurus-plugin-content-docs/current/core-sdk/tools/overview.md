---
title: "Tools Overview"
description: "Breaks down the four layers of the AI4J tools subsystem: tool declaration, request-scoped allowlist, provider schema projection, and local execution loopback, clarifying the complete execution chain and capability boundary with ToolUtil as the dispatch center."
tags: [concept]
---

# Tools Overview

This chapter is not about the one-liner "models can call functions"; it is about how AI4J organizes executable capabilities into a formal subsystem.

From the source, `Tools` comprises at least 4 layers:

- tool declaration
- request-scoped allowlist exposure
- provider-side schema projection
- local execution and result loopback

Covering only the first layer of annotations would thin out the real execution chain.

## 1. Where `Tools` actually sits in the foundation

`Tools` belongs to `Core SDK`; it is not an appendage that only `Agent` or `Coding Agent` provides.

The reason is straightforward:

- `ChatCompletion` requests can attach tools
- `ResponseRequest` requests can also attach tools
- tool schema assembly and the local execution bridge both live in the `ai4j/` module

In other words, even if you never touch the upper runtime, the moment you want the model to call in-JVM capabilities, this chapter is already relevant to you.

## 2. What this subsystem actually solves

It answers 4 questions:

1. How a capability is declared as a model-callable object
2. Which tools are actually exposed for a given request
3. How these tools are uniformly converted into provider-recognizable schema
4. After the model emits a tool call, how the invocation lands back on the local execution chain

So `Tools` is not "annotation syntax" but a complete bridge from declaration to execution.

## 3. What tool sources exist in AI4J today

From the `ToolUtil` implementation, the "tool surface" that ultimately reaches the model is not of a single kind.

### 3.1 Built-in coding tools

Provided by `BuiltInTools` / `BuiltInToolExecutor`, currently 8 in total (see `BuiltInTools.allCodingToolNames()`):

- `bash` —— execute shell commands and manage background processes (`start`/`status`/`logs`/`write`/`stop`/`list`)
- `read_file` —— read text files under the workspace or under the root of an approved read-only skill
- `write_file` —— create, overwrite, or append text files
- `apply_patch` —— apply structured patches to workspace files
- `glob` —— fast-match file paths by glob pattern (e.g. `**/*.java`)
- `grep` —— search file contents by regex (ripgrep-style output)
- `edit` —— perform precise string replacement within a file
- `update_agents_md` —— read and write the project's `AGENTS.md` memory file

These tools already have fixed schema and fixed executors, and do not go through ordinary business-function reflection. Among them `bash`/`read_file`/`glob`/`grep` form the read-only set (`BuiltInTools.readOnlyCodingToolNames()`), making it easy for the host to expose them in a tiered "read-only / writable" manner.

### 3.2 Annotation-based Function tools

Declared by:

- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`

then scanned, cached, and invoked by `ToolUtil`.

### 3.3 Local MCP tool projection

Local `@McpService` / `@McpTool` can also be scanned by `ToolUtil.scanMcpTools()` and converted into ordinary `Tool.Function` views.

Note that what is discussed here is "the final projection into tool schema"; it does not mean MCP conceptually belongs to the `Tools` subsection. The MCP lifecycle, transport, and gateway governance still belong to an independent protocol layer.

### 3.4 Gateway-managed remote MCP tools

When `McpGateway` is initialized, `ToolUtil` can also merge the remote service tools in the gateway into the tool set of the current request.

This is why the "single tool list" the model sees may, at the implementation level, actually come from several different capability sources.

### 3.5 Third-party extension plugins (SPI)

The four categories above are all built-in sources of the foundation. To let a **third-party jar** also inject tools (or commands, Skills, Prompts, Guardrails), the path is the extension SPI — `ServiceLoader` discovery plus the `ExtensionRegistry` `discover/enable/exposeTool` three-stage gate, which **does not automatically expose plugin tools to the model by default**. This injection path complements the annotation/allowlist mechanism in this chapter; see [Plugin Packages](/docs/core-sdk/extension/plugin-packages).

## 4. The single most important spine

If you only remember one chain, start with this:

```text
tool declaration
  -> request-scoped whitelist
    -> provider tool schema
      -> model emits tool call
        -> ToolUtil routes invocation
          -> result returns as string payload
```

There are two most important dividing points in this chain:

- before the provider: it is a tool exposure problem
- after the provider: it is a tool execution problem

Many docs conflate these two into one, which makes them very confusing to read.

## 5. `ToolUtil` is the center of the entire subsystem

If you only read one file, read:

- `ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java`

It is responsible for at least 5 things:

1. scanning annotation tools
2. scanning local MCP tools
3. generating the tool allowlist for the current request from `functions` / `mcpServices`
4. the unified execution entry point `invoke(...)`
5. routing invocations across built-in, Function, local MCP, and remote MCP

Therefore `ToolUtil` is not a "small utility class" but the dispatch center of the entire current tool subsystem.

## 6. The relationship between `Tools` and `Function Calling`

`Function Calling` is the spine of this chapter, but not all of it.

More precisely:

- `Function Calling` is about the bridge between the model and executable capabilities
- `Tools` is about how AI4J unifies capabilities from different sources onto this bridge

So `Function Calling` is the core mechanism of this chapter, and `Tools` is the larger capability surface.

## 7. The boundary between `Tools` and `Skill`

This point must be kept very clear.

- `Tool` is responsible for execution
- `Skill` is responsible for explanation

The model reading a `Skill` does not mean it has gained execution permission; the model seeing a `Tool` does not mean it understands the best way to work.

These two are often used together, but their responsibilities differ.

## 8. The boundary between `Tools` and `MCP`

This point must not be conflated either.

- `Tools` focuses on the execution surface that the model can ultimately call
- `MCP` focuses on how external capabilities integrate into the host through a protocol

Before the request is sent, MCP tools are indeed converted into `Tool.Function`-style schema; but that is only a projection result, not an ownership relationship.

## 9. The real limitations of the current subsystem

From the source, a few limitations should be stated up front.

### 9.1 Reflection scanning is classpath-level

`ToolUtil` scans `@FunctionCall` and `@McpService` based on `Reflections`. This fits small-to-medium tool sets but is not an infinitely scalable registry.

### 9.2 Local tool return values ultimately go through strings

Whether built-in, Function, or gateway calls, what `ToolUtil.invoke(...)` ultimately returns is a `String`. What the upper runtime sees is a textualized result, not a strongly typed Java object.

### 9.3 Execution governance does not close at this layer

Core SDK is responsible for:

- tool organization
- tool exposure
- tool bridging

It is not directly responsible for:

- human-in-the-loop approval
- multi-step retry strategies
- long-task checkpointing
- host-level permission policies

These must be taken over by a higher-layer runtime.

## 10. Who this chapter best fits

### Only want to expose local JVM functions

Focus on:

- [Function Calling](/docs/core-sdk/tools/function-calling)
- [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)

### Want to understand how execution happens after the model returns a tool call

Focus on:

- [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)

### Care about the exposure boundary and host security

Focus on:

- [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)

## 11. Recommended reading order

1. [Function Calling](/docs/core-sdk/tools/function-calling)
2. [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
3. [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
4. [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
5. [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)

## 12. The conclusion most worth remembering from this page

AI4J's `Tools` is not a handful of annotations, but a foundation subsystem that unifies multiple capability sources into a model-callable execution surface.

What it actually solves is:

- what capabilities can be declared
- what is exposed for the current request
- how invocations return to the host

rather than every governance concern of the upper runtime.
