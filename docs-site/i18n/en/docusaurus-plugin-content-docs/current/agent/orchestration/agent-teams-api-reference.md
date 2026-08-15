---
sidebar_position: 15
title: "Agent Teams API Reference"
description: "Navigate the Agent Teams runtime from the source view: how AgentTeamBuilder fields map to the execution chain, the task board state machine, the team tool surface, persistence recovery, and extension points."
tags: [reference]
---

# Agent Teams API Reference

This page is not a script-extracted class catalog; it is a source-code navigation guide for the `ai4j-agent` Team runtime.

If your goal is to:

- Determine which segment of the execution chain each `AgentTeamBuilder` entry point actually affects
- Understand the Team runtime's default behavior and failure semantics
- Add extension, persistence, approval, or debugging to the team collaboration capability

Then the more valuable thing than "a full enumeration of classes/fields/methods" is to first see the real control surface of the Team runtime clearly.

## 1. Start with the entry points: what exactly you are building

Core source files:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamAgentRuntime.java`

:::tip All code on this page is runnable
The wiring examples below come from
[`AgentTeamsDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamsDocExamplesTest.java),
with an inline scripted model client — zero network, runs in CI.
:::

`AgentTeamBuilder` has two terminal points:

### 1.1 `build()`

Returns `AgentTeam`. Suitable when you want to use team-specific API directly (read the task board, message bus, persisted state):

```java
AgentTeam team = Agents.team()
        .planner((objective, members, options) -> AgentTeamPlan.builder()
                .tasks(Collections.singletonList(
                        AgentTeamTask.builder()
                                .id("t1").memberId("worker").task("do work").build()))
                .build())
        .synthesizerAgent(synthAgent)
        .member(AgentTeamMember.builder()
                .id("worker").name("Worker").agent(workerAgent).build())
        .build();                              // ← returns AgentTeam

AgentTeamResult result = team.run(AgentRequest.builder().input("go").build());
result.getOutput();                            // aggregated output
team.snapshotState();                          // task board / message bus / persisted state
team.listTaskStates();
```

### 1.2 `buildAgent()`

Returns a plain `Agent`, but the runtime is replaced with `AgentTeamAgentRuntime`. Suitable when you want the Team to plug into the unified `Agent` call surface (session, listener):

```java
Agent teamAgent = Agents.teamAgent(Agents.team()
        .planner((objective, members, options) -> AgentTeamPlan.builder()
                .tasks(Arrays.asList(
                        AgentTeamTask.builder()
                                .id("collect").memberId("researcher")
                                .task("Collect requirements").build()))
                .build())
        .synthesizerAgent(synthAgent)
        .member(AgentTeamMember.builder()
                .id("researcher").name("Researcher").agent(researcherAgent).build()));

AgentResult result = teamAgent.run(AgentRequest.builder().input("prepare plan").build());
result.getOutputText();                        // goes through the unified Agent interface
result.getRawResponse();                       // actually an AgentTeamResult
```

This wrapper does two things:

- Copies and builds a new `AgentTeam` from the `AgentTeamBuilder` template each time
- Attaches a fresh `InMemoryAgentMemory` to the wrapped `Agent`

So the point of `buildAgent()` is not "to turn the Team into another object model", but rather:

- Letting Team orchestration be attached into the unified `Agent` interface
- Letting the Team plug into sessions, listeners, and the general call surface just like a plain Agent

## 2. How Builder fields map to the execution chain

The configuration items exposed by `AgentTeamBuilder` can be split into five groups by runtime role.

### 2.1 Planning and synthesizer roles

- `leadAgent(...)`
- `plannerAgent(...)`
- `synthesizerAgent(...)`
- `planner(...)`
- `synthesizer(...)`

The default fallback chains are not symmetric.

The planner fallback order is:

1. Explicit `planner(...)`
2. `plannerAgent(...)`
3. `leadAgent(...)`

The synthesizer fallback order is:

1. Explicit `synthesizer(...)`
2. `synthesizerAgent(...)`
3. `leadAgent(...)`
4. `plannerAgent(...)`

