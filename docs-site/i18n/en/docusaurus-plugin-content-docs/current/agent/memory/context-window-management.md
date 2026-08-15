---
title: "Context Window Management"
description: "Explains ai4j's context window management: ContextBudget sets the budget (maxItems/maxApproxChars/pinnedPrefixItems), DefaultContextProjector projects using a keep head + tail strategy, and ContextReport reports drop diagnostics. This is the first gate before Compaction — it decides what enters the window, while Compaction decides how to compress what is still too large."
tags: [concept]
---

# Context Window Management

> **Context window management = deciding which history items enter the model's context window.** It is the first gate before Compaction: Projection decides "what gets in", and Compaction decides "how to compress what is still too large".

## Why it matters

Every model has a finite context window (e.g. 8K / 32K / 200K tokens). But the full history of a long-running session — all user/assistant/tool-call/tool-output messages — often far exceeds the window. Cram it all in and you either hit a limit error or get truncated by the provider (losing the recent, critical information).

ai4j's approach is **two gates**:

```
Full Memory (all history)
    ↓
① ContextProjector (projection: select a subset by budget)     ← covered here
    ↓
② Compaction (compression: if still too large, summarize/trim)  ← next gate
    ↓
Context sent to the model
```

:::note Projection vs Compaction
- **Projection**: **selects a subset** from the full history without changing item content. What gets dropped is "less important middle items".
- **Compaction**: **transforms** items into a shorter form (summary, microcompact), changing content.
- Project then compact: first trim the clearly surplus items by budget; if still too large, compact. You can use only one, or stack them.
:::

## ContextBudget: setting the budget

`ContextBudget` is a simple value object that defines "how much the context window keeps at most":

| Field | Type | Meaning |
|---|---|---|
| `maxItems` | Integer | Maximum number of messages to keep (null = unlimited) |
| `maxApproxChars` | Integer | Maximum approximate characters to keep (null = unlimited) |
| `pinnedPrefixItems` | Integer | How many head items to pin and never trim (default 0) |

```java
// Keep at most the latest 20 messages
ContextBudget budget = ContextBudget.maxItems(20);

// Keep at most ~10000 characters
ContextBudget budget = ContextBudget.maxApproxChars(10000);

// At most 50 items, and the first 3 (system + first 2 user/assistant) always kept
ContextBudget budget = ContextBudget.builder()
        .maxItems(50)
        .pinnedPrefixItems(3)
        .build();
```

The role of `pinnedPrefixItems`: the system prompt and the first few turns at the start of a session are usually the most important (they define the task and the role) and should not be trimmed. Setting `pinnedPrefixItems(3)` ensures the first 3 items are always kept, and trimming only applies to the middle.

## DefaultContextProjector: the projection strategy

`ContextProjector` is an interface with a single method:

```java
public interface ContextProjector {
    ContextProjection project(List<Object> items, ContextBudget budget);
}
```

`DefaultContextProjector` is the default implementation, using a **keep head + tail** (pin head + keep tail) strategy:

1. **Trim by maxItems first**: if the item count exceeds the limit, keep `pinnedPrefixItems` head items + the latest `maxItems - pinnedPrefixItems` tail items, **dropping the middle**.
2. **Then trim by maxApproxChars**: if the character count still exceeds the limit, apply the same head + tail, drop the middle to the remaining items, until the character budget is met.

:::tip Why keep head + tail
- **Head** (pinned prefix): system prompt + early instructions — define the task and role; lose them and the agent "forgets what it is doing".
- **Tail** (recent): the latest turns — carry current task progress and the newest tool results; lose them and the agent "forgets what it just did".
- **Middle** (middle drop): older intermediate steps — usually completed intermediate results; dropping them has the least impact.
:::

## ContextReport: drop diagnostics

Every projection returns a `ContextReport` that tells you **how much was trimmed and why**:

| Field | Meaning |
|---|---|
| `sourceItemCount` | Original item count |
| `projectedItemCount` | Item count kept after projection |
| `droppedItemCount` | Number of items dropped |
| `sourceApproxChars` | Original approximate character count |
| `projectedApproxChars` | Approximate character count after projection |
| `itemLimitApplied` | Whether the item limit was triggered |
| `characterLimitApplied` | Whether the character limit was triggered |
| `notes` | Human-readable note (e.g. `"maxItems applied: 20"`) |

```java
ContextProjection projection = projector.project(items, budget);
ContextReport report = projection.getReport();

if (report.getDroppedItemCount() > 0) {
    System.out.println("Projection dropped " + report.getDroppedItemCount() + " items"
            + ", original " + report.getSourceItemCount() + " → kept " + report.getProjectedItemCount());
    // Example output: Projection dropped 35 items, original 55 → kept 20
}
```

## Configuring in AgentBuilder

```java
Agent agent = Agent.builder()
        .modelClient(modelClient)
        .contextBudget(ContextBudget.builder()
                .maxItems(30)
                .maxApproxChars(20000)
                .pinnedPrefixItems(2)
                .build())
        .build();
```

When `contextBudget` is not set, no projection happens by default (all history goes straight into the window) — under long sessions you need to configure it manually, or rely on Compaction as a fallback.

## Token budget on the RAG side

Retrieval results injected by RAG also consume the context window. `TokenAwareRagContextAssembler` does similar budget management on the RAG side: based on the remaining token budget, it decides how many retrieval chunks to inject. It is **orthogonal** to ContextProjector — one governs history messages, the other governs RAG results — but the goal is the same: do not exceed the window.

→ See [Search and RAG overview](/docs/capabilities/rag/overview)

## Further reading

- [Memory and Compact Context](/docs/agent/memory/memory-compact-context) — Compaction (the next gate after projection)
- [Memory overview](/docs/capabilities/chat-memory/overview) — ChatMemory is the input to Projection
- [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint) — the compaction pipeline at the Coding Agent layer
- [Agent concept map](/docs/agent/agent-concepts) — the full Memory → Context Window → Compaction → Checkpoint chain
