---
title: "Annotation-based Tools"
description: "An in-depth look at how the three annotations @FunctionCall, @FunctionRequest, and @FunctionParameter bind Java types to provider tool schema, with details on the ToolUtil generation pipeline, type mapping rules, and real-world constraints."
tags: [concept]
---

# Annotation-based Tools

AI4J recommends annotation-based tools not because the code is shorter, but because it binds Java types, field descriptions, and provider tool schema onto a single generation pipeline.

If this page stopped at "how to write the three annotations," it would miss the two points that actually matter:

- How `ToolUtil` generates schema from annotations
- The real limitations of this approach today

## 1. What a standard annotation-based tool looks like

The typical pattern is:

```java
@FunctionCall(name = "query_weather", description = "Query weather by city")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    @Override
    public String apply(Request request) {
        ...
    }

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "City name")
        private String city;

        @FunctionParameter(description = "Forecast days", required = false)
        private Integer days;
    }
}
```

From the perspective of how `ToolUtil` invokes it, the only things this pattern must satisfy are:

- The class has `@FunctionCall`
- There is a parameter class marked with `@FunctionRequest`
- The tool class has an `apply(RequestType)` method

Implementing `java.util.function.Function` is a common idiom, but it is not the interface contract that `ToolUtil` enforces; what actually gets invoked via reflection is the `apply(...)` method itself.

## 2. What role each of the three annotations plays

### `@FunctionCall`

Defines the tool identity:

- `name`
- `description`

It is placed on the class and determines the name under which the tool is ultimately exposed to the model.

### `@FunctionRequest`

Marks "which class is the parameter carrier."

The current `ToolUtil` implementation looks for this annotation inside the tool class's `declaredClasses`, so the safest convention is:

- Use a static inner class of the tool class as the request object

### `@FunctionParameter`

Defines field-level schema information:

- `description`
- `required`

There is currently no `name` alias field, so the tool parameter name defaults directly to the Java field name.

## 3. Why this mechanism is more stable than hand-written schema

The typical problems with hand-written tool schema are:

- The Java field changes, but the JSON schema is forgotten
- The same tool has three descriptions maintained separately across Chat, Responses, and Agent
- Field descriptions are separated from the execution logic

The value of annotation-based tools is precisely that:

- The schema source stays close to the Java type
- Field descriptions stay close to the field itself
- One definition can be reused by multiple runtimes

In essence, AI4J uses the Java type system to generate the model tool contract.

## 4. How `ToolUtil` actually generates the schema

The core pipeline lives in these methods:

- `scanFunctionTools()`
- `getFunctionEntity(...)`
- `setFunctionParameters(...)`
- `createPropertyFromType(...)`

The rough flow is:

1. Scan all `@FunctionCall` classes
2. Record `toolName -> toolClass`
3. Find the inner `@FunctionRequest` class and cache it
4. Iterate over the fields of the request class
5. Only include fields annotated with `@FunctionParameter` in the schema
6. Map each field type to a `Tool.Function.Property`

This implies two important facts:

- Not every field in the request class is automatically exposed
- Field schema generation depends on field type mapping, rather than automatically expanding arbitrary-depth objects

## 5. What the current type mapping rules are

`createPropertyFromType(...)` currently maps roughly as follows:

- `String` -> `string`
- `int/Integer/long/Long/...` -> `integer`
- `float/double/...` -> `number`
- `boolean/Boolean` -> `boolean`
- `enum` -> `string + enumValues`
- `array/Collection` -> `array`
- `Map` -> `object`
- Other complex objects -> `object`

This brings a few very practical limitations.

### Complex objects are not expanded into deep properties

If a field's type is a plain POJO, the current schema only treats it as:

- `type: object`

It will not reflect further into its internal fields.

### Generic collection element types degrade

For `Collection`, Java's type erasure prevents retrieving a precise element type, so in many cases the result is only:

- `array` of `object`

### Parameter aliases are not configurable

`FunctionParameter` has only `description` and `required`, with no way to rename parameters, so field naming must be kept concise and stable on your own.

This is also why AI4J tool parameters are better suited to flat structures than to deeply nested DTOs.

## 6. Real-world constraints and common pitfalls

### Best to define only one `@FunctionRequest`

From the current `ToolUtil` implementation:

- `scanFunctionTools()` caches only the first request class it finds
- `setFunctionParameters(...)`, however, iterates over all inner classes marked `@FunctionRequest`

This leaves "multiple request classes" in an ambiguous state.

In real projects, keep only one request class per tool.

### `FunctionRequest.description` currently barely reaches the final schema

Although this annotation has a `description` field, `ToolUtil` currently mainly consumes the `@FunctionParameter` on fields, not the overall description of the request class.

