---
title: Harness Tools and Persistence
description: Explains Harness Function Call, the Command Gateway, async tool invocation, File/JDBC persistence, leases, and recovery boundaries.
tags: [concept, harness]
---

# Harness Tools and Persistence

Harness management capabilities are exposed to the Agent as Function Calls — but a Function Call is only the Agent's entry point into the management surface, not the final authority boundary, and not persistence itself.

The full path is:

```text
model selects a Harness Function Call
              |
              v
HarnessToolRegistry (declares the tools)
              |
              v
HarnessToolExecutor (routing, idempotency, Task/approval/wait constraints)
              |
              v
HarnessCommandGateway (the single durable command surface)
              |
              v
FileHarnessStore or JdbcHarnessStore
```

Business tools pass through the same `HarnessToolExecutor`:

```text
HarnessToolExecutor
  ├─ harness_* management calls -> HarnessManagementToolExecutor -> CommandGateway
  └─ business Tool calls         -> business ToolExecutor
                                       └─ Invocation / Wait / Approval / UNKNOWN records
```

## Harness Execution Lifecycle Sequence Diagram

Interactive sequence diagram: the full lifecycle of one `run` — claim lease+fencingToken, open(prevState) starts a new session, run one slice, snapshot exports state, a wait persists WaitRecord+WAITING, deliver atomically writes answer+wakeup→READY and the next slice continues.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe src={useBaseUrl('/archify/harness-lifecycle.html')} title="Harness execution lifecycle sequence diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-lifecycle.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 1. Management Tool Inventory

Harness automatically adds the following reserved names to the existing tool registry. Business Tools must not use these names.

| Tool | Purpose | Typical operations |
| --- | --- | --- |
| `harness_context_get` | Reads the current Execution's Task, runnable Tasks, Waits, Facts, Decisions, Evidence, and tool invocations | Inspect the current long-running context |
| `harness_task_manage` | Manages Tasks and dependencies | `create`, `split`, `update`, `transition`, `add_dependency`, `get`, `list`, `runnable` |
| `harness_fact_record` | Records or invalidates a sourced Fact | `record`, `invalidate` |
| `harness_decision_propose` | Proposes a Decision, or resolves one for entitled non-Agent actors | `propose`, `resolve` |
| `harness_evidence_record` | Records Evidence produced by the model, tools, tests, files, or external systems | `record` |
| `harness_relation_manage` | Manages generic relations between entities | `create`, `add`, `get`, `list` |
| `harness_control_request` | Requests a checkpoint, user input, async operation, external event, or approval wait | `checkpoint`, `wait`, `approval` |
| `harness_submission_request` | Submits a Task for external review | `submit` |

These tools take generic arguments. Business objects — orders, customers, code files, episode assets — belong in Task metadata, Evidence contentRef, Relation metadata, or the business database, not in fields the SDK would have to guess.

### Fact vs Evidence: Conclusion vs Artifact

Both are durable records, but they differ in semantics and lifecycle — do not mix them up:

| | Fact (conclusion) | Evidence (artifact) |
| --- | --- | --- |
| Records | An assertion: "this order qualifies for a refund" | A pointer to an artifact: `location`/`contentRef` + `kind` + `summary` |
| Confidence | `confidence` + `source` | None needed — an artifact is history |
| Lineage | Anchored to `taskId` only | Also carries `executionId`, traceable to the run that produced it |
| Invalidatable? | Yes: `invalidateFact` sets `valid=false` with a trail | No — append-only; "that test run happened" stays true forever |
| Answers | "What do we currently believe" (may go stale) | "Why should we believe it" (never goes stale) |

The typical wiring: an Evidence `SUPPORTS` a Fact through a Relation; a Submission's `evidenceIds[]` references existing Evidence at submit time, and the acceptance Gate checks that reference chain. Facts can be recorded at any point during execution — they are not "produced at closeout". The closeout act itself produces the Submission, the Review, and the `AcceptanceRecord` (the evaluation record of which Gates ran and how they ended, bound to the Submission).

> **Note: a Gate verifies the integrity of the evidence reference chain** (the referenced Evidence exists, scopes match, and it belongs to the current Submission) — **it does not verify that the artifact at `contentRef` is real or says what the summary claims**. Content truth is the Reviewer's job, or a custom `HarnessGate`'s — e.g. a Gate that checks the `contentRef` file actually exists and the test log actually passed.

## 2. Can the Agent Bypass the Harness?

Two cases need to be distinguished:

### Writes to management state

The Agent never writes `HarnessStore` directly. Management Function Calls go through `HarnessManagementToolExecutor`, and every write lands in `HarnessCommandGateway`. The Gateway checks:

