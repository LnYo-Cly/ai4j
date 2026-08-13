---
sidebar_position: 6
title: "Tools and the Approval Mechanism"
description: "Breaks down how the Coding Agent's eight built-in tools (bash/read_file/write_file/apply_patch/glob/grep/edit/update_agents_md) are wired together, executor routing, where the approval decorator intercepts, and why approval and workspace boundaries must be understood separately."
tags: [concept]
---

# Tools and the Approval Mechanism

If there is one outward-facing difference between `Coding Agent` and an ordinary agent, the answer is usually not the prompt — it is the tools.
But what this page really needs to make clear is not simply "there are 8 built-in tools". It is:

- how these tools get wired in
- who actually executes them
- why the tool surface a given session sees is more than 8
- where approval actually intercepts
- why approval and the workspace boundary are not the same thing

## 1. Eight fixed built-in tools — but the runtime surface is larger than eight

`CodingToolRegistryFactory.createBuiltInRegistry()` currently mounts exactly eight local tools:

- `bash`
- `read_file`
- `write_file`
- `apply_patch`
- `glob`
- `grep`
- `edit`
- `update_agents_md`

These eight come from:

- `BuiltInTools.codingTools()`

The read-only subset of them (`BuiltInTools.readOnlyCodingToolNames()`) is:

- `bash` (exec)
- `read_file`
- `glob`
- `grep`

But the set of tools the model can ultimately see in a given session is not necessarily limited to those eight.
`CodingAgentBuilder` may also merge in:

- custom tool registry
- `delegate_*` tools
- `subagent_*` tools
- subagent/team tools injected via `/experimental`
- MCP tools

So the most accurate statement is:

- fixed built-in local execution tools = 8
- the visible tool surface for the current session = fixed built-in tools + runtime-injected tools

## 2. Which layer actually connects tools to executors

The key entry point is not the registry, but rather:

- `CodingAgentBuilder.createBuiltInToolExecutor(...)`

It routes each tool to a dedicated executor:

- `ReadFileToolExecutor`
- `WriteFileToolExecutor`
- `ApplyPatchToolExecutor`
- `BashToolExecutor`
- `GlobToolExecutor`
- `GrepToolExecutor`
- `EditToolExecutor`
- `UpdateAgentsMdToolExecutor`

And finally uses:

- `RoutingToolExecutor`

to dispatch by tool name.

This matters, because it shows that AI4J's coding tools are not simply annotated functions — they are a set of dedicated executors.

## 3. What the actual boundary of `read_file` is

`ReadFileToolExecutor` currently hands the request off to:

- `WorkspaceFileService.readFile(...)`

and supports:

- `path`
- `startLine`
- `endLine`
- `maxChars`

If `maxChars` is not provided, it falls back to:

- `CodingAgentOptions.defaultReadMaxChars`

In other words, `read_file` does not have a fixed "read the whole file" semantic. It is a:

- range-scoped
- length-capped
- workspace-file-service-backed

controlled read interface.

Combined with `WorkspaceContext.resolveReadablePath(...)`, its core boundary is:

- anything inside the workspace is readable
- extra read-only roots such as skills are also readable
- by default it cannot freely cross the workspace to read arbitrary paths

## 4. What the actual semantic of `write_file` is

`WriteFileToolExecutor` currently supports three modes:

- `create`
- `overwrite`
- `append`

It returns:

- `resolvedPath`
- `mode`
- `created`
- `appended`
- `bytesWritten`

But there is one implementation fact that must be stated clearly here:

**Its own `resolvePath(...)` does not call `WorkspaceContext.resolveWorkspacePath(...)`.**

The current behavior is:

- relative paths land under the workspace root
- absolute paths are normalized and used directly

:::warning write_file can write outside the workspace
This means its boundary is not fully symmetric with `read_file`.
If the caller passes an absolute path, the current implementation can indeed write outside the workspace.
:::

This is exactly why:

- approval must not be mistaken for a sandbox
- and the workspace constraint must not be mistaken for being uniform across all tools

## 5. Why `apply_patch` is more coding-native than `write_file`

`ApplyPatchToolExecutor` does not simply "write a piece of text". It:

1. Validates the patch envelope:
   - `*** Begin Patch`
   - `*** End Patch`
2. Parses:
   - `*** Add File:`
   - `*** Update File:`
   - `*** Delete File:`
3. Performs anchor matching on update hunks
4. Applies changes file by file
5. Returns a structured `ApplyPatchResult`

More importantly, when locating files it goes through:

- `workspaceContext.resolveWorkspacePath(path)`

That is, `apply_patch` is currently bound by the workspace boundary more strictly than `write_file`.

This is why, in coding scenarios, `apply_patch` is not "an alternative way to write files", but rather:

**a code-editing tool with structural constraints, context matching, and a workspace boundary.**

## 6. Why `bash` cannot be understood as just "run a command"

`BashToolExecutor` currently supports the actions:

- `exec`
- `start`
- `status`
- `logs`
- `write`
- `stop`
- `list`

This shows that `bash` inside the Coding Agent is the union of two semantics:

- one-shot command execution: `exec`
- long-running process management: `start/status/logs/write/stop/list`

And behind it sits directly:

- `LocalShellCommandExecutor`
- `SessionProcessRegistry`

So `bash` is not an ordinary function tool — it is a session-level process-surface entry point.

## 7. Search, edit, and project-memory tools

Beyond the four "classic" tools above, `BuiltInTools.codingTools()` also mounts four more, covering search, precise editing, and cross-session project memory.

### 7.1 `glob` — filename pattern matching

`GlobToolExecutor` runs glob matching inside the workspace (e.g. `**/*.java`) and returns the list of matching paths relative to the workspace root.

Parameters:

- `pattern` (required): the glob pattern
- `path`: the base search directory, relative to the workspace root; defaults to the workspace root
- `maxResults`: upper bound on returned paths

It is a read-only tool (part of `readOnlyCodingToolNames`), suited to letting the model quickly locate "which files satisfy some naming convention" rather than flooding the context with the entire directory.

### 7.2 `grep` — file content search

`GrepToolExecutor` searches file contents with a regex and returns ripgrep-style results (filename + line number + matched line).

Parameters:

- `pattern` (required): a regular expression
- `path`: the base search directory or a single file, relative to the workspace root
- `include`: a filename glob filter (e.g. `*.java`)
- `caseInsensitive`: whether the search is case-insensitive; defaults to false
- `maxResults`: upper bound on returned matched lines

It is likewise a read-only tool. `glob` answers "which files", `grep` answers "which files contain this piece of content" — together they are the coding agent's primary combination for code location.

### 7.3 `edit` — precise string replacement

`EditToolExecutor` performs precise in-file string replacement, and is complementary to `apply_patch`:

- `apply_patch`: suited to structured, multi-file patches combining additions, deletions, and modifications
- `edit`: suited to localized replacement within a single file

Parameters:

- `path` (required): the file to edit, relative to the workspace root
- `old_string` (required): the exact text to find; indentation and whitespace must match exactly
- `new_string` (required): the replacement text
- `replaceAll`: replace every match; by default only a unique match is allowed

By default `old_string` must match **uniquely** in the file, otherwise the call fails — this is a deliberate design to prevent `edit` from silently modifying multiple places. To replace several identical fragments, pass `replaceAll=true` explicitly.

### 7.4 `update_agents_md` and the AGENTS.md project memory

`update_agents_md` is a somewhat special built-in tool: it is dedicated to maintaining the project-level **AGENTS.md memory file**, letting the agent persist project conventions, decisions already made, and to-dos across sessions.

Parameters:

- `action` (required): `read` / `write` / `append`
- `content`: full content (used when `action=write`)
- `text`: the text to append (used when `action=append`)

