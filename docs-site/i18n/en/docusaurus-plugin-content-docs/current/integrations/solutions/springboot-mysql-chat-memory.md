---
title: "Spring Boot MySQL Chat Memory"
description: "A multi-turn chat session persistence solution based on Spring Boot + MySQL, covering JdbcChatMemory wiring, sessionId binding, and trimming policies."
tags: [integration]
---

# Spring Boot + MySQL Chat Memory

This solution addresses "getting multi-turn chat and session persistence right first", rather than introducing the heavier `Agent runtime` from the start.

## 1. Suitable scenarios

- Web chat pages
- Enterprise Q&A assistants
- Multi-turn customer service bots
- The same user session needs to recover across instances

If you currently only need stable multi-turn context, and don't need tool loops, runtime state, handoff, or team, this is usually the more appropriate first stop.

## 2. Core module combination

The main chain of this solution is clear:

- `ai4j-spring-boot-starter`
- `ChatMemory`
- `JdbcChatMemory`
- `DataSource / MySQL`
- Session recovery based on `sessionId`

It is essentially:

> Spring containerized wiring + basic session memory persistence

rather than a full Agent session system.

:::tip All code on this page is runnable
The cross-instance recovery example below comes from
[`JdbcMemorySolutionDocTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/JdbcMemorySolutionDocTest.java),
verified with embedded H2 (in production, switch to MySQL by only changing `jdbcUrl`). No keys required, runs in a normal CI.
:::

## 2.1 The core of this solution: cross-instance recovery of the same session

```java
// Production: jdbcUrl = "jdbc:mysql://host:3306/db"
JdbcChatMemoryConfig config = JdbcChatMemoryConfig.builder()
        .jdbcUrl(jdbcUrl)
        .sessionId(sessionId)        // ← key: stable session identifier
        .build();

// First instance (e.g., the Pod handling request A) writes the session
JdbcChatMemory first = new JdbcChatMemory(config);
first.addSystem("You are helpful");
first.addUser("Hello");
first.addAssistant("Hi");

// Second instance (Pod handling request B, or after a service restart) reads back with the same sessionId
JdbcChatMemory second = new JdbcChatMemory(config);
List<ChatMemoryItem> items = second.getItems();   // [system, user, assistant] fully recovered
```

Persisted memory also supports window trimming — preventing sessions from growing unboundedly:

```java
JdbcChatMemory memory = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
        .jdbcUrl(jdbcUrl)
        .sessionId(sessionId)
        .policy(new MessageWindowChatMemoryPolicy(20))   // keep the most recent 20 non-system messages
        .build());
```

## 3. Advantages of this solution

- The most direct path for Spring Boot projects
- Simple architecture, easy to ship first
- Promotes "multi-turn context" from a hand-written Controller list into a formal capability
- Cross-instance session recovery is far more reliable than an in-memory list

## 4. Unsuitable scenarios

If you already have the following needs, it is time to upgrade:

- Need to write tool results back
- Need runtime state
- Need task-level memory compaction
- Need the Agent to decide the next step itself

In these cases, see:

- [Spring Boot + JDBC Agent Memory](/docs/integrations/solutions/springboot-jdbc-agent-memory)
- [Agent / Memory and State](/docs/agent/memory/memory-and-state)

## 5. Which main lines to establish first

1. [Spring Boot / Quickstart](/docs/integrations/spring-boot/quickstart)
2. [Spring Boot / Common Patterns](/docs/integrations/spring-boot/common-patterns)
3. [Core SDK / Memory](/docs/capabilities/chat-memory/overview)

## 6. Continue to implementation details

If you want to look at:

- Dependency configuration
- `application.yml`
- How to write a `JdbcChatMemory` factory
- Controller / Service organization

Continue to the deep page:

- [Legacy path case page](/docs/integrations/solutions/springboot-mysql-chat-memory)

## 7. Key objects

The objects most worth further reading on this page are usually:

- `memory/ChatMemory.java`
- `memory/JdbcChatMemory.java`
- `memory/MessageWindowChatMemoryPolicy.java`
- The `DataSource` in the Spring container

They correspond respectively to the context contract, the persistence implementation, the trimming policy, and the database wiring surface.

## 8. The boundary this solution actually solves

This solution addresses "how multi-turn chat context stably enters the model request", and does not solve:

- How tool results enter long-term task state
- How the Agent runtime recovers execution context
- How multi-role collaboration manages shared memory

If your problem has escalated to these levels, it is time to switch to the agent memory solution.

## 9. Notes during implementation

:::warning Notes during implementation
- `sessionId` must have stable binding rules, otherwise database persistence is meaningless
- The trimming and compaction policy for historical messages must be defined up front, otherwise the session will grow unboundedly
- Successful persistence does not equal semantic correctness; you still need to verify that the memory window matches business expectations
:::
