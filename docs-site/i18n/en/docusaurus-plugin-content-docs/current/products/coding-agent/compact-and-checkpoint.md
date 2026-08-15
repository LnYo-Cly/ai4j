---
sidebar_position: 6
title: "Compact and Checkpoint Mechanism"
description: "Explains the compact/checkpoint pipeline of ai4j-coding: the responsibility split and tuning entry points for tool-result microcompact, checkpoint summary, aggressive compact, fallback, and the auto-compact circuit breaker."
tags: [concept]
---

# Compact and Checkpoint Mechanism

The long-session capability of `Coding Agent` is not simply about piling historical messages into memory forever.

The real problems to solve are three engineering challenges:

- The context window is finite, but coding tasks tend to be multi-turn, cross-file, and cross-process.
- Tool output can be very large, especially `bash`, test logs, build logs, and file diffs.
- If compact cannot continue to work stably, the outer loop easily loses semantics, repeats work, or even spins idle.

This page specifically describes the responsibility split, working principles, public API, and tuning entry points of the current compact / checkpoint pipeline in `ai4j-coding`.

---

## 1. Design Boundaries

This mechanism is deliberately not pushed back into `BaseAgentRuntime` of `ai4j-agent`.

The current boundaries are:

- `BaseAgentRuntime`: keeps the underlying single-turn tool-loop semantics, without changing the compatibility behavior of the traditional Agent.
- `CodingSession`: maintains the memory, checkpoint, process snapshot, and compact state of the coding session.
- `CodingAgentLoopController`: responsible for the task-level outer loop and post-compact continuation.
- `CodingSessionCompactor`: responsible for checkpoint compact.
- `CodingToolResultMicroCompactor`: responsible for lightweight compaction of large tool results.

The reasons for this are:

- Regular `IChatService` / `AiService` / `Agent` still keep their original call semantics.
- The Coding Agent can evolve the outer loop, resume, fork, checkpoint, and process management independently.
- CLI / TUI / ACP can share the same session compact mechanism instead of each reimplementing it.

---

## 2. Core Entry Points

| Class | Role | Why it matters |
| --- | --- | --- |
| `CodingSession` | Main session object; provides `run`, `compact`, `snapshot`, `exportState`, `restore` | The first entry point of the public API |
| `CodingSessionCompactor` | Main flow of checkpoint compact | Handles slicing, summary, fallback, and strategy computation |
| `CodingToolResultMicroCompactor` | Micro-compaction of stale, large tool results | Prioritizes reducing context noise to avoid unnecessary checkpoint summaries |
| `CodingSessionCheckpoint` | Structured checkpoint data model | The "task summary" actually left for subsequent turns after compact |
| `CodingSessionCompactResult` | Diagnostic result of a single compact | Used by CLI `/compacts`, ACP/headless events, and status pages |
| `CodingSessionSnapshot` | Lightweight session snapshot | For status display and UI |
| `CodingSessionState` | Full persistent state | For save / resume / fork |
| `CodingContinuationPrompt` | Hidden continuation prompt after compact | Used to re-anchor the checkpoint and reduce semantic drift |
| `CodingAgentLoopController` | Outer loop controller | Decides when to continue, when to stop, and when to re-inject the compact result |

The source entry points are mainly at:

- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/CodingSession.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/compact/CodingSessionCompactor.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/compact/CodingToolResultMicroCompactor.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingAgentLoopController.java`
- `ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding/loop/CodingContinuationPrompt.java`

---

## 3. How to Use It Externally

### 3.1 Java API

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("gpt-5-mini")
        .workspaceContext(workspaceContext)
        .codingOptions(CodingAgentOptions.builder()
                .autoCompactEnabled(true)
                .build())
        .build();

try (CodingSession session = agent.newSession()) {
    session.run("Read the repository and prepare a refactor plan.");

    CodingSessionCompactResult compactResult = session.compact();
    CodingSessionSnapshot snapshot = session.snapshot();
    CodingSessionState state = session.exportState();

    // Can be restored later
    try (CodingSession restored = agent.newSession(state)) {
        restored.run("Continue the same task.");
    }
}
```

