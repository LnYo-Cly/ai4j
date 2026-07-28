---
sidebar_position: 12
---

# A2A Protocol (Agent-to-Agent)

ai4j provides a Java 8-compatible A2A integration surface for agent discovery, JSON-RPC task
exchange, Skills metadata, and API-key protected local services. An ai4j agent can call an A2A
endpoint as a client or be exposed as an A2A service. JDK stdlib only, no new dependency.

> Scope note: non-streaming A2A 1.0 JSON-RPC `SendMessage` and AgentCard discovery are verified
> against the official `a2a-sdk==1.1.0` Python implementation. Streaming, task query/cancel,
> push notifications, standard security schemes, and other A2A operations remain follow-up work.

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

The standard card advertises `supportedInterfaces`, default `text/plain` modes, and standard
Skill fields (`id`, modes, tags, and examples). The root endpoint requires
`A2A-Version: 1.0`; it returns `result.task` with `TASK_STATE_COMPLETED` or
`TASK_STATE_FAILED`. The existing aliases keep their previous lower-case task-state contract.

## Auth

Both client and server support optional shared-secret auth via `X-API-Key` header:

- **Client**: `new A2AClient("secret-key")` — sends `X-API-Key` on all requests.
- **Server**: `new A2AServer(agent, port, name, desc, "secret-key")` — validates `X-API-Key` on
  task endpoints. The AgentCard endpoint is always open (discovery should be public).

Full JWT/OIDC auth is a future addition.

## External Interoperability Regression

`A2AOfficialJsonRpcTest` has deterministic JVM contract coverage and two opt-in tests against the
official Python SDK: ai4j calls a configured Python peer, then the Python client discovers and
calls an ai4j server. Supply the peer URL, Python executable, and its working directory as Maven
system properties; none of them are committed or required by default CI.

## Where this fits

- **Interception hooks** (control what the agent does): [Interception Hooks](/docs/agent/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory-compact-context).
