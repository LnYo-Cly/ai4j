---
title: "Tool Execution Model"
description: "Breaks the AI4J tool execution model into four stages — discovery and registration, request-level allowlist, provider returning the tool call, and local call routing and execution — and clarifies the built-in/Function/MCP precedence and how results flow back as text."
tags: [concept]
---

# Tool Execution Model

This page is not about "how to declare a tool", but rather about what AI4J actually does once a tool enters the request chain:

- Toolset assembly
- Call routing
- Local execution
- Result flow-back

If you only look at the annotations and ignore the execution model, you might mistakenly conclude that the Core SDK is already a complete agent runtime. The source code is not layered that way.

## 1. Split the execution chain into 4 stages first

A single tool-related request goes through at least the following 4 stages:

1. Discovery and registration
2. Request-level allowlist exposure
3. Provider returns a tool call
4. Local call routing and execution

The first two stages are primarily handled by `ToolUtil`; the third stage enters the provider / listener; only the fourth stage actually lands back on the host for execution.

## 2. `ToolUtil` is the real dispatch center

The most critical entry point is still:

- `tool/ToolUtil.java`

It simultaneously handles:

- Initialization scanning
- Schema aggregation
- Built-in tool interception
- Local Function calls
- Local MCP calls
- `McpGateway` remote calls

So this is not a "helper class", but rather the central router of the current tool execution model.

## 3. What the initialization stage actually scans

`ToolUtil.ensureInitialized()` performs initialization only once, and internally calls:

```java
scanAndRegisterAllTools();
```

And this method currently only scans two categories:

1. `scanFunctionTools()`
2. `scanMcpTools()`

In other words, at initialization time it caches:

- Local Function tools declared via `@FunctionCall`
- Local MCP tools declared via `@McpService/@McpTool`

The built-in coding tools (8 in total: `bash`/`read_file`/`write_file`/`apply_patch`/`glob`/`grep`/`edit`/`update_agents_md`) do not need to be registered via scanning to be executed, because they have fixed implementations in `BuiltInToolExecutor`.

## 4. How the request-level exposure is assembled

### Local Function allowlist

Calling:

```java
ToolUtil.getAllFunctionTools(functionList)
```

returns only the tools corresponding to the `functionList` you explicitly pass in.

### Remote MCP allowlist

Calling:

```java
ToolUtil.getGlobalMcpTools(mcpServerIds)
ToolUtil.getUserMcpTools(mcpServerIds, userId)
```

extracts the tools of the specified services from `McpGateway`.

### Final aggregation entry point

A request ultimately goes through:

```java
ToolUtil.getAllTools(functionList, mcpServerIds)
ToolUtil.getAllTools(functionList, mcpServerIds, userId)
```

These two entry points only merge:

- The local Function tools explicitly passed in
- The MCP service tools explicitly passed in

It does not automatically expose every tool on the classpath to the model just because the class is there.

## 5. When a call returns to the host, what is the real precedence

The precedence of `ToolUtil.invoke(functionName, argument)` is currently roughly:

1. Built-in tool
2. User-level remote MCP tool
3. Local MCP tool
4. Annotation-based Function tool
5. Global `McpGateway` remote tool

This order is very important, because "tools with the same name" will ultimately land on different executors according to this precedence.

The most easily overlooked point is:

- Even if names like `read_file` and `bash` also have `@FunctionCall` classes
- At execution time they are still intercepted by `BuiltInToolExecutor` first

Therefore the exposure layer and the execution layer are not a simple one-to-one correspondence.

### 5.1 Multi-tenant routing: the `user_{userId}_tool_{toolName}` naming convention

The "user-level remote MCP tool" that follows built-in in the precedence is identified not by an extra parameter, but by **function-name encoding**. After the built-in lookup misses, `ToolUtil.invoke(...)` first uses `extractUserIdFromFunctionName(functionName)` to check whether the name matches:

```text
user_{userId}_tool_{toolName}
```

For example, `user_123_tool_create_issue` is parsed into `userId=123`, `toolName=create_issue`, and then goes directly through:

```java
gateway.callUserTool("123", "create_issue", argumentObject).join()
```

