---
title: Durable Agent Harness Runtime
description: Use ai4j-harness to add dynamic Tasks, durable Executions, checkpoints, wait/resume, dependencies, and completion governance to an existing Agent.
tags: [concept, harness]
---

# Durable Agent Harness Runtime

`ai4j-harness` is an optional SDK module. It ports the core management ideas of Harness Anything into a Java runtime boundary — without bringing in the `ha` CLI, and without hardcoding any business workflow into the SDK.

Its positioning can be summarized in one sentence:

> The `Agent` owns thinking and execution inside one slice; the `Harness` owns work state, recovery, and governance across requests, processes, and time.

## Harness Outer-Loop Mechanism Diagram

Interactive architecture diagram: `AgentHarness` acts as the durable outer loop around an Agent — each `run` executes one bounded slice (claim lease → adapter.open → session.run → status mapping → persistOutcome), while the Agent's ReAct/tools/permissions/sandbox run unchanged; CommandGateway is the only write path, and Wait/Wakeup/Checkpoint all land in HarnessStore.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe src={useBaseUrl('/archify/harness-runtime.html')} title="Harness outer-loop mechanism diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-runtime.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 1. It Does Not Replace Your Existing Agent

Without Harness configured, the existing `Agent`, `AgentSession`, `ToolExecutor`, MCP, Function Call, Skill, A2A, Subagent, Agent Team, Memory, context compaction, Sandbox, Permission, Hook, and Plugin behaviors stay unchanged.

With Harness enabled, the existing Agent still owns:

| Existing Agent capability | Responsibility boundary |
| --- | --- |
| Model calls and protocol adaptation | `ai4j` / `ai4j-agent` |
| Per-run strategies such as ReAct, CodeAct, Workflow | `ai4j-agent` |
| Tool declarations, MCP, Function Call, Skill | `ai4j-agent` and the business side |
| Memory, context projection, context compaction | `ai4j-agent` / `ai4j-coding` |
| Sandbox, Permission, Hook, Plugin, Subagent, Agent Team | Existing Agent/Coding Runtime |
| When one Agent invocation stops | The Agent's step, token, and wall-clock settings, plus the Harness budget for this run |

Harness only adds, on the outside:

| Harness capability | What it does |
| --- | --- |
| Task | Dynamically records a long-running goal at runtime; no fixed task list must be declared up front |
| Execution | A durable execution instance for one input or one resumption |
| Checkpoint | Persists the Agent/Adapter state and summary needed to resume across slices |
| Wait/Wakeup | Persists waits for user input, approvals, async services, external events, or timers |
| Lease/Fencing | Prevents multiple workers from mutating the same Execution or Session concurrently |
| Task relation | Expresses parent/child tasks and dependency DAGs; rejects cyclic dependencies |
| Fact/Decision/Evidence/Relation | Turns long-running facts, decisions, evidence, and relations into queryable records |
| Submission/Review/Gate | Separates "the Agent says it is done" from "the system accepts it as done" |
| File/JDBC store | Persists all of the above to disk or a database instead of JVM memory only |

## 2. Core Object Relationships

Harness deliberately does not force-bind Task to Session:

```text
business input message / external event / worker dispatch
                 |
                 v
        HarnessRunRequest
                 |
                 v
        Execution (one slice)
          |             |
          |             +--> Session ID: restores Agent context
          |
          +--> Task ID: optional; the Agent may create and bind one at runtime
                 |
                 v
        Agent or HarnessExecutionAdapter
                 |
                 v
        one bounded Agent slice
                 |
                 v
     checkpoint + durable outcome + wait/wakeup
```

The distinction between these identities matters:

| Identity | Typical meaning | Binding rule |
| --- | --- | --- |
| Task | Long-running work like "complete the refund investigation", "maintain the payment module", "produce one episode" | One Task can have many Executions |
| Execution | The concrete slice for this incoming message, resumed wait, or worker continuation | An Execution has at most one current Task, but may have none |
| Agent Session | The Agent's memory, event log, and run identity | One Session can participate in many independent Executions |
| scopeKey | Partitions tenants, projects, or workspaces inside one Harness ledger | Optional; it is neither a Conversation nor a Session |

So a new customer-service message normally creates a new Execution; it can reuse the customer's Agent Session, but reusing a Session does not automatically inherit the previous message's Task. A Coding Agent's Task usually belongs to the project and can be continued by Executions from different CLI, TUI, ACP, or background workers.

