---
sidebar_position: 12
title: A2A Protocol (Agent-to-Agent)
description: "Discover agents, exchange JSON-RPC tasks, stream SSE updates, and expose your ai4j agent as an A2A service with optional auth — JDK stdlib only."
tags: [integration]
---

# A2A Protocol (Agent-to-Agent)

ai4j provides a Java 8-compatible A2A integration surface for agent discovery, JSON-RPC task
exchange, task lifecycle, SSE streaming, push-notification configuration, Skills metadata, and
standard API-key or Bearer authentication. An ai4j agent can call an A2A endpoint as a client or
be exposed as an A2A service. JDK stdlib only, no new dependency.

> Scope note: `SendMessage` and `SendStreamingMessage` have bidirectional, opt-in verification
> against the official `a2a-sdk==1.1.0` Python implementation. Task lookup, listing,
> cancellation, push configuration, and standard security schemes have deterministic local
> regression coverage. Task state is intentionally in-memory and is not restart-durable.

## A2A Client — call external agents

```java
A2AClient client = new A2AClient();
// optional auth:
// A2AClient client = new A2AClient("shared-secret-key");

AgentCard card = client.discover("https://other-agent.example.com");
// discover() tries /.well-known/agent-card.json first, then the legacy /.well-known/agent.json.
String response = client.sendTask("https://other-agent.example.com", "What is 2+2?");
```

When the standard AgentCard advertises a JSON-RPC 1.0 interface, `sendTask()` sends
`SendMessage` to that interface URL with `A2A-Version: 1.0` and reads the returned
`result.task.artifacts[].parts[].text`. If no standard interface is advertised, it retains the
legacy `POST /tasks/send` behavior.

### Tasks and streaming

Standard JSON-RPC peers can return immediately and be polled, canceled, or subscribed to:

```java
JSONObject result = client.sendTaskResponse("https://other-agent.example.com", "Process this", true);
String taskId = result.getJSONObject("task").getString("id");

JSONObject current = client.getTask("https://other-agent.example.com", taskId);
JSONObject all = client.listTasks("https://other-agent.example.com");
client.cancelTask("https://other-agent.example.com", taskId);

client.sendStreamingTask("https://other-agent.example.com", "Stream this", event -> {
    // event contains task, statusUpdate, or artifactUpdate.
});
client.subscribeToTask("https://other-agent.example.com", taskId, event -> {
    // receives the current task followed by updates until terminal state.
});
```

Wrap the client as a tool so your agent can call external agents:

```java
A2ATool a2a = new A2ATool("https://other-agent.example.com");
Agent agent = Agents.react()
        .modelClient(modelClient).model("glm-5.1")
        .toolExecutor(a2a)
        .toolRegistry(a2aRegistry)  // defines the ask_remote_agent tool
        .build();
// agent can now call the external A2A agent as a tool
```

## A2A Server — expose your agent

```java
A2AServer server = new A2AServer(
        myAgent,      // the ai4j Agent to expose
        0,            // port (0 = auto-assign)
        "my-agent",   // name (shown in AgentCard)
        "does stuff", // description
        "secret-key"  // optional shared-secret auth (null = open)
);

// External agents (LangChain, CrewAI, etc.) can now call:
//   GET  http://localhost:PORT/.well-known/agent-card.json  → AgentCard
//   POST http://localhost:PORT/                             → A2A 1.0 SendMessage JSON-RPC
//   GET  http://localhost:PORT/.well-known/agent.json       → legacy alias
//   POST http://localhost:PORT/tasks/send                   → JSON-RPC task
//   POST http://localhost:PORT/message:send                 → compatible task alias
server.close();
```

The standard card advertises `supportedInterfaces`, default `text/plain` modes, standard Skill
fields (`id`, modes, tags, and examples), streaming and push capabilities, and security schemes
when configured. The root endpoint supports `SendMessage`, `GetTask`, `ListTasks`, `CancelTask`,
`SendStreamingMessage`, `SubscribeToTask`, and task push-configuration CRUD. It accepts
`A2A-Version: 1.0` and rejects an explicit unsupported version; omission remains accepted for
official SDK compatibility. The existing aliases keep their previous lower-case task-state
contract.

Push callbacks default to public HTTPS URLs only, revalidate that policy again immediately before
delivery, and do not follow redirects. Local and private targets are rejected during URL validation.
Callback tokens and `authentication.credentials` are write-only: create, get, and list responses
never disclose them, while the server retains them only for callback delivery. A test-only or
deployment-specific callback policy must be configured explicitly; high-risk deployments must also
enforce destination restrictions at the network egress layer because DNS resolution cannot be made
atomic with a JDK URL connection. A push config may include A2A `authentication` fields (`scheme`
and `credentials`), which the server sends as the callback `Authorization` header.
Callback delivery uses a separate bounded executor so slow callback receivers do not consume Agent
execution slots. Each task accepts at most 32 push configurations; callers must delete stale
configurations before adding more.

## Auth

Both client and server support optional shared-secret authentication. AgentCard discovery stays
open so peers can select the advertised scheme.

- **API key**: `new A2AClient("secret-key")` reads a standard `apiKeySecurityScheme` from the
  AgentCard and uses its declared header. `A2AServer.withAuthentication("api-key", "X-My-Key", ...)
  configures the matching server metadata and check.
- **Bearer**: `A2AClient.bearerToken("token")` sends `Authorization: Bearer token`.
  `A2AServer.withBearerAuthentication(...)` advertises and validates the matching HTTP Bearer
  scheme.

:::note
The server intentionally does not implement JWT/OIDC token verification; use a terminating
identity-aware gateway for those flows.
:::

## External Interoperability Regression

`A2AOfficialJsonRpcTest` has deterministic JVM contract coverage plus four opt-in checks against
the official Python SDK: both client directions for `SendMessage` and `SendStreamingMessage`.
Supply the peer URL, Python executable, and its working directory as Maven system properties;
none are committed or required by default CI.

## Where this fits

- **Interception hooks** (control what the agent does): [Interception Hooks](/docs/agent/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory-compact-context).
