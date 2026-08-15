---
sidebar_position: 9
title: "ACP integration"
description: "Explains the real wiring of ACP as a headless host for IDEs/desktop shells: newline-delimited JSON-RPC, session lifecycle RPCs, available_commands_update, session/load replay, and the server-side reverse session/request_permission."
tags: [integration]
---

# ACP integration

`ACP` is the standard integration surface that the current `Coding Agent` exposes to IDEs, desktop shells, and other host programs.

Its goal is not to "remotely control a terminal window", but rather to let the host directly drive:

- session creation and loading
- prompt execution
- permission confirmation
- slash command invocation
- session event consumption

If you only look at the high-level concepts, it is easy to misread ACP as simply "swapping CLI output for JSON".

The current source code actually does more.

---

## 1. First, the real entry chain of ACP

Compress the main chain into a single line:

```text
ai4j-cli acp ...
  -> AcpCommand.run(...)
  -> CodeCommandOptionsParser.parse(...)
  -> new AcpJsonRpcServer(...)
  -> initialize / session/new / session/load / session/prompt
  -> HeadlessCodingSessionRuntime
  -> session/update + session/request_permission
```

The two most important points here are:

- `acp` and `code` share the same command-line option parsing rules
- ACP is not a separate runtime, but rather a headless host over the same coding runtime

So:

- the parsing rules for provider / model / workspace are essentially identical between ACP and the CLI
- the differences are mainly in the host protocol and how events are transported

---

## 2. The transport contract is more specific than just "stdio mode"

The help text of `AcpCommand` and the read loop of `AcpJsonRpcServer.run()` define the hard contract of the current ACP:

- `stdin`: newline-delimited JSON-RPC requests
- `stdout`: newline-delimited JSON-RPC responses and notifications
- `stderr`: logs, warnings, diagnostics

`AcpJsonRpcServer.run()` currently:

1. reads stdin line by line
2. skips blank lines
3. does a separate JSON parse for each line
4. writes parse errors to `stderr`
5. keeps processing subsequent messages

This means the protocol layer currently assumes:

- newline-delimited JSON-RPC

Rather than:

- LSP-style framing with a `Content-Length` header

If a host gets the framing assumption wrong, communication will fail right at the start.

---

## 3. The capability boundary that `initialize` currently exposes

`buildInitializeResponse(...)` currently returns a very explicit capability set.

Key fields include:

- `protocolVersion`
- `agentInfo`
- `agentCapabilities.loadSession=true`
- `agentCapabilities.mcpCapabilities.http=true`
- `agentCapabilities.mcpCapabilities.sse=true`
- `agentCapabilities.promptCapabilities.audio=false`
- `agentCapabilities.promptCapabilities.image=false`
- `agentCapabilities.promptCapabilities.embeddedContext=false`
- `agentCapabilities.sessionCapabilities.list`

Taken together, these fields describe the real boundary of the current ACP:

- it can list sessions
- it can load sessions
- it can have HTTP / SSE MCP injected from the host
- but prompt input is still primarily text
- you should not currently assume that image, audio, or embedded context are first-class supported by ACP

So the safest host mental model right now is still:

- text prompt + structured events + approval callbacks

---

## 4. `session/new` and `session/load` are not exactly the same path

Both enter `createSession(...)`, but the subsequent actions differ.

### 4.1 `session/new`

The execution order is:

1. parse `cwd` and optional `sessionId`
2. parse session-level `mcpServers`
3. create the permission gateway
4. prepare `CodingCliAgentFactory`
5. build `PreparedCodingAgent`
6. create `CodingSessionManager`
7. create a new `ManagedCodingSession`
8. return `sessionId + configOptions + modes`
9. send `available_commands_update`

### 4.2 `session/load`

The execution order is similar to `session/new`, but it does one extra thing before responding:

- `handle.replayHistory()`

In other words:

- `session/load` first replays the historical `session/update` events
- then sends the success response for this RPC
- and finally sends `available_commands_update`

This order matters for the host, because it determines whether you receive historical content first or the "session opened" confirmation first.

---

## 5. Why `cwd` must be an absolute path

Both `createSession(...)` and `listSessions(...)` require `cwd` to go through `requireAbsolutePath(...)`.

So the current ACP host should not pass:

- relative paths
- IDE-internal workspace aliases
- logical project ids

Instead it should pass:

- a real absolute filesystem path

