---
sidebar_position: 11
title: "Agent Teams"
description: "AI4J Agent Teams is a team runtime with a control plane: the planner decomposes the task, the task board maintains dependencies and state, members collaborate via injected team_* tools, and the synthesizer aggregates the final answer."
tags: [concept]
---

# Agent Teams

`AgentTeam` is not "put multiple Agents in a list and call them one by one"; it is a team runtime with a control plane.

It breaks multi-member collaboration into 5 explicit components:

- planner: turns the objective into a set of tasks
- task board: maintains dependencies, status, claims, and reclaims
- member dispatch: dispatches ready tasks to members for execution
- message bus: persists collaboration messages instead of hiding the collaboration inside the prompt
- synthesizer: aggregates member outputs into the final answer

If you only look at the surface API, `AgentTeam.run("...")` looks like a "more advanced multi-Agent demo"; from the source, it is closer to a lightweight orchestration runtime.

:::tip Inter-member communication model
Unlike the [SubAgent tool-call RPC](/docs/agent/orchestration/subagent-handoff-policy#0-通讯与并行模型先建立正确心智), Teams members are **not** in a tool-call relationship with each other; they collaborate through two shared components:

- **TaskBoard**: the planner decomposes tasks → members `claim_task` to grab them → execute → the orchestrator automatically `markCompleted`. This is the source of the division of labor.
- **MessageBus**: members send messages via `team_send_message` (point-to-point) / `team_broadcast`; there are two receive paths — the orchestrator injects history before the next member executes (pull model, §7.2), and members can also actively pull new messages during execution by calling `team_read_messages` (reactive, §7.3).

Members relate **cooperatively** (shared state), not hierarchically (parent calls child). Multiple ready tasks in the same round may run in parallel (§5); the board and bus are fully `synchronized` for thread safety.
:::

## 1. Start with 3 key design decisions

To understand Agent Teams, the most important thing is not to memorize class names first, but to grasp 3 design decisions.

### 1.1 `AgentTeam` is itself the control plane

`AgentTeam` itself implements `AgentTeamControl`. This is not a minor detail; it is the center of the entire team collaboration model.

This means:

- Team tools are not dispatched to an external controller
- When a member invokes a `team_*` tool during execution, the call lands directly back on the current `AgentTeam` instance
- Task claim, reassignment, message publish, and heartbeat updates all happen on the currently running Team object

So the Team is not "members freely collaborating among themselves", but "members collaborating through a unified control plane".

### 1.2 Members are stateless task executors by default

`runMemberTask(...)` calls the following every time:

```java
AgentSession session = dispatch.member.agent.newSession();
```

That is, even if the same member is repeatedly assigned tasks across multiple rounds, the previous task's session memory is not reused by default.

This has a critical consequence:

- The team's continuity does not live in member-local memory
- Continuity is externalized into `task context + message bus + task board state`

This is a very deliberate architectural tradeoff: members are repeatable execution units, and team state is held by the external runtime.

### 1.3 Collaboration is "structured state + tools", not "write a bit more prompt"

Many multi-Agent solutions simply write "cooperate with each other" into the prompt. AI4J Agent Teams does not.

It implements collaboration capability explicitly as:

- `AgentTeamTaskBoard`
- `AgentTeamMessageBus`
- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

This means member collaboration is no longer just a natural-language convention, but genuinely interceptable, observable, and recoverable runtime behavior.

## 2. The real lifecycle of `run()`

The most valuable way to read `AgentTeam` is to follow the execution chain of `run(...)`.

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`

`run(AgentRequest)` is roughly divided into 6 phases.

### 2.1 Phase 1: Pre-run initialization

When the run starts, `AgentTeam` does a few basic things:

- Records `lastRunStartedAt`
- Extracts the objective
- If the message bus is enabled, runs `messageBus.clear()` first
- Snapshots the current member list
- Fires the `beforePlan(...)` hook

The most notable point here is `messageBus.clear()`.

This means every new `run()` is treated as a fresh round of team collaboration, not as a natural continuation of the previous collaboration history. Therefore:

- Messages restored by `loadPersistedState()` are better suited for observation, audit, and UI hydration
- They are not meant for "resume the original run seamlessly and keep going after recovery"

This is an important boundary of the current implementation.

### 2.2 Phase 2: planner generates the plan

Next it executes:

```java
AgentTeamPlan plan = planner.plan(objective, members, options);
```

The default planner is `LlmAgentTeamPlanner`. Its behavior is more concrete than it appears:

1. Uses a fixed prompt to require the model to output JSON
2. Lists available members with their id, name, and description
3. Requires a task array, each task containing `id/memberId/task/context/dependsOn`
4. Calls `plannerAgent.newSession().run(...)`
5. Parses the result tolerantly with `AgentTeamPlanParser.parseTasks(...)`

The core of the planner prompt is not "let the model improvise", but to constrain the planner into a JSON task planner.

### 2.3 Phase 3: Plan normalization and approval

The tasks produced by the planner do not enter dispatch directly; they first go through:

```java
AgentTeamTaskBoard board = new AgentTeamTaskBoard(plan.getTasks());
plan = plan.toBuilder().tasks(board.normalizedTasks()).build();
```

This step is critical because the task board normalizes first:

- When `id` is missing, it auto-generates `task_1`, `task_2`, ...
- Duplicate ids are auto-deduplicated and renamed
- Dependency ids are normalized uniformly
- Initial status uniformly enters `PENDING`

Only then does it:

- `fireAfterPlan(...)`
- `ensurePlanApproved(...)`

That is, `planApproval` sees the "already standardized" plan, not the raw model output.

This matters for governance, because approval logic can reason over stable ids and dependency structure rather than parsing unstable raw text.

### 2.4 Phase 4: dispatch loop

The real team scheduling happens in `dispatchTasks(...)`.

The logic in this layer is not "iterate the member list", but a round-based loop:

1. If `taskClaimTimeoutMillis` is enabled, first reclaim timed-out claims
2. If `rounds >= maxRounds`, mark remaining tasks as blocked
3. Compute batch size from `parallelDispatch` and `maxConcurrency`
4. Pull a batch of `READY` tasks from the task board
5. Resolve the target member for each task
6. `claimTask(...)`
7. Execute this round's tasks in batch
8. Collect results and decide whether to continue to the next round

This shows the basic scheduling unit of the Team is not "member", but "ready task batch".

### 2.5 Phase 5: Member execution and collaboration tool injection

Before each ready task is actually executed, it enters `executePreparedTask(...)` and `runMemberTask(...)`.

This layer does two important things:

1. Builds the member execution input
2. Injects the `team_*` collaboration tools

When building the input, `buildDispatchInput(...)` assembles per configuration:

- member role / expertise
- task id
- dependsOn
- objective
- assigned task
- optional task context
- recent team messages

Then it rewrites the tool layer on the session context:

```java
mergedRegistry = new CompositeToolRegistry(originalRegistry, teamToolRegistry);
sessionContext.setToolExecutor(new AgentTeamToolExecutor(this, memberId, taskId, originalExecutor));
```

This step is the key injection point of the entire Team mechanism:

- The schema exposure surface is extended
- The execution surface is also wrapped
- And the wrapper holds `this`, `memberId`, and `taskId`

So members not only "see" the team tools, they also automatically carry the current task context when calling them.

### 2.6 Phase 6: synthesis and wrap-up

After all rounds finish, the Team invokes the synthesizer:

```java
AgentResult synthesis = synthesizer.synthesize(objective, plan, dispatch.results, options);
```

The default synthesizer is `LlmAgentTeamSynthesizer`, which:

- Creates a fresh session
- Provides the objective
- Provides the plan JSON
- Provides all member outputs / errors
- Requires a directly usable final answer

This means synthesis does not reuse the lead's internal context; it reorganizes the team results and hands them to a new Agent call.

Finally the Team assembles an `AgentTeamResult`, containing:

- `teamId`
- `objective`
- `plan`
- `memberResults`
- `taskStates`
- `messages`
- `rounds`
- `output`
- `synthesisResult`
- `totalDurationMillis`

## 3. How the Planner subsystem actually tolerates failure

`LlmAgentTeamPlanner`'s fault tolerance is not "it's fine if the planner errs"; it is a more specific strategy.

### 3.1 It tolerates "non-standard output", not "the call throwing an exception directly"

`AgentTeamPlanParser` does its best to parse tasks from the model output:

- Supports a direct array
- Supports a direct object
- Supports extracting the first JSON fragment from mixed text
- Supports multiple field aliases, such as `taskId/member/assignee/instruction/goal`

So it tolerates planner output that is "not clean enough in format".

But note:

- If `plannerAgent.newSession().run(...)` itself throws
- `LlmAgentTeamPlanner` does not swallow this exception

Therefore the planner's "fallback" only covers the "parsed to empty" case, not a failure of the planner call chain itself.

### 3.2 The actual meaning of `broadcastOnPlannerFailure`

This config name is easy to misread.

In the current implementation, its real semantics are closer to:

- "When the planner output cannot be parsed into any task, whether to generate a fallback task for each member"

rather than:

- "Once the planner run fails, automatically broadcast and continue"

The fallback task generation rule is also clear:

- One task per member
- Task id shaped like `fallback_1`
- Task content includes the member description and the original objective
- Context is fixed at `planner_fallback`

This is a very pragmatic degradation path: when the planner slips, the system still tries to let each member complete part of the work, rather than aborting outright.

## 4. Task Board is a real state machine

`AgentTeamTaskBoard` is one of the most central mechanisms of Agent Teams, because the real "collaboration order" and "scheduling legality" live here.

### 4.1 What normalization happens at initialization

When constructing the task board, it uniformly handles:

- id normalization: lowercasing, replacing illegal characters, removing redundant `_`
- Auto-filling missing ids
- Auto-evading duplicate ids
- Uniform normalization of dependencies

Then it generates the initial `AgentTeamTaskState` for each task:

- `status = PENDING`
- `phase = planned`
- `percent = 0`

So the planner output is not "the system's internal task truth"; the standardized board is.

### 4.2 `refreshStatuses()` defines the dependency progression semantics

The task board does not simply store state; it calls `refreshStatuses()` before many operations to recompute whether a task is:

- `READY`
- `PENDING`
- `BLOCKED`

Its judgment logic is:

- A task with no dependencies is directly ready
- A task is ready only when all dependencies are completed
- A missing dependency -> blocked
- A dependency that failed / blocked -> blocked
- Otherwise it stays pending

This means the Team's dependency progression is explicit and explainable, not implicitly expressed by planner text.

### 4.3 claim / release / reassign / heartbeat are not ancillary features

These control actions all directly mutate `AgentTeamTaskState`:

- `claimTask(...)`: enters `IN_PROGRESS`
- `releaseTask(...)`: returns to `PENDING`
- `reassignTask(...)`: keeps in-progress, but switches `claimedBy`
- `heartbeatTask(...)`: updates `lastHeartbeatTime` and `heartbeatCount`
- `recoverTimedOutClaims(...)`: after timeout, reclaims an `IN_PROGRESS` task as `PENDING`

This is why `team_*` tools are not "chat assistant features", but part of the scheduling control plane.

### 4.4 There is more than one source of blocked

In the current implementation, there are at least 4 categories of reasons a task or an entire round is marked blocked:

- Missing dependency
- Dependency failed
- `maxRounds` exceeded
- No ready task at the moment, inferred as unresolved dependencies or a cyclic plan

In other words, `BLOCKED` is not only "dependency unmet"; it may also be "the scheduling policy has judged this round cannot make further progress".

## 5. The concurrency model is not complex, but it is explicit

Agent Teams concurrency is not an actor model, nor a complex async framework; it is a fairly direct Java concurrency model.

### 5.1 The task board itself is a synchronized object

Almost all public methods of `AgentTeamTaskBoard` use `synchronized`.

The effect of this is:

- Internal board state mutation is very straightforward
- The thread-safety model is easy to reason about
- The cost is that the throughput ceiling is not particularly aggressive

This is an implementation biased toward engineering stability, not toward extreme concurrency.

### 5.2 The Team body uses only two locks to separately protect members and runtime state

There are two explicit locks inside `AgentTeam`:

- `memberLock`
- `runtimeLock`

They respectively protect:

- Member registration, removal, lookup
- The current active board, objective, lastTaskStates

This split avoids shoving every behavior into one giant synchronized block.

### 5.3 Real parallelism only happens within "the same round's ready tasks"

If `parallelDispatch=true` is enabled and there are multiple ready tasks this round, `executeRound(...)` will:

- Build a thread pool per `maxConcurrency`
- Execute each `PreparedDispatch` in parallel
- Wait for all futures to return
- Finally `shutdownNow()`

Therefore the Team's concurrency semantics are:

- Parallel within a round
- Serial between rounds
- Board state merged via synchronized methods

This makes behavior relatively easy to reason about: the same round can do things concurrently, but global progression is still discrete rounds.

## 6. Why `team_*` tools are the soul of this system

Without `team_*` tools, Agent Teams could still "let a lead assign tasks and members return results"; but that is only a static dispatch model.

What makes it behave like a team runtime is that members can actively manipulate tasks and messages.

### 6.1 What `AgentTeamToolRegistry` exposes

There are 8 default tools:

- `team_send_message`
- `team_broadcast`
- `team_read_messages`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

The first three are the messaging surface (send, broadcast, receive); the latter five are the task control surface.

:::tip `team_read_messages`: a member's reactive receive
During execution a member may call `team_read_messages` (no args) to actively check its own mailbox and pull new messages **since the last read** sent to it by peers (point-to-point + broadcast). Each call returns only unread messages; already-read ones are not reported again. This makes **debate-style collaboration** — "member A sends a critique to B, B receives it mid-execution and fires back" — possible, aligning with Claude Code agent teams' peer-to-peer messaging capability.

Note that AI4J implements this with a **shared message log + per-member read set**, rather than Claude Code's per-agent mailbox JSON file (file-based IPC is designed for multi-process; an in-JVM SDK does not need it).
:::

### 6.2 The interception semantics of `AgentTeamToolExecutor`

This executor handles only `team_*`:

- A `team_*` hit -> routed through Team control logic
- Other tools -> delegated directly to the original `delegate`

So it does not replace the member's original tool chain; it adds a layer of team-control interceptor in front of the existing tool chain.

### 6.3 `defaultTaskId` lets the model pass half the boilerplate

When constructing `AgentTeamToolExecutor`, the current `taskId` is passed in.

Later, when the executor parses arguments, if the tool call does not explicitly supply `taskId`, it automatically falls back to the current task.

This design matters because it lowers the bar for the model to use team tools:

- The model does not need to backfill the current task id every time
- But execution results can still be stably bound to the correct task

### 6.4 Tool merging does not deduplicate

`CompositeToolRegistry` simply concatenates the `getTools()` results of multiple registries; it does not deduplicate.

This means:

- If a member already has a tool with the same name as a `team_*` tool
- Or multiple registries expose schemas with the same name

The tool surface visible to the model may show conflicts or duplicate declarations.

The current Team design assumes by default:

- Team tool names are a reserved prefix
- Consumers should not redefine `team_*` in a member's original toolset

This is a constraint that should be explicitly observed.

## 7. The message bus is not an ancillary log; it is part of the collaboration state

The role of `AgentTeamMessageBus` is not simply "record who said what"; it bears three responsibilities:

- Provides an explicit communication surface for member collaboration
- Feeds historical messages into subsequent task injection
- Provides the team trajectory for persistence and UI

### 7.1 The Team itself also sends system messages

For example, on task assignment and completion, the Team proactively sends:

- `task.assigned`
- `task.result`
- `task.error`
- `run.complete`

So the message bus holds not only members chatting with each other, but also collaboration events from the system perspective.

### 7.2 Historical messages enter the member prompt

When both of the following switches are on:

- `enableMessageBus = true`
- `includeMessageHistoryInDispatch = true`

Recent message history is woven into the member execution input.

This further proves a key point:

- The Team's continuity comes mainly from externalized messages and task state
- Not from long-term reuse of member sessions

### 7.3 Two receive paths: dispatch injection + active read

There are two complementary paths for a member to see peer messages:

- **Dispatch injection (pull model, §7.2)**: the orchestrator weaves `historyFor(member)` into the prompt **before the member starts executing**. This gives the member its starting context.
- **Active read (reactive, §6.1)**: the member calls `team_read_messages` **during execution** to pull newly arrived messages. This covers the "mid-long-task, received a peer's new finding / question" scenario.

The two paths do not conflict: dispatch injects the "pre-start" history, and `team_read_messages` returns "since last read" new messages (tracked by message id; already-read messages do not repeat). Collaboration that needs debate / mutual challenge (the flagship scenario of Claude Code agent teams) is realized via the second path.

## 8. The boundary of persistence and recovery, stated clearly

Agent Teams' persistence capability is useful, but it is easily mistaken for "pause and then resume the same team process".

The current implementation does not have that semantics.

### 8.1 `snapshotState()` saves a runtime snapshot

The snapshot contains:

- team metadata
- objective
- member snapshots
- task states
- messages
- last output, rounds, start/end time

What it saves is "what this team looked like at that moment", not "this team's Java execution现场 (live execution site)".

### 8.2 `loadPersistedState()` is closer to UI hydration / inspection

After invocation, it will:

- Validate `teamId`
- Restore the message bus
- Restore `lastTaskStates`
- Restore `lastOutput` / `lastRounds` / timestamps

But it does not restore:

- The currently running thread pool
- Member-internal session memory
- The real instance state of the planner / synthesizer / member agent

### 8.3 `storageDirectory(...)` is only a conventional file storage entry

If you only set `storageDirectory(...)`, it derives by default:

- `mailbox/<teamId>.jsonl`
- `state/<teamId>.json`

This makes it easy for a Team to land on local disk, but it is still "snapshot recovery", not "process-level continuation".

### 8.4 A new round of `run()` clears the message bus

This is the easiest boundary to overlook.

Even if you just called `loadPersistedState()`, once you call a new `run()`, the current implementation still runs `messageBus.clear()` first.

So the current recovery is better suited for:

- Inspecting the last team state
- Letting the UI / CLI redisplay history
- Serving as a data source for external governance or audit

It is not appropriate to understand it as "after recovery, naturally continue collaborating from the previous round".

## 9. The genuinely high-leverage parts of the config

Not all `AgentTeamOptions` are equally important. The ones that actually change the system's behavioral shape are mainly the groups below.

### 9.1 Scheduling shape

- `parallelDispatch = true`
- `maxConcurrency = 4`
- `maxRounds = 64`

These three decide whether the Team is:

- Biased toward concurrent batch processing
- Or biased toward conservative serial execution

### 9.2 Failure semantics

- `continueOnMemberError = true`
- `failOnUnknownMember = false`
- `broadcastOnPlannerFailure = true`

Together, these make the default Team lean toward "try to keep making progress", rather than "abort immediately on any exception".

:::note
Pay special attention to `failOnUnknownMember = false`:

- When the planner points to an unknown member, `resolveMember(...)` falls back to the first member
- This improves fault tolerance, but may also mask planner assignment errors
:::

### 9.3 Source of team continuity

- `enableMessageBus = true`
- `includeMessageHistoryInDispatch = true`
- `messageHistoryLimit = 20`
- `includeTaskContextInDispatch = true`

This group decides how much externalized state members share with each other.

Because members default to `newSession()` every time, these externalized-context switches have a larger impact than in a single-Agent scenario.

### 9.4 Team governance

- `requirePlanApproval = false`
- `allowDynamicMemberRegistration = true`
- `taskClaimTimeoutMillis = 0L`
- `enableMemberTeamTools = true`

This group decides whether the Team behaves more like:

- A free-collaboration default runtime
- Or a strictly controlled approval-style system

## 10. An integration example closer to real semantics

The point of the example below is not "it runs", but to reflect several key structures of the Team.

```java
Path storage = Paths.get(".ai4j/teams");

AgentTeam team = Agents.team()
        .teamId("release-review-team")
        .leadAgent(leadAgent)
        .member(AgentTeamMember.builder()
                .id("reader")
                .name("Reader")
                .description("Read code and extract behavior changes.")
                .agent(readerAgent)
                .build())
        .member(AgentTeamMember.builder()
                .id("reviewer")
                .name("Reviewer")
                .description("Assess risks, regressions, and missing coverage.")
                .agent(reviewerAgent)
                .build())
        .storageDirectory(storage)
        .options(AgentTeamOptions.builder()
                .parallelDispatch(true)
                .maxConcurrency(2)
                .continueOnMemberError(true)
                .enableMemberTeamTools(true)
                .taskClaimTimeoutMillis(30_000L)
                .build())
        .build();

AgentTeamResult result = team.run("Review the latest release candidate and summarize the main risks.");
```

The real meaning this configuration expresses is:

- The planner / synthesizer can default to fall back to leadAgent
- Team messages and state can land on disk
- Members can proactively invoke `team_*` tools
- If a task is claimed but has no heartbeat for a long time, the task board can reclaim it

## 11. A few real limitations of the current implementation

If you want to use the Team for production-grade long-running tasks, you must know these limitations.

### 11.1 The planner and synthesizer are still prompt-driven Agents

They are not special system components; in essence they are still roles wrapped around an ordinary `Agent.newSession().run(...)`.

The upside is simple reuse; the cost is that their quality depends heavily on:

- Model capability
- Prompt quality
- Output parsability

### 11.2 Member memory does not accumulate naturally

By default each task is a fresh session.

So if you want:

- The same member to remember across tasks
- Members to form a long-term local context

The current default Team implementation does not hand you this capability directly.

### 11.3 Recovery is not "suspend then continue execution"

What is recovered is the state view, not the live runtime.

If you need true resumable execution, you need to supplement at a higher layer with:

- An external scheduler
- Idempotent task design
- A stronger state machine / checkpoint mechanism

### 11.4 Hook failure does not interrupt the main flow

:::note
All `AgentTeamHook` invocations swallow exceptions.

This ensures that observation and audit logic never breaks the main chain, but it also means:

- A hook's own failure is silent by default
- If you rely on hooks for critical governance, you must record failures inside the hook yourself
:::

## 12. Recommended reading order

If you want to dig in along the source, the suggested order is:

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java`
3. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamPlanner.java`
4. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamPlanParser.java`
5. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java`
6. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`
7. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamSynthesizer.java`

## 13. Recommended verification cases

It is recommended to read these tests alongside; they essentially form the current behavior contract:

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/FileAgentTeamStateStoreTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/DoubaoAgentTeamBestPracticeTest.java`

## 14. Further reading

If you finish this page and want to go deeper, continue with:

1. [Agent Teams API Reference](/docs/agent/orchestration/agent-teams-api-reference)
2. [SubAgent and Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy)
3. [Workflow StateGraph](/docs/agent/runtimes/workflow-stategraph)
4. [Trace and Observability](/docs/agent/observability/trace-observability)