The compact-related public capabilities on `CodingSession` include:

- `compact()` / `compact(String summary)`: trigger a compact manually.
- `snapshot()`: get a lightweight session snapshot, suitable for `/status`, status panels, and debug output.
- `exportState()`: export the full persistent state.
- `restore(CodingSessionState)`: restore the full state.
- `drainPendingAutoCompactResults()`: drain the auto-compact results of the current turn for the host event stream.
- `getLastAutoCompactResult()` / `getLatestCompactResult()`: read the most recent compact diagnostics.

### 3.2 CLI / ACP

The main command entry points are:

- `/compact`
- `/compacts`
- `/checkpoint`
- `/session`
- `/status`

Among them:

- `/compact` is the active compaction.
- `/compacts` shows compact history and diagnostic fields.
- `/checkpoint` shows the structured checkpoint itself.
- `/session` / `/status` focus more on the current session state and the most recent compact summary.

---

## 4. What Is Actually Stored in a Checkpoint

`CodingSessionCheckpoint` is not "a random snippet of summary text", but a fixed structure:

- `goal`
- `constraints`
- `doneItems`
- `inProgressItems`
- `blockedItems`
- `keyDecisions`
- `nextSteps`
- `criticalContext`
- `processSnapshots`
- `generatedAtEpochMs`
- `sourceItemCount`
- `splitTurn`

This means what compact leaves behind is not loose prose, but a summary closer to a task state machine.

`CodingSessionCheckpointFormatter` currently supports two sources:

- Structured JSON; the summary model is asked to return against this schema first.
- An already-rendered markdown checkpoint, which can also be parsed back into a structured object on recovery.

So the checkpoint is suitable for both:

- Letting the model continue working.
- Human-readable display in CLI/TUI/ACP.

---

## 5. How the Compact Pipeline Works

### 5.1 Trigger Points

There are two types of compact entry points:

- Manual: `session.compact()` or CLI `/compact`.
- Automatic: after each `runSingleTurn()`, via `CodingSession.maybeAutoCompactAfterTurn()`.

Auto-compact only takes effect when `CodingAgentOptions.autoCompactEnabled=true`.

### 5.2 First layer: tool-result microcompact

This is the lightest layer, targeting "old, huge, but not worth fully retaining" `function_call_output`.

`CodingToolResultMicroCompactor` will:

- Estimate the current context tokens first.
- Find all `function_call_output`.
- Keep the most recent few tool results untouched.
- Only compact older tool results beyond the threshold.
- Replace the original large output with a short summary with a preview.

Characteristics of this layer:

- Does not depend on the summary model.
- Does not change the checkpoint.
- Suitable for quickly reclaiming log-style context space.
- On success, `strategy = tool-result-micro`.

### 5.3 Second layer: checkpoint compact preparation

If context still exceeds the budget after microcompact, it enters `CodingSessionCompactor.prepare(...)`.

Here memory is sliced into three segments:

- `itemsToSummarize`: older history to be summarized into the checkpoint.
- `turnPrefixItems`: if the split point lands inside an unfinished turn, the earlier prefix is pulled out separately.
- `keptItems`: messages recently retained in memory, not directly folded into the summary.

The core purpose of this step is:

- Preserve the most recent context verbatim as much as possible.
- Avoid hard-cutting "the in-flight current turn".
- Mark `splitTurn=true` when necessary, for subsequent continuation re-anchoring.

### 5.4 Third layer: generate or update the checkpoint

`CodingSessionCompactor.buildCheckpoint(...)` distinguishes two cases:

- No existing checkpoint: runs the initial summary.
- Existing checkpoint: runs a checkpoint + delta update.

That is:

- `checkpoint`
- `checkpoint-delta`

The old checkpoint is not discarded directly, but treated as "existing session memory" and keeps accumulating.

### 5.5 Fourth layer: aggressive compact

