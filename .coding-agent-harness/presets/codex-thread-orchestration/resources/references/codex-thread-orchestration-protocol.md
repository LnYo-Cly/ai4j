# Codex Thread Orchestration Protocol

## Purpose

This reference describes a Codex-only method for coordinating complex Harness tasks through background threads. It is designed for human visibility: the coordinator can keep separate workstreams in separate Codex threads while preserving the Harness task package as the durable record.

## Non-Goals

- This preset does not automate Codex App thread tools from npm Harness.
- This preset does not define repository-specific pull request, merge, release, or branch cleanup policy.
- This preset does not replace task package evidence, review, verification, or closeout records.

## Coordinator Flow

1. Read the task plan, execution strategy, and preset required references.
2. Decide whether the task has independent workstreams worth splitting into threads.
3. For each thread, define title, role, mode, scope, forbidden scope, stop conditions, and handoff format.
4. Create a background thread only after the prompt packet is complete.
5. Rename the thread with a stable title that includes the task id and workstream.
6. Send the dispatch prompt.
7. Read thread status before integrating work.
8. Record the thread id, title, summary, handoff, verification, and residual risk in the task package.
9. Archive or unpin threads after their useful output is captured.

## Suggested Thread Operations

When available in Codex, use these operations:

| Operation | Purpose |
| --- | --- |
| Create thread | Start a bounded worker, reviewer, or research workstream. |
| Send message to thread | Provide follow-up context, unblock a worker, or request a corrected handoff. |
| Read thread | Inspect recent status and output before integration. |
| Set thread title | Make ownership and workstream visible to the human operator. |
| Pin thread | Keep active coordination surfaces visible. |
| Archive thread | Remove completed workstreams after durable evidence capture. |

## Title Convention

Use stable, scan-friendly titles:

```text
<task-id> coordinator
<task-id> worker: <workstream>
<task-id> reviewer: <topic>
<task-id> research: <topic>
```

## Prompt Packet

Each thread prompt should include:

- task id and title,
- role,
- required reads,
- exact scope,
- forbidden scope,
- read-only or writable mode,
- working directory or worktree when applicable,
- verification expectations,
- stop conditions,
- handoff format.

Do not rely on hidden context from the coordinator thread. A worker should be able to understand the assignment from the prompt and referenced task files.

## Evidence Rules

Thread transcripts are not enough. The coordinator must record durable evidence in the task package:

- thread registry rows,
- dispatch prompt summaries,
- handoff summaries,
- verification results,
- accepted findings,
- skipped checks,
- residual risks,
- fallback reason if thread tools were unavailable.

## Fallback

If background thread tools are unavailable or unsuitable, record:

```text
Thread fallback: thread-tools-unavailable; using normal coordinator workflow.
```

The task can still proceed. The important part is that the task package honestly records the coordination model that was actually used.