Because all subsequent session store, workspace config, skills, MCP, and file tool boundaries ultimately depend on this real workspace root.

---

## 6. `session/prompt` actually has two execution branches

`promptSession(...)` first flattens the `prompt` array into an input string, then checks:

- whether `AcpSlashCommandSupport.supports(input)` is true

So the current prompt has two paths:

### 6.1 Regular prompt

Goes through:

- `HeadlessCodingSessionRuntime.runPrompt(...)`

This path produces the full set of runtime events: real model calls, tool calls, loop decisions, auto-compact, the event ledger, and so on.

### 6.2 ACP-known slash command

Goes through:

- `SessionHandle.runSlashCommand(...)`

This path does not hand the input back to the model; instead it executes the command logic locally, then still sends the text result back to the host via the standard `session/update` event.

So:

- slash command discovery in ACP is a protocol capability
- but execution is still triggered through an ordinary `session/prompt` input

This is a different design from "sending a separate command RPC".

---

## 7. `available_commands_update` is a metadata event, not a model event

The current ACP proactively sends it on these occasions:

- after `session/new`
- after `session/load`

The event type is:

- `session/update`
- `update.sessionUpdate = "available_commands_update"`

Its purpose is not to reply to some prompt, but to tell the host:

- which slash commands this ACP session currently supports

The safest host behavior is to:

1. cache this command list
2. map it to a slash menu / command palette
3. never hardcode a separate command list on the client side

---

## 8. ACP currently exposes a "headless-friendly" command subset by default

The notable commands currently exposed by `AcpSlashCommandSupport.COMMANDS` include:

- `help`
- `status`
- `session`
- `save`
- `providers`
- `provider`
- `model`
- `experimental`
- `skills`
- `agents`
- `mcp`
- `sessions`
- `history`
- `tree`
- `events`
- `team`
- `compacts`
- `checkpoint`
- `processes`
- `process`

This is not identical to the full command surface of the CLI/TUI.

The reason is not missing features, but rather that ACP currently emphasizes:

- structured host integration
- no dependence on terminal-specific interaction
- command results that can degrade cleanly to plain text

So commands like `team`, although complex, still fit ACP, because their output can first be shown as text and then optionally rendered into richer UI by the host.

---

## 9. The key of the text event model is not "how many categories", but "order is replayable"

The most common notification from ACP is still:

- `session/update`

Common `sessionUpdate` values include:

- `available_commands_update`
- `user_message_chunk`
- `agent_thought_chunk`
- `agent_message_chunk`
- `tool_call`
- `tool_call_update`

The most important property of the current design is not the event names themselves, but:

- live turns and history replay share the same update model

In other words:

- you do not need to write two completely different renderers for "live rendering" and "history playback"
- as long as you consume `session/update` in order, most hosts can handle both uniformly

---

## 10. Why slash command results also go through `agent_message_chunk`

`runSlashCommand(...)` will:

1. first append a `USER_MESSAGE` ledger event
2. send `user_message_chunk`
3. execute the ACP slash command locally
4. append an `ASSISTANT_MESSAGE` ledger event marked `kind=command`
5. then send the result to the host through `agent_message_chunk`

This means that, for the host:

- slash command results and ordinary assistant text results can share UI at the consumption layer

If the host wants finer-grained distinction, it can read from the ledger payload:

- `kind = command`

But this is not required.

---

## 11. The replay of `session/load` is not "asking the model again"

`SessionHandle.replayHistory()` only reads historical `SessionEvent`s from the event store and converts them into ACP `session/update`s.

It does not:

- re-run the model
- re-execute tools
- re-fetch external state

So the ACP host must understand replay as:

- event ledger playback

Rather than:

- reconstructing the real runtime scene

If some live external resources have changed, replay still only shows the session events that were written down at the time.

---

## 12. `session/cancel` does not only interrupt the current turn, it also uniformly settles the pending approval state

`cancelSession(...)` currently does two things:

1. cancels the active prompt on that session
2. completes all pending permission futures as cancelled

This shows that the "stop" semantics of ACP cover more than just text generation interruption; they also include:

- if the host side is currently blocked on a tool approval, that wait should also be exited together

This is closer to "stop the current unit of work" that the user actually wants, rather than simply interrupting a thread.

---

## 13. Permission confirmation is a server-initiated reverse RPC

