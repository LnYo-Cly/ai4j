---
sidebar_position: 12
---

# A2A Protocol (Agent-to-Agent)

ai4j provides a Java 8-compatible A2A integration surface for agent discovery, JSON-RPC task
exchange, Skills metadata, and API-key protected local services. An ai4j agent can call an A2A
endpoint as a client or be exposed as an A2A service. JDK stdlib only, no new dependency.

> Scope note: this page documents the current ai4j contract and its legacy aliases. It does not
> claim complete coverage of every A2A 1.0 feature. Streaming, task query/cancel, standard-schema
> version negotiation, and independent third-party framework interoperability remain follow-up
> work.

## A2A Client — call external agents

```java
A2AClient client = new A2AClient();
// optional auth:
// A2AClient client = new A2AClient("shared-secret-key");

AgentCard card = client.discover("https://other-agent.example.com");
// discover() tries /.well-known/agent-card.json first, then the legacy /.well-known/agent.json.
String response = client.sendTask("https://other-agent.example.com", "What is 2+2?");
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
//   GET  http://localhost:PORT/.well-known/agent.json       → legacy alias
//   POST http://localhost:PORT/tasks/send                   → JSON-RPC task
//   POST http://localhost:PORT/message:send                 → compatible task alias
server.close();
```

## Auth

Both client and server support optional shared-secret auth via `X-API-Key` header:

- **Client**: `new A2AClient("secret-key")` — sends `X-API-Key` on all requests.
- **Server**: `new A2AServer(agent, port, name, desc, "secret-key")` — validates `X-API-Key` on
  task endpoints. The AgentCard endpoint is always open (discovery should be public).

Full JWT/OIDC auth is a future addition.

## Where this fits

- **Interception hooks** (control what the agent does): [Interception Hooks](/docs/agent/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory-compact-context).
