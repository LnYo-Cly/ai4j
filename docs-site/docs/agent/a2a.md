---
sidebar_position: 12
---

# A2A Protocol (Agent-to-Agent)

ai4j supports the Google [Agent2Agent (A2A) protocol](https://github.com/a2aproject/A2A) — an open
standard for cross-framework agent interop. An ai4j agent can both call external A2A agents (as a
client) and be exposed as an A2A service (as a server). JDK stdlib only, no new dependency.

## A2A Client — call external agents

```java
A2AClient client = new A2AClient();
// optional auth:
// A2AClient client = new A2AClient("shared-secret-key");

AgentCard card = client.discover("https://other-agent.example.com");
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
//   GET  http://localhost:PORT/.well-known/agent.json  → AgentCard
//   POST http://localhost:PORT/tasks/send              → JSON-RPC task
server.close();
```

## Auth

Both client and server support optional shared-secret auth via `X-API-Key` header:

- **Client**: `new A2AClient("secret-key")` — sends `X-API-Key` on all requests.
- **Server**: `new A2AServer(agent, port, name, desc, "secret-key")` — validates `X-API-Key` on
  `/tasks/send`. The AgentCard endpoint is always open (discovery should be public).

Full JWT/OIDC auth is a future addition.

## Where this fits

- **Interception hooks** (control what the agent does): [Interception Hooks](/docs/agent/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory-compact-context).
