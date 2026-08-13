---
sidebar_position: 3
title: "CLI / TUI Usage Guide"
description: "Explains how the code and tui entry points share the same coding runtime, where they diverge (JLINE/legacy/TUI runtime), and the real boundaries of host-layer behavior such as slash commands, /stream, and the session store."
tags: [concept]
---

# CLI / TUI Usage Guide

A page that only covers launch commands and keyboard shortcuts does not carry enough information.
Looking at the current implementation, the difference between `code` and `tui` is not merely "one is fullscreen and the other is not". Rather:

- They share the same coding runtime, session manager, MCP runtime, and approval semantics
- But use different input, rendering, and interaction paths at the host layer

So the most reliable way to understand `CLI / TUI` is to first look at how they share the runtime, and then see where they diverge.

## 1. Both entry points actually start from `CodeCommand`

The current main CLI entry point is:

- `ai4j-cli/.../command/CodeCommand.java`

It always does a few fixed things first:

1. Parse `CodeCommandOptions`
2. Create `CodingSessionManager`
3. Decide the interactive backend
4. Call `agentFactory.prepare(...)`
5. Select different runners based on the run mode

In other words, `code` / `tui` are not two fully independent programs at the outermost layer; they are two host shapes under the same command system.

## 2. What exactly they share at the bottom

From `CodeCommand.run(...)` and `DefaultCodingCliAgentFactory`, `code` and `tui` currently share:

- `PreparedCodingAgent`
- `CodingAgent`
- `CliProtocol`
- `CliMcpRuntimeManager`
- `CodingSessionManager`
- approval decorator
- provider / protocol / model configuration logic

This means the following behaviors are, in principle, consistent between `code` and `tui`:

- Model call semantics
- Session save and restore
- Tool execution and approval
- MCP injection
- outer loop / compact / checkpoint

So the UI is not the core divergence point of this system; the runtime is.

## 3. The real first-level divergence: the interactive backend

`CodeCommand` does not simply enter a fixed path just because of `--ui tui`.
It first checks:

- Whether `uiMode == TUI`
- Whether it is not a one-shot prompt
- Whether the terminal is `JlineTerminalIO`
- Whether `AI4J_TUI_BACKEND` / `ai4j.tui.backend` requests legacy

And only then decides on:

- `InteractiveBackend.JLINE`
- or `InteractiveBackend.LEGACY`

This shows that the current `tui` is not a single implementation; there are at least two host backend strategies.

## 4. How the JLINE path and the legacy path actually split

### JLINE path

If the conditions are met, `CodeCommand` creates:

- `SlashCommandController`
- `JlineShellContext`
- `JlineShellTerminalIO`
- `JlineCodeCommandRunner`

This path leans toward command-shell-enhanced interaction:

- slash command
- palette
- completion
- shell input control

### legacy path

Otherwise it goes through:

- `CodingCliSessionRunner`

Combined with:

- `CodingCliTuiFactory`
- `TuiInteractionState`

to complete the run.

So if the current `CLI/TUI` docs only say "tui is a more complete text UI", they miss an important implementation fact:

**The TUI run mode itself may still take different interaction backends.**

## 5. What `DefaultCodingCliTuiFactory` actually decides

The current TUI factory is:

- `DefaultCodingCliTuiFactory`

It wires up:

- `TuiConfig`
- `TuiTheme`
- `TuiSessionView`
- `TuiRuntime`

And decides based on configuration:

- Whether to use `AppendOnlyTuiRuntime`
- or `AnsiTuiRuntime`
- Whether to enable the alternate screen

This shows that TUI in AI4J is not "a few extra colors", but a separate rendering runtime layer.

## 6. At which layer the difference between `code` and `tui` should be understood

The most robust mental model is:

- `CodingAgent`: execution core
- `CodingSessionManager`: session lifecycle
- `CodingCliSessionRunner` / `JlineCodeCommandRunner`: interaction runner
- `TuiRuntime` / `TuiSessionView`: rendering layer

So:

- `code` leans toward a shell/repl interaction shell
- `tui` leans toward a persistent state view shell

But the two are not "two different products"; they are two host surfaces that share the same execution core.

## 7. How provider / protocol / model land here

`DefaultCodingCliAgentFactory.resolveProtocol(...)` and `createModelClient(...)` decide how the current session calls the model underneath.

