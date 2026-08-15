---
title: "Spring Boot JDBC Agent Memory"
description: "A solution for persisting agent sessions to JDBC, covering cross-instance recovery of tool results, runtime state, and compaction summaries."
tags: [integration]
---

# Spring Boot + JDBC Agent Memory

This solution addresses the case where "plain multi-turn chat is no longer enough, and you need to persist the agent session itself."

## 1. Suitable scenarios

- `ReAct Agent`
- Business agents with tool calls
- Multi-turn task agents
- Agent sessions that need cross-instance recovery

Unlike the `ChatMemory` scenario, what we care about here is not only the conversation history, but also:

- Tool results
- runtime state
- Compacted summaries
- Task continuity

## 2. Core module combination

The main chain of this solution is:

- `ai4j-agent`
- `ai4j-spring-boot-starter`
- `JdbcAgentMemory`
- `WindowedMemoryCompressor`
- `DataSource / MySQL`

It is no longer "basic chat persistence", but rather the session layer of a general-purpose agent runtime.

## 3. Strengths of this solution

- Tool call results can be retained across turns
- Agent state can be recovered across processes
- Suitable for introducing compaction strategies incrementally
- Closer to the runtime truth than a hand-written session state

## 4. When you should not adopt it directly

If you are just doing plain chat, without:

- Tool calls
- runtime state
- Session-level task continuity

Then you should first look at:

- [Spring Boot + MySQL Chat Memory](/docs/integrations/solutions/springboot-mysql-chat-memory)

Starting with the lighter solution is often more stable.

## 5. Mainline pages to read first

1. [Agent / Overview](/docs/agent/overview)
2. [Agent / Memory and State](/docs/agent/memory/memory-and-state)
3. [Spring Boot / Bean Extension](/docs/integrations/spring-boot/bean-extension)

## 6. Moving on to implementation details

If you want to see:

- How `sessionId` is bound
- The `JdbcAgentMemory` factory
- How `WindowedMemoryCompressor` is wired
- Agent construction and Controller examples

Continue to the deep page:

- [Legacy path case page](/docs/integrations/solutions/springboot-jdbc-agent-memory)

## 7. Key objects

The objects most worth a closer look in this solution are usually:

- `agent/memory/JdbcAgentMemory`
- `WindowedMemoryCompressor`
- Agent session related objects
- The `DataSource` in the Spring container

Together they determine how the agent session is persisted, compacted, and recovered.

## 8. The real difference from the `ChatMemory` solution

Compared with basic chat memory, what is persisted here is not only the message history, but also:

- The context after tool interactions
- Runtime-related state
- A session skeleton closer to task-execution semantics

So this solution is not just "swapping `ChatMemory` for JDBC", but rather entering a heavier runtime layer.

## 9. First priority when landing

It is recommended to verify first:

1. Whether the binding between `sessionId` and the business session is stable
2. Whether the compaction strategy will break subsequent tool reasoning
3. Whether, after a restart or cross-instance handover, the agent can recover to the expected semantic state