Behind it sit `UpdateAgentsMdToolExecutor` + `AgentsMdStore`. The AGENTS.md lookup order is:

1. `AGENTS.md` at the workspace root
2. `.agents/AGENTS.md` under the workspace root

When neither exists, `read` returns empty content, and `write` creates `AGENTS.md` at the workspace root.

:::tip AGENTS.md is "project memory", not a skill
Keep these distinct:

- A **Skill** (see [Skills usage and organization](/docs/coding-agent/skills)) is a "library of practices": file-based workflow knowledge, read by read_file on demand.
- **AGENTS.md** is "project memory": project conventions, decisions, and to-dos that the agent itself writes via `update_agents_md`, persisted across sessions.

Both are part of the read-only knowledge-input surface, but AGENTS.md is **writable** by the agent and is runtime state the agent actively maintains.
:::

This tool gives the coding agent the ability to "remember the key facts about this project over the long term", instead of rediscovering them every session.

## 8. Why a session may also see `delegate_*`, `subagent_*`, and MCP tools

Beyond the fixed local tools, `CodingAgentBuilder` also merges in:

- `CodingDelegateToolRegistry`
- `SubAgentRegistry`
- custom registry
- MCP registry

And when `DefaultCodingRuntime` spawns a child session, it additionally goes through:

- `CodingToolPolicyResolver`

to filter the allowed tool names by agent definition.

This means tools in the Coding Agent are not a static list, but rather:

- a base local tool surface
- a runtime-extended tool surface
- the effective tool surface after per-session / per-definition filtering

So when reading tool-related code, distinguish between:

- the registration surface
- the execution surface
- the policy surface

## 9. Which layer approval actually intercepts at

Approval is currently not:

- an OS hook
- a shell wrapper
- a JVM agent
- "confirm after the command has already run"

It is a very explicit executor-decorator model.

The chain is:

1. CLI/ACP first decides the `ApprovalMode`
2. `DefaultCodingCliAgentFactory` attaches the decorator into `CodingAgentOptions`
3. `CodingAgentBuilder` calls `decorate(...)` when building the built-in executors
4. Before a tool is actually executed, the decorator first decides whether approval is required

So the approval interception points are:

- the ToolExecutor assembly stage
- the ToolExecutor call entry

This layering is clean, because it allows:

- one runtime
- different host interaction styles

to share a single approval semantic.

## 10. What `CliToolApprovalDecorator` currently actually intercepts

From the implementation, the current rules are:

- `manual`: every tool call is approved
- `safe`:
  - `apply_patch` is always approved
  - `bash` `exec/start/stop/write` are approved
  - `read_file` is not approved by default
  - `write_file` is not approved by default
- `auto`: passed through by default

Read this according to "the current code", not according to what the name suggests.
Many people assume `safe` means "all write operations are intercepted", but that is not the case at this stage.

If you want to widen the approval scope, the correct entry points are:

- swap the decorator
- change the decorator rules

not change the prompt.

## 11. How a rejected approval is propagated back to the runtime

When approval is rejected, `CliToolApprovalDecorator` does not swallow it silently — it throws a rejection message prefixed with:

- `[approval-rejected]`

Then `CodingAgentLoopController` recognizes this kind of tool result as:

- `BLOCKED_BY_APPROVAL`

This connects "the host interaction refused execution" and "the runtime stops advancing" into a complete semantic chain.

So approval in AI4J is not a UX detail — it is part of the stop reason.

## 12. Why ACP approval is yet another path

Under CLI/TUI, approval is a terminal interaction:

- print the approval block
- read `y/yes`

Under ACP, it is:

- `AcpToolApprovalDecorator`
- `PermissionGateway`
- `session/request_permission`

That is, approval in ACP is fundamentally a protocol round-trip, not a local stdin interaction.

But both paths still share the same core idea:

- both are ToolExecutor decorators
- both intercept before execution
- both can propagate a rejection back to the runtime

## 13. Why "approval" and "workspace sandbox" must be understood separately

