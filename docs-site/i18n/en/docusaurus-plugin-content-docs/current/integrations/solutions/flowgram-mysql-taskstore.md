---
title: "Flowgram MySQL Task Store"
description: "A JDBC persistence solution for promoting FlowGram from a single-process demo to a platform backend, covering the task lifecycle and the task store boundary."
tags: [integration]
---

# Flowgram MySQL Task Store

This solution addresses "promoting Flowgram from a single-process demo to a persistable platform backend".

## 1. When it fits

- Flowgram platform backend
- Task execution needs to be visible across processes
- You want to retain task result / report / trace projection

If you are only running a local demo, an in-memory task store is enough.
If you are moving toward a platform, you should usually adopt a JDBC task store early.

## 2. Core module combination

The main chain of this solution is:

- `ai4j-flowgram-spring-boot-starter`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramRuntimeService`
- `JdbcFlowGramTaskStore`
- `DataSource / MySQL`

The focus is no longer "how nodes run", but rather "how the task lifecycle is persisted by the platform".

## 3. The value of this solution

- Task state no longer lives only in process memory
- report / result is better suited to platform queries
- Easier to support multi-instance or asynchronous task panels
- Convenient for downstream ops governance and troubleshooting

## 4. When you can skip it for now

- Only running a local demo
- Do not care about task recovery and historical results
- Have not yet wired the frontend and backend together end to end

It is usually more stable to get the runtime main chain working first, then adopt the JDBC store.

## 5. Main-line pages to read first

1. [Flowgram / Runtime](/docs/products/flowgram/runtime)
2. [Flowgram / Frontend Backend Integration](/docs/products/flowgram/frontend-backend-integration)
3. [Spring Boot / Overview](/docs/integrations/spring-boot/overview)

## 6. Where to go for implementation details

If you want to see:

- `task-store.type=jdbc`
- Auto-configuration conditions
- Table creation and fields
- Persistence semantics of report / result

Continue to the deep page:

- [Legacy path case page](/docs/integrations/solutions/flowgram-mysql-taskstore)

## 7. Key objects

If you want to keep reading the implementation, focus first on:

- `JdbcFlowGramTaskStore`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramRuntimeService`

These correspond respectively to task persistence, the external API, the runtime facade, and the task execution service.

## 8. Core boundary of this solution

This solution addresses "how the task lifecycle is persisted and made queryable", and does not directly address:

- The business logic design of the nodes themselves
- The frontend canvas interaction experience
- The permission and organization model of the entire platform

Clarifying the task storage boundary first keeps later platform design from conflating layers.

## 9. What to confirm first during implementation

- Whether the task state fields are sufficient to support frontend queries and ops troubleshooting
- Whether the storage strategy for `report / result / trace projection` is consistent
- After the JDBC store is wired in, whether the runtime semantics stay consistent with the in-memory implementation