- whether Task, Execution, Session, and `scopeKey` are consistent;
- whether dependencies would form a cycle;
- whether the Wait belongs to the current Execution;
- whether the idempotency key has already been used;
- whether the current Actor holds approval, review, completion, or reconciliation authority;
- whether the Task is already in a terminal state;
- whether the lease and fencing token are still valid.

By default, the Agent can propose Facts, Decisions, Evidence, Tasks, and Submissions — but it cannot approve its own Submission, complete its own Task, or perform final reconciliation for external side effects.

### Will the Agent reliably call the management tools?

Model prompting cannot be treated as a semantic-completeness proof. Harness uses `HarnessPrompts` to tell the Agent to read context and maintain Tasks when complex work begins, but the model may still forget to record a fact or choose not to split a task.

For critical business flows, therefore, use all of the following together:

1. `HarnessContract` sets `taskRequiredTool` and `approvalRequiredTool` on critical business Tools;
2. the host performs necessary state writes through `HarnessCommandGateway` at entry points, webhooks, and the human back-office;
3. Submission/Gate/external review decides whether completion is allowed;
4. side-effecting business APIs use external idempotency keys, and the real business system is queried when the outcome is `UNKNOWN`.

This guarantees the Agent cannot bypass the real execution and completion boundaries by "simply not calling a management tool" — while also acknowledging that Harness cannot infer every business fact from the model's natural language.

## 3. Durable Flow of an Async Function Call

The business side can keep implementing synchronous `ToolExecutor`s; Harness records their results as completed Tool Invocations. When a remote service must be awaited, implement the optional `AsyncToolExecutor`:

```java
final class SubmitRefundExecutor implements AsyncToolExecutor {
    @Override
    public AgentToolExecution start(AgentToolCall call) {
        String operationId = refundApi.submitAsync(call.getArguments());
        CompletableFuture<AgentToolResult> completion =
                refundApi.completion(operationId);
        return AgentToolExecution.pending(
                operationId,
                null,
                "Refund submitted; awaiting the payment system result",
                1000L,
                completion);
    }
}
```

Inside the Harness boundary, the actual order is:

1. reserve a durable `ToolInvocation` for this call;
2. invoke the business `AsyncToolExecutor.start`, which returns an `operationId` immediately;
3. create an `ASYNC_OPERATION` Wait and a checkpoint;
4. the Agent returns `WAITING` and the current Execution becomes `WAITING`;
5. if the CompletionStage is still in the same process, Harness can receive the completion automatically;
6. if the process restarted, the business webhook locates the Wait by the persisted `operationId` and calls `harness.deliver(waitId, result)`;
7. the Wait, Wakeup, and restored Agent/Adapter state are persisted atomically first; only then does the Execution become `READY` and continue with a new slice.

```java
HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
        .scopeKey("shop-A")
        .sessionId("customer-A-agent-session")
        .input(customerMessage)
        .build());

if (waiting.getStatus() == HarnessRunStatus.WAITING) {
    saveOperationBinding(waiting.getOperationId(), waiting.getWaitId());
}

// Executed by the payment-system webhook, a queue consumer, or the human back-office;
// it does not depend on the original JVM Future.
HarnessRunResult resumed = harness.deliver(
        loadWaitIdByOperation(operationId),
        refundResult);
```

Multiple parallel async tool calls create multiple Waits; the Execution can only continue after all of them are delivered. Late async results arriving after cancellation or human takeover do not reopen cancelled work — they are recorded as quarantined late results.

If the external service executed but the JVM crashed before recording the result, Harness keeps the `UNKNOWN` state and does not auto-retry an operation that could cause a duplicate charge or duplicate refund. The business must reconcile via the `operationId` or the external system's query API — which is also why important business APIs must be idempotent.

Approvals follow the same side-effect boundary: if the existing Agent's Permission layer requires approval after Harness has already reserved the Invocation, Harness atomically associates that Invocation with an `APPROVAL` Wait and marks it `WAITING` in one transaction. After approval, the resumed Agent may retry with a new provider `callId`; the original Invocation is only reused when the same Execution, same tool, and equivalent arguments hold with no ambiguity, and it is atomically restored to `STARTED` before actual execution. A rejection is recorded as a failure — the tool is never executed.

## Worker Failure-Recovery Sequence Diagram

Interactive sequence diagram: Worker A's heartbeat stops or its lease expires → the Execution is marked UNKNOWN (not FAILED) → explicit reconcile is required → Worker B takes a new lease and fencing token and continues from prevState; late writes from the stale worker are rejected by fencing.