This is the easiest place in the current docs to get wrong.

Approval answers:

- "should this call be executed at all"

The workspace boundary answers:

- "even if executed, where is this call allowed to reach"

The two are not the same layer of control.

For example, in the current implementation:

- `apply_patch` strictly goes through `resolveWorkspacePath(...)`
- `read_file` goes through `resolveReadablePath(...)`
- `write_file` has its own looser `resolvePath(...)`

So you cannot simply say:

- "turning on approval makes it safe"
- "all tools are protected by the same workspace sandbox"

The more accurate statement is:

- the path-boundary implementations of different tools are not fully symmetric
- approval is only a pre-execution control, not a filesystem isolation layer

## 14. How far sandbox routing goes today

`ai4j-coding` already has the first P3 slice: once the host binds a live sandbox via `CodingAgentBuilder.sandbox(SandboxSession)`, `bash action=exec` goes through `SandboxShellCommandExecutor` to call `SandboxSession.execute(SandboxCommand)`.

This chain only changes where foreground shell commands run:

```text
no sandbox -> LocalShellCommandExecutor -> host shell
with sandbox -> SandboxShellCommandExecutor -> SandboxSession.execute(...)
```

The returned result carries `executionEnvironment`, `sandboxSessionId`, and `sandboxProviderId`, so that the CLI/TUI, logs, or an upstream host can show "where this command actually ran".

Note: this does not mean every tool has been remoted yet. `read_file`, `write_file`, `apply_patch`, background processes, browser, and git/project run still need subsequent slices to be wired in. For the full boundary, see [Sandbox Routing](/docs/coding-agent/sandbox-routing).

## 15. Where the most stable extension points are today

If you want to integrate the Coding Agent into an enterprise environment, the most stable extension entry points are usually:

- a custom `toolRegistry`
- a custom `toolExecutor`
- a custom `ToolExecutorDecorator`

They respectively fit:

- controlling the tool surface the model sees
- rewriting the actual tool execution logic
- uniformly attaching approval, audit, rate limiting, and authentication

One important constraint here:

- if you pass a custom `toolRegistry`, you must also provide a matching `toolExecutor`

otherwise the tool surface and the execution surface will get out of sync.

## 16. The five easiest places to go wrong

### 16.1 Treating "fixed built-in tools" as "all visible tools"

The current session's tool surface may also contain delegate, subagent, and MCP tools.

### 16.2 Treating approval as an OS-level hook

It is essentially just a ToolExecutor decorator.

### 16.3 Imagining `safe` mode as stricter than the current implementation

Not every write operation is automatically approved today.

### 16.4 Assuming `write_file` and `apply_patch` have the same path boundary

The current implementations are not fully symmetric.

### 16.5 Designing only the registry, not the executor

The tool exposure surface and the execution surface must be considered together.

## 17. The conclusion worth remembering from this page

AI4J's current Coding Agent tools mechanism is not "8 functions + a confirmation dialog". It is an entire runtime surface:

- a registry decides what the model sees
- a dedicated executor decides how it runs locally
- a decorator decides how it is approved before execution
- a policy resolver decides which tools a given child session is allowed to use

And approval and the workspace boundary are two different controls.
Separate these layers clearly, and only then can you truly understand where the controllability of the Coding Agent comes from.

## 18. Further reading

1. [Sessions, streaming, and processes](/docs/coding-agent/session-runtime)
2. [Compact and checkpoint mechanism](/docs/coding-agent/compact-and-checkpoint)
3. [Sandbox Routing](/docs/coding-agent/sandbox-routing)
4. [Lifecycle hooks and workspace trust](/docs/coding-agent/lifecycle-hooks)
5. [Runtime architecture](/docs/coding-agent/runtime-architecture)

→ API Javadoc: [`BuiltInToolExecutor`](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/tool/BuiltInToolExecutor.html) (the `ai4j` module; the entry class for built-in tool execution routing)