If a role still cannot be resolved in the end, an `IllegalStateException` is thrown directly. This means the Team runtime does not allow "no one is responsible for planning" or "no one is responsible for synthesizing".

### 2.2 Member registration

- `member(...)`
- `members(...)`

The build phase requires at least one member; otherwise the `AgentTeam` constructor fails immediately.

Members are converted into internal `RuntimeMember` instances and indexed by `id`. Duplicate `id`s also fail at construction time, rather than conflicting halfway through a run.

### 2.3 Collaboration and governance

- `options(...)`
- `messageBus(...)`
- `planApproval(...)`
- `hook(...)`
- `hooks(...)`

This section controls:

- Concurrent dispatch strategy
- Whether message history enters member context
- Whether the plan requires human or host approval
- Whether custom event logic can be attached before and after tasks

### 2.4 State persistence

- `stateStore(...)`
- `teamId(...)`
- `storageDirectory(...)`

If `stateStore` is explicitly provided, the Team uses it directly.

If `stateStore` is not provided but `storageDirectory` is, `AgentTeam` automatically derives:

- `FileAgentTeamStateStore(storageDirectory.resolve("state"))`
- `FileAgentTeamMessageBus(storageDirectory.resolve("mailbox").resolve(teamId + ".jsonl"))`

In other words, `storageDirectory` is not a decorative field; it automatically lands both state and mailbox onto the file system.

### 2.5 Wrapping the team as an Agent

`buildAgent()` uses:

- `new AgentTeamAgentRuntime(this)`
- `new InMemoryAgentMemory()`

Here `stateStore` is not mapped into `AgentMemory`. The Team's own long-term state remains the responsibility of `AgentTeamStateStore`; the wrapped `AgentMemory` is only a session container that the generic Agent interface requires.

## 3. The real lifecycle of the Team runtime

Class names alone make the execution order hard to see; the real control chain lives in `AgentTeam.run(...)`.

### 3.1 Planning phase

The runtime first calls the planner to produce an `AgentTeamPlan`, then hands the plan to `AgentTeamTaskBoard` for normalization.

If `planApproval` is provided, the approval callback runs first.

If no callback is provided but `options.requirePlanApproval = true`, the runtime fails directly; it does not silently let it pass.

### 3.2 Dispatch phase

`dispatchTasks(...)` drives the main loop. Each round it:

1. If a timeout is configured, first attempts `recoverTimedOutClaims(...)`
2. Checks whether `maxRounds` has been exceeded
3. Takes a batch of `READY` tasks according to `parallelDispatch` and `maxConcurrency`
4. Resolves the target member for each task and performs the claim
5. Executes that round's tasks in batch or serial fashion

Two easily overlooked boundaries live here:

- If `readyTasks` is empty but the board still has unfinished work, the runtime marks the remaining tasks `BLOCKED`, on the grounds that dependencies cannot be resolved or a cycle exists
- If the round count exceeds the limit, the runtime also marks stalled tasks `BLOCKED`

It does not spin indefinitely waiting.

### 3.3 Member execution phase

Each task eventually reaches `runMemberTask(...)`.

There are three key implementation points here:

- The Team does not reuse the member Agent's current context; instead it creates a `newSession()` for the member
- If `enableMemberTeamTools = true`, the `AgentTeamToolRegistry` is merged into the member's original registry
- At the same time, the member's original executor is wrapped with `AgentTeamToolExecutor`

This means the Team collaboration tools are not "special capabilities that only the Team runtime can see", but are injected into the member session through the ordinary tool chain.

### 3.4 Synthesis phase

Once member results have been collected, the runtime calls the synthesizer to aggregate the list of `AgentTeamMemberResult` into the final output.

So the structure of the Team runtime is not "naturally concatenated after multiple agents run concurrently", but rather:

- planner produces the plan
- members execute by task
- synthesizer owns the final synthesis

## 4. `AgentTeamTaskBoard` is the true core of the task state machine

