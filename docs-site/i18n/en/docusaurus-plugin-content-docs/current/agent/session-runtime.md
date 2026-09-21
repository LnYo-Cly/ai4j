---
sidebar_position: 5
title: "Agent Session Runtime"
description: "Walks through the AgentSession long-running state container: sessionId, independent memory, event log, snapshot/restore and AgentSessionStore, plus its boundaries against memory/compact, the Coding Agent/CLI, and production implementation guidance."
tags: [concept]
---

# Agent Session Runtime

`AgentSession` is the long-running runtime entry point of `ai4j-agent`. It is not another model client, nor a replacement for a CLI session; instead, it gathers the state of a single Agent task into one container that can be saved, restored, and observed.

## 1. What problem it solves

A plain `agent.run(...)` fits a one-shot call: the host hands the request to the runtime, gets back an `AgentResult`, and finishes.

`agent.newSession()` fits long-running tasks:

- Each session has a stable `sessionId`.
- Each session has independent memory.
- Runtime events flow into the session event log.
- A session can produce a snapshot.
- Once an `AgentSessionStore` is configured, it can be saved and restored.
- The semantics of the legacy `Agent.run(...)` stay unchanged.

This lets upper-layer products treat an Agent as a continuously running task container rather than a loose string of model requests.

## 2. Current minimum capability

P0-A ships the base container first, rather than pulling in far-future capabilities like sandbox, artifact, fork, or rewind all at once.

The current minimum structure is:

```text
Agent
  ├─ run(...)                 // compatible one-shot run entry point
  └─ newSession()
       └─ AgentSession
            ├─ sessionId / metadata
            ├─ independent AgentMemory
            ├─ AgentSessionEventLog
            ├─ snapshot / restore
            └─ optional AgentSessionStore
```

Related classes:

| Class | Responsibility |
| --- | --- |
| `AgentSession` | Session run entry point facing the user |
| `AgentSessionMetadata` | `sessionId`, created/updated timestamps, and business attributes |
| `AgentSessionEventLog` | Interface for the runtime event log inside a session |
| `InMemoryAgentSessionEventLog` | Default in-memory event log implementation |
| `AgentSessionSnapshot` | A portable snapshot of metadata, memory, and events |
| `AgentSessionStore` | Saves, reads, deletes, and lists session snapshots |
| `InMemoryAgentSessionStore` | In-process store, suitable for tests and lightweight demos |

## 3. Minimum usage

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .build();

AgentSession session = agent.newSession();
session.putMetadata("project", "demo");

AgentResult result = session.run("First, analyze this problem");

System.out.println(session.getSessionId());
System.out.println(result.getOutputText());
System.out.println(session.getEventLog().getEvents().size());
```

:::warning memorySupplier must return an independent instance
The key point is `memorySupplier(...)`: every `newSession()` should get an independent memory instance. Otherwise, multiple sessions may end up sharing the same memory instance.
:::

### 3.1 Async run: `runAsync`

`Agent.runAsync(request)` schedules one run on a shared daemon-thread pool and returns `CompletableFuture<AgentResult>`:

```java
CompletableFuture<AgentResult> future = agent.runAsync(
    AgentRequest.builder().input("analyze this problem").build());

// bring your own Executor when you need scheduling control
CompletableFuture<AgentResult> onMine = agent.runAsync(request, myExecutor);
```

`runAsync` is a thin facade on `Agent` — same semantics as `agent.run(request)` (it does not create a session), just on a different thread; failures complete the future exceptionally. The default pool uses daemon threads named `ai4j-agent-async-N`; for long blocking calls prefer passing your own `Executor`.

## 4. Save and restore

If you need a session to survive across requests or process boundaries, configure the Agent with an `AgentSessionStore`.

```java
InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .sessionStore(store)
    .build();

AgentSession session = agent.newSession();
session.run("Remember this task context");
session.save();

AgentSession resumed = agent.resumeSession(session.getSessionId());
```

:::warning InMemoryAgentSessionStore is not for production
`InMemoryAgentSessionStore` is only suitable for local tests and demos. Production environments should implement their own `AgentSessionStore`, for example over JDBC, Redis, object storage, or a business-owned session table.
:::

## 5. What a snapshot contains

`session.snapshot()` produces:

| Field | Contents |
| --- | --- |
| metadata | session id, created/updated timestamps, attributes |
| memory | `MemorySnapshot`, provided by the current `AgentMemory` |
| events | A list of `AgentSessionEvent` |

The snapshot performs a defensive copy. Modifying the returned object after reading the snapshot should not leak back into the current session.

## 6. The relationship between Event Log and Trace

`AgentSessionEventLog` records the runtime events that have occurred within a session, for example:

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL_OUTPUT`
- `STEP_END`
- `ERROR`

