---
sidebar_position: 4
title: "Sessions, streaming, and processes"
description: "Explains how the Coding Agent session runtime turns a local coding task into a sustainable, interruptible, and resumable working session: CodingSession, the outer loop, the event ledger, the process plane, and the headless event stream."
tags: [concept]
---

# Sessions, streaming, and processes

If this page only said "supports save / resume / fork / process", the information density would be too low.
Read from the source, the Coding Agent session runtime actually solves a more specific problem:

**How to turn a local coding task into a sustainable, interruptible, resumable, continuously advancing working session.**

In AI4J this is not done by a single class but by several layers cooperating:

- `CodingSession`
- `CodingAgentLoopController`
- `DefaultCodingSessionManager`
- `FileSessionEventStore`
- `HeadlessCodingSessionRuntime`
- `SessionProcessRegistry`

## 1. `CodingSession` is not an ordinary chat session

Seen from the Java API, the most central object is still:

- `ai4j-coding/.../CodingSession.java`

But the responsibilities it carries are clearly heavier than an ordinary `AgentSession`.

Beyond `run(...) / runStream(...)`, it directly exposes:

- `snapshot()`
- `exportState()`
- `restore(...)`
- `compact()`
- `listProcesses()`
- `processStatus(...)`
- `processLogs(...)`
- `writeProcess(...)`
- `stopProcess(...)`
- `delegate(...)`

This shows that a Coding Agent session is not a "model context container" but a working-session object that carries:

- memory
- process state
- compact state
- loop decisions
- delegation entry points

## 2. A single `run(...)` does not necessarily run only one model call

`CodingSession.run(...)` ultimately does not call the underlying `AgentSession.run()` directly. Instead it enters:

- `CodingAgentLoopController`

This outer loop:

1. Runs one agent turn
2. Aggregates tool calls / tool results
3. Checks whether an auto-continue is needed
4. Decides a stop reason when problems arise
5. Generates a continuation prompt and keeps going if necessary

So in Coding Agent, "one user request" and "one model turn" are not one-to-one.

This is also why the coding scenario needs its own session runtime, rather than just reusing the generic Q&A loop.

## 3. By what rules does the outer loop actually continue or stop

`CodingAgentLoopController` currently inspects several categories of signals explicitly:

- approval rejected
- tool error
- explicit question
- completion-like output
- continuation-like output
- max auto follow-ups
- max total turns

The corresponding stop reasons are modeled explicitly:

- `COMPLETED`
- `NEEDS_USER_INPUT`
- `BLOCKED_BY_APPROVAL`
- `BLOCKED_BY_TOOL_ERROR`
- `MAX_AUTO_FOLLOWUPS_REACHED`
- `MAX_TOTAL_TURNS_REACHED`
- `INTERRUPTED`

This matters, because in Coding Agent "done" does not have a single semantics.
A task may stop because:

- it is genuinely finished
- it needs you to answer a question
- it is blocked by approval
- a tool errored
- the auto-advance budget ran out

To build a good experience, the host must understand these stop reasons, not just read the last line of output text.

## 4. Why the continuation prompt is hidden

In the current implementation, the outer loop's subsequent advancement does not write "continue with the next step" as a new user message. Instead it keeps the continuation prompt as a hidden instruction and runs the next turn with it.

This has two consequences:

- Session history is not polluted with large amounts of "system self-continuation"
- But the runtime can still keep advancing the work internally

So the session runtime's job is not only to persist chat history, but also to maintain the semantic layer of "which turns were user-initiated and which were runtime-continued".

## 5. Why `snapshot()` and `exportState()` are separate

These two interfaces have similar names but different semantics.

`snapshot()` produces:

- `CodingSessionSnapshot`

It leans toward display and diagnostics, and includes:

- memory item count
- process count
- active/restored process count
- estimated context tokens
- last compact mode / tokens before / tokens after
- auto compact failure count
- list of process info

`exportState()` produces:

- `CodingSessionState`

It leans toward being a recovery object, and includes:

- `MemorySnapshot`
- `CodingSessionCheckpoint`
- `CodingSessionCompactResult`
- process snapshots
- auto-compact circuit breaker state

So the most robust understanding is:

- `snapshot()` is for humans to read
- `exportState()` is for the system to restore

## 6. Who is actually responsible for save / resume / fork

What really manages the session lifecycle is not `CodingSession` itself, but:

- `DefaultCodingSessionManager`

It is responsible for:

- `create(...)`
- `resume(...)`
- `fork(...)`
- `save(...)`
- `load(...)`
- `list()`
- `appendEvent(...)`
- `listEvents(...)`

This shows AI4J splits "session execution" and "session persistence" into two layers:

- `CodingSession` runs
- `CodingSessionManager` handles storage, retrieval, forking, and bookkeeping