Source entry point:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java`

Without understanding `AgentTeamTaskBoard`, you will mistake the Team for "a few Agents running in sequence".

### 4.1 Normalization happens at construction

When constructing `AgentTeamTaskBoard(tasks)`, it first:

- Normalizes task ids
- Auto-generates ids like `task_1`, `task_2` for empty tasks
- De-duplicates conflicting ids
- Normalizes the dependency list
- Builds all tasks as `PENDING`

It then calls `refreshStatuses()` to advance dependency-free tasks to `READY`.

### 4.2 The state set

Task states are not free text but a fixed state machine:

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

This state machine encodes dependency resolution, not just UI display.

### 4.3 Claim and release

- `claimTask(taskId, memberId)` only allows `READY -> IN_PROGRESS`
- `releaseTask(taskId, memberId, reason)` only allows the current owner to return a task to `PENDING`
- `reassignTask(...)` only changes the owner, without altering the `IN_PROGRESS` state
- `heartbeatTask(...)` only updates the heartbeat and metadata

If the member ID does not match, release/reassign/heartbeat all fail. This is not advisory validation; it is a real ownership constraint.

### 4.4 Timeout recovery

`recoverTimedOutClaims(timeoutMillis, reason)` reclaims timed-out `IN_PROGRESS` tasks back to `PENDING`, then re-runs `refreshStatuses()`.

This means a task claim timeout is not "a warning to show the user"; it actually changes the scheduling outcome.

### 4.5 Completion, failure, stall

- `markCompleted(...)` writes the output and elapsed time, then triggers a status refresh
- `markFailed(...)` writes the error and elapsed time, then triggers a status refresh
- `markStalledAsBlocked(...)` changes remaining `PENDING/READY` tasks to `BLOCKED`

This lets the final task board express both the success path and the failure path of "the plan itself cannot be resolved" accurately.

## 5. `AgentTeamOptions` defaults are not neutral

Source entry point:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamOptions.java`

The defaults are as follows:

| Option | Default | Consequence |
| --- | --- | --- |
| `parallelDispatch` | `true` | Dispatches in concurrent batches by default, not serial execution |
| `maxConcurrency` | `4` | Default concurrency cap is 4 |
| `continueOnMemberError` | `true` | After a single member fails, the Team continues and synthesizes by default |
| `broadcastOnPlannerFailure` | `true` | On planner failure, falls back to broadcast dispatch |
| `failOnUnknownMember` | `false` | When the plan references unknown members, the whole team does not fail immediately by default |
| `includeOriginalObjectiveInDispatch` | `true` | Each member sees the complete original objective by default |
| `includeTaskContextInDispatch` | `true` | Task context enters the member prompt by default |
| `includeMessageHistoryInDispatch` | `true` | Recent message history enters the member prompt by default |
| `messageHistoryLimit` | `20` | At most 20 team messages are injected by default |
| `enableMessageBus` | `true` | Team communication is on by default |
| `allowDynamicMemberRegistration` | `true` | Dynamic member add/remove is allowed at runtime by default |
| `requirePlanApproval` | `false` | Plan approval is not enforced by default |
| `maxRounds` | `64` | The scheduling main loop runs at most 64 rounds by default |
| `taskClaimTimeoutMillis` | `0L` | Claim timeout reclaim is disabled by default |
| `enableMemberTeamTools` | `true` | Members can call `team_*` tools by default |

Combined, these defaults indicate the Team runtime's default lean is toward:

- Concurrency whenever possible
- Single-point failures should not drag down the whole
- Members get a fairly complete collaboration context by default
- Team communication and team tools are on by default

:::tip
If your business is more about strong constraints than high throughput, you should usually at least re-evaluate:

- `continueOnMemberError`
- `enableMemberTeamTools`
- `includeMessageHistoryInDispatch`
- `taskClaimTimeoutMillis`
:::

## 6. The team tool surface: how members collaborate proactively