This path bypasses the regular local MCP / Function / global gateway lookup and directs the call to the `McpClient` dedicated to that user. Accordingly, on the toolset assembly side, `ToolUtil.getUserMcpTools(mcpServerIds, userId)` / `getAllTools(functionList, mcpServerIds, userId)` only projects the user-level service tools that the user has registered into the current request, so the `user_123_tool_*` names the model sees correspond one-to-one with the user tools it can call.

You can also skip the name encoding and pass `userId` explicitly to invoke:

```java
ToolUtil.invoke("create_issue", argument, "123");   // Equivalent to the routing above
```

Design notes:

- **Isolation relies on the key, not on runtime judgment**: user-level MCP clients are registered into the gateway under `user_{userId}_service_{serviceId}` / `user_{userId}_tool_{toolName}`, so isolation is completed at the mapping layer.
- **User-level takes precedence over global**: once the function name matches `user_..._tool_...`, it will never fall back to the global gateway lookup; tools with the same name will not cross tenants.
- **This is a capability for SaaS / multi-tenant hosts**: single-tenant scenarios have no use for this convention, and the model just gets ordinary tool names.

## 6. Why built-in tools are a special path

`ToolUtil.invoke(...)` first tries, at the very beginning:

```java
BuiltInToolExecutor.invoke(functionName, argument, builtInToolContext)
```

If it returns non-null, the subsequent local Function / MCP routing is skipped entirely.

There are currently 8 built-in tools (see `BuiltInTools.allCodingToolNames()`):

- `bash` — shell commands + background process management (`exec`/`start`/`status`/`logs`/`write`/`stop`/`list`)
- `read_file` — read text files in the workspace or under approved read-only roots
- `write_file` — create / overwrite / append text files
- `apply_patch` — apply structured patches
- `glob` — match file paths with glob patterns
- `grep` — regex search over file contents
- `edit` — exact string replacement
- `update_agents_md` — read and write the `AGENTS.md` memory file

The characteristics of these tools are:

- The schema is defined directly by `BuiltInTools`
- Execution is performed directly by `BuiltInToolExecutor`
- The security boundary is constrained by `BuiltInToolContext`

This is also why they are more like "host-level foundation capabilities" rather than ordinary business functions.

## 7. How annotation-based Function tools are executed

When both built-in and local MCP miss, `ToolUtil` goes through:

```java
invokeFunctionTool(functionName, argument)
```

The execution chain is:

1. Find the cached `functionClass`
2. Find the corresponding `requestClass`
3. `JSON.parseObject(argument, requestClass)`
4. Reflectively invoke `apply(requestObject)`
5. `JSON.toJSONString(result)` on the result

Two implementation details to note here:

- The input parameter is deserialized according to the request class
- The output is ultimately wrapped uniformly as string JSON

So what the upper-layer runtime sees is still a textualized result, not a strongly typed return value.

## 8. How the local MCP tool execution chain differs from Function tools

Local MCP tools go through:

```java
invokeMcpTool(functionName, argument)
```

What differs from Function tools is:

- The parameters are not first mapped to a request class
- Instead they are first parsed into a `Map<String, Object>`
- Then type-converted one by one according to the method parameters

In addition, the local MCP tool name is not the original method name, but an API-friendly name generated by:

```java
generateApiFunctionName(serviceName, toolName)
```

It will:

- Keep only letters, digits, underscores, and hyphens
- Cap at 64 characters
- Add a `tool_` prefix when necessary

This means the final exposed name of a local MCP tool does not necessarily match the Java method name exactly.

## 9. What the remote MCP tool execution chain looks like

Only when all local paths miss does the call land on the gateway:

```java
gateway.callTool(functionName, argumentObject).join()
gateway.callUserTool(userId, toolName, argumentObject).join()
```

So the role of remote MCP tools in the tool execution model is:

- First projected as `Tool.Function` by the gateway
- Then, at execution time, routed by the gateway to the actual `McpClient` based on the `tool -> client` mapping

This also explains why MCP tools "look like tools", but their runtime governance still belongs to the protocol layer.

## 10. How `BuiltInToolContext` enters the execution chain

`ToolUtil` internally maintains a thread-local stack:

- `pushBuiltInToolContext(...)`
- `popBuiltInToolContext()`
- `currentBuiltInToolContext()`

This means built-in tools do not share a single global host configuration; instead, they can be temporarily injected within the current execution context:

- Workspace root
- Extra directories allowed for reading
- File-read and command-execution limits