So what you should write carefully is the field descriptions, rather than expecting the request class description to flow straight into the provider schema.

### Fields without `@FunctionParameter` are not exposed

This is not a bug, but the current design: only explicitly annotated fields enter the model tool contract.

### Tool name stability is your own governance

:::warning
`@FunctionCall(name = "...")` is the name ultimately exposed to the model and the upper-layer runtime. Renaming it affects:

- The `functions(...)` allowlist in requests
- The model's historical memory
- Upper-layer tool routing

Do not change it frequently.
:::

## 7. When annotation-based tools fit best

This approach fits best when:

- The capability lives inside the current JVM
- Input parameters are relatively stable
- It needs to be reused by Chat, Responses, and Agent
- You intend to maintain it as a formal contract long-term

If the problem has shifted to:

- Remote service integration
- Multi-transport lifecycles
- Multi-service gateway governance

then you should stop pushing logic into local annotation tools and move to MCP instead.

## 8. How built-in tools relate to annotation-based tools

The built-in coding tools in the repository, such as `ReadFileFunction`, `BashFunction`, and `WriteFileFunction`, also use the same set of annotations on the surface:

- `@FunctionCall(name = "read_file", ...)`
- `@FunctionRequest`
- `@FunctionParameter`

But at execution time they do not go through the ordinary business function path; they are intercepted first by `BuiltInToolExecutor`.

This shows that the annotation layer and the execution layer are separate:

- The annotation layer is responsible for exposing the contract
- The execution layer can be an ordinary `apply(...)`
- Or a built-in dedicated executor

## 9. Local MCP tools have a corresponding set of annotations

The previous three sections all cover the `@FunctionCall` set of **local Function tool** annotations. If a tool is to go through the MCP protocol (transport, service-ification, unified governance by `McpGateway`), AI4J provides a separate set of **method-level** annotations — no need to write `Function<Request,String>`, just annotate the class and methods directly:

- `@McpService` (on the class) — defines the service identity: `name` / `version` / `description` / `transport` (`stdio`/`sse`/`streamable_http`) / `port` / `autoStart`
- `@McpTool` (on the method) — defines the tool: `name` (defaults to the method name) / `description` / `inputSchema` (auto-generated from the method parameters if omitted)
- `@McpParameter` (on the parameter) — defines the parameter: `name` (defaults to the parameter name) / `description` / `required` / `defaultValue`

A minimal example:

```java
@McpService(name = "weather-service", description = "Weather MCP service")
public class WeatherMcpService {

    @McpTool(name = "query_weather", description = "Query weather by city")
    public String queryWeather(@McpParameter(name = "city", description = "City name") String city,
                               @McpParameter(name = "days", required = false, defaultValue = "3") int days) {
        return "Weather(" + city + ", " + days + "d)";
    }
}
```

Key differences between it and the Function annotations:

| | `@FunctionCall` tools | `@McpService`/`@McpTool` tools |
|---|---|---|
| Annotation granularity | Class + inner request class + fields | Class + method + method parameters |
| Parameter carrier | `@FunctionRequest` static inner class | Method formal parameters directly |
| Execution chain | Reflectively calls `apply(Request)` | Parses into a `Map` first, then converts each parameter by type |
| Exposed name | `@FunctionCall(name)` as-is | Generated by `generateApiFunctionName(service, tool)` (keeps only letters/digits/underscores/hyphens, max length 64, with a `tool_` prefix added when necessary) |
| Scan entry point | `ToolUtil.scanFunctionTools()` | `ToolUtil.scanMcpTools()` |

Both annotation sets are ultimately scanned and cached by `ToolUtil` and projected into a unified `Tool.Function` view that enters the request allowlist (see [Tool Execution Model](/docs/capabilities/tools/tool-execution-model)). The full transport lifecycle, gateway governance, and remote/local projection semantics belong to the MCP protocol layer; see [Build an MCP Service](/docs/capabilities/mcp/build-your-mcp-server).

## 10. The safest design advice

Based on the current implementation, the most robust tool design is usually:

- Short, stable tool names
- Only one inner static request class
- Parameters kept as flat as possible
- No deep nesting of complex objects
- Field names designed directly as model parameter names
- A clear `description` on every field

This significantly reduces schema drift and the chance of model mis-invocation.

## 11. The conclusion to take away from this page

AI4J's annotation-based tools are not "sticking a few labels on a class," but rather a generation pipeline from Java types to provider tool schema.

Their strength is contract uniformity; their limitations are:

- Keep the request structure simple
- Deep types are not automatically expanded
- Multiple request classes introduce ambiguity

Understanding these real boundaries matters more than merely knowing how to write the annotations.
