---
sidebar_position: 6
title: "Memory Compact Context Projector"
description: "Introduces the ContextProjector and CompactPolicy in ai4j-agent: how to project a memory snapshot into the current turn's prompt, compress long-horizon context with a structured CompactResult, and keep the compaction process diagnosable and recoverable."
tags: [concept]
---

# Memory Compact Context Projector

This page describes the newly added Memory / Compact / Context Projector foundation capability in `ai4j-agent`.

Conclusion first: it is not an "automatically smarter summarizer", but rather separates the three things that get most chaotic in long-horizon agents:

```text
SessionEventLog = complete factual history
AgentMemory     = state source for the Agent loop
ModelContext    = what is actually sent to the model this turn
```

The value of P0-B is that these three layers can be saved, projected, compacted, and diagnosed, rather than leaving developers to hard-truncate an ever-growing `List<Object>`.

## 1. Why this layer is needed

A long-horizon agent keeps accumulating:

- user input
- model output
- tool calls
- tool results
- failed commands
- test results
- human approvals
- sandbox state
- file and artifact changes

If everything is stuffed directly into the model context, three problems arise:

1. The context window overflows.
2. Old and new information have no priority.
3. After compaction, nobody knows what was lost.

So P0-B adds two clear boundaries:

- `ContextProjector`: decides which items the current turn's prompt carries.
- `CompactPolicy`: compacts the memory snapshot into a structured result and a new memory snapshot.

## 2. New core classes

| Class | Package | Responsibility |
| --- | --- | --- |
| `ContextBudget` | `io.github.lnyocly.ai4j.agent.context` | Describes the context budget, e.g. max item count, approximate character count, retained prefix count, Tier-1/2 tiering switches |
| `ContextProjector` | `io.github.lnyocly.ai4j.agent.context` | Projects memory items into the current turn's prompt items |
| `DefaultContextProjector` | `io.github.lnyocly.ai4j.agent.context` | Default projector: retains the pinned prefix and the recent tail |
| `TypeAwareContextProjector` | `io.github.lnyocly.ai4j.agent.context` | Type-aware projector: Tier-1 tool result microcompact + Tier-2 reasoning trimming |
| `ContextProjection` | `io.github.lnyocly.ai4j.agent.context` | The projected items and report |
| `ContextReport` | `io.github.lnyocly.ai4j.agent.context` | Records item count, approximate character count, drop count, and notes before and after projection |
| `CompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | Compaction policy interface |
| `CompactResult` | `io.github.lnyocly.ai4j.agent.compact` | Structured compact result |
| `StructuredSummaryCompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | Built-in deterministic structured-summary policy |
| `LlmCompactPolicy` | `io.github.lnyocly.ai4j.agent.compact` | Tier-3: LLM structured summary, backfills `CompactResult` fields |
| `CompactPolicyMemoryCompressor` | `io.github.lnyocly.ai4j.agent.compact` | Adapts a `CompactPolicy` into the existing `MemoryCompressor` |

## 2.5 Three-tier tiered compaction: projection vs compaction

The two boundaries above (`ContextProjector` / `CompactPolicy`) together form a **three-tier tiering**, with cost and destructiveness rising per tier:

| Tier | Where it lands | Trigger | LLM-dependent | Mutates memory? |
| --- | --- | --- | --- | --- |
| **Tier-1** tool result microcompact | `TypeAwareContextProjector` | Every turn | No | **No** (projection only) |
| **Tier-2** reasoning trim | `TypeAwareContextProjector` | Every turn (opt-in) | No | **No** (projection only) |
| **Tier-3** LLM summary | `LlmCompactPolicy` | Threshold-triggered | Yes | **Yes** (`memory.restore`) |

The key distinction, and the core of this design:

```text
Tier-1/2 = projection layer. The facts are still in memory; the model just cannot see them this turn.
Tier-3   = compaction layer. Memory is replaced by the summary; the old items are really gone.
```

**So Tier-1 can be aggressive.** A trimmed tool result is not a deletion — the original text still lies in memory, Tier-3 summarization can still fetch the full content, and replay and audit are unaffected. This differs from Claude Code, where microcompact edits the message array in place: once trimmed, it is trimmed.

