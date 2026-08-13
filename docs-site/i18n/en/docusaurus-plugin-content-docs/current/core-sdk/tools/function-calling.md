---
title: "Function Calling"
description: "Explains the Function Calling execution chain at the AI4J foundation layer: declaring tools with annotations, generating provider tool schemas via ToolUtil against an allowlist, mounting tools onto a request and reading tool call results back, and the boundary between this layer and MCP and Agent."
tags: [concept]
---

# Function Calling

`Function Calling` is the most central execution chain in the AI4J foundation layer. It is not simply "exposing a Java method to the model"; rather, it stably converts local capabilities into a schema the model can understand, then hands the call results back to the current runtime for processing.

If this page does not make the picture clear, everything that follows — `Tool`, `Skill`, `MCP`, `Agent`, `Coding Agent` — will look like a tangle of "model extensions".

## 1. Positioning first

In the AI4J architecture, `Function Calling` is responsible for exactly three things:

1. Declaring what a tool is
2. Deciding which tools a given request exposes
3. Mounting the tool schema into the model request and reading the tool call response

It is **not** responsible for:

- Approval
- Permission checks
- Multi-step state progression
- checkpoint / resume
- Long-running task governance

All of these belong to higher-layer runtimes.

So you can understand it as:

- `Function Calling` is the "tool protocol bridge" at the foundation layer
- `Agent` / `Coding Agent` is the "tool governance host" at the upper layer

## 2. Where the source entry points are

The key entry points for this chain are highly concentrated:

- Annotation definition: `ai4j/src/main/java/io/github/lnyocly/ai4j/annotation/FunctionCall.java`
- Request object marker: `FunctionRequest.java`
- Parameter field marker: `FunctionParameter.java`
- Tool scanning and schema generation: `ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java`
- `Chat` request mount point: `platform/openai/chat/entity/ChatCompletion.java`
- `Responses` request mount point: `platform/openai/response/entity/ResponseRequest.java`

You can summarize this mechanism as:

> AI4J describes tools with annotations, and `ToolUtil` resolves the local function allowlist into a unified tool schema before the request is sent. `Chat` and `Responses` share this same chain.

## 3. How a tool is declared

The recommended way to declare a tool in AI4J is not "hand-writing a JSON schema", but pinning the schema down with Java types and annotations.

A typical tool looks like this:

```java
@FunctionCall(name = "queryWeather", description = "Query the weather forecast for a target location")
public class QueryWeatherFunction {

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Target city")
        private String city;

        @FunctionParameter(description = "Number of days to query")
        private Integer days;
    }
}
```

In this definition, three layers of information map respectively to:

- `@FunctionCall`: tool identity
- `@FunctionRequest`: parameter object
- `@FunctionParameter`: field description and required semantics

The benefit here is not "the annotations look tidy", but that **the Java type system and the model schema are bound together**.

## 4. What `ToolUtil` actually does

`ToolUtil` is the core of this chain.

It mainly does four things:

1. Scans classes marked with `@FunctionCall`
2. Finds the inner `@FunctionRequest` class
3. Converts the `@FunctionParameter` on each field into schema
4. Assembles the actual `tools` according to the current request's allowlist

You can see this most clearly from these methods:

- `scanFunctionTools()`
- `getFunctionEntity(...)`
- `setFunctionParameters(...)`
- `getAllTools(...)`

Here `setFunctionParameters(...)` maps field types into the parameter descriptions a provider can accept; enums, strings, numbers, booleans, and so on are all translated into a unified schema structure.

This is why AI4J's `Function Calling` is not "naively reflecting over class names", but has a deliberate schema-generation layer.

## 5. How tools are mounted onto a request

Tools are not automatically exposed in full. The request side must specify them explicitly:

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(memory.toChatMessages())
        .functions("queryWeather")
        .build();
```

Or in `Responses`:

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4.1")
        .input(memory.toResponsesInput())
        .functions("queryWeather")
        .build();
```

What matters most here is not the builder syntax, but the **allowlist semantics**:

- The model sees only the tool names you pass in
- Tools you do not pass in should not be exposed by default, even if the class is on the classpath

This is also AI4J's current safe default.

## 6. How a single call actually runs

Stringing the whole chain together, the flow is actually quite clear:

1. You declare the tool class
2. At runtime, `@FunctionCall` is detected by scanning
3. You select the tools needed for this call via `functions(...)` in the request
4. The provider service calls `ToolUtil.getAllTools(...)` before sending
5. Local functions are converted into the provider `tools` payload
6. The model returns tool call / function arguments
7. The current runtime decides whether to execute automatically or pass the tool call transparently up to the upper layer

The step most easily misunderstood here is step 7.

Core SDK takes care of wiring up "a tool can be invoked by the model"; but **whether to execute automatically** is a runtime concern, not the sole responsibility of `Function Calling` itself.

## 7. Relationship with `Chat` and `Responses`

Both `ChatCompletion` and `ResponseRequest` keep two sets of helper fields:

- `functions`
- `mcpServices`

These are not sent to the provider as-is; they are first resolved into the actual `tools` structure.

The difference is:

- The `Chat` side leans toward message-style tool call consumption
- The `Responses` side leans toward event-style tool call / args consumption

But underneath, both share the same foundation-layer tool exposure chain.

This is why `Function Calling` should not be understood as "an OpenAI Chat feature", but rather as **the unified tool bridge of the AI4J Core SDK**.

## 8. How it relates to `MCP`

This is also the easiest place to get confused.

The relationship between local `Function Calling` and `MCP` is not substitution, but parallel coexistence:

- Local `Function Calling`: exposes capabilities inside the current JVM process
- `MCP`: integrates external service capabilities

By the time tools are "sent to the model", `MCP` tools are ultimately expanded into the same `Tool.Function`-style schema, so **the runtime behavior is similar**; conceptually, though, `MCP` is a protocol integration layer, not a local function declaration layer.

## 9. Caveats

### 9.1 Assuming the model can directly see a tool class just because it is on the classpath

:::warning
Incorrect. AI4J currently defaults to an explicit allowlist; without passing `functions(...)`, a tool should not be exposed.
:::

### 9.2 Treating `Function Calling` as a complete agent loop

Incorrect. It only covers tool schema and request mounting; it does not automatically fill in approval, retry, or state progression.

### 9.3 Designing parameter objects that are too complex

Models are not naturally friendly toward deeply nested parameters. Tool parameters are best kept flat, clear, and action-oriented.

### 9.4 Exposing side-effect tools and query tools together

:::warning
This exposes an overly large execution surface to the model. File-writing, command-executing, and remote-mutation tools should be governed separately.
:::

## 10. When to stop at this layer, and when to upgrade

Staying at the Core SDK's `Function Calling` is usually enough as long as you are in any of these situations:

- You do not have many local Java tools
- The goal is just to wire up the model and tools
- You do not yet need complex approval or long-running task orchestration

When you start needing:

- Tool approval
- Multi-tool routing governance
- checkpoint / compact / resume
- Workspace-level execution isolation

you should upgrade to `ai4j-agent` or `ai4j-coding`.

## 11. Design summary

AI4J does not hand-write tool JSON; it pins down the Java tool contract with annotations, and `ToolUtil` resolves it into a unified schema against an allowlist before the request is sent.
This layer is responsible only for the tool bridge, not for agent-level governance, which is why it can be reused by `Chat`, `Responses`, `Agent`, and `Coding Agent`.

## 12. Further reading

- [Tools / Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
- [Tools / Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
- [Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
