---
title: "Memory and State"
description: "Breaks down the state model of AgentMemory in ai4j-agent: how user input, model output, and tool output are uniformly fed back into the next turn's prompt, and the real semantics of writes, compaction, and session isolation in InMemoryAgentMemory and JdbcAgentMemory."
tags: [concept]
---

# Memory and State

In `ai4j-agent`, memory is not a "chat history accessory" — it is the state source of the Agent loop.

As soon as you enter multi-step reasoning, the runtime has to answer four questions every turn:

- Where does the context before the current turn come from
- Which parts of the model output enter the next turn
- How tool results are fed back to the model
- How long sessions are trimmed without losing key information

This set of semantics converges on `AgentMemory`. Only by understanding memory can you truly see why the ReAct, CodeAct, and Team runtimes keep running, rather than being a "one request + one response" chat wrapper.

## 1. Start with the shortest definition of the state model

Source entry points:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/AgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/InMemoryAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/JdbcAgentMemory.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

The `AgentMemory` interface is deliberately restrained:

```java
public interface AgentMemory {
    void addUserInput(Object input);
    void addOutputItems(List<Object> items);
    void addToolOutput(String callId, String output);
    List<Object> getItems();
    String getSummary();
    void clear();
}
```

It models only three kinds of writes:

- User input
- Model output items
- Tool output

This shows that memory is not a secondary copy for rendering a transcript in the UI; it is the direct source of the Agent's next-turn prompt.

## 1.5 The four pillars of context management: memory / session / checkpoint / compact

These four terms are scattered across four pages, but they are really **four layers of the same thing** — keeping a long-horizon Agent coherent within a finite context window. Locking the relationship down with one diagram first keeps the details below from blurring together.

```text
                  ┌─────────────────────────────────────────────┐
   each runtime   │  buildPrompt(memory.getItems())             │  ← ④ compact compresses before this step
   turn reads     └─────────────────────────────────────────────┘
   from memory          ▲
                        │ read/write
                 ┌──────┴──────┐
                 │ ① memory    │  AgentMemory: all facts of the current session (input/output/tool results/summary)
                 └──────┬──────┘
                        │ belongs to
                 ┌──────┴──────┐
                 │ ② session   │  AgentSession: one session = memory + runtime context; the isolation boundary
                 └──────┬──────┘
                        │ snapshot/restore
                 ┌──────┴──────┐
                 │ ③ checkpoint│  AgentSessionStore: persists the session snapshot for recovery across restarts/instances
                 └─────────────┘
```

In one sentence, each:

| Concept | What it solves | When it kicks in | Source entry point |
| --- | --- | --- | --- |
| **① memory** (`AgentMemory`) | "What to bring into the next turn's prompt" | Read by the runtime every turn | `agent/memory/AgentMemory.java` |
| **② session** (`AgentSession`) | "The isolation boundary of one session" | Created by `agent.newSession()`, `run()` executes inside it | `agent/AgentSession.java` |
| **③ checkpoint** (`AgentSessionStore`) | "Recover a session across restarts/instances" | Explicit `snapshot()` to store, `restore()` to fetch | `agent/InMemoryAgentSessionStore` / `FileAgentSessionStore` |
| **④ compact** (`ContextProjector` + `CompactPolicy`) | "Context is about to overflow — what to compress away" | `ContextProjector` projects every turn; `CompactPolicy` compresses the memory snapshot when triggered | `agent/memory/ContextProjector.java` / `CompactPolicy.java` |