<iframe src={useBaseUrl('/archify/harness-worker-recovery.html')} title="Worker failure-recovery sequence diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-worker-recovery.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 4. Wait Types

| Wait type | Who delivers | Examples |
| --- | --- | --- |
| `USER_INPUT` | The user channel layer | The Agent asks "which order should be refunded?" |
| `ASYNC_OPERATION` | External service webhooks, message queues, or callback consumers | Refunds, payments, logistics, remote builds |
| `APPROVAL` | A human or an entitled system Actor | High-risk refunds, releases, pushes |
| `EXTERNAL_EVENT` | Business event consumers | Inventory changes, manual handling completed, CI events |
| `TIME` / `RETRY` | Business schedulers or timer workers | Due checks, backoff retries |

"Waiting for user input" and "the customer-service Conversation is still open" are not the same concept. Harness only persists the Wait; the customer-service business decides how to map the Wait to the message channel, and whether a Conversation permanently stops the bot after human takeover.

## Ledger Dataflow Diagram

Interactive dataflow: execution/governance write commands → CommandGateway as the single write surface (actor/state-machine/fencing/idempotency checks) → HarnessState records the lineage (executions & waits, governance & audit) → HarnessPersistence writes to disk → two store implementations: File / JDBC.


<iframe src={useBaseUrl('/archify/harness-ledger-dataflow.html')} title="Ledger dataflow diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-ledger-dataflow.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 5. File Persistence

Local projects or single-machine Coding Agents can use:

```java
HarnessPersistence persistence = HarnessPersistence.file(
        projectRoot.resolve(".ai4j/harness"));

AgentHarness harness = AgentHarness.builder()
        .agent(agent)
        .persistence(persistence)
        .build();
```

The directory is managed by Harness and mainly contains:

```text
.ai4j/harness/
  state.json       # full current state snapshot
  journal.jsonl    # append-only recovery log
  .lock            # cross-process file lock
```

The File store generates a new version on every update, appends to the journal, replaces the snapshot, and compacts the journal once it grows too large and the snapshot is safe. It suits long-lived local workflows inside a project directory; multi-instance production services, network filesystems, and high-concurrency cross-host workers should use JDBC or a business-implemented `HarnessStore`.

Note: `harness/` at the repository root is the private Harness Anything ledger used by project maintainers, and `.harness/` is its generated projection; they are not the same as the SDK runtime's `.ai4j/harness/` and must not read or write each other.

## 6. JDBC Persistence

Multi-instance services use:

```java
HarnessPersistence persistence = HarnessPersistence.jdbc(
        dataSource,
        "shop-A-customer-support");

AgentHarness harness = AgentHarness.builder()
        .agent(customerSupportAgent)
        .persistence(persistence)
        .build();
```

The two parameters mean:

| Parameter | Meaning |
| --- | --- |
| `dataSource` | The JDBC `DataSource` provided by the business application — it owns pooling, the database address, credentials, and the transaction environment |
| `harnessId` | The stable name of one logical Harness ledger, used to isolate different projects, shops, or Agent systems inside the same database |

`"shop-A-customer-support"` is neither a Conversation ID nor a Session ID, and it creates no customer-service rules by itself. Workers sharing one `harnessId` share the same Task/Execution/Wait state; different values mean different ledgers. Inside a ledger, `scopeKey` can further partition shops, projects, or subsystems.

The JDBC store uses full state rows plus journal rows, and applies transactions, row locks, and version-conditional updates on state writes. Applications should point every instance that shares long-running state at the same database and the same `harnessId`, and define operational rules for backups, retention, migration windows, and connection pools.

The current auto-DDL stores JSON state in `TEXT` columns, consistent with the SDK's existing `JdbcAgentMemory` convention and verified in H2 regression tests — suitable for MySQL, MariaDB, PostgreSQL, H2, and SQLite-class databases. For Oracle, SQL Server, or databases with different large-object types, do not assume auto-DDL works just because the JDBC connection succeeds: pre-create the two equivalent tables on the target database, or implement a `HarnessStore` for it, and treat schema initialization as part of deployment migration.

## 7. There Is No Production In-Memory Harness Store

Long-running facts, Tasks, Executions, Waits, checkpoints, leases, and audit records cannot live only in the JVM heap — so the SDK deliberately does not ship a production `InMemoryHarnessStore`.

Agent-side `AgentMemory` can still be configured the usual SDK way; it is model context state, not the Harness ledger. For long-horizon recovery, Harness must use File, JDBC, or a business-implemented durable `HarnessStore`. Tests should also use a temporary File store or a test database so that restart recovery, concurrency, and journal behavior are actually exercised.