If the current approval mode is not `auto`, and a tool call matches an approval rule, ACP does not just wait locally.

It proactively sends a JSON-RPC request to the host:

- `method = "session/request_permission"`

The current option set is fixed to:

- `allow_once`
- `allow_always`
- `reject_once`
- `reject_always`

After the server receives the host response, it treats only:

- `allow_once`
- `allow_always`

as approval.

All other results go through rejection or cancellation.

Therefore what the host needs to support is not "display some text", but rather:

- receive a server-initiated request
- suspend this tool call
- return the final choice

:::warning You must handle the reverse permission RPC
If this chain is not implemented, ACP integration under `manual` / `safe` mode will get stuck at the permission wait point.
:::

---

## 14. The current capability boundary of `modes` and `configOptions` is very narrow

`buildSessionOpenResult()` sends two groups back to the host:

- `modes`
- `configOptions`

But what is actually supported right now is quite limited.

### `modes`

Currently this is essentially the approval mode set, for example:

- `auto`
- `safe`
- `manual`

### `configOptions`

Currently only:

- `mode`
- `model`

In other words, ACP is not yet a complete "settings center".

It only supports:

- switching approval mode
- switching model

If the host wants to change provider, MCP store, or workspace binding, it still has to go through other paths, rather than expecting ACP to already provide a full configuration API.

---

## 15. MCP injection under ACP is a chain independent of the local store

In `session/new` / `session/load`, the host can pass `mcpServers` directly.

`AcpJsonRpcServer.resolveMcpConfig(...)` assembles them directly into `CliResolvedMcpConfig`, which is then handed to the ACP agent factory.

The characteristics of this chain are:

- treated as workspace-enabled by default
- does not depend on `~/.ai4j/mcp.json`
- does not depend on `workspace.json.enabledMcpServers`
- behaves more like a temporary session injection by the host

Therefore MCP under ACP is best suited for:

- IDEs dynamically mounting tools per project
- desktop shells temporarily allocating MCP per session
- multi-tenant hosts that do not want to depend on a user's local global config

---

## 16. The most common integration pitfalls right now

### 16.1 Sending messages with LSP framing

The current ACP reads JSON line by line, not `Content-Length` framing.

### 16.2 Passing `cwd` as a relative path

An absolute path is currently required; otherwise session creation fails.

### 16.3 Assuming slash commands need another set of RPCs

Slash commands are still triggered through the `session/prompt` text today.

### 16.4 Assuming `session/load` re-runs historical tools

It only replays the event ledger; it does not re-enact the real execution.

### 16.5 Ignoring the server-side reverse `session/request_permission`

If the host can only send requests and cannot handle the approval request that comes back from the server, ACP is incomplete under any non-`auto` mode.

---

## 17. Host implementation recommendations

- use `stdout` only as the protocol channel; do not mix logs into it
- route logs and warnings separately to `stderr`
- send all requests as newline-delimited JSON-RPC
- always pass a real absolute path as `cwd`
- consume `session/update` uniformly in the order received
- drive the slash menu from `available_commands_update`; do not hardcode it locally
- the permission dialog must be able to handle the server-initiated `session/request_permission`
- prompt input is currently text-centric; do not assume image/audio/embeddedContext are available yet

---

## 18. The conclusions most worth remembering from this page

- ACP is a headless host, not a "remote terminal mirror"
- `acp` and `code` share the same base configuration parsing rules
- `session/new`, `session/load`, and `session/prompt` each have an explicit local runtime chain behind them; they are not just JSON forwarding
- slash command discovery relies on `available_commands_update`; execution relies on an ordinary `session/prompt`
- permission confirmation is a server-initiated reverse RPC
- `session/load` replays the event ledger; it does not re-execute historical runs

---

## 19. Further reading

1. [CLI / TUI usage guide](/docs/products/coding-agent/cli-and-tui)
2. [Sessions, streaming, and processes](/docs/products/coding-agent/session-runtime)
3. [MCP and ACP](/docs/products/coding-agent/mcp-and-acp)
4. [MCP integration](/docs/products/coding-agent/mcp-integration)
5. [Command reference](/docs/products/coding-agent/command-reference)

→ API Javadoc: [`AcpJsonRpcServer`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-cli/2.4.2/io/github/lnyocly/ai4j/cli/acp/AcpJsonRpcServer.html) (the `ai4j-cli` module; the main protocol class of the ACP stdio server)
