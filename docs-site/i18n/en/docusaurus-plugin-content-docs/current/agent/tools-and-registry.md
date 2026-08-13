---
title: "Tools and Registry"
description: "Breaks down the ai4j-agent tool system: AgentToolRegistry only owns the exposure surface, ToolExecutor owns execution and the permission boundary, how the runtime normalizes, validates, and executes tools and feeds results back into memory, and which layer approval interception belongs on."
tags: [concept]
---

# Tools and Registry

In `ai4j-agent`, the tool system does not really solve "how to expose functions to the model". It solves how to separate four boundaries:

- Which tools the model can see
- Which tools the host is allowed to execute
- Which layer is responsible for structural validation, and which for approval and interception
- How tool results re-enter the Agent loop

If these four concerns are not split apart, the usual outcome is:

- Schema exposure and permission approval get tangled together
- Different runtimes each reinvent their own tool governance
- There is no unified semantics for how to continue reasoning after a tool failure

The design choices in `ai4j-agent` are deliberate:

- `AgentToolRegistry` owns the exposure surface
- `ToolExecutor` owns the execution surface
- `BaseAgentRuntime` owns pulling tool calls into the main loop

## 1. The shortest object graph: see the roles first

Source entry points:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

The minimal object relationship compresses into a single chain:

```text
AgentBuilder
  -> AgentToolRegistry
  -> ToolExecutor
  -> AgentContext
  -> BaseAgentRuntime.runInternal()
  -> AgentToolCallSanitizer
  -> ToolExecutor.execute(call)
  -> AgentMemory.addToolOutput(callId, output)
```

This split matters because it stops "the visible tool surface" and "actual execution authority" from being an implicit side effect of the same object.

## 2. `AgentToolRegistry` does one thing: hand schema to the model

The `AgentToolRegistry` interface is narrow:

```java
public interface AgentToolRegistry {
    List<Object> getTools();
}
```

It does not execute tools, and it does not decide permissions. It answers only:

- Which tool schemas the current Agent intends to pass to the model

Common implementations include:

- `StaticToolRegistry`
- `CompositeToolRegistry`
- `ToolUtilRegistry`
- `AgentTeamToolRegistry`

The core design principle here is: what the model sees is not necessarily what the system will allow to execute.

## 3. `ToolExecutor` is the execution boundary, and the permission boundary

`ToolExecutor` is equally narrow:

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

It answers a different question:

- When the model actually issues a tool call, how does the system execute it

This is also the best place to apply the following governance:

- Permission approval
- allow-list / deny-list
- Argument rewriting
- Audit logging
- Remote proxy
- Sandbox execution
- Retry and timeout control

If you need to explain "how ai4j intercepts tool permission approval", the most accurate answer is:

- The stable interception point for an ordinary Agent tool chain is `ToolExecutor.execute(...)`
- Not `AgentToolCallSanitizer`
- And not some generic hook

### 3.1 The multi-tenant filter for RAG-as-tool must be fixed on the server side

If you expose knowledge base retrieval as a `RagTool`, the tool schema visible to the model should contain only the user query, e.g.:

```json
{"query": "user question"}
```

Filters such as tenant, department, project, and permission scope should not go into the tool input schema for the model to pass itself:

```json
{"query": "user question", "filter": {"tenant": "tenant_a"}}
```

The correct approach is to bind them when constructing the tool on the host side:

```java
Map<String, Object> tenantFilter = new HashMap<String, Object>();
tenantFilter.put("tenant", tenantIdFromSession);

RagTool ragTool = RagTool.builder(ragService)
        .dataset("shared-kb")
        .embeddingModel("text-embedding")
        .topK(5)
        .filter(tenantFilter)
        .build();
```

:::warning Tenant filter must be fixed on the server side
This way the LLM only produces the retrieval query; the real tenant boundary is written into `RagQuery.filter` by the server-side executor. Like ordinary tool permissions, this belongs on the execution boundary, not in parameters the model can negotiate.
:::

## 4. The default Builder wiring is more concrete than it looks

The default logic in `AgentBuilder.build()` is not "automagically wire the tools for you". It is an explicit decision chain.

### 4.1 You did not pass `toolRegistry(...)`

The default goes through:

```java
StaticToolRegistry.empty()
```

This means the model sees no tools at all.

### 4.2 You used the convenience method `toolRegistry(List<String> functions, List<String> mcpServices)`

