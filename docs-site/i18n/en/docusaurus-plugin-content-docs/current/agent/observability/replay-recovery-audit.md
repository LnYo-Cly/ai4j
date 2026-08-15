---
sidebar_position: 10
title: "Replay, Recovery & Audit"
description: "The four production-grade capabilities layered on the ai4j-agent event stream, explained in depth: node I/O capture and replay (IoCaptureAgentListener/NodeReplayer/NodeIoRecord), resume-cache failure recovery (ResumeCache/ResumableModelClient/ResumableToolExecutor — content-addressed, side effects not replayed), persistent session store, and SHA-256 hash-chain tamper-evident audit (HashChainedEventLog)."
tags: [reference]
---

# Replay, Recovery & Audit

`ai4j-agent` layers four **optional production-grade capabilities** on top of the runtime event stream. They are all **consumers/decorators** — they don't modify the runtime, they reuse the events the runtime already emits (`MODEL_REQUEST`/`MODEL_RESPONSE`/`MODEL_REASONING`/`MODEL_RETRY`/`TOOL_CALL`/`TOOL_RESULT`/`STEP_END`). Wire up whichever layer you need.

| Capability | Main classes | What it solves |
| --- | --- | --- |
| Node I/O capture + replay | `IoCaptureAgentListener`, `NodeIoRecord`, `NodeReplayer` | Persist the complete input/output of every model/tool node to disk, individually replayable (real re-invocation or deterministic playback) |
| Failure recovery / resume | `ResumeCache`, `ResumableModelClient`, `ResumableToolExecutor` | Re-run after a crash, skip completed nodes, **don't repeat side effects that have already taken effect** |
| Persistent session store | `FileAgentSessionStore`, `JdbcAgentSessionStore` | Survive cross-process restarts; long tasks can be resumed |
| Tamper-evident audit | `HashChainedEventLog` | Prove the recorded activity was not altered after the fact |

## 0. Key design decisions to grasp first

These decisions run through all four layers. State them up front so each later section doesn't re-argue them.

### 0.1 All are event-stream consumers, not runtime changes

All four layers plug in via `AgentListener` or decorators (`AgentModelClient`/`ToolExecutor`). The runtime doesn't know you're capturing, resuming, or auditing — it keeps emitting events and calling models/tools as usual. This means these capabilities can be combined freely and removed at any time without affecting the agent's main flow.

### 0.2 Capture and resume are two independent mechanisms — don't conflate them

- **Capture (IoCapture)** records node I/O as `NodeIoRecord`, for **replay and audit**.
- **resume (ResumeCache)** is a content-addressed cache, for **skipping re-execution**.

Both "record", but with different goals: capture must be fully replayable, resume only needs enough to decide "has this node been done?". You can use either one alone.

### 0.3 These layers are best-effort; they don't block the main flow

The capture listener's `onEvent` swallows every exception (`catch (Exception ignored)`) — an audit/capture component must never take down an agent run. A failed resume cache lookup degrades to a real call. This is by design: observability/recovery is an optimization layer, not the critical path.

### 0.4 Single-machine model, not a distributed execution-log system

Like tracing, these four layers are lightweight mechanisms within a single JVM. Strongly consistent cross-instance execution logs are not their goal — for those semantics you need an external system.

## 1. Node I/O capture: `IoCaptureAgentListener`

### 1.1 What it does

An `AgentListener` that pairs events into `NodeIoRecord`:

- **MODEL nodes**: `MODEL_REQUEST` (input = `AgentPrompt`) paired with `MODEL_RESPONSE` (output = raw response). Pairing key is `runId|turnId|step`.
- **TOOL nodes**: `TOOL_CALL` (input = `AgentToolCall`) paired with `TOOL_RESULT` (output = `AgentToolResult`). Pairing key is `runId|turnId|step|callId` (falls back to toolName when callId is absent, then to `anon`).

```java
InMemoryIoCaptureSink sink = new InMemoryIoCaptureSink();
Agent agent = Agents.react().anthropicMessages(key, baseUrl).model("glm-5.1").build();

agent.newSession().runStreamResult(
        AgentRequest.builder().input("...").build(),
        new IoCaptureAgentListener(sink));   // attach as a listener

List<NodeIoRecord> modelNodes = sink.records(NodeIoRecord.NodeType.MODEL);
List<NodeIoRecord> toolNodes  = sink.records(NodeIoRecord.NodeType.TOOL);
```