This is designed in concert with skill lazy-loading and the coding-agent host constraints.

## 11. The real shape of result flow-back

From the Core SDK layer, tool execution results ultimately appear uniformly as:

- `String`

This rule holds for:

- Built-in tool
- Function tool
- Local MCP tool
- Remote MCP tool

The benefit of doing this is that it unifies the consumption interface for the upper-layer runtime; the cost is:

- Structured type information is textualized at this layer
- If the upper layer needs strong structure, it has to parse it again itself

### 11.1 Structured sub-trace: making a tool's internal steps visible

The textualized `output` is fed to the LLM, but "what actually happened inside the tool" (which chunks a RAG retrieval hit, whether reranking was applied, which sources were cited) is equally important for observability. The agent runtime layer provides a structured channel parallel to `output` for this purpose, **without changing Core SDK's string flow-back contract**:

- `TraceableToolExecutor` in `ai4j-agent` (which extends `ToolExecutor`) adds an `Object lastTrace()` that returns the sub-trace produced by the most recent execution of that executor (e.g. `RagResult`, containing `retrievedHits` / `rerankedHits` / `citations`).
- After calling `execute(...)`, the runtime reads `lastTrace()`, writes it to `AgentToolResult.trace`, and lets it flow into `IoCapture`, so that a TOOL node records not only the final string but also the tool's internal steps.
- The LLM still reads only `AgentToolResult.output`; `trace` is for trace / replay / audit consumption only, with zero impact on the model's context.

Concurrency constraint (explicitly required by the interface contract): the runtime may execute multiple tool calls in parallel on the same executor instance, so `lastTrace()` must return the trace produced by the **current thread's** most recent execution. The implementation holds it in a `ThreadLocal`, for example `RagTool.RagToolExecutor` in `ai4j-agent`:

```java
// io.github.lnyocly.ai4j:ai4j-agent:2.4.2
private class RagToolExecutor implements TraceableToolExecutor {
    private final ThreadLocal<RagResult> lastResult = new ThreadLocal<RagResult>();

    @Override
    public AgentToolResult execute(AgentToolCall call) {
        RagResult result = ragService.search(buildQuery(call));
        lastResult.set(result);                 // ponytail: ThreadLocal, per-call trace
        return AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(formatContext(result))  // String for the LLM
                .build();
    }

    @Override
    public Object lastTrace() {
        return lastResult.get();                // Structured trace for IoCapture
    }
}
```

For an ordinary tool (one that does not implement `TraceableToolExecutor`), `trace` is `null` and behavior is exactly as before; only tools that need to expose internal steps implement the interface. This keeps result flow-back uniformly string-based while leaving an optional structured side channel for observability.

## 12. What this layer does not do today

This boundary must be made explicit.

Core SDK is currently not directly responsible for:

- Human approval
- Serialization strategy for side-effecting tools
- Multi-turn retry and error recovery
- Trace persistence
- Checkpoint / compact / resume

What it is responsible for is making "a tool can be seen by the model and lands back on the host for execution" work end-to-end.

## 13. The 5 most easily misunderstood points

### 13.1 "A tool appears in the request" does not equal "it has been executed"

Exposure only means the provider can see it; execution has to wait until the model actually returns a tool call.

### 13.2 `ToolUtil` manages more than just local Function

It also unifies built-in, local MCP, and remote gateway tools.

### 13.3 A built-in tool is not an example of an ordinary Function

Their schemas may look like a Function tool, but at execution time they go through an independent interceptor.

### 13.4 Remote MCP tools are not automatically fully open

:::note
They still have to pass the `mcpServices` allowlist first.
:::

### 13.5 The return value is uniformly a string

Do not mistake this layer for a strongly typed business-call framework.

## 14. The conclusion to remember most from this page

AI4J's current tool execution model is essentially a "unified tool router":

- At initialization, it scans local capabilities
- At request time, it assembles the toolset by allowlist
- At execution time, it routes by the built-in / local / remote precedence
- Results flow back to the upper-layer runtime uniformly as text

Once you understand this chain, the approval, tracing, and long-task governance of Agent or Coding Agent will no longer get mixed up across layers.

## Further reading

- → [ToolUtil API Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/tool/ToolUtil.html) (tool dispatch entry points such as `getAllTools(...)` / `invoke(...)` / `getUserMcpTools(...)`)