The Builder will create:

- `ToolUtilRegistry`

Behind that, `ToolUtilRegistry` merges through `ToolUtil.getAllTools(functionList, mcpServerIds)`:

- Local function tools
- MCP service tools

In other words, MCP here is not "a sub-page problem of tools"; it is one source feeding the unified tool exposure surface.

### 4.3 You did not explicitly pass `toolExecutor(...)`

The Builder first extracts tool names from the "base registry", then tries to create a `ToolUtilExecutor`.

This step has two important consequences:

- The default executor only allows executing tool names the registry has already exposed
- If the base registry cannot resolve tool names, the default executor may be `null`

The second point is a common trap. `resolveToolNames(...)` only pulls names out of `Tool`-typed objects; if you supply custom schema objects and do not pass your own `ToolExecutor`, the Builder will not magically know how to execute them.

### 4.4 How subagents get wired in

Once a subagent registry is configured, the Builder will:

- Use `CompositeToolRegistry` to merge the original tool surface with the subagent tool surface
- Use `SubAgentToolExecutor` to wrap the original executor

This shows that subagent governance is not an exception at the registry layer; it is a specialized wrapper on the executor side.

## 5. How the runtime consumes tool calls

The main chain lives in `BaseAgentRuntime.runInternal()`.

### 5.1 The model returns `toolCalls` first

The model client folds the response into `AgentModelResult`, which may contain:

- `outputText`
- `memoryItems`
- `toolCalls`

### 5.2 The runtime normalizes first

`normalizeToolCalls(...)` fills in any missing `callId`; the default format is:

- `tool_step_<step>_<index>`

The point of this step is not cosmetics. It is to guarantee a stable reference when tool results are written back to memory.

### 5.3 Then structural validation

`AgentToolCallSanitizer.validationError(...)` only validates structural legality, e.g.:

- The tool name must not be empty
- `arguments` must be a JSON object
- The required fields for the different `bash` actions exist
- `read_file.path` is not empty
- `apply_patch.patch` is not empty

It answers "does this look like an executable call", not "are you allowed to execute it".

### 5.4 Then it enters the executor

Calls that pass validation all converge on:

```java
toolExecutor.execute(call)
```

A tool exception does not bring down the whole Agent by default. `BaseAgentRuntime.executeTool(...)` wraps the exception into:

- `TOOL_ERROR: {"errorType":"...","error":"...","tool":"...","callId":"..."}`

By default it carries no stack trace. This error result is then fed back to memory and the subsequent turns. This is the classic "model-recoverable failure semantics".

### 5.5 Finally it re-enters memory

Whether it is a normal result or a wrapped error, both call:

```java
memory.addToolOutput(callId, output)
```

Tool execution is therefore not an outer-loop side action; it is part of the loop itself.

## 6. Where approval, interception, and hooks really belong

This is the easiest place to write things ambiguously.

### 6.1 The correct interception point for an ordinary Agent

If you want to do approval or access control, prefer wrapping it around the executor:

```java
ToolExecutor guarded = call -> {
    approvalService.check(call.getName(), call.getArguments());
    auditService.record(call);
    return delegate.execute(call);
};
```

The advantages of this layer are very direct:

- You already hold the normalized tool name
- You already hold the structurally valid argument JSON
- You are still inside the unified Agent loop, so errors, audit, trace, and memory write-back do not get distorted

### 6.2 Why not put it in `AgentToolCallSanitizer`

Because the sanitizer's responsibility is too narrow. Pushing business authorization logic into it leads to:

- Structural errors and permission denials getting conflated into one kind of error
- Different executors being unable to share the same authorization logic
- Misplaced layering, making it harder to extend later

### 6.3 Why not rely on a generic hook

Today `ai4j-agent` does not provide a unified "tool approval hook" abstraction for ordinary Agents.

The places that do have approval/policy concepts are:

- The Team layer's `planApproval` and `hooks`
- The SubAgent layer's `HandoffPolicy`

For an ordinary Agent, tool governance is fundamentally an executor-wrapping problem.

## 7. What `SubAgentToolExecutor` and `AgentTeamToolExecutor` illustrate

These two classes capture the ai4j tool design philosophy very well.

### 7.1 `SubAgentToolExecutor`

It does not shove handoff logic into the registry. Instead it applies stronger governance at execution time, e.g.:

- `allowedTools`
- `deniedTools`
- `maxDepth`
- `timeoutMillis`
- `inputFilter`
- `onDenied`
- `onError`

In other words, subagent does not overturn the "registry owns exposure, executor owns governance" boundary.

### 7.2 `AgentTeamToolExecutor`

It only intercepts `team_*` tools:

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

Other tools are delegated straight to the original executor. If there is no delegate and a member invokes a non-team tool, it throws immediately.

This again proves:

- Team tools are not a separate runtime magic
- They are a wrapper layer on the unified tool execution chain

## 8. What `parallelToolCalls` actually means

`BaseAgentRuntime.runInternal()` executes tools in parallel when two conditions are met:

- `context.getParallelToolCalls() == true`
- The current turn has more than one valid tool call

This imposes a hard requirement on the executor:

- Your `ToolExecutor` must be thread-safe

If the executor reuses mutable state internally, shares temp files, or relies on single-threaded ordering, turning on parallelism will break it. This problem usually does not surface as a model-layer error; it shows up as a tool-layer race condition.

## 9. Typical wiring patterns

### 9.1 Quickly assemble a unified tool surface with `ToolUtil` + MCP

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .toolRegistry(
                java.util.Arrays.asList("queryWeather", "read_file"),
                java.util.Arrays.asList("github", "filesystem")
        )
        .build();
```

Suitable for:

- Tools are already registered in `ToolUtil` or MCP services
- You only want to expose a minimal allowlist to the current Agent

### 9.2 Custom executor for approval and audit

```java
ToolExecutor guardedExecutor = call -> {
    approvalService.requireApproved(call.getName(), call.getArguments());
    auditService.record(call);
    return ToolUtil.invoke(call.getName(), call.getArguments());
};

Agent agent = Agents.builder()
        .modelClient(modelClient)
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .toolExecutor(guardedExecutor)
        .build();
```

Suitable for:

- You do not want permission approval scattered across individual tool functions
- You want rejection, audit, and failure information to be folded into the loop uniformly

### 9.3 Schema and execution fully separated

```java
AgentToolRegistry registry = new StaticToolRegistry(myToolSchemas);

ToolExecutor executor = call -> gateway.execute(call);

Agent agent = Agents.builder()
        .modelClient(modelClient)
        .toolRegistry(registry)
        .toolExecutor(executor)
        .build();
```

Suitable for:

- The schema source is not `ToolUtil`
- Real execution has to go through a remote gateway, sandbox, or proxy process

## 10. Failure boundaries and easy misreadings

### 10.1 Tool results default to string semantics

The return value of `ToolExecutor.execute(...)` is `String`. Complex objects ultimately have to be serialized to a string by you before the model consumes them.

### 10.2 The default executor is only friendly to the default tool system

If you use a custom registry but do not explicitly provide an executor, the Builder most likely cannot help you. The default executor's creation logic assumes it can understand the tool objects in the registry.

### 10.3 The tool allowlist only constrains the default executor

`ToolUtilExecutor` validates `allowedToolNames`, but only for itself. Once you switch to a custom executor, allowlist, deny-list, and approval are all on you.

### 10.4 Tool failure does not terminate the Agent by default

This is usually correct, because the model can still decide to retry, change arguments, or switch strategy based on `TOOL_ERROR`. But if your business requires "certain tools failing must abort immediately", you have to implement that explicitly in the executor, instead of assuming the runtime will do it for you. The default error payload has only `errorType`, `error`, `tool`, and `callId`; no stack trace.

## 11. When debugging, look at these entry points first

When you hit "the model can see tools but cannot invoke them", "approval logic is not working", or "the tool returned but the next turn did not use it", look first at:

- What `toolRegistry` and `toolExecutor` `AgentBuilder.build()` ultimately produced
- Whether `AgentToolCallSanitizer.validationError(...)` blocked the call as a structural error
- Whether `ToolExecutor.execute(...)` actually reached the target logic
- Whether `BaseAgentRuntime.executeTool(...)` wrapped the exception into a `TOOL_ERROR`
- Whether `memory.addToolOutput(...)` got a stable `callId`

These spots get you closer to the root cause than staring at the final natural-language output.

## 12. Further reading

1. [Memory and State](/docs/agent/memory-and-state)
2. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
3. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/agent-teams)
5. [Trace Observability](/docs/agent/trace-observability)