### 1.2 Output accumulation rules for MODEL nodes (easy to misread)

A streamed response emits multiple `MODEL_RESPONSE` events. The listener handles them as follows:

- `outputText`: the `message` (delta text) of each event is **accumulated by concatenation** — this is the human-readable output.
- `outputs`: only the **latest non-empty raw payload** is kept — overwrite, not concatenation.

This keeps `outputText` (readable text) and `outputs` (provider-specific response objects) separate so they don't pollute each other. `NodeReplayer.replayModelMock(...)` can therefore prefer the raw payload and fall back to accumulated text when absent.

### 1.3 Extra enrichment for MODEL nodes (these fields are empty/0 on TOOL nodes)

| Field | Source event | Notes |
| --- | --- | --- |
| `reasoningText` | `MODEL_REASONING` | Model chain-of-thought; multiple segments joined by newlines when streaming |
| `retryCount` | `MODEL_RETRY` | Retry count for this step |
| `inputTokens` / `outputTokens` | `usage` block of the raw response | best-effort parse, may be `null` |

Token parsing is **provider-agnostic, multi-key, fault-tolerant**: inputs accept `prompt_tokens`/`promptTokens`/`input`/`input_tokens`, outputs accept `completion_tokens`/`completionTokens`/`output`/`output_tokens`. A slot of `-1` means "key absent", distinguished from a real `0`.

### 1.4 When a node is persisted

- MODEL node: flushed at `STEP_END` (the model calls of a step are done).
- TOOL node: flushed immediately at `TOOL_RESULT`.
- A `TOOL_RESULT` with no paired `TOOL_CALL` (e.g. an external result injected directly): best-effort capture, without input.

### 1.5 Full field map of `NodeIoRecord`

Each record is self-describing; you can do cost accounting and auditing without deserializing `outputs`:

| Field | Meaning |
| --- | --- |
| `recordId` | UUID (randomly generated when unspecified) |
| `runId`/`sessionId`/`turnId`/`step` | Locates the original run |
| `nodeType` | `MODEL` or `TOOL` |
| `nodeId` | Node locator string, prefixed `model@` or `tool@`, followed by stepKey (TOOL appends callId) |
| `modelId` | Model name for a MODEL node (from `AgentPrompt.getModel()`) |
| `inputs` | MODEL=`AgentPrompt`, TOOL=`AgentToolCall` (raw object, for live replay) |
| `outputText` | Accumulated MODEL text |
| `outputs` | Latest MODEL raw payload / `AgentToolResult` for TOOL |
| `startedAtEpochMs` / `capturedAtEpochMs` | Node start/finish; `getDurationMs()` gives single-node latency |
| `reasoningText` / `retryCount` / `inputTokens` / `outputTokens` | MODEL enrichment (see 1.3) |

### 1.6 Two sinks

| Sink | Use |
| --- | --- |
| `InMemoryIoCaptureSink` | Query by `NodeType` in memory; testing, ad-hoc analysis |
| `JsonlIoCaptureSink` | One node per JSON line, append-only; **persistent audit/replay artifact** |

For cross-process replay or archival, use `JsonlIoCaptureSink`.

## 2. Node replay: `NodeReplayer`

Replays a captured `NodeIoRecord`. MODEL nodes have two modes with very different semantics:

| Mode | Method | Calls the model? | Deterministic? | Use case |
| --- | --- | --- | --- | --- |
| live | `replayModelLive(record, modelClient)` | **Yes** (real LLM call) | No | Re-run a node, A/B compare models, reproduce a flow with fresh output |
| mock | `replayModelMock(record)` | No | **Yes** | Exactly reproduce a past turn |

```java
NodeReplayer replayer = new NodeReplayer();
// live: take the captured prompt and call the real model again
AgentModelResult fresh = replayer.replayModelLive(modelNodes.get(0), modelClient);
// mock: don't call the model; rebuild the result from captured output (raw first, outputText fallback)
AgentModelResult same  = replayer.replayModelMock(modelNodes.get(0));
```

Key points:

- `replayModelLive` requires the record's `inputs` to be an `AgentPrompt`, otherwise it throws — it just feeds the captured prompt as-is to `modelClient.create(...)`.
- `replayModelMock` prefers `outputs` (raw payload), with `outputText` as fallback; when neither is present, `outputText` may also be a `String`-typed outputs.
- Live replay of a TOOL node is `replayToolLive(record, reinvoker)`: the SDK **makes no assumption about how a tool re-binds**; the caller passes a `Function` mapping `AgentToolCall` to `AgentToolResult` to decide how it's re-invoked.
- Passing the wrong type for a non-MODEL/TOOL node throws `IllegalArgumentException` — a hard check, not a silent skip.

## 3. Failure recovery / resume: `ResumeCache` + decorators

### 3.1 Core mechanism: content-addressed cache

`ResumeCache` uses **content addressing** to decide "has this node been done?":

| Node type | Cache key | How it's computed |
| --- | --- | --- |
| MODEL | serialized prompt | `JSON.toJSONString(prompt)` |
| TOOL | `name\|arguments` | tool name + `|` + arguments string |

Same input key hits the same cache entry — that's the essence of "skip completed work".

### 3.2 Two decorators: the same instance captures and resumes

```java
ResumeCache cache = new ResumeCache();
Agent agent = Agents.react()
        .modelClient(new ResumableModelClient(realModelClient, cache))
        .toolExecutor(new ResumableToolExecutor(realToolExecutor, cache))
        .build();
agent.newSession().run(request);   // first run: every node misses → real call + record
agent.newSession().run(request);   // re-run with same input: all hits → zero real calls
```

- `ResumableModelClient.create`: lookup hits → return cached `AgentModelResult`, **does not call the delegate**; miss → delegate call + record.
- `ResumableToolExecutor.execute`: lookup hits → return cached output, **does not call the delegate**; miss → delegate call + record.

### 3.3 Side effects are not replayed — this is the safety core of resume

When `ResumableToolExecutor` hits the cache it **does not execute the real tool**. This is critical: when re-running after a crash, side effects that have already taken effect (file writes, API calls, billing) cannot happen again. The semantics of resume are not "redo", but "continue from where we last finished".

### 3.4 The streaming trap (easy to step on)

`createStream` also consults the cache; on a hit it **returns the cached result directly and does not re-emit deltas to the listener**. That is:

- resume is primarily aimed at the non-streaming `create` path.
- during streaming resume, downstream streaming listeners do not receive a token-by-token replay — they get the terminal result.

If your logic depends on a stream of deltas, don't expect resume to replay it.

### 3.5 Cross-process persistence

```java
cache.saveToJson(Path.of("/var/ai4j/resume.json"));     // persist after the first run
ResumeCache loaded = ResumeCache.loadFromJson(Path.of("/var/ai4j/resume.json"));  // load after restart
```

`saveToJson` writes `modelResults` + `toolOutputs` to a single JSON file; `loadFromJson` returns an empty cache when the file is absent (no throw). Cross-process resume = run1 captures + persists → restart → run2 loads + resumes.

### 3.6 Counters and crash simulation (for testing)

- `getModelHits()/getModelMisses()/getToolHits()/getToolMisses()`: assert how many nodes were replayed vs. really executed.
- `removeLastModelEntry()`: removes the most recent model record, **simulating "crashed just before the last step"** — pair with resume to test "after a crash, a re-run can continue from the second-to-last step".

## 4. Persistent session store

`AgentSessionStore` is backend-neutral; it checkpoints a session to survive restarts:

- **`FileAgentSessionStore(dir)`**: one snapshot per JSON file, zero dependencies (filesystem only). The lightweight default.
- **`JdbcAgentSessionStore(config)`**: one snapshot per row, uses only JDK `javax.sql` (bring your own driver). Suitable for shared/multi-instance production databases.

```java
AgentSessionStore store = new FileAgentSessionStore(Path.of("/var/ai4j/sessions"));
store.save(session.snapshot());
AgentSessionSnapshot restored = store.load(sessionId);   // after restart
session.restore(restored);
```

Note: the session store holds **session snapshots** (memory + event log + compact results), a different thing from the `ResumeCache` in §3 (a node-level content-addressed cache). The session store lets a session survive restarts; ResumeCache lets a single run skip completed nodes. The two can be stacked.

## 5. Tamper-evident audit: `HashChainedEventLog`