It is not the same thing as a trace:

| Capability | Primary use |
| --- | --- |
| Event Log | Recovery, debugging, product UI timeline, the session's factual history |
| Trace | Observability, external tracing/exporter, performance and call-chain analysis |

When a session is created, it copies the existing listeners from the base `AgentEventPublisher` and additionally attaches an event log listener. Therefore, existing trace listeners do not stop working just because sessions are in use.

## 7. Boundaries against Memory / Compact

P0-A first provides the session container boundary; P0-B has already added the basic compact/context projection capability.

The recommended mental model is:

```text
SessionEventLog = the complete event history
AgentMemory     = the state and history that can be fed to the model
Compact         = projects events / memory / artifacts into a shorter working context
```

P0-B has already added:

- `CompactPolicy`
- `CompactResult`
- `ContextProjector`
- `ContextBudget`
- `ContextReport`
- `AgentSession.compact(...)`
- `AgentSessionSnapshot.compactResult`

For usage details, see [Memory Compact Context Projector](/docs/agent/memory/memory-compact-context).

## 8. Session event contract (every memory mutation emits an event)

The session event log is not just an observability surface — it is a reconstructable record of session state. Every write the runtime performs against memory publishes a matching event, so folding the event stream in order rebuilds the exact `items + summary` that `memory.snapshot()` reports.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/session-event-contract.html')}
  title="Session event contract - interactive architecture diagram"
  style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/session-event-contract.html')} target="_blank" rel="noopener noreferrer">Open the full-screen diagram in a new window</a> (dual theme, zoom and export; the view switcher focuses on write-path eventization, the unified restore funnel, and projection consistency).

Mutation-semantic events:

| Event | Trigger | Payload |
| --- | --- | --- |
| `USER_INPUT` | `memory.addUserInput` | the raw input object |
| `MEMORY_ITEMS_APPENDED` | `memory.addOutputItems` | the appended item list |
| `TOOL_RESULT` | `memory.addToolOutput` (existing event reused) | `AgentToolResult` |
| `TOOL_OUTPUT_REPLACED` | `session.replaceToolOutput` async patch | `{callId, output}` |
| `MEMORY_COMPRESS` | `session.compact` / autoCompact | `CompactResult` (carries the post-compaction memory snapshot) |
| `SESSION_RESTORED` | `session.restore(snapshot)` — the single funnel for resume, fork, and harness snapshot patching | the installed `MemorySnapshot` baseline |

The companion projector `SessionEventProjector` (`deriveSnapshot` / `deriveItems` / `deriveSummary`) folds the event stream back into memory state — usable for audit, fork, offline analysis, and consistency checks (`SessionEventConsistencyTest` asserts projection == snapshot).

Two caveats:

- `MEMORY_COMPRESS` carries two payload kinds: a `CompactResult` means a real memory rewrite; a projection report means a request-scoped `ContextProjection` (it shaped one prompt, not memory). The projector discriminates by payload type.
- Memory-internal compressors (`InMemoryAgentMemory.setCompressor`) rewrite inside `add*` calls and do not emit per-write events — that path is outside the event contract.

## 9. Relationship to the Coding Agent / CLI

`AgentSession` is a general-purpose SDK-layer capability.

`ai4j-coding` and `ai4j-cli` can later bind further concerns on top of it:

- workspace
- file/shell/git/browser tool state
- approvals
- checkpoint
- compact
- sandbox
- TUI session timeline

But none of these should leak back into the general runtime of `ai4j-agent`. The SDK layer keeps only the general contracts for session, events, memory, snapshot, and store.

## 10. Production implementation guidance

When implementing an `AgentSessionStore` for production, note the following:

- Session ids must be isolated per tenant or per project.
- Do not store provider tokens in plaintext inside the snapshot.
- Event payloads may contain prompts, tool arguments, and model output; mask them when necessary.
- Whether store writes are synchronous or asynchronous should be decided by the business; do not force every event to block the main loop.
- If memory or events grow large, combine them with a compact / retention policy.

## 11. Next steps

P0-A is only the foundation of the runtime container. The full Agent SDK will keep moving forward:

1. Plugin lifecycle hooks
2. YAML Agent Blueprint
3. Sandbox SPI
4. Coding Agent sandbox routing
5. CLI `/sandbox` experience
6. Remote Agent Runner

For the full roadmap, see [AI4J Agent SDK Roadmap](/docs/reference/about/sdk-roadmap).
