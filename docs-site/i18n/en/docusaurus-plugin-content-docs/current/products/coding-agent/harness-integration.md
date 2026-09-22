---
title: Coding Agent Harness Integration
description: Put an existing Coding Agent inside the durable Harness so project-level Tasks keep running across CLI, TUI, ACP, workers, and restarts.
tags: [coding-agent, harness]
---

# Coding Agent Harness Integration

This integration targets Coding Agents in the style of Codex or Claude Code: the user supplies a complex goal, and the Agent analyzes it, splits it, edits code, runs tests, handles build output — and can maintain a large codebase across processes for a long time.

Harness does not turn the Coding Agent into a different product, and it does not require the SDK to ship the Harness Anything `ha` command. It only places the existing `CodingAgent` inside a unified durable Task/Execution envelope.

## 1. Project-Level Tasks, Not Bound to a CLI Session

For coding scenarios, the right relationship is usually:

```text
project/repository
  └─ Task: "finish the payment-module refactor"
       ├─ Execution: the first CLI/TUI input
       ├─ Execution: a background worker continues
       ├─ Execution: another CLI session resumes
       └─ Execution: Harness recovery after a restart

Agent Session: holds the context of one Agent runtime, can be used by many
Executions — but it is not the owner of a Task, and it is not the project.
```

The user can open a new terminal, switch between TUI/ACP, or let a background worker take over a READY Execution — while the Task still belongs to the project workspace. Harness leases and fencing prevent two workers from advancing the same Execution or the same Session concurrently.

## 2. Minimal Integration

The `CodingAgent`'s workspace tools, MCP, Skills, CodeAct, compact, process registry, subagents, and existing permission policies remain owned by `ai4j-coding`:

```java
CodingAgent codingAgent = existingCodingAgent(projectRoot);

CodingAgentHarness harness = CodingAgentHarness.builder()
        .codingAgent(codingAgent)
        .persistence(HarnessPersistence.file(
                projectRoot.resolve(".ai4j/harness")))
        .contract(HarnessContract.builder()
                // Whether approvals and external review are required is a project-policy decision.
                .requiresApprovedReview(false)
                .build())
        .autoResume(false)
        .build();

HarnessRunResult first = harness.run(HarnessRunRequest.builder()
        .scopeKey("repo:" + repositoryId)
        .sessionId("coding-session:" + clientSessionId)
        .idempotencyKey("prompt:" + promptId)
        .input("Implement the payment-module refactor and run the related tests")
        .build());
```

No fixed `Task` is created up front. On the first slice, the Agent can create a Task from the user's goal via `harness_task_manage(create)`, then split out subtasks like code search, API design, implementation, testing, and documentation.

If the CLI already learned the Task ID from a project database or a user selection, pass `taskId` directly; otherwise omit it and let the Agent decide dynamically.

## CodingAgent Adapter Layering Diagram

Interactive architecture diagram: CodingAgentHarness facade → AgentHarness → CodingAgentHarnessExecutionAdapter — open decodes CodingSessionState and opens a real CodingSession per slice, applyHarnessOverlay injects HarnessToolRegistry/Executor/Interceptor/Prompts/Budget, snapshot and applyDelivery carry state across slices.

import useBaseUrl from '@docusaurus/useBaseUrl';


<iframe src={useBaseUrl('/archify/harness-coding-adapter.html')} title="CodingAgent adapter layering diagram" style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}} />

<a href={useBaseUrl('/archify/harness-coding-adapter.html')} target="_blank" rel="noopener noreferrer">Open the interactive diagram in a new window</a>


## 3. Long-Horizon Autonomous Progress

One slice ending does not mean the project task is done. A coding host can resume the same Execution when it sees `CONTINUATION_REQUIRED`:

```java
HarnessRunResult current = first;
while (current.getStatus() == HarnessRunStatus.CONTINUATION_REQUIRED) {
    current = harness.resume(current.getExecution().getExecutionId());
}

if (current.getStatus() == HarnessRunStatus.WAITING) {
    // Waiting for user approval, CI, a remote build, or an external event; do not busy-loop.
    publishCodingWait(current);
}
```

For background workers, let them pull runnable Tasks from the durable ledger:

```java
List<HarnessRunResult> results = harness.runReady(
        HarnessRunBudget.builder()
                .maxExecutions(1)
                .build());
```

`HarnessRunBudget` only bounds this scheduling slice; it does not impose a uniform 24-hour or round cap on every Coding Agent. A project can set the Agent's `maxSteps`, wall-clock, and token budget very high, or let a worker keep servicing `CONTINUATION_REQUIRED` until the Task's submission, Gate, and external review actually permit completion.

## 4. The Typical Shape of a Long Code Task

Take "maintain a large repository and refactor the payment module" as an example:

1. The user's prompt enters a new Execution; the Agent creates a project-level Task.
2. The Agent calls `harness_task_manage(split)` to create subtasks for architecture analysis, code changes, tests, and migration docs.
3. Subtasks are linked into a DAG with `add_dependency` — e.g. tests depend on implementation, migration docs depend on a stable interface.
4. The Agent records repository constraints with `harness_fact_record`, technical trade-offs with `harness_decision_propose`, and test commands, build results, and code locations with `harness_evidence_record`.
5. When CodeAct or ordinary tools hit the Agent step boundary, Harness saves the coding session state and a checkpoint, then continues the same Task.
6. Remote CI, long builds, or human approvals enter the recovery chain through `ASYNC_OPERATION` / `APPROVAL` Waits instead of blocking a CLI thread.
7. The Agent submits the change set, test evidence, known gaps, and residual risks via `harness_submission_request`.
8. A human or trusted system reads the Submission and performs review and Gate evaluation; only an entitled Actor can complete the Task.

"Tests pass" is Evidence; "the Agent believes it can ship" is a Submission; "the project allows the Task to become DONE" is an independent decision made after Review/Gate. These concepts cannot be replaced by a single model output.

## 5. Multiple Coding Clients Sharing One Project

CLI, TUI, ACP, and background workers can all open the same `.ai4j/harness`:

```text
CLI/TUI/ACP prompt
        |
        +--> FileHarnessStore(project/.ai4j/harness)
        |
        +--> same project Task / Execution / checkpoint

background worker --+
another client    --+--> lease + fencing; only the legitimate holder advances the current Execution
```

For cross-host or multi-instance collaboration, switch to:

```java
HarnessPersistence.jdbc(dataSource, "repository-coding");
```

`harnessId` is the logical name of a shared ledger, not the name of one CLI session. Each client can still have its own Agent Session; Tasks are not bound to any particular client.

## 6. Autonomy and Governance for a Coding Agent

A coding project may choose minimal constraints:

```java
HarnessContract contract = HarnessContract.builder()
        .requiresApprovedReview(false)
        .build();
```

This does not disable checkpoints, leases, dependencies, Evidence, UNKNOWN handling, or recovery; it only means the project does not require an external review for every Submission. Conversely, projects involving `git push`, production releases, database migrations, or secret reads can mark the corresponding business Tools as approval-required.

Management tools are exposed to the Agent as Function Calls, but completion authority stays at the Gateway/Contract. The Agent must not hold the authority to approve its own Submission, complete its own Task, or confirm unknown external side effects. The project host can call the Gateway as a human/system Actor inside its own review service.

## 7. Relationship to Harness Anything

When using Harness Anything with external Coding Agents, the `ha` CLI, the governance directory, and `AGENTS.md` form the project-management surface visible outside the Agent. The SDK integration does not duplicate that CLI — it puts the same core behaviors inside the Java runtime:

| Harness Anything usage | SDK equivalent |
| --- | --- |
| Agent creates/updates Tasks via the CLI | Agent calls `harness_task_manage`; the Gateway persists |
| plans, facts, decisions, evidence stored under `harness/` | File/JDBC Harness ledger stores structured records and checkpoints |
| CLI/external Agent continues a work package | `resume`, `deliver`, `runReady`, or a business worker |
| human review / completion boundary | Submission, Review, Gate, and Actor permissions |
| multiple clients working in one project directory | shared File/JDBC store with lease/fencing |

So existing Coding Agent capabilities remain the foundation; Harness only adds a recoverable management layer across time — it does not turn project workflows into a fixed task table inside the SDK.
