---
sidebar_position: 6
title: "Build and Publish an MCP Server"
description: "Explains how AI4J uses the @McpService/@McpTool annotations, adapters, and McpServerEngine to publish Java capabilities as an MCP Server, covering the real differences across the Tool/Resource/Prompt capability chains and the three server-side transports."
tags: [how-to]
---

# Build and Publish an MCP Server

In AI4J, "publishing an MCP Server" is not just adding a few annotations to a class — it is a complete server-side pipeline:

1. Declare the capability surface with annotations
2. Project local Java capabilities as MCP capabilities
3. Choose a server-side transport
4. Let `McpServerEngine` handle protocol requests
5. Decide which capabilities are actually exposed externally

This page focuses not on "can it run" but on how the current implementation actually publishes, what is already wired up, and what you still need to fill in yourself.

## 1. The main server-side pipeline: these classes first

If you want to understand the publish flow from the source, the main entry points are:

- Annotation layer
  - `@McpService`
  - `@McpTool`
  - `@McpResource`
  - `@McpPrompt`
- Adapter layer
  - `McpToolAdapter`
  - `McpResourceAdapter`
  - `McpPromptAdapter`
- Protocol layer
  - `McpServerEngine`
- Transport/server layer
  - `StdioMcpServer`
  - `SseMcpServer`
  - `StreamableHttpMcpServer`
  - `McpServerFactory`

You can think of it as:

- Annotations declare "what local capabilities exist"
- Adapters organize capabilities into an MCP view
- The server engine routes MCP requests to local capabilities
- The server implementation runs the protocol on different transports

## 2. Capability declaration is not just Tool

The AI4J server side explicitly splits MCP capabilities into three categories:

- Tool
  Action-oriented capability, ultimately responding to `tools/list` / `tools/call`
- Resource
  Read-only content capability, responding to `resources/list` / `resources/read`
- Prompt
  Template-oriented capability, responding to `prompts/list` / `prompts/get`

Declaration looks like this:

```java
@McpService(name = "weather-service", description = "Weather MCP service")
public class WeatherMcpService {

    @McpTool(name = "query_weather", description = "Query weather by city")
    public String queryWeather(@McpParameter(name = "city") String city) {
        return "Weather(" + city + ")";
    }

    @McpResource(uri = "weather://city/{city}", name = "city-weather")
    public String weatherResource(@McpResourceParameter(name = "city") String city) {
        return "Resource(" + city + ")";
    }

    @McpPrompt(name = "weather-summary", description = "Generate weather summary")
    public String weatherPrompt(@McpPromptParameter(name = "city") String city) {
        return "Please summarize weather for " + city;
    }
}
```

What matters most here is not syntax but keeping the capability roles distinct:

- Anything that can trigger side effects or actions → use Tool
- Read-only content → use Resource
- Templated prompts → use Prompt

## 3. When actually exposed externally, the three capability chains differ

### Tool chain

`tools/list` and `tools/call` go through:

1. `McpServerEngine.handleToolsList(...)`
2. `ToolUtil.getLocalMcpTools()`
3. `ToolUtil.scanMcpTools()`
4. Convert `@McpTool` methods into an MCP `inputSchema`

On invocation:

1. `McpServerEngine.handleToolsCall(...)`
2. `ToolUtil.invoke(toolName, argumentsJson)`
3. Dispatch to local MCP / Function / Gateway implementations by current tool priority

This means the Tool exposed by the current MCP Server is not an isolated system — it directly reuses AI4J's existing `ToolUtil` execution stack.

### Resource chain

`resources/list` and `resources/read` go through:

1. `McpServerEngine.handleResourcesList(...)`
2. `McpResourceAdapter.getAllMcpResources()`
3. `McpResourceAdapter.readMcpResource(uri)`

There is an implementation boundary here that must be stated plainly:

- `McpResourceAdapter` provides `scanAndRegisterMcpResources()`
- But `McpServerEngine` itself does not auto-trigger resource scanning

:::warning Resources/Prompts are not auto-registered
That is, if you have not explicitly completed resource registration before the service starts, `resources/list` may simply be empty.
:::

### Prompt chain

`prompts/list` and `prompts/get` go through:

1. `McpServerEngine.handlePromptsList(...)`
2. `McpPromptAdapter.getAllMcpPrompts()`
3. `McpPromptAdapter.getMcpPrompt(name, arguments)`

