---
sidebar_position: 12
title: "A2A Protocol (Agent-to-Agent)"
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

## 0. First understand what A2A is (it is completely different from SubAgent/Teams)

A2A is a **cross-implementation open protocol**, not an in-process call mechanism. The key to understanding it is to contrast it with the previous two capabilities:

| Dimension | SubAgent / Agent Teams | A2A |
| --- | --- | --- |
| Scope | Within a single JVM process | Cross-process, cross-language, cross-implementation |
| Who the peer is | An `Agent` object in your own code | Any remote service that follows the A2A protocol (LangChain, CrewAI, Google, another ai4j instance, ...) |
| How they communicate | Tool calls / shared memory (message bus) | HTTP + JSON-RPC (network protocol) |
| Discovery required? | No, hold an object reference directly | Yes — discover the peer's capabilities via the AgentCard |

In one sentence: **SubAgent/Teams is "collaboration between my own agents"; A2A is "collaboration between my agent and someone else's agent."** The former relies on memory; the latter relies on a standard protocol.

### The three cornerstones of A2A

**1. AgentCard — capability discovery.** Every A2A service exposes a "calling card" at `/.well-known/agent-card.json`, declaring its name, description, skills (what it can do), supported interfaces, and authentication method. The caller discovers this card first, and only then knows what the peer can do and how to call it.

**2. JSON-RPC task — request-response.** The core operations are `SendMessage` (synchronous) and `SendStreamingMessage` (SSE streaming), running JSON-RPC 2.0 over HTTP with the `A2A-Version: 1.0` header. The request carries a message; the response carries `task.artifacts[].parts[].text`. ai4j also retains the legacy `tasks/send` / `message:send` HTTP aliases for backward compatibility.

**3. Task lifecycle — an asynchronous state machine.** A task is not a single "fire-and-forget" RPC; it goes through state transitions:

```
SUBMITTED → WORKING → COMPLETED
                  ↘ → FAILED
                  ↘ → CANCELED
              WORKING → INPUT_REQUIRED  (more input needed)
              WORKING → AUTH_REQUIRED   (authentication required)
```

`COMPLETED/FAILED/CANCELED/INPUT_REQUIRED/AUTH_REQUIRED` are terminal or intervention-required states. A long task can return early, and the client can poll with `GetTask`, cancel with `CancelTask`, or subscribe to updates with `SubscribeToTask` (SSE push until a terminal state).

### Why this combination of JSON-RPC + SSE

A2A tasks can run for a long time (the agent has to call tools, reason, take multiple steps). Plain HTTP request-response cannot handle long-running operations, so the protocol is designed as: **JSON-RPC for request structure + SSE for streaming updates + push-notification for asynchronous callbacks**. This is the standard solution for agent scenarios (OpenAI Responses and Anthropic Messages use a similar combination). ai4j's push callbacks default to allowing only public HTTPS, do not follow redirects, and reject private network addresses — to prevent SSRF.

:::note Difference from OpenAI Responses
A2A is **agent-to-agent** (two agents conversing with each other); Responses is **client-to-agent** (your application calling an agent). A2A's task lifecycle and AgentCard discovery mechanism are what it adds on top of Responses, because peer agents need to negotiate "who you are, what you can do."
:::

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
int port = server.getPort();        // actual port when 0 = auto-assign
String baseUrl = server.getBaseUrl(); // http://localhost:<port>

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

## Publish ai4j Skills to the AgentCard

`A2ASkill` describes a capability in an A2A AgentCard; an ai4j `SkillDescriptor` describes a local `SKILL.md` Skill (see [Agent Skills](/docs/agent/skills)). They are different models, so the SDK ships a small bridge — `io.github.lnyocly.ai4j.agent.a2a.A2ASkillMapper` — that publishes your resolved ai4j Skills as A2A skills on the server's card.

`A2ASkillMapper` is a final utility class with three static entry points:

| Method | Purpose |
| --- | --- |
| `mapToA2ASkill(SkillDescriptor)` | Maps a single ai4j skill to an `A2ASkill`; returns `null` when the descriptor is `null` or its name is empty. |
| `mapToA2ASkills(List<SkillDescriptor>)` | Batch mapping, automatically skipping `null` entries. |
| `addSkillsToServer(A2AServer, List<SkillDescriptor>)` | Maps each one and calls `server.withSkill(name, description, inputSchema)` to attach the skill directly to the AgentCard. |

Currently only `name` and `description` are mapped; `inputSchema` is left empty (optional in A2A). Later, if the `SKILL.md` frontmatter defines an input schema field, it can be extracted here.

Typical usage: publish the list of allowed skills resolved by an `AgentSkillResolver` to the A2A service.

```java
import io.github.lnyocly.ai4j.agent.a2a.A2AServer;
import io.github.lnyocly.ai4j.agent.a2a.A2ASkillMapper;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;

import java.util.List;

A2AServer server = new A2AServer(myAgent, 0, "my-agent", "does stuff", null);

// allowedSkills comes from your AgentSkillResolver / tenant authorization, not from an SDK built-in source
List<SkillDescriptor> allowedSkills = resolver.resolveAllowedSkills(currentTenantId());

// Publish ai4j Skills as A2A skills on the AgentCard
A2ASkillMapper.addSkillsToServer(server, allowedSkills);
```

:::warning Mapping is not authorization
`A2ASkillMapper` only carries the metadata (name/description) of already-authorized skills. It does not read the skill body, nor does it bypass the tenant authorization decision of `AgentSkillResolver` — the `SkillDescriptor` list passed in must already be the skills the current tenant/user is allowed to see. The fact that an A2A caller can see these skills does not mean it can execute them directly; execution is still subject to the host's tool, approval, and sandbox policies (see [Agent Skills — Prompt and tool boundary](/docs/agent/skills#prompt-and-tool-boundary)).
:::

## Auth

Both client and server support optional shared-secret authentication. AgentCard discovery stays
open so peers can select the advertised scheme.

- **API key**: `new A2AClient("secret-key")` reads a standard `apiKeySecurityScheme` from the
  AgentCard and uses its declared header. `A2AServer.withAuthentication("api-key", "X-My-Key", ...)`
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

- **Interception hooks** (control what the agent does): [Interception Hooks](/docs/agent/governance/tool-interceptor).
- **Sandbox SPI** (isolate tool execution): [Sandbox SPI](/docs/agent/governance/sandbox-spi).
- **Session + compaction** (manage long sessions): [Memory & Compact](/docs/agent/memory/memory-compact-context).