If, after a normal checkpoint compact, `keptItems + summary` is still too large, it enters the aggressive path.

At this point it will:

- Re-summarize a larger context range more aggressively.
- Clear or further compact `keptItems`.
- Return:
  - `aggressive-checkpoint`
  - `aggressive-checkpoint-delta`

This layer is not the default path, but a secondary safety net for "still cannot compress after a normal compact".

### 5.6 Fifth layer: restore memory and publish the result

After compact completes, `CodingSession` restores memory to:

- `keptItems`
- `rendered checkpoint summary`

Then updates:

- `checkpoint`
- `latestCompactResult`
- auto-compact-related state

The compact information seen by CLI, TUI, and ACP all essentially comes from the `CodingSessionCompactResult` generated at this step.

---

## 6. How to Read the Compact Result Fields

`CodingSessionCompactResult` currently contains these key fields:

| Field | Meaning |
| --- | --- |
| `beforeItemCount` / `afterItemCount` | Memory item counts before and after compact |
| `summary` | Human-readable summary or checkpoint text for display |
| `automatic` | Whether it came from auto-compact |
| `splitTurn` | Whether the split landed on an unfinished turn |
| `estimatedTokensBefore` / `estimatedTokensAfter` | Estimated tokens before and after compact |
| `strategy` | Which path this compact took |
| `compactedToolResultCount` | How many tool results microcompact compacted |
| `deltaItemCount` | Number of delta items merged into the checkpoint this time |
| `checkpointReused` | Whether the old checkpoint was reused |
| `fallbackSummary` | Whether a fallback summary was used instead of a successful model summary |
| `checkpoint` | The structured checkpoint itself |

### 6.1 Semantics of `strategy`

The following values can currently appear:

| strategy | Description |
| --- | --- |
| `tool-result-micro` | Only lightweight compaction of old tool results was done |
| `checkpoint` | Created a new checkpoint |
| `checkpoint-delta` | Reused the old checkpoint and merged the new delta |
| `aggressive-checkpoint` | Normal checkpoint was still too large, switched to aggressive compaction |
| `aggressive-checkpoint-delta` | Aggressive delta compact on top of an existing checkpoint |

### 6.2 Semantics of `fallbackSummary`

`fallbackSummary=true` does not mean compact failed.

It indicates:

- The final compact still completed successfully.
- But the summary was not generated normally by the summary model; it went through local fallback logic.

This is critical in long sessions, because it allows the session to keep working when the summary model is unavailable, instead of the entire turn erroring out directly.

---

## 7. Failure and Fallback Paths

### 7.1 prompt-too-long retry

If "the summary request itself" of compact exceeds the model context, it does not fail immediately.

`CodingSessionCompactor.summarize(...)` will:

- Trim the oldest portion of the segments to be summarized.
- Regenerate the summary request.
- Retry up to the limit.

If it ultimately still does not work, it enters fallback.

### 7.2 Local fallback summary

If no summary model is available, or the summary call throws, a local fallback checkpoint is attempted.

This fallback tries to preserve:

- The latest user goal.
- A few recent key pieces of information.
- Important signals such as tool errors / approval blocks.

### 7.3 session-memory fallback

If an old checkpoint already exists, the fallback does not simply discard the old summary and start over.

Instead, it will:

- Reuse the existing checkpoint.
- Merge the recent delta context into it.
- Mark `fallbackSummary=true`.
- Let subsequent compact / continuation continue in the "checkpoint + recent delta" form.

This is also the actual landing point of "session-memory-first" in the current implementation.

### 7.4 auto-compact circuit breaker

If auto-compact fails consecutively up to the threshold, `CodingSession` opens the breaker:

- Stops further auto-compact.
- Prevents the outer loop from spinning due to repeated compact failures.
- Retains the error in the host state.

A successful manual `compact()` resets this failure counter and breaker state.

---

## 8. How Compact Connects to the Outer Loop

The hard part of compact is not "generating a summary", but "whether work can continue stably after the summary is generated".