### Tier-1: keep only the most recent N tool results

Tool results are the most space-consuming things: reading a large file or running a test pushes thousands of characters into memory, yet a few turns later the model rarely looks back at them.

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .contextProjector(new TypeAwareContextProjector())
    .contextBudget(ContextBudget.builder()
        .maxItems(40)
        .maxRecentToolResults(5)   // only the 5 most recent tool results keep their original text
        .build())
    .build();
```

Earlier `function_call_output` items are replaced with a placeholder rather than pulled out directly:

```text
{type: function_call_output, call_id: call-3, output: "[tool result cleared: read(call-3)]"}
```

Keeping a placeholder is deliberate — the model needs to know "read was called here, the result is gone now", rather than seeing a chunk of history vanish. The tool name is obtained by joining `call_id` back to the assistant message's `tool_calls[].id` (the `function_call_output` itself carries only `call_id`, not the tool name).

When `maxRecentToolResults` is not configured (default `null`), Tier-1 is not engaged and `TypeAwareContextProjector` behaves **identically** to `DefaultContextProjector` — a backward-compatibility contract guarded by regression tests.

### Tier-2: clear reasoning from old turns

```java
ContextBudget.builder()
    .maxRecentToolResults(5)
    .trimOldReasoning(true)     // default false
    .build();
```

Only `reasoning` items **before the last user message** are cleared — the current turn's chain of reasoning is kept, since the model may still be building on it. The item's `type` and `id` are retained; only the payload is cleared, so the sequential structure is not broken.

:::note Only effective for the Responses API shape
`reasoning` existing as an independent memory item is a shape of the OpenAI Responses API. Under the Anthropic / OpenAI Chat paths, reasoning goes through `AgentModelResult.reasoningText` and never enters memory in the first place, so Tier-2 is a no-op for them.
:::

### Which tier took effect: see ContextReport.notes

All three tiers emit the `MEMORY_COMPRESS` event and are distinguished by `notes`:

```text
tier1 microcompact: cleared 3 old tool result(s), kept recent 5
tier2 reasoning-trim: cleared 2 reasoning item(s)
maxItems applied: 40
```

### Comparison with Claude Code

| Claude Code | AI4J | Difference |
| --- | --- | --- |
| Tier-1 microcompact (clears old tool results each turn) | `TypeAwareContextProjector` + `maxRecentToolResults` | AI4J goes through projection and does not mutate memory; keep-N is configurable, not hardcoded |
| Tier-2 type-aware trim | `trimOldReasoning` | In Claude Code this layer is partly API server-side behavior; AI4J as an SDK only does what is client-controllable |
| Tier-3 nine-section structured summary | `LlmCompactPolicy` | AI4J uses seven sections (see below); `sandboxState`/`userConfirmations` belong to coding-agent scenarios and are not forced at the core agent layer |
| `cache_edits` (surgical prompt-cache deletion) | Not done | Requires server-side coordination; there is no SDK-side counterpart |


## 3. Context Projector: controlling the current turn's prompt

The default projector does one simple but stable thing:

```text
Keep the first N pinned prefix items
+
Keep the most recent tail items
+
Return a ContextReport
```

Example:

```java
Agent agent = Agents.react()
    .modelClient(modelClient)
    .model("gpt-4.1")
    .memorySupplier(InMemoryAgentMemory::new)
    .contextProjector(new DefaultContextProjector())
    .contextBudget(ContextBudget.builder()
        .maxItems(20)
        .pinnedPrefixItems(1)
        .build())
    .build();
