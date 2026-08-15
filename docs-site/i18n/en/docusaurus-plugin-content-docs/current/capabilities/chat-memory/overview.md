---
title: "Memory Overview"
description: "Overview of the AI4J Core SDK session fact layer: ChatMemory records multi-turn conversations and tool results, with storage and trimming policies kept separate, projectable to both Chat and Responses inputs, with support for snapshot recovery and summary compaction."
tags: [concept]
---

# Memory Overview

The `memory` chapter is not about the agent long-term state machine. It is about **how the Core SDK stores, trims, compacts, and re-projects multi-turn session facts**.

The central object here is `ChatMemory`, but its real role carries far more weight than a "chat history array".

## 1. Where this chapter sits in the Core SDK

This layer handles four things:

- How session facts are recorded
- How those facts are projected into `Chat` or `Responses` inputs
- How context is trimmed or summarized when it grows too long
- Whether session state lives only in memory or is persisted to external storage

It is not responsible for:

- Agent-level task progression
- Multi-agent collaboration state
- The coding runtime's checkpoint / compact / resume
- Tool approval and side-effect governance

Those already belong to the `ai4j-agent` or `ai4j-coding` layers.

## 2. The real entry points

When you read the source, look at these objects first:

- `memory/ChatMemory.java`
- `memory/ChatMemoryItem.java`
- `memory/InMemoryChatMemory.java`
- `memory/JdbcChatMemory.java`
- `memory/ChatMemoryPolicy.java`
- `memory/MessageWindowChatMemoryPolicy.java`
- `memory/SummaryChatMemoryPolicy.java`

Together these objects form a very clear design:

- `ChatMemory` defines the capability contract
- `ChatMemoryItem` defines the session fact format
- The storage implementation decides where facts live
- The policy decides how many facts are retained

## 3. What this layer actually stores

AI4J does not store "the last line of chat text". It stores a more complete set of session facts:

- `system`
- `user`
- `assistant`
- `assistant tool calls`
- `tool output`
- Image and text input
- Summary entries

This is visible directly from the fields on `ChatMemoryItem`:

- `role`
- `text`
- `imageUrls`
- `toolCallId`
- `toolCalls`
- `summary`

So memory records "what the model has seen and what tools returned", not just the natural-language conversation.

## 4. Why `Chat` and `Responses` can share one memory

`ChatMemory` exposes both:

- `toChatMessages()`
- `toResponsesInput()`

This means AI4J already separates "session facts" from "request protocol format" at the foundation layer.

The same context can be:

- Projected into `List<ChatMessage>`
- Projected into the `List<Object>` that `Responses` requires

This is not a documentation-layer concept; it is an explicit interface-level commitment.

## 5. Storage and policy are decoupled

There is one important design point in this layer: **where sessions live** and **how much of a session is retained** are two independent questions.

### Storage implementations

- `InMemoryChatMemory`
- `JdbcChatMemory`

### Retention policies

- `UnboundedChatMemoryPolicy`
- `MessageWindowChatMemoryPolicy`
- `SummaryChatMemoryPolicy`

This lets you decide independently:

- Whether sessions should survive across processes
- Whether context should be window-trimmed
- Whether history should be compacted into a summary

## 6. Current default behavior

By default, both `InMemoryChatMemory` and `JdbcChatMemory` use the following when no explicit policy is set:

- `UnboundedChatMemoryPolicy`

:::note
In other words, AI4J does not truncate history automatically by default. If you are concerned about context growing without bound, configure a window or summary policy explicitly, rather than assuming the SDK will do length control for you.
:::

## 7. The real boundary of the persistence implementation

`JdbcChatMemory` is not "just dumping the in-memory object somewhere". It is a first-class implementation:

- Identifies the session by `sessionId`
- Uses `ai4j_chat_memory` as the default table name
- Initializes the schema automatically by default
- On every write, deletes and re-inserts the entire session
- Persists entries as JSON

The point of this implementation is recoverability and consistency, not high-concurrency incremental write optimization.

## 8. When to stop at this layer, and when to move up

If all you need is:

- Multi-turn conversation
- Session-level context
- Image and text input history
- Tool output write-back
- Basic trimming and summary

This layer is usually enough.

If you have started caring about:

- The runtime loop
- Plan state
- Approval and side effects
- Multi-agent handoff

That means you should move on to `Agent` or `Coding Agent`, rather than piling more responsibility onto `ChatMemory`.

## 9. Recommended reading order

1. [Chat Memory](/docs/capabilities/chat-memory/)
2. [Memory and Tool Boundaries](/docs/capabilities/chat-memory/memory-and-tool-boundaries)
3. [Agent / Memory and State](/docs/agent/memory/memory-and-state)

## 10. The takeaway of this page

> The AI4J memory foundation is not a simple chat-history container. It is a composite layer of "session facts + storage implementation + trimming/summary policy + multi-protocol projection". As long as your problem still belongs to multi-turn context management, this layer is sufficient; once you enter task progression and governance semantics, you should move up to a higher-layer runtime.
