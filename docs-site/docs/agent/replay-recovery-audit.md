---
sidebar_position: 10
---

# Replay, Recovery & Audit

`ai4j-agent` gives you four production-grade capabilities on top of the runtime event stream.
Each is a small, optional layer — wire in the ones your scenario needs.

| Capability | Class | Solves |
| --- | --- | --- |
| Node I/O capture + replay | `IoCaptureAgentListener`, `NodeReplayer` | Re-run a single model/tool node to reproduce or A/B a turn |
| Failure recovery / resume | `ResumeCache`, `ResumableModelClient`, `ResumableToolExecutor` | Re-run a crashed task without redoing completed work or side effects |
| Durable session store | `FileAgentSessionStore`, `JdbcAgentSessionStore` | Survive process restart; resume long tasks |
| Tamper-evident audit | `HashChainedEventLog` | Prove the recorded activity wasn't edited after the fact |

All of these are **consumers/decorators**, not runtime changes — they reuse the events the runtime
already publishes (`MODEL_REQUEST`/`MODEL_RESPONSE`/`TOOL_CALL`/`TOOL_RESULT`).

## 1. Capture + node replay

Run an agent, capture every model/tool node's input+output, then replay a node — for real (a fresh
LLM call with the captured prompt) or deterministically (return the captured output).

```java
InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
Agent agent = Agents.react().anthropicMessages(key, baseUrl).model("glm-5.1").build();

// attach the capture listener for the run
agent.newSession().runStreamResult(
        AgentRequest.builder().input("...").build(),
        new IoCaptureAgentListener(sink));

List<NodeIoRecord> modelNodes = sink.records(NodeIoRecord.NodeType.MODEL);

// live replay: re-invoke the real model with the captured prompt
AgentModelResult fresh = new NodeReplayer().replayModelLive(modelNodes.get(0), modelClient);
// deterministic replay: return the captured output, no LLM call
AgentModelResult same = new NodeReplayer().replayModelMock(modelNodes.get(0));
```

For a durable capture file (audit/replay artifact), use `JsonlIoCaptureSink` instead of
`InMemoryIoCaptureSink` — one JSON line per node, append-only.

## 2. Failure recovery (resume)

Wrap the model client and tool executor so a re-run skips already-completed work. The model returns
the cached result (no LLM call); the tool returns the cached output (**without re-performing the side
effect**). Same `ResumeCache` captures on the first run and resumes on the next.

```java
ResumeCache cache = new ResumeCache();
Agent agent = Agents.react()
        .modelClient(new ResumableModelClient(realModelClient, cache))
        .toolExecutor(new ResumableToolExecutor(realToolExecutor, cache))
        .build();
agent.newSession().run(request);          // run 1: captures every node
agent.newSession().run(request);          // run 2: same input -> ZERO real calls + zero side effects
```

A crashed run is the same idea: persist the cache (`cache.saveToJson(path)`), and after restart load
it (`ResumeCache.loadFromJson(path)`) so resume crosses the process boundary.

## 3. Durable session store

Checkpoint a session so it survives a restart. `AgentSessionStore` is backend-neutral:

- **`FileAgentSessionStore(dir)`** — one JSON file per snapshot. Zero dependency (filesystem only). The lightweight default.
- **`JdbcAgentSessionStore(config)`** — one row per snapshot. Uses only JDK `javax.sql` (bring your own driver). For shared/multi-instance production DBs.

```java
AgentSessionStore store = new FileAgentSessionStore(Path.of("/var/ai4j/sessions"));
store.save(session.snapshot());
AgentSessionSnapshot restored = store.load(sessionId);   // after a restart
session.restore(restored);
```

## 4. Tamper-evident audit

Drop-in replacement for `InMemoryAgentSessionEventLog` that seals each event into a SHA-256 hash
chain. `verifyChain()` reports the first link whose stored hash no longer matches — any after-the-fact
edit, deletion, or reordering is detectable.

```java
HashChainedEventLog auditLog = new HashChainedEventLog();
// register it as an AgentListener for the run, e.g.
agent.newSession().runStreamResult(request, auditLog);
// ... every event is sealed into the chain ...

ChainVerification v = auditLog.verifyChain();
if (!v.isValid()) {
    // tampering detected at link v.getFirstBrokenIndex()
}
```

## Where this fits

- **Tracing** (spans, OTel/Langfuse export): see [Trace & Observability](/docs/agent/trace-observability).
- **Session lifecycle** (snapshot/restore): see [Session Runtime](/docs/agent/session-runtime).
- **Sandbox execution** (where the replayed/recovered tools may run): see [Sandbox SPI](/docs/agent/sandbox-spi).