### 5.1 How the hash chain is linked

A drop-in replacement for `InMemoryAgentSessionEventLog` (implements the same `AgentSessionEventLog` + `AgentListener` interfaces). Each event is sealed into a Link:

```
hash = sha256( prevHash || "|" || canonical(event) )
```

- `canonical(event)` = `JSON.toJSONString(event)` (the canonical form serialized by fastjson2).
- `prevHash` = the hash of the previous Link; the first Link uses the genesis value = 64 `0`s.
- The hash is output as lowercase hex.

Each Link stores three things: `event`, `prevHash`, `hash`. The chain is interdependent: alter the payload of any event and its own hash changes, and every subsequent Link's hash changes with it.

### 5.2 What `verifyChain()` catches

It recomputes the whole chain from genesis, comparing Link by Link, and **reports the first broken index**. It checks two things at once:

1. The recomputed `hash` matches the stored `hash`;
2. The stored `prevHash` matches the expected hash of the previous Link.

So these after-the-fact alterations **are all detected**:

| Tampering | Detection point |
| --- | --- |
| Altering an event payload (without re-sealing) | That Link's hash doesn't match |
| Deleting a middle entry | The subsequent Link's prevHash breaks the chain |
| Swapping two entries' order | The involved Links' hash/prevHash are both wrong |

```java
HashChainedEventLog auditLog = new HashChainedEventLog();
agent.newSession().runStreamResult(request, auditLog);   // as a listener, each event is sealed into the chain

ChainVerification v = auditLog.verifyChain();
if (!v.isValid()) {
    // v.getFirstBrokenIndex() points at the first broken Link
}
```

### 5.3 `restore` re-seals the chain

`restore(events)` does not trust stored hashes directly — it **clears the chain and recomputes every hash from scratch**. So events restored from a trusted source get re-sealed. The `sequence` counter is aligned to the maximum of the restored events.

### 5.4 `tamperEvent` for testing

`tamperEvent(index, replacement)` replaces a Link's payload **but does not re-seal the hash** — it simulates "after-the-fact editing" on purpose. The next `verifyChain()` must report this index as broken. This is a backdoor for audit testing; don't call it from production code.

### 5.5 What it proves and what it doesn't

The hash chain proves **integrity and after-the-fact tamper resistance**: the recorded event sequence was not altered. It does **not** prove the events themselves actually happened (that needs trusted capture), and it is **not** encrypted — anyone who can read the JSON can read the events. For confidentiality, encrypt at an outer layer; the hash chain only covers integrity.

## 6. Boundaries and easily misjudged semantics

- **Capture and resume are two separate records**: capture must be fully replayable (`NodeIoRecord`), resume only needs enough to deduplicate (content key → result). Don't expect `ResumeCache` to do replay, and don't expect `IoCaptureAgentListener` to do resume.
- **The MODEL cache key is the entire serialized prompt**: if any field in the prompt changes (including temperature, or tools-list order affecting serialization), it misses. This is the cost of content addressing — precise but sensitive.
- **Streaming resume doesn't replay deltas**: see §3.4.
- **The audit chain is not encrypted**: see §5.5.
- **All four layers are in-process**: cross-instance strong consistency is not their design goal.
- **Token parsing is best-effort**: the token fields on `NodeIoRecord` may be `null`; don't assume a value in audit logic.

## 7. How the four layers combine

| Scenario | Combination |
| --- | --- |
| Reproduce a past turn locally | Capture (InMemory) + `replayModelMock` |
| A/B compare two models' output on the same node | Capture + `replayModelLive` (swap modelClient) |
| Re-run after a crash without side effects | resume (`ResumableModelClient` + `ResumableToolExecutor`); add `saveToJson` for cross-process |
| Long task resumed across restarts | session store (File/Jdbc) |
| Compliance audit "the record wasn't altered" | `HashChainedEventLog` + `verifyChain` |
| Full production config | Capture (Jsonl to disk) + resume + session store + audit chain |

## 8. Further reading

- **Tracing** (spans, OTel/Langfuse export): [Trace & Observability](/docs/agent/observability/trace-observability)
- **Session lifecycle** (snapshot/restore): [Session Runtime](/docs/agent/session-runtime)
- **Where replay/recovery tools run**: [Sandbox SPI](/docs/agent/governance/sandbox-spi)