## Execution State Machine Diagram

Interactive state machine: Execution migrates among READY / RUNNING / WAITING / SUCCEEDED / FAILED / UNKNOWN — when a lease is lost or the persisted outcome is uncertain it is marked UNKNOWN rather than FAILED; a WAITING execution can only return to READY after `deliver` atomically writes answer+wakeup; terminal states cannot transition further.


<iframe src={useBaseUrl('/archify/harness-execution-states.html')} title="Execution state machine diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-execution-states.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 3. How One `run` Works

`AgentHarness.run(...)` executes one bounded slice each time:

1. The host puts its own business input object into `HarnessRunRequest.input`, along with a stable `sessionId`, an optional `taskId`, a `scopeKey`, and a message-level idempotency key.
2. Harness creates or loads a durable Execution. Even when no Task exists yet, it can first record the input and the execution outcome.
3. Harness acquires the Execution lease, and acquires a session lease for the shared Agent Session.
4. Harness restores the existing checkpoint or Session snapshot, then overlays the management tools and enforced execution boundary onto the existing Agent.
5. The Agent runs inside its own model, tool, MCP, Memory, compaction, and permission semantics; it may decide — based on the current input — whether to create, split, update, or relate Tasks.
6. When the slice ends, Harness atomically persists the Agent/Adapter state, checkpoint, tool invocations, wait states, and the Execution outcome.
7. The host then `resume`s, calls `deliver`, waits for external events, or hands runnable Tasks to the next worker.

```java
// message, messageId, and sessionId are your own business-side concepts and fields.
HarnessRunResult result = harness.run(HarnessRunRequest.builder()
        .scopeKey("shop-A")
        .sessionId("customer-A-agent-session")
        .idempotencyKey("message:msg-1001")
        .input(message)
        .build());

if (result.getStatus() == HarnessRunStatus.WAITING) {
    // Persist result.getWaitId() on the business side and hand the "awaiting user answer / processing"
    // state to your own channel layer.
    publishWaitingReply(result.getOutputText(), result.getWaitId());
} else if (result.getStatus() == HarnessRunStatus.CONTINUATION_REQUIRED) {
    // Work remains but this slice hit its boundary; a worker can continue it — do not treat it as done.
    enqueueExecution(result.getExecution().getExecutionId());
}
```

`message` does not have to implement any SDK-fixed interface. It can be an e-commerce message DTO, an HTTP request, an event object, a CLI prompt, a ticket object, or any business input. Harness only persists the input summary and the run state the Agent needs to resume; whether and how the full business object is persisted or masked is the business system's decision.

## Single-Run Internal Flow Diagram

Interactive flow diagram: run/resume entry → claimExecution lease+fencing → adapter.open(ctx,budget,prevState) → bounded Agent/ReAct slice → snapshot → status mapping → persistOutcome / ensureWait; the deliver loop atomically writes answer+wakeup, then continues the next slice from prevState+answer.


<iframe src={useBaseUrl('/archify/harness-run-internals.html')} title="Single-run internal flow diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-run-internals.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 4. Tasks Are Created Dynamically at Runtime

Developers do not need to write fixed `TaskDefinition`s for "refund", "change address", or "query order", nor create a single Task at application startup.

When the current input does not yet correspond to a Task, the Agent can call the auto-injected:

```text
harness_task_manage {
  "operation": "create",
  "title": "Verify the customer's refund request",
  "goal": "Confirm the order, refund eligibility, and execution result"
}
```

If the current Execution has no Task, the first `create` binds the new Task to that Execution; afterwards, within the same long-running work, the Agent can:

- `split` out subtasks such as order verification, policy verification, refund submission, and result notification;
- use `add_dependency` to express "refund submission depends on order verification";
- `update` the Task's goal and plan as new facts arrive;
- record Facts, Decisions, and Evidence;
- save a checkpoint at slice boundaries;
- submit for external review via `harness_submission_request` instead of declaring completion itself.

The host only passes `taskId` when it already knows the long-running work identity — for example, a background retry of a known refund ticket, or continuing a project-level coding Task. A Task can still have multiple Sessions and multiple Executions.

## Submission-Review-Gate Flow Diagram

Interactive flow diagram: the Agent cannot mark a Task IN_REVIEW directly — it must submitTask → reviewSubmission (APPROVED / CHANGES_REQUESTED) → AcceptanceCoordinator evaluates evidence and completion gates → only then can completeTask move it to DONE; repair(parentExecutionId) creates a child execution under the parent's lineage.