The current protocol only explicitly exposes to the user:

- `chat`
- `responses`

And the default resolution rule is not static documentation, but the factory's real implementation:

- `openai` + official OpenAI host: prefer `responses`
- `openai` + custom compatible host: lean toward `chat`
- `doubao` / `dashscope`: support `responses`
- Other providers: usually go through `chat`

So when the CLI/TUI page covers protocol selection, it cannot only state "which one is recommended"; it must also explain that this is the runtime factory's actual default logic.

## 8. Why slash command and palette are at the host layer, not the agent layer

The current slash command controller is:

- `SlashCommandController`

It is responsible for the discovery, completion, selection, and routing of commands such as:

- `/provider`
- `/providers`
- `/cmd`
- `/commands`
- `/palette`
- `/stream`
- `/mcp`
- `/team`

The `Ctrl+P` palette, `/` opening the command list, and local completion all belong to the host interaction layer.
They do not change the `CodingAgent` core itself; they only change:

- How the user feeds intent into the runtime

So slash command is not part of the model; it is a host-side command surface.

## 9. Why `/stream` is not a simple UI switch

Although `/stream` is exposed as a command at the interaction layer, it ultimately affects whether subsequent requests use streaming mode.

So its meaning is not:

- "Print character by character on screen, or display all at once"

But rather:

- How subsequent requests interact with the model runtime

This kind of command captures the essence of CLI/TUI exactly:

- Entry at the host
- Impact lands at the runtime

## 10. Why the session store is independent of UI mode

`CodeCommand.createSessionManager(...)` decides, based on:

- `--no-session`
- `--session-dir`

to create:

- `InMemoryCodingSessionStore + InMemorySessionEventStore`
- or `FileCodingSessionStore + FileSessionEventStore`

This shows that whether the session is persisted is a runtime/storage dimension, not a `code` / `tui` dimension.
In other words, you cannot equate:

- "I am working in the TUI"

with:

- "My session will definitely be persisted"

These are two separate configuration axes.

## 11. Why MCP startup warnings appear during the CLI/TUI startup phase

After `CodeCommand` obtains `PreparedCodingAgent`, it directly reads:

- `CliMcpRuntimeManager.buildStartupWarnings()`

and prints the warning to the terminal.

This shows that in CLI/TUI, MCP does not "wait until the model first calls a tool to find a problem"; it exposes its running state at startup.

This is critical for UX, because the user can immediately see:

- Which server is missing
- Which server failed to connect

instead of finding out halfway through a task that the tool surface is incomplete.

## 12. The real differences between `code` one-shot, persistent CLI, and TUI

### one-shot

More like:

- One prompt
- One result
- Suitable for scripting or CI

### Persistent CLI

More like:

- A shell-style persistent session
- slash command / session / process / replay

### TUI

More like:

- A persistent state panel
- transcript + palette + status + team board integrated

Architecturally, they share the underlying execution surface, but the host interaction complexity rises step by step.

## 13. The 5 most common pitfalls

### 13.1 Treating `code` and `tui` as two different runtimes

Currently they mainly share the same set of coding/session/model/MCP semantics.

### 13.2 Assuming `--ui tui` must take the same backend implementation

There is still a JLINE versus legacy backend split.

### 13.3 Treating slash command as a model capability

They belong to the host command surface, not the agent core.

### 13.4 Treating `/stream` as a pure display option

It affects the model request mode.

### 13.5 Treating session persistence as a built-in UI capability

What actually decides it is the session manager/store configuration.

## 14. The conclusion worth remembering from this page

AI4J's current `CLI / TUI` is not "one simplified, one beautified"; they are different host interaction surfaces that share the same coding runtime:

- `CodeCommand` is the unified entry point
- `DefaultCodingCliAgentFactory` uniformly assembles the model, workspace, MCP, and approval
- `CodingSessionManager` uniformly governs the session lifecycle
- JLINE / legacy / TUI runtime each handle different interaction and rendering paths

So when analyzing behavioral differences, first separate:

- Whether it is an execution-core difference
- or a host-interaction difference

That is more effective than only staring at the UI surface.

## 15. Further reading

1. [Runtime architecture](/docs/coding-agent/runtime-architecture)
2. [Session, streaming, and process](/docs/coding-agent/session-runtime)
3. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
4. [Command reference](/docs/coding-agent/command-reference)