**Common points of confusion**:
- memory is the "fact layer", session is the "isolation layer", checkpoint is the "persistence layer", compact is the "governance layer" — the four do not overlap.
- compact compresses the **memory snapshot** (§7), not the session and not the prompt itself; after compression it writes the result back to memory, so the next turn's prompt naturally shrinks.
- session isolation works by **copying memory** (§8), not by copying the runtime.
- A checkpoint stores a session snapshot (memory + runtime state), not a single message — this is the fundamental difference from `ChatMemory` (the Core SDK's message storage): `ChatMemory` stores session facts, a checkpoint stores the **entire recoverable execution state**.

**One loop of a typical long-horizon Agent**:

1. The runtime fetches the current context from `memory.getItems()`
2. `ContextProjector` projects this turn's prompt by budget/policy (④'s projection responsibility)
3. Model + tools execute; results are written back to memory (①)
4. When the context is about to exceed the limit, `CompactPolicy` compresses the memory snapshot into a structured result + a new snapshot (④'s compaction responsibility)
5. When the user pauses/restarts, `AgentSessionStore` persists the session snapshot (③); on recovery, `restore()` rebuilds it (② ← ③)

The sections from §2 on expand each layer.

## 2. The real execution chain: how state enters the next turn

The actual main chain lives in `BaseAgentRuntime.runInternal()`:

```text
AgentRequest.input
  -> memory.addUserInput(...)
  -> buildPrompt(items = memory.getItems())
  -> modelClient.create(...)
  -> memory.addOutputItems(modelResult.getMemoryItems())
  -> toolExecutor.execute(...)
  -> memory.addToolOutput(callId, output)
  -> buildPrompt(items = memory.getItems())
```

Two key conclusions emerge here:

- The runtime does not keep an independent "hidden history"; the next turn's context is always rebuilt from `memory.getItems()`
- Tool results and model output are not bypass data — they formally enter the next turn's state

Therefore, the behavior of memory directly changes the Agent's reasoning trajectory, not merely whether the history is viewable.

## 3. `AgentMemory` solves state unification, not storage

If you only keep a single string prompt, you quickly hit three problems:

- User input, model output, and tool output have no unified data plane
- Compaction can only trim whole text segments and cannot preserve structured items
- Different runtimes struggle to share one set of context semantics

`ai4j-agent` chooses to keep items as `List<Object>` rather than pre-concatenating into a large text. This brings three direct benefits:

- `BaseAgentRuntime.buildPrompt()` can construct an `AgentPrompt` uniformly
- Tool output can re-enter the model with `function_call_output` semantics
- The compactor can trim at the item level, instead of doing irreversible surgery on the final prompt text

## 4. `summary` carries more weight than a "summary field"

Many projects treat summary as metadata, but `ai4j-agent` goes further.

For both `InMemoryAgentMemory.getItems()` and `JdbcAgentMemory.getItems()`, whenever `summary` is non-empty, it first inserts one entry:

```java
AgentInputItem.systemMessage(summary)
```

And then appends the original items.

This means:

- summary enters the model's context, not just something for the host application to read
- The prompt strength of summary approaches a system-level memory item
- If the summary is written badly, all subsequent turns can be persistently led astray by it

This is also why memory compaction is not an "optional micro-optimization", but a core path that affects reasoning quality.

## 5. `InMemoryAgentMemory`: the real semantics of the default implementation

`AgentBuilder.build()` defaults to:

```java
Supplier<AgentMemory> resolvedMemorySupplier =
        memorySupplier == null ? InMemoryAgentMemory::new : memorySupplier;
```

That is, when not explicitly configured, each Agent gets an in-process memory.

### 5.1 Write behavior

The three writes in `InMemoryAgentMemory` are all straightforward:

- `addUserInput(String)` wraps the value as `AgentInputItem.userMessage(...)`
- `addOutputItems(...)` directly appends the memory items returned by the model
- `addToolOutput(callId, output)` wraps the value as `AgentInputItem.functionCallOutput(...)`

There is one important boundary here:

- When `callId == null`, the tool output is silently ignored

:::note callId must be non-null
Normally, the runtime's `normalizeToolCalls()` fills in any missing `callId` with the default format `tool_step_<step>_<index>`, but if you bypass the runtime and write the chain yourself, you must enforce this constraint yourself.
:::

### 5.2 When compaction triggers

`InMemoryAgentMemory` synchronously calls `maybeCompress()` after every write.

This means:

- Compaction happens on the write path
- A compaction failure directly affects the current request
- The compactor must be written as critical-path code

One easily overlooked detail:

- `setCompressor(...)` only swaps the compactor reference; it does not immediately recompress the existing state

That is, for the in-memory implementation the new compactor only takes real effect on the next `addUserInput`, `addOutputItems`, or `addToolOutput`.

### 5.3 `snapshot()/restore()` are implementation-level capabilities only

`InMemoryAgentMemory` exposes `snapshot()` and `restore()`, but they are not part of the `AgentMemory` interface.

This shows that the runtime layer depends only on the unified read/write contract, while "snapshot export/recovery" is an engineering capability tacked on by the specific storage implementation — not a public API that every memory backend is forced to support.

## 6. `JdbcAgentMemory`: it brings recovery, and also write amplification

`JdbcAgentMemory` is not a thin persistence layer over `InMemoryAgentMemory`; it redefines a clear set of persistence boundaries.

### 6.1 Construction constraints

At construction it runs several hard validations:

- `sessionId` must not be empty
- `tableName` must pass a SQL identifier regex check
- At least one of `dataSource` and `jdbcUrl` must be provided

If `initializeSchema = true`, the constructor also attempts to create the table automatically.

### 6.2 Read/write model

Its read/write pattern is not append-only, but rather:

1. `loadSnapshot()`
2. Modify the snapshot in memory
3. `replaceSnapshot(...)`

The implementation of `replaceSnapshot(...)` is:

- First delete old records by `session_id`
- Then re-insert the summary record
- Then batch-insert item records

This indicates that `JdbcAgentMemory` currently prioritizes:

- Keeping semantics consistent with the in-memory implementation
- Keeping the recovery path simple
- Allowing summary and items to be replaced atomically together

Rather than:

- Minimal incremental writes
- Extreme high-throughput

For long sessions, high QPS, or large tool output scenarios, this write amplification cost must be assessed separately.

### 6.3 `setCompressor(...)` behaves differently from the in-memory implementation

`JdbcAgentMemory.setCompressor(...)` immediately executes:

```java
replaceSnapshot(applyCompressor(loadSnapshot()))
```

That is, when the JDBC implementation switches compressors, it rewrites the current persisted state on the spot; the in-memory implementation does not. This is one of the easiest differences between the two implementations to misjudge.

### 6.4 How summary is stored in the database

The JDBC implementation splits the snapshot into two kinds of entries:

- `entry_type = item`
- `entry_type = summary`

The summary is not a redundant string concatenated into the items; it is stored as a separate record. But during `getItems()`, it is still converted back into `systemMessage(summary)` and injected into the context.

## 7. The compactor contract: it compresses snapshots, not individual messages

Source entry points:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemoryCompressor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/MemorySnapshot.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/memory/WindowedMemoryCompressor.java`

What the compactor works on is:

- `items`
- `summary`

Not "how to trim a single message as it comes in". This gives implementers enough freedom to:

- Keep a recent window
- Fold older context into the summary
- Keep the original text for some tool outputs while retaining only a summary for others

### 7.1 The built-in `WindowedMemoryCompressor`

The default built-in windowed compactor does one thing only:

- When `maxItems` is exceeded, keep only the last N items

It does not auto-generate a summary, nor does it modify an existing summary.

So it behaves more like a "hard trim window" than a "long-term memory strategy". If your Agent needs to carry conclusions, constraints, or earlier tool discoveries across many turns, pure windowed compaction is usually not enough.

## 8. Session isolation is not a runtime copy, it is a memory copy

The key action of `Agent.newSession()` is not cloning the runtime, but fetching another memory from `memorySupplier`.

This yields a very important criterion:

- Whether sessions are isolated does not depend on the `AgentSession` class name
- It depends on whether `memorySupplier` truly returns a fresh state container each time

:::warning memorySupplier must not be a shared singleton
If you write `memorySupplier` as a shared singleton, even if you seemingly get two sessions on the surface, they will still leak state to each other.
:::

For `JdbcAgentMemory`, there is an extra requirement:

- The same `sessionId` must not be reused

Otherwise, even if the object instance is new, it will still land on the same persisted snapshot.

## 9. The boundary of memory: it is not all runtime state

To avoid misuse, you need to separate memory from the other concepts.

### 9.1 It is not the entire runtime state

`AgentMemory` is responsible only for the context state that can be fed back to the model. Runtime information such as step counts, event streams, thread execution state, and tool thread pools is not all stored here.

### 9.2 It is not `ChatMemory`

`ChatMemory` leans toward ordinary multi-turn conversation context; `AgentMemory` targets the inputs, model outputs, tool outputs, and compaction strategies internal to the Agent loop.

### 9.3 It is also not the complete session of the Coding runtime

A session in `ai4j-coding` also contains host-state information such as processes, file systems, checkpoints, and compaction. It is broader than `AgentMemory`; do not conflate the two.

## 10. The constraints you truly must uphold when writing a custom implementation

If you are going to implement Redis, MongoDB, or business-side session storage yourself, at minimum uphold these semantics:

- `getItems()` must return items that can directly participate in the next turn's prompt
- `addToolOutput()` should convert tool results into item semantics the model can understand
- `summary` and recent items must not overwrite each other into an unrecoverable single-layer text
- Compaction must behave predictably in its error semantics and must not silently swallow state
- The session isolation boundary must stay consistent with your business session ID

If any one of these drifts, the Agent will still appear to run on the surface, but its long-term behavior will start to drift.

## 11. Where to look first when debugging this

When you see "the model forgets things", "tool results aren't fed back", "multiple sessions leak state", or "long tasks drift further as they run", check these entry points first:

- Whether `BaseAgentRuntime.runInternal()` actually calls `memory.addUserInput / addOutputItems / addToolOutput`
- In the list returned by `memory.getItems()`, whether summary is injected as a `systemMessage`
- Whether your custom `memorySupplier` reuses a singleton
- Whether the `sessionId` of `JdbcAgentMemory` is mistakenly reused
- Whether the compactor dropped key items outright instead of folding them into the summary

These spots are closer to the real source of the problem than inspecting the front-end chat log.

## 12. Further reading

1. [Memory Management and Compaction Strategy](/docs/agent/memory-compact-context)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Agent Architecture](/docs/agent/architecture)
4. [Trace Observability](/docs/agent/trace-observability)