<iframe src={useBaseUrl('/archify/harness-submission-gate.html')} title="Submission-review-gate flow diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-submission-gate.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 5. What the Agent, the Host, and the Harness Each Own

### The Agent owns

- understanding the input and choosing business tools;
- deciding when to create or split Tasks based on work complexity;
- writing important facts, decisions, and evidence into the Harness;
- requesting a Wait when it needs the user, an approval, or an external system;
- producing staged answers or submission material;
- continuing work within its Agent step budget.

### The business developer owns

- defining input DTOs, output DTOs, message idempotency keys, and external event formats;
- deciding how customers, orders, tickets, projects, repositories, and other entities map to `scopeKey`, Task metadata, or the business database;
- implementing business Tools and async service calls;
- deciding which tools require an existing Task and which require approval;
- deciding the time, round, token, and cost budget of one slice;
- defining human-takeover, conversation-close, retry, and timeout rules;
- configuring Gates for completion submissions and providing human or system review entry points;
- choosing File or JDBC, and owning the database, backups, workers, callbacks, and operations.

### The Harness Runtime owns

- atomically saving and restoring long-running state;
- leasing, idempotency, and concurrency isolation for Executions, Sessions, and tool invocations;
- maintaining waits, wakeups, checkpoints, dependencies, evidence, and completion gates;
- marking outcomes as `UNKNOWN` when a lease expires and the result is uncertain — never assuming an external side effect succeeded or failed;
- quarantining late Agent/async results that arrive after cancellation or human takeover;
- leaving an existing Agent untouched when Harness is not configured.

Do not stuff business rules into the fixed fields of `HarnessTaskSpec` or `HarnessContract`. For example, "close a Conversation after 24 hours" is a customer-service rule, not a generic Harness Task state; Harness only provides durable Waits, Executions, and event records on which business rules can be built.

## 6. Configuring a Harness

For a standard Agent, use `AgentHarness`:

```java
AgentHarness harness = AgentHarness.builder()
        .agent(existingAgent) // existing ai4j Agent; its capability assembly is preserved
        .persistence(HarnessPersistence.file(
                projectRoot.resolve(".ai4j/harness")))
        .contract(HarnessContract.builder()
                .taskRequiredTool("submitRefund")
                .approvalRequiredTool("submitRefund")
                .build())
        .build();

try {
    HarnessRunResult result = harness.run(HarnessRunRequest.builder()
            .scopeKey("shop-A")
            .sessionId("customer-A-agent-session")
            .input(message)
            .build());
} finally {
    // In a long-running service the Harness is usually an application-lifecycle bean,
    // closed once on shutdown.
    harness.close();
}
```

`HarnessContract` is a governance rule set, not a business task template. The rules above say: `submitRefund` cannot be called without a Task, and calling it requires approval; they say nothing about the Task's title, order fields, or conversation fields.

For `ai4j-coding`, use `CodingAgentHarness`, which preserves workspace tools, CodeAct, compact, processes, MCP, subagents, and the existing approval semantics — see [Coding Agent Harness Integration](/docs/products/coding-agent/harness-integration).

## 7. No CLI Is Brought In

Harness Anything's `ha` command suits humans and external Coding Agents operating a project governance directory; the SDK does not duplicate that CLI.

What the SDK provides:

- Java `AgentHarness` / `CodingAgentHarness` entry points;
- a Java `HarnessCommandGateway` for hosts, workers, webhooks, human back-offices, and tests;
- optional Harness Function Call tools so the Agent can manage its own Tasks, facts, and waits mid-run;
- File/JDBC persistence implementations.

Developers can call these APIs from their own HTTP services, message consumers, CLIs, TUIs, background workers, or schedulers. The same Harness ledger can be shared by multiple Agent workers and modified by a human back-office that updates Tasks or delivers Waits — without requiring users to install `ha`.

## 8. Other Runtimes

Standard `Agent` and `CodingAgent` already have direct adapters. If your business has an irreplaceable runtime of its own, implement `HarnessExecutionAdapter`, which only needs to:

- open or resume its own run state from a checkpoint;
- execute one bounded slice;
- export serializable Adapter state;
- apply Wait results delivered by the host.

Task, Execution, lease, wait, checkpoint, dependency, review, and completion gating remain managed by the Harness. This extension point exists to onboard existing runtimes — ordinary business developers are not asked to implement another Agent.