Like Resource, Prompt has the same current boundary:

- It has scan-and-register capability
- But it is not auto-initialized by the server engine

So in the current implementation:

- The Tool exposure chain is the most complete
- Resource / Prompt are usable, but you must ensure registration is completed before startup yourself

## 4. Which requests does the protocol engine actually handle

The handling entry points for Tool, Resource, and Prompt stay consistent, but how the protocol starts up depends on the transport profile.

### Streamable HTTP: AUTO default and the modern branch

`StreamableHttpMcpServer` and `McpServerFactory.ServerConfig` use `AUTO` by default. AUTO accepts both modern requests and initialization-era Streamable HTTP requests on the same `/mcp` endpoint; it identifies modern requests via modern headers or `_meta`.

Modern requests identified as `2026-07-28` handle:

- `server/discover`
- `tools/list` / `tools/call`
- `resources/list` / `resources/read`
- `prompts/list` / `prompts/get`

Each request must carry modern `_meta` and HTTP headers. It does not use `initialize`, `notifications/initialized`, or protocol sessions.

### Initialization-era Streamable HTTP and other legacy transports

The AUTO server's same `/mcp` endpoint also retains initialization-era Streamable HTTP handling. STDIO, the explicit `sse` transport, and specific legacy Streamable HTTP profiles likewise retain session-era handling:

- `initialize`
- `notifications/initialized`
- `tools/list` / `tools/call`
- `resources/list` / `resources/read`
- `prompts/list` / `prompts/get`
- `ping` (only when the server transport enables it)

This shows AI4J does more than a "tool execution interface", but it also cannot turn the legacy handshake into a common prerequisite for all MCP transports.

## 5. The real differences between the three server-side transports

### `StdioMcpServer`

- Sends and receives JSON-RPC over standard input/output
- Suitable for being spawned as a subprocess by a local host
- Internal `McpServerEngine` fixed at `2024-11-05`
- `initializationRequired = false`

It is more like an "embedded local tool process".

### `SseMcpServer`

- `GET /sse` establishes the event stream
- `POST /message` sends MCP messages
- Supports `ping`
- `initializationRequired = true`
- Protocol version fixed at `2024-11-05`

It is more compatible with old-style SSE clients, but the endpoint model is more fragmented than Streamable HTTP.

### `StreamableHttpMcpServer`

- Unified main endpoint `/mcp`
- Defaults to `McpProtocolProfile.AUTO`, accepting both modern and initialization-era Streamable HTTP on the same `/mcp`
- Modern requests use stateless `POST /mcp`, returning JSON or SSE on the same request
- Modern requests validate `MCP-Protocol-Version`, `Mcp-Method`, and validate `Mcp-Name` and schema-declared `Mcp-Param-*` when needed
- Modern requests provide `server/discover` and response cache hints
- Does not provide MRTR or subscriptions
- You can explicitly select `McpProtocolProfile.MODERN_2026_07_28` to allow only modern requests, or select a specific legacy profile to fix sessions, `GET`/`DELETE`, and the initialization handshake
- The deprecated HTTP+SSE remains a separate `sse` transport, not a third branch of AUTO

If you plan to have external systems, platforms, or gateways consume it long-term, this is the server form you should prioritize right now.

## 6. Start the service with `McpServerFactory`

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig(
        "weather-server", "1.0.0"
).withPort(8081).withProtocolProfile(
        McpProtocolProfile.AUTO
);

McpServer server = McpServerFactory.createServer("streamable_http", config);
server.start().join();
```

`McpServerFactory` is responsible for two things:

- Normalizing the type string
- Creating the corresponding server instance

Supported types:

- `stdio`
- `sse`
- `streamable_http`
- `http`
  Compatibility-only alias, ultimately mapped to `streamable_http`

`http` is only a transport type alias for `streamable_http`, and retains AUTO's limited Streamable HTTP compatibility semantics. Known session-era peers can pin a specific legacy profile in `ServerConfig` or in the client `TransportConfig`; the deprecated HTTP+SSE should use `sse`. See [Streamable HTTP](/docs/capabilities/mcp/streamable-http) for details.

## 7. Server-side security defaults and the HTTP-layer extension

This section applies only to the `sse` / `streamable_http` HTTP servers; `stdio` has no network layer and is not involved in authentication, binding, or CORS.

### 7.1 Default binding to loopback

The default host of `ServerConfig` is the loopback address, not the wildcard:

- `ServerConfig.DEFAULT_HOST = "127.0.0.1"` (default, only visible to localhost)
- `ServerConfig.WILDCARD_HOST = "0.0.0.0"` (binds all NICs; the factory logs a WARNING)

Specify explicitly when you need external exposure:

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withHost("0.0.0.0"); // Bind all NICs; McpServerFactory prints a security warning
```