Currently, when `CodingAgentLoopController` decides on auto-continue, it hands the compact result to `CodingContinuationPrompt`.

The hidden continuation prompt re-injects this information:

- compact strategy
- checkpoint goal
- constraints
- blocked items
- next steps
- critical context
- in-progress items
- process snapshots

If the checkpoint comes from a split-turn compact, it also explicitly reminds the model that:

- The current summary only covers the first half of the turn.
- The recent kept messages are the latest turn tail.

The point of this re-anchoring is to:

- Reduce semantic drift at the compact boundary.
- Prevent the model from treating the post-compact continuation as a new task.
- Avoid "having just summarized, yet asking the user for repeated information".

---

## 9. Which Configurations to Tune

The configurations most relevant to compact / outer loop are in `CodingAgentOptions`:

| Configuration | Role |
| --- | --- |
| `autoCompactEnabled` | Whether to enable auto-compact |
| `compactContextWindowTokens` | The context window budget allowed for use |
| `compactReserveTokens` | Token space reserved for model output |
| `compactKeepRecentTokens` | Budget for the most recent context to preserve verbatim during compact |
| `compactSummaryMaxOutputTokens` | Maximum output of the summary model when generating a checkpoint |
| `toolResultMicroCompactEnabled` | Whether to enable microcompact |
| `toolResultMicroCompactKeepRecent` | How many of the most recent tool results are not compacted |
| `toolResultMicroCompactMaxTokens` | How many tokens a single tool result must exceed to be compacted |
| `autoCompactMaxConsecutiveFailures` | After how many consecutive auto-compact failures the breaker opens |
| `autoContinueEnabled` | Whether the outer loop is allowed to auto-continue |
| `maxAutoFollowUps` | Maximum number of auto-continue follow-ups |
| `maxTotalTurns` | Maximum turns allowed for a single user task |
| `continueAfterCompact` | Whether continuing the current task is allowed after compact |
| `stopOnApprovalBlock` | Whether to stop on approval rejection |
| `stopOnExplicitQuestion` | Whether to stop when the model explicitly asks the user a question |

A common tuning principle:

- Start with the defaults.
- If log/test output is too large, tune microcompact first.
- If the model often loses the task after compact, check `compactKeepRecentTokens` and continuation re-anchoring first.
- If the provider tends to throw length errors during the summary phase, tune `compactSummaryMaxOutputTokens` and the context budget.

---

## 10. Difference Between `snapshot()` and `exportState()`

These two interfaces have similar names but different purposes.

### 10.1 `CodingSessionSnapshot`

Leans toward a "status display object", used for:

- `/status`
- `/session`
- TUI status panels
- Lightweight status viewing in headless/ACP

It retains:

- Current checkpoint goal
- Most recent compact mode and summary
- Token estimate
- Breaker state
- Process overview

### 10.2 `CodingSessionState`

Leans toward a "full recovery object", used for:

- save / resume
- fork
- Cross-host persistence

It retains:

- Raw memory snapshot
- process snapshots
- checkpoint
- latestCompactResult
- auto-compact failure / breaker state

In one sentence:

- `snapshot()` is for humans to read.
- `exportState()` is for the runtime to recover.

---

## 11. What the Host Side Sees

### 11.1 CLI / TUI

`CodingCliSessionRunner` turns the compact result into:

- Readable diagnostic lines for `/compacts`.
- Structured summary for `/checkpoint`.
- Recent compact state within `/session` / `/status`.

### 11.2 ACP / Headless

`HeadlessCodingSessionRuntime` sends the compact result as a structured event payload to the host.

This means the host does not need to re-understand the compact logic; it only needs to consume these fields:

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`
- `fallbackSummary`

---

## 12. Further Reading

1. [Session, Streaming, and Process](/docs/products/coding-agent/session-runtime)
2. [Coding Agent Architecture](/docs/products/coding-agent/architecture)
3. [Configuration System](/docs/products/coding-agent/configuration)
4. [Command Reference](/docs/products/coding-agent/command-reference)