Source entry points:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`

### 6.1 Built-in tools exposed to members

`AgentTeamToolRegistry` exposes a fixed set of 7 tools:

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

This shows that "collaboration" in the Team is not the runtime secretly forwarding on its own, but a tool surface that member Agents can call explicitly.

### 6.2 The executor's interception strategy

`AgentTeamToolExecutor.execute(...)` only applies built-in handling to these 7 `team_*` tools.

If a different tool is called:

- If there is a delegate, the call passes through to the original executor
- If there is no delegate, it throws directly

This ensures that Team tool injection does not swallow the tool capabilities the member already had.

### 6.3 Return value semantics

Team tools return a JSON string that usually contains at least:

- `action`
- `ok`
- `memberId`

Specific actions also carry:

- `taskId`
- `taskState`
- `toMemberId`
- `tasks`

So it is suitable both for the model to read and for the host to use for debugging and auditing.

## 7. State persistence and recovery

`AgentTeam` maintains its own team state, broader than `AgentMemory`.

Key APIs include:

- `snapshotState()`
- `loadPersistedState()`
- `restoreState(...)`
- `clearPersistedState()`

The persisted content is not just the final answer; it also includes:

- `teamId`
- The current objective
- Member snapshots
- task states
- message history
- `lastOutput`
- `lastRounds`
- Run start and end times

This indicates the Team recovery semantics target "collaboration process reconstruction", not merely preserving the last piece of text output.

## 8. What the runtime mutable fields tell you

`AgentTeam` has a set of runtime mutable fields internally:

- `activeBoard`
- `lastTaskStates`
- `activeObjective`
- `lastOutput`
- `lastRounds`
- `lastRunStartedAt`
- `lastRunCompletedAt`

These fields show that the Team is not a purely functional object. It maintains the current execution state during a run and also retains the most recent run snapshot.

Therefore, an `AgentTeam` instance is more like "a stateful collaboration runner" than "a read-only configuration object".

## 9. Where extension points should attach

If you want to extend Agent Teams, prefer the following interfaces rather than modifying the main loop directly.

### 9.1 Change the planning strategy

Implement or replace:

- `AgentTeamPlanner`
- `planner(...)`

Suitable for custom task decomposition, member matching, or plan format.

### 9.2 Change the final synthesis strategy

Implement or replace:

- `AgentTeamSynthesizer`
- `synthesizer(...)`

Suitable for custom final aggregation, ordering, conflict resolution, or structured output.

### 9.3 Change state or message storage

Implement or replace:

- `AgentTeamStateStore`
- `AgentTeamMessageBus`

Suitable for wiring to a database, object storage, event stream, or external audit system.

### 9.4 Change approval and lifecycle callbacks

Implement or inject:

- `AgentTeamPlanApproval`
- `AgentTeamHook`

Suitable for plan approval, pre/post task audit, metrics collection, alerting, or human intervention.

### 9.5 Change the member collaboration surface

If you only want to change the team tools visible to members, look first at:

- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

:::note
Do not just "verbally ask members to collaborate" in the planner prompt; that is only a prompt, not a capability surface.
:::

## 10. When debugging the Team runtime, check these source points first

When you hit "the plan was produced but tasks are not running", "members chaining tasks", "collaboration messages have no effect", or "state is wrong after recovery", check first:

- Whether `AgentTeam.dispatchTasks(...)` marked tasks `BLOCKED` due to `maxRounds` or dependency issues
- Whether `AgentTeamTaskBoard.refreshStatuses()` correctly advanced dependency states
- Whether `runMemberTask(...)` actually called `newSession()` for the member
- Whether `enableMemberTeamTools` caused team tools to be correctly merged into the member registry
- Whether `resolveMessageBus(...)` / `resolveStateStore(...)` landed on the file system or your custom backend as expected

Looking only at the final output text usually does not reveal these control-surface problems.

## 11. Test entry points

The following test classes are best used as a behavioral map:

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`

They respectively cover:

- The team's main execution chain
- The task board state machine
- Persistence and recovery

## 12. Further reading

1. [Agent Teams](/docs/agent/orchestration/agent-teams)
2. [Subagent Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory/memory-and-state)
5. [Trace Observability](/docs/agent/observability/trace-observability)