The default port is `8080`, overridable with `withPort(int)`. The loopback default means: a freshly started MCP server is not automatically exposed to the public network unless you explicitly change the host.

### 7.2 Bearer Token authentication on by default

`ServerConfig` defaults to `authEnabled = true`. When no `McpAuthProvider` is explicitly configured, `resolveAuthProvider()` lazily creates a `BearerTokenAuthProvider` on first call, using `SecureRandom` to generate a random token. In other words, the HTTP/SSE server **ships with Bearer Token authentication by default** — it is no longer unauthenticated.

Behavior of the built-in `BearerTokenAuthProvider`:

- Validates the `Authorization: Bearer <token>` header
- Constant-time comparison, resisting timing side channels
- Generates a random token (at least 32 hex bytes) when constructed with no arguments
- `getToken()` retrieves the token, for printing at startup or out-of-band delivery
- `describe()` returns a masked description (e.g. `Bearer token: abcd...wxyz`), for startup logs

```java
BearerTokenAuthProvider auth = new BearerTokenAuthProvider("my-secret-token");
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withAuth(auth); // Inject a custom provider; authentication enabled
```

On authentication failure, the server uniformly responds with HTTP 401 and an `WWW-Authenticate: Bearer` header via `McpHttpServerSupport.requireAuth(...)`. Health endpoints like `/health` do not require authentication; only business endpoints (`/mcp`) are validated.

### 7.3 Custom authentication SPI: `McpAuthProvider`

To plug in OAuth, JWT, API key, or an internal authentication system, implement the two-method `McpAuthProvider` SPI:

```java
import com.sun.net.httpserver.HttpExchange;
import io.github.lnyocly.ai4j.mcp.server.McpAuthProvider;

public class JwtAuthProvider implements McpAuthProvider {
    @Override
    public boolean authenticate(HttpExchange exchange) {
        // Read Authorization / Cookie / custom headers; true = pass, false = reject
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && validateJwt(header.replace("Bearer ", ""));
    }

    @Override
    public String describe() {
        return "JWT (HS256)"; // For startup logs only; do not leak secrets
    }
}
```

Key points:

- `authenticate(HttpExchange)` is called once per inbound HTTP request; returning `false` triggers a 401.
- `describe()` is for startup logs only — do not output sensitive information in it.
- Inject it via `withAuth(new JwtAuthProvider())` — no change to the server implementation is required.

If the server already sits behind a reverse proxy or controlled network and authentication is handled upstream, you can explicitly turn it off with `withNoAuth()`:

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withNoAuth(); // Unsafe; only for scenarios already authenticated by an upstream gateway
```

:::warning Authentication is not authorization
`McpAuthProvider` addresses "whether a request can come in" (authentication). Authorization policies such as tenant isolation, per-tool authorization, and quota limits still need to be filled in at the business layer.
:::

### 7.4 CORS configuration

By default no CORS headers are sent (same-origin policy, safest). When cross-origin access is needed, use `withCorsAllowedOrigin(...)`:

```java
McpServerFactory.ServerConfig config = new McpServerFactory.ServerConfig("orders", "1.0.0")
        .withPort(8081)
        .withCorsAllowedOrigin("https://app.example.com"); // Specify a trusted origin