This layering is important, because the CLI, TUI, and ACP all share this lifecycle semantics.

## 7. `resume` and `fork` are not just copying an object

When resuming and forking, `DefaultCodingSessionManager` currently also does several key things:

- Validates whether the current workspace matches the stored session
- Restores `CodingSessionState`
- Preserves `rootSessionId / parentSessionId`
- Appends `SESSION_RESUMED / SESSION_FORKED` events

The workspace validation in particular is critical.
It shows that a session is not pure prompt history, but is bound to a specific workspace.

Otherwise, taking a session from one repository and continuing to run it in another would distort a lot of file semantics.

## 8. The event ledger is not an auxiliary feature, it is part of the session runtime

`FileSessionEventStore` writes each session's events to disk under:

- `<sessionId>.jsonl`

This layer of design is valuable because it gives the session an event perspective independent of memory.

From `HeadlessCodingSessionRuntime` and `DefaultCodingSessionManager`, the events currently written include at least:

- `SESSION_CREATED`
- `SESSION_RESUMED`
- `SESSION_FORKED`
- `SESSION_SAVED`
- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`
- `COMPACT`
- `ERROR`

This shows the session runtime records not a "final summary" but a process ledger.

## 9. Why the headless / ACP path needs a separate runtime

The CLI / TUI can consume streaming events directly through the terminal, but ACP / headless cannot.
So AI4J provides a separate:

- `HeadlessCodingSessionRuntime`

It breaks one `runPrompt(...)` into:

1. Generate a `turnId`
2. Record a `USER_MESSAGE`
3. Consume agent events as a stream
4. Transcribe them into tool / assistant / reasoning / error / loop decision / compact events
5. Persist the session automatically when necessary
6. Return a `PromptResult`

So the value of the headless runtime is not "no UI", but turning the coding session into a structured event stream.

## 10. Why streaming output here cannot be understood only as text deltas

What `HeadlessAgentListener` actually handles is not just assistant text. It also includes:

- reasoning
- tool call
- tool result
- handoff event
- team event
- team message event
- final output
- error

This means the coding session's streaming protocol is in essence not a single text stream, but a mixed event stream.
If the host understands it only as "token output", it will miss a lot of key information:

- when a certain tool call happens
- why a certain auto-continue triggered
- why a certain turn was blocked

## 11. Why background processes belong to the session runtime, not to private state of the bash tool

Behind `BashToolExecutor` is:

- `SessionProcessRegistry`

And `CodingSession` directly exposes process management methods.

This shows that long-running processes are not a transient side effect of a single tool call, but part of the current session state.
Precisely because of this:

- `snapshot()` counts processes
- `exportState()` exports process snapshots
- `restore(...)` restores process registry snapshots

So in the coding scenario, the process plane is a session component as important as memory.

## 12. What auto-compact means to the session runtime

After each turn, `CodingSession` attempts:

- tool-result micro compact
- session compact

And records:

- the most recent auto compact result
- the most recent auto compact error
- the consecutive failure count
- whether the circuit breaker is open

These states are then:

- exposed to the host by `snapshot()`
- transcribed into `COMPACT / ERROR` events by `HeadlessCodingSessionRuntime`
- persisted by `exportState()`

This shows compact is not an isolated maintenance task, but the core stability mechanism of the session runtime.

## 13. The five easiest pitfalls to fall into

### 13.1 Treating one user prompt as one model call

In Coding Agent, one user prompt may correspond to multiple outer-loop turns.

### 13.2 Persisting only memory, not session state

This loses process snapshots, compact diagnostics, and breaker state.

### 13.3 Using `snapshot()` and `exportState()` wrong

The former suits display; only the latter suits recovery.

### 13.4 Ignoring workspace binding

Current resume and fork both carry workspace semantics. A session is not free-floating plain-text history that drifts across repositories at will.

### 13.5 Treating streaming output as only assistant text

In the coding runtime, streaming events are far richer than text deltas.

## 14. The conclusion most worth remembering on this page

The current AI4J session runtime is not "chat session + a save button". It is a working-session system:

- Uses `CodingSession` to carry memory, processes, compact, and delegation
- Uses `CodingAgentLoopController` to handle multi-turn advancement and stop reasons
- Uses `DefaultCodingSessionManager` to manage lifecycle and persistence
- Uses `FileSessionEventStore` to retain the event ledger
- Uses `HeadlessCodingSessionRuntime` to transcribe the whole process into a structured event stream that the host can consume

This is precisely the foundation that lets Coding Agent handle long tasks, recoverable tasks, and local delivery tasks.

## 15. Further reading

1. [Compact and checkpoint mechanism](/docs/coding-agent/compact-and-checkpoint)
2. [Tools and approval mechanism](/docs/coding-agent/tools-and-approvals)
3. [Runtime architecture](/docs/coding-agent/runtime-architecture)
