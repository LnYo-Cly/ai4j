---
sidebar_position: 5
title: "Prompt Assembly and Context Sources"
description: "Breaks down the 5 categories of prompt sources fed into the model (base system prompt, workspace prompt, instructions, session memory, current turn) plus the standalone tool schemas, so you know what order to check when tracking down behavior drift."
tags: [concept]
---

# Prompt Assembly and Context Sources

In the Coding Agent, a prompt is never as simple as "user input + system prompt".
Looking at the current implementation, a request actually sent to the model layers together at least 5 categories of sources:

- base system prompt
- workspace prompt
- instructions
- session memory / compact / checkpoint
- the current user turn or runtime continuation

Plus one piece that is often misfiled into "prompt" discussions but is actually an independent structure:

- tool schemas

The point of this page is to pull these sources apart, rather than waving it away with "context is complicated".

## 1. Where the real assembly starts

The most critical entry point is not a CLI command, but:

- `CodingAgentBuilder.build()`

At this stage, the builder decides whether to run:

```java
CodingContextPromptAssembler.mergeSystemPrompt(systemPrompt, resolvedWorkspaceContext)
```

This is governed by default:

- `CodingAgentOptions.prependWorkspaceInstructions = true`

This tells us the first key fact about current prompt assembly:

**The workspace prompt is merged into the system prompt during agent build.**

It is not a chunk of text temporarily assembled each turn, and it is not injected extra when a slash command fires.

## 2. `systemPrompt` and `workspace prompt` are not the same layer

In the current implementation, both layers eventually land in the system prompt region, but they come from completely different sources.

### `systemPrompt`

Sources are typically:

- `--system` from CLI/TUI
- `.systemPrompt(...)` from the Java API

It fits:

- long-lived, stable role constraints
- output style requirements
- team-wide conventions

### `workspace prompt`

The source is:

- `CodingContextPromptAssembler.buildWorkspacePrompt(workspaceContext)`

It fits:

- the current workspace root
- workspace description
- the built-in tools list
- tool call rules
- shell usage guidance
- access restrictions outside the workspace
- a summary of discovered skills

So semantically:

- `systemPrompt` answers "how you should work"
- `workspace prompt` answers "what execution environment you are in right now"

## 3. Why `WorkspaceContext` directly drives prompt content

`WorkspaceContext` currently provides at least these key fields:

- `rootPath`
- `description`
- `allowOutsideWorkspace`
- `skillDirectories`
- `allowedReadRoots`
- `availableSkills`

And `CodingContextPromptAssembler` explicitly writes several of them into the prompt.

This means the Coding Agent's prompt is not pure linguistic guidance, but an environment declaration carrying host state.
What the model sees is not the abstract "you can edit files", but something more concrete:

- what the workspace root directory is
- whether dependencies outside the workspace are allowed by default
- which skills are available

This is one of the essential differences between the coding scenario and an ordinary chat scenario.

## 4. Why skills affect both the prompt and the read path

`CodingSkillDiscovery.enrich(...)` currently does two things:

- discovers skills
- writes `allowedReadRoots` and `availableSkills` back into `WorkspaceContext`

These two pieces then feed two separate layers:

- `availableSkills` enters the workspace prompt
- `allowedReadRoots` affects `resolveReadablePath(...)`

So in the Coding Agent, a skill is not merely "a capability list for the model to look at". It is simultaneously:

- a prompt capability surface
- part of the read-only root allowlist

This also explains why the model must first `read_file` to read `SKILL.md`, yet does not need to dump every skill's body into context up front.

## 5. Where the boundary between `instructions` and `systemPrompt` lies

`instructions` currently comes from:

- `--instructions` from CLI/TUI
- `.instructions(...)` from the Java API

It is not auto-generated from the environment the way workspace prompt is; it is a session-level policy input.

The cleanest split is:

- `systemPrompt`: long-lived, stable global rules
- `workspace prompt`: declaration of the current execution environment
- `instructions`: additional policy for the current session
- user input: what to do this turn

If you interleave these layers, tracking down behavior drift later becomes very hard to pin down.

## 6. Why tool schemas do not belong to "prompt text"

This is a point where a lot of documentation goes sideways.

Tools of course affect model behavior, but the way they enter the model is not "yet another paragraph of explanatory text".

Today, built-in tools, custom tools, subagent tools, and MCP tools all end up in:

- `toolRegistry`
- `toolExecutor`

That is, they are a structured tool surface, not part of the prompt text.

The workspace prompt contains the line "Available built-in tools: bash, read_file, write_file, apply_patch, glob, grep, edit, update_agents_md."
That is just an environment hint, not the schema itself.

So if the model cannot invoke a certain tool, the first thing to check is not the prompt, but:

- whether that tool actually made it into the registry

## 7. Where the current user input layer comes from

The current user turn does not come from a single text box.

It may come from:

- one-shot `--prompt`
- interactive CLI/TUI input
- ACP `session/prompt`
- the rendered result of `/cmd <name> [args]`
- runtime continuation

In other words, "current user input" is itself a multi-source surface.

## 8. Why `/cmd` is essentially a prompt macro, not a system-layer extension

Custom commands currently load templates from:

- `~/.ai4j/commands`
- `<workspace>/.ai4j/commands`

Then render variables such as:

- `$ARGUMENTS`
- `$WORKSPACE`
- `$SESSION_ID`
- `$ROOT_SESSION_ID`
- `$PARENT_SESSION_ID`

Once rendered, it is sent into the session as a new user turn.
So the essence of `/cmd` is:

- a user input template

Rather than:

- a new system prompt layer
- a new instructions layer

Once this boundary is clear, a lot of "why didn't /cmd override system behavior" questions dissolve on their own.

## 9. Why ACP's `session/prompt` is not a "system layer" either

ACP passes in a structured prompt array, but when the current Coding Agent consumes it, it still turns it into the turn's input and hands it to the session runtime.

That is, ACP's structuring is only:

- transport-layer structuring

Not:

- inherently multimodal, multi-segment structuring at the model-consumption layer

This needs to be stated plainly, otherwise it is easy to assume ACP inherently carries "higher-order" prompt orchestration semantics than CLI. That is not the case today.

## 10. Which layer continuation prompts come from

When the Coding Agent's outer loop needs to continue, it is:

- `CodingAgentLoopController`

that produces continuation semantics and lets the session run another turn.

These continuations are not new user messages; they are part of the runtime's internal advancement mechanism.
So when multiple auto-advancing turns show up in a session, what you see is:

- the session keeps running

But underneath, that does not mean there are many real user inputs.

This matters for understanding:

- why auto-continue happens
- why the history does not contain the same number of user turns

## 11. Why compact / checkpoint changes subsequent prompt behavior

Once a session grows long, `CodingSession` will perform:

- tool-result micro compact
- session compact
- checkpoint reuse or rebuild

This means old context no longer always exists in its original full-conversation form.
What later turns show the model may already be:

- summary
- checkpoint goal
- constraints
- blocked items
- next steps
- critical context
- process snapshots

So the same current input behaves differently in:

- a fresh session
- an already-compacted old session

This is normal under the current architecture, not necessarily model variance.

## 12. Why runtime rebuild affects the prompt

Because the workspace prompt is injected during the build phase, any runtime rebuild can cause the following to re-influence the final system prompt:

- workspace description
- discovered skills
- allowOutsideWorkspace
- experimental surface

This is also why behavior changes immediately after some config switches.
It is not that "the model suddenly changed personality"; the prompt assembly environment itself changed.

## 13. What order to check when tracking down behavior drift

If the model seems to behave wrongly, work through the following order:

1. Is `systemPrompt` too strong or self-conflicting?
2. Does the workspace description mischaracterize the project?
3. Did `instructions` override the original intent?
4. Is the current turn actually from a `/cmd` template?
5. Has the skill been discovered, and is `SKILL.md` actually readable?
6. Has the current session gone through compact / checkpoint?
7. Has the visible tool surface undergone a runtime rebuild?

Checking in this order is usually far more effective than tweaking one user prompt over and over.

## 14. The 5 most common pitfalls

### 14.1 Treating workspace prompt as per-turn temporarily assembled text

Today it is primarily merged into the system prompt at the builder stage.

### 14.2 Counting tool schemas as "prompt text"

Tools are a structured registry surface, not plain explanatory text.

### 14.3 Treating `/cmd` as a system-layer mechanism

It is essentially still a user input template.

### 14.4 Ignoring that skills affect both the prompt and the read path

Skills are not just for display.

### 14.5 Ignoring that context has already been summarized after compact

Behavior changes in an old session often come from a change in the form of the context, not from model randomness.

## 15. The conclusion worth remembering from this page

AI4J's current Coding Agent prompt assembly is not single-layer string concatenation, but a multi-source environment composition:

- `systemPrompt` sets the long-lived rules
- workspace prompt sets the current execution environment
- `instructions` sets session policy
- the current user turn sets this turn's task
- session memory / compact / checkpoint sets history continuation
- tool schemas, as an independent structure, determine callable capabilities

Once these source layers are cleanly separated—whether you are tuning behavior, tracking down drift, or extending the host—you can locate the problem faster.

## 16. Further reading

1. [Runtime Architecture](/docs/products/coding-agent/runtime-architecture)
2. [Sessions, Streaming, and Processes](/docs/products/coding-agent/session-runtime)
3. [Tools and Approval Mechanism](/docs/products/coding-agent/tools-and-approvals)
4. [Skill Usage and Organization](/docs/products/coding-agent/skills)
5. [Compact and Checkpoint Mechanism](/docs/products/coding-agent/compact-and-checkpoint)