```

Key points:

- Passing `null` (default) = same-origin, no `Access-Control-Allow-Origin` is sent.
- Passing `"*"` allows any origin, but is not recommended for token-protected endpoints.
- Browser `Origin` validation (`McpHttpServerSupport.isAllowedOrigin(...)`) does not accept `"*"` as origin verification — wildcard CORS is not equivalent to origin verification.
- The `Mcp-Param-*` headers declared by the tool schema are appended to the CORS preflight as a syntactic allowlist, not arbitrarily reflected (see below).

### 7.5 The `x-mcp-header` extension for tool inputSchema

Modern Streamable HTTP (`2026-07-28`) lets a tool declare in its `inputSchema`: mirror a primitive-typed property as a `Mcp-Param-<Name>` HTTP header on a `tools/call`. This way proxies, gateways, and cache layers can route, cache, or authenticate by parameter without parsing the JSON body. The declaration is done by adding `"x-mcp-header": "<Header-Name>"` on a **primitive-typed** property under `properties`:

```json
{
  "type": "object",
  "properties": {
    "region": { "type": "string",  "x-mcp-header": "Region" },
    "count":  { "type": "integer", "x-mcp-header": "Count" }
  }
}
```

When calling `tools/call`, the client automatically derives the `Mcp-Param-Region` and `Mcp-Param-Count` headers from the parameters. This mechanism is implemented by `McpHttpHeaderSupport`, with the following rules (if any is violated, the tool schema is deemed illegal, skipped during `tools/list`, and warned):

- Only primitive types (`string` / `integer` / `boolean`) under `properties` are accepted; annotations on nested objects or array `items` are rejected.
- The header name must be a legal HTTP token (no spaces or other illegal characters).
- Case-insensitive duplicate header names are rejected.
- `integer` must be an exact integer and within the JavaScript safe integer range, encoded as a decimal string.
- A `string` containing non-ASCII printable characters is encoded as `=?base64?<b64>?=`.
- The server cross-validates the header value against the JSON-RPC request body, rather than treating the header as the single source of truth.

:::note The annotation path does not yet expose x-mcp-header
`@McpParameter` currently only generates `name/description/required/defaultValue`, and does not carry `x-mcp-header`. To use this extension, declare it directly on a custom inputSchema (e.g. a hand-built `McpToolDefinition` or a remote tool schema). AI4J's modern clients and servers automatically recognize and handle valid `x-mcp-header`.
:::

## 8. In the current implementation, what is automated and what is not

### What is already wired up

- Tool annotation scanning and parameter schema projected
- Tool invocation to the local execution chain
- The three server transports
- AUTO accepts both modern stateless and initialization-era Streamable HTTP on the same endpoint, plus a separate explicit SSE transport

### What you still need to fill in yourself

- Resource / Prompt scan-and-register before startup
- Authentication policies, tenant isolation (note: the HTTP/SSE server's Bearer Token authentication is built in and on by default; custom authentication can go through the `McpAuthProvider` SPI — see Section 7)
- Server-side timeout, concurrency control, audit
- External version governance and compatibility policy

This part must be stated clearly, otherwise the docs would misrepresent "the protocol can run" as "platform capabilities are complete".

## 9. How to design boundaries when publishing externally

At minimum, settle these five things first:

1. Namespace
   - Are the `tool/resource/prompt` names stable, will they conflict
2. Capability classification
   - Do not cram read-only content and templates all into Tool
3. Transport form
   - Local host prefers `stdio`
   - Service-style publishing prefers `streamable_http`
4. Version compatibility
   - New parameters should stay backward compatible where possible
5. Security surface
   - Publishing a capability does not mean every client is allowed to call it by default

:::note The publish layer only ensures capabilities are reachable
The publish layer only ensures "capabilities are reachable", not "anyone can use them freely".
:::

## 10. Common failure points

### `tools/list` is empty

Check first:

- Whether `@McpService` / `@McpTool` are within the scan range
- Whether tool names conflict
- Whether the local class can be instantiated via a no-arg constructor

### `resources/list` or `prompts/list` is empty

Check first:

- Whether you actually defined `@McpResource` / `@McpPrompt`
- Whether you invoked the registration-scan logic before startup

### The HTTP service starts, but the client cannot connect

Check first:

- Whether the endpoint is really `/mcp`
- Whether the client mixed up `streamable_http` and `sse`
- Whether a proxy layer rewrites the path

## 11. Recommended minimal publishing posture

If you want to get one chain stable first, the recommended order is:

1. Publish only Tool first
2. Prefer `streamable_http`
3. On the AUTO server, self-test with the modern `server/discover -> tools/list -> tools/call` and the legacy `initialize -> tools/list -> tools/call` flows separately; then validate HTTP+SSE via the `sse` transport on its own
4. Then add Resource / Prompt
5. Finally connect `McpGateway` or an external platform

This is because the Tool chain is the most mature in the current implementation and is best suited as the first closed loop.