```

If the first item in memory is a long-term summary or system-level working memory, you can use `pinnedPrefixItems(1)` to keep it; the rest of the budget prioritizes the most recent context.

The runtime calls the projector before prompt construction. `ReActRuntime`, `DeepResearchRuntime`, and `CodeActRuntime` all go through this projection entry point today.

## 4. Context Report: knowing what was lost

Whenever projection occurs, the runtime publishes an `AgentEventType.MEMORY_COMPRESS` event whose payload is `ContextReport`.

`ContextReport` contains:

- `sourceItemCount`
- `projectedItemCount`
- `droppedItemCount`
- `sourceApproxChars`
- `projectedApproxChars`
- `itemLimitApplied`
- `characterLimitApplied`
- `notes`

This lets the host, trace, or session event log know:

```text
Did the model see the full memory this turn, or a projected working context?
How much was kept after projection? How much was dropped? Which limit fired?
```

## 4.5 Compaction mechanism and timing: when it triggers, what it compacts, what happens on failure

This is the most easily misunderstood part of compaction. Three rules are pinned:

### Timing: checked at the top of each step, before the model call; the policy decides whether to compact

At the **top** of each step (before the model call), the runtime calls `autoCompactIfNecessary` in `BaseAgentRuntime`:

```text
For each step:
  → autoCompactIfNecessary (before this step's model call):
      policy = context.getCompactPolicy()
      if policy == null: skip
      snapshot = memory.snapshot()
      if !policy.shouldCompact(snapshot): skip           ← policy decides timing
      → policy.compact(snapshot)
      → memory.restore(result.getMemory())               ← write back to memory after compaction
  → executeModel(...)                                     ← build the prompt from (possibly compacted) memory
  → tool execution (memory.addToolOutput ...)             ← facts enter memory
  → next step
```

Key point: **the runtime does not decide "time to compact" itself; the timing decision is fully delegated to `CompactPolicy.shouldCompact(snapshot)`**. Compaction happens before the model call, so this step's prompt uses the compacted memory.

- The base `CompactPolicy.shouldCompact` returns `false` by default — **without a policy configured, no automatic compaction happens** (manual only).
- `StructuredSummaryCompactPolicy` overrides it as `snapshot.getItems().size() > maxItems` — **compaction only when the item count crosses the threshold**.
- `LlmCompactPolicy.shouldCompact` is the same counting threshold (`items.size() > maxItems`); **the model only generates the summary inside `compact()` and does not participate in the "should I compact" decision**. Its timing decision is identical to `StructuredSummaryCompactPolicy`; the difference is what means it uses when compacting (LLM summary vs mechanical projection).

So the answer to "when to compact" is not fixed — **the policy you choose decides**. Three typical strategies:

| Policy | shouldCompact check | Fits |
| --- | --- | --- |
| Base (default) | Never (manual only) | Short tasks, no automation needed |
| `StructuredSummaryCompactPolicy` | item count > maxItems | Deterministic item-count threshold |
| `LlmCompactPolicy` | item count > maxItems (same) | Same count threshold, but compaction uses an LLM structured summary |

### What gets compacted: the memory snapshot, not the prompt

`compact(MemorySnapshot)` receives the full snapshot of memory (items + summary), and after compaction returns a **new `MemorySnapshot`** that the runtime writes back via `memory.restore(...)`. **It does not edit the prompt or delete individual messages** — it replaces memory's set of facts, so the next turn's `buildPrompt(memory.getItems())` is naturally smaller.

This also explains why old item details are lost after compaction (replaced by the summary) — they are no longer in `memory.getItems()`.

### Two trigger paths

1. **Automatic** (the default developer choice): configure a policy with `shouldCompact` (e.g. `StructuredSummaryCompactPolicy`), and the runtime checks every turn.
2. **Manual**: call `session.compact(policy)` at any time to compact once immediately. Fits UI cases like "user clicked compact" or "proactively close out at a task-phase switch".

```java
// Automatic: attached to the context, the runtime checks every turn
Agent agent = Agents.react()
        .modelClient(client)
        .compactPolicy(new StructuredSummaryCompactPolicy(
                ContextBudget.builder().maxItems(30).pinnedPrefixItems(1).build()))
        .build();

// Manual: explicitly compact once
session.compact(new StructuredSummaryCompactPolicy(budget));
```

### Failure safety: a compaction exception does not abort the run

`autoCompactIfNecessary` wraps `policy.compact(...)` in try/catch: **if compaction throws, the run continues with the uncompacted memory and is not dragged down by a compaction failure**. This is deliberate — compaction is an optimization, not the critical path, and a compaction failure must not crash the whole agent run.

:::warning Manual compact does not get this protection
The automatic path (runtime-invoked) swallows exceptions; but an exception thrown by a direct `session.compact(policy)` call propagates. In the manual case you decide for yourself how to handle compaction failure.
:::

## 5. Compact Policy: compacting the memory snapshot

`CompactPolicy` operates on `MemorySnapshot`, not on individual messages.

```java
public interface CompactPolicy {
    CompactResult compact(MemorySnapshot snapshot);
}
```

`StructuredSummaryCompactPolicy` will:

1. Use `ContextProjector` to pick the retained items.
2. Generate a summary tagged with `AI4J_COMPACT_SUMMARY`.
3. Return a new `MemorySnapshot`.
4. Return a `ContextReport`.

Example:

```java
AgentSession session = agent.newSession();

session.compact(new StructuredSummaryCompactPolicy(
    ContextBudget.builder()
        .maxItems(30)
        .pinnedPrefixItems(1)
        .build()
));

CompactResult result = session.getLastCompactResult();
System.out.println(result.getContextReport().getDroppedItemCount());
```

## 6. Why CompactResult is structured

`CompactResult` is not just a string summary. It reserves structured fields:

- `completed`
- `pending`
- `decisions`
- `changedArtifacts`
- `failedCommands`
- `testResults`
- `userConfirmations`
- `sandboxState`
- `openQuestions`
- `contextReport`

How these fields get filled, by policy:

| Policy | Which fields it fills |
| --- | --- |
| `StructuredSummaryCompactPolicy` (deterministic) | Fills only `contextReport`; no semantic extraction |
| `LlmCompactPolicy` (Tier-3) | Fills `completed` / `pending` / `decisions` / `failedCommands` / `testResults` / `openQuestions`, plus `readFiles` / `modifiedFiles` from a mechanical scan |

`changedArtifacts` / `userConfirmations` / `sandboxState` are still left empty — they need workspace and approval context, which belongs to `ai4j-coding`'s territory, and the core agent layer does not fake them.

## 7. Relationship with AgentSession

P0-A already lets `AgentSession` snapshot / save / resume. P0-B adds on top of that:

- `AgentSession.compact(CompactPolicy)`
- `AgentSession.getLastCompactResult()`
- `AgentSessionSnapshot.compactResult`

This means:

```text
The structured result of a session compact can be saved with the snapshot
After resume, the last compact result is still readable
```

This matters for long-task recovery, UI display, and remote Runner.

## 8. Compatibility with the old MemoryCompressor

Existing `AgentMemory` implementations can still use `MemoryCompressor`.

If you want to wire a new policy into the old compaction entry point, you can use:

```java
CompactPolicyMemoryCompressor compressor =
    new CompactPolicyMemoryCompressor(
        new StructuredSummaryCompactPolicy(ContextBudget.maxItems(20))
    );

InMemoryAgentMemory memory = new InMemoryAgentMemory(compressor);
```

This way, writes to memory still go through the old compressor mechanism, while `compressor.getLastResult()` gives you the structured result.

## 9. Its boundary with Coding Agent Compact

Compaction in `ai4j-agent` is a general-purpose Agent SDK capability, focused on:

- memory snapshot
- model context projection
- compact result
- session snapshot

Compaction / checkpoint in `ai4j-coding` will be broader later, potentially including:

- workspace file changes
- shell command history
- git diff
- browser state
- project run/test state
- approval state
- sandbox artifact

So do not mechanically lift all of `ai4j-coding`'s checkpoint logic up into `ai4j-agent`. P0-B only provides the general foundation.

## 10. Where customization fits

You can customize:

| Extension point | Fits when |
| --- | --- |
| `ContextProjector` | You want to select context by role, tool result, token estimate, or RAG relevance |
| `ContextBudget` | You want to set the budget by model, tenant, or task type |
| `CompactPolicy` | You want a model to generate a structured summary, or to compact by combining the event log / artifacts |
| `MemoryCompressor` | You want to stay compatible with the old memory write path |

:::note Custom implementations must be diagnosable
Minimum requirement: a custom implementation must keep "what was kept, what was lost" diagnosable. Otherwise, when a long-horizon agent goes wrong, it is very hard to locate the cause.
:::

## 11. Current limitations

P0-B is a foundation layer and does not include:

- Token-level exact budgeting (the character count in `ContextBudget` is approximate).
- Full refinement from the event log into the compact result.
- Real compaction of sandbox artifacts.
- A checkpoint protocol for the remote Runner.

These will be filled in later across the plugin lifecycle, Sandbox SPI, Coding Agent routing, and Runner tasks.

## 12. Further reading

- [Agent Session Runtime](/docs/agent/session-runtime)
- [Memory and State](/docs/agent/memory-and-state)
- [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap)
- [Coding Agent Compact and Checkpoint](/docs/coding-agent/compact-and-checkpoint)

## Auto-compaction (runtime-triggered)

The runtime auto-compacts at the top of each step when the configured `CompactPolicy.shouldCompact`
returns true. Configure via `AgentBuilder.compactPolicy(...)`:

```java
// LLM-powered: uses the model to generate a structured summary of old items
LlmCompactPolicy policy = new LlmCompactPolicy(modelClient, "glm-5.1", 10);
Agent agent = Agents.react()
        .modelClient(modelClient).model("glm-5.1")
        .compactPolicy(policy)   // auto-compact when items > 10
        .build();
```

`LlmCompactPolicy` keeps the most recent N items and asks the LLM to summarize everything older.
For a mechanical (non-LLM) option, use `StructuredSummaryCompactPolicy` with a `ContextBudget`.

### Structured backfill

The summarizer is asked for a JSON object, which is parsed back into `CompactResult`:

```text
goal · completed · pending · decisions · errors · testResults · openQuestions
        ↓           ↓         ↓           ↓        ↓             ↓
   getCompleted  getPending  getDecisions  getFailedCommands  getTestResults  getOpenQuestions
```

The JSON is then rendered into a section-formatted summary (`## Goal`, `## Pending`, …) rather than
stored raw, because that summary is what gets injected as a system message on the next turn.

Parsing is deliberately forgiving — a summarizer is a model, not a schema-guaranteed API:

- ```` ```json ```` fences and surrounding prose are tolerated (first `{` to last `}` is extracted).
- A single string where an array was requested is accepted as a one-element list.
- **Unparseable output is not an error.** The raw text becomes the summary and the lists stay empty —
  exactly the pre-structured behaviour. A prose-returning model degrades, it does not break compaction.

Cross-compaction accumulation needs no extra state: the rendered summary already contains the
`Pending` and `Key Decisions` sections, and it is fed back in as `Previous summary` on the next
compaction, where the prompt instructs the model to carry unresolved entries forward. Because the
channel is the summary text itself, accumulation survives session save/resume — and cannot leak
between sessions the way a stateful policy instance would.

The `BEFORE_COMPACT` lifecycle hook fires before compaction (interception/customize);
`ON_COMPACT` fires after (observe).

### Turn-boundary safe cut

When deciding what to summarize vs keep, `LlmCompactPolicy` respects turn boundaries: the cut
point walks backwards from the naive position to the nearest user-role message. This prevents
leaving assistant messages or tool results orphaned without their preceding context.

### Cumulative file tracking

`LlmCompactPolicy` scans the items being summarized for tool calls referencing files
(`read`/`grep`/`find` → readFiles; `write`/`edit`/`create`/`delete` → modifiedFiles), deduplicates,
includes them in the summary prompt so the LLM knows what was touched, and carries them forward
in `CompactResult.readFiles` / `CompactResult.modifiedFiles` for the next compaction to accumulate.

### Restore keeps the summary

`AgentMemory.restore(snapshot)` writes `snapshot.getSummary()` back as well, instead of restoring only
items. The summary produced by Tier-3 compaction therefore survives a restore — otherwise the summary
would be lost after a round of resume/compact, and the next turn's prompt could not retrieve it. This
behavior covers both the default implementation and `InMemoryAgentMemory` / `JdbcAgentMemory`.
