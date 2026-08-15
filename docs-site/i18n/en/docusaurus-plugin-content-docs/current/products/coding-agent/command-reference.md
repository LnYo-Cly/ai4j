---
sidebar_position: 11
title: "Command Reference"
description: "Summary of the high-frequency slash commands implemented by the Coding Agent (provider/model/mcp/session/process/team/compact, etc.), including their scope, common parameters, and command-visibility differences across the CLI/TUI/ACP hosts."
tags: [reference]
---

# Command Reference

This page summarizes the high-frequency commands currently implemented by the Coding Agent, along with each command's scope, common parameters, and usage guidance.

---

## 0. First, distinguish three command-visibility layers

Although every command surfaces as a slash command, the command surfaces exposed by the three hosts are not identical:

- `CLI`: the most complete surface, suitable for direct terminal use
- `TUI`: the most complete surface, additionally integrated with the palette, completion, and panels
- `ACP`: exposes only a safe subset appropriate for headless hosts and IDE integration

The standard command set that ACP currently exposes by default is:

- `/help`
- `/status`
- `/session`
- `/save`
- `/providers`
- `/provider`
- `/model`
- `/experimental`
- `/skills`
- `/agents`
- `/mcp`
- `/sessions`
- `/history`
- `/tree`
- `/events`
- `/team`
- `/compacts`
- `/checkpoint`
- `/processes`
- `/process`

In other words:

- High-value configuration commands such as `provider/profile/model` are already exposed in the ACP command palette
- Commands like `theme`, `stream`, and pure terminal interaction remain better suited to CLI/TUI
- ACP clients should obtain the command list via `available_commands_update` rather than hardcoding it themselves

---

## 1. Provider / Model / Runtime Flags

### `/providers`

Lists saved provider profiles.

```text
/providers
```

Suitable for:

- Seeing which profiles are already stored on the current machine
- Troubleshooting misspelled profile names

---

### `/provider`

Shows the current effective provider status.

```text
/provider
```

Typically includes:

- Current active profile
- Current default profile
- Effective provider
- Effective protocol
- Effective model

---

### `/provider use`

Switches the profile in use by the current workspace and immediately rebuilds the current session runtime.

```text
/provider use <profile-name>
```

Example:

```text
/provider use zhipu-main
```

---

### `/provider save`

Saves the currently running provider / protocol / model / baseUrl / apiKey as a profile.

```text
/provider save <profile-name>
```

Example:

```text
/provider save openai-main
```

---

### `/provider add`

Creates a new profile with explicit parameters.

```text
/provider add <profile-name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]
```

Example:

```text
/provider add zhipu-main --provider zhipu --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4
```

Notes:

- `--provider` is required
- When `--protocol` is omitted, the default protocol is derived from the provider/baseUrl
- The result is written to `~/.ai4j/providers.json`

---

### `/provider edit`

Updates an existing profile.

```text
/provider edit <profile-name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]
```

Example:

```text
/provider edit zhipu-main --model glm-4.7-plus
/provider edit openai-main --protocol responses
/provider edit zhipu-main --clear-api-key
```

Notes:

- Only the fields you explicitly pass are updated
- `--clear-model` / `--clear-base-url` / `--clear-api-key` are used to clear fields
- If the modified profile is the current effective profile, the current session runtime is rebuilt immediately

---

### `/provider default`

Sets or clears the global default profile.

```text
/provider default <profile-name|clear>
```

Example:

```text
/provider default openai-main
/provider default clear
```

---

### `/provider remove`

Deletes a saved profile.

```text
/provider remove <profile-name>
```

---

### `/model`

Shows the current effective model and the workspace override.

```text
/model
```

---

### `/model <name>`

Saves a workspace model override and immediately switches the current session runtime.

```text
/model <name>
```

Example:

```text
/model glm-4.7-plus
```

---

### `/model reset`

Clears the workspace model override and falls back to the profile model.

```text
/model reset
```

---

### `/experimental`

Views or toggles the experimental runtime feature switches for the current workspace.

```text
/experimental
/experimental <subagent|agent-teams> <on|off>
```

Example:

```text
/experimental
/experimental subagent off
/experimental agent-teams on
```

Notes:

- The current state is persisted to `<workspace>/.ai4j/workspace.json`
- `subagent` controls whether the experimental background-worker subagent tool `subagent_background_worker` is injected
- `agent-teams` controls whether the experimental delivery-team subagent tool `subagent_delivery_team` is injected
- When the corresponding field is missing, the current implementation treats it as `on (default)`
- Toggling immediately rebuilds the current session runtime so that the visible toolset for the current session takes effect
- This command changes the "agent tool surface visible to the current session", not the fixed built-in local tool list
- Whether these agent tools can be reliably triggered also depends on the tool-calling quality of the current provider / model

---

## 2. Skills / MCP / Stream

### `/skills`

Lists the coding skills discovered in the current session.

```text
/skills
```

Typically includes:

- The number of skills currently discovered
- The workspace configuration file location
- The skill roots currently in effect
- Each skill's `name / source / path / description`

---

### `/skills <name>`

Views detailed information about a single skill.

```text
/skills <skill-name>
```

Example:

```text
/skills repo-review
```

Notes:

- Shows the skill's source, path, and description
- Shows the current skill roots, so you can confirm where it was discovered from
- Only metadata is displayed; the body of `SKILL.md` is not printed
- Skill names can be obtained via slash completion

---

### `/mcp`

Shows the current MCP services and their status.

```text
/mcp
```

Common uses:

- See which services are registered
- See whether the workspace is enabled
- See whether the current session is running, paused, or needs reconnection

---

### `/mcp add`

Adds a global MCP service.

```text
/mcp add --transport <stdio|sse|http> <name> <target>
```

Usage notes:

- `stdio`: `target` is a command line
- `sse` / `http`: `target` is a URL

Additional notes:

- `http` here is the CLI-compatible parameter name
- When persisting to MCP config, `type: "streamable_http"` is recommended

---

### `/mcp enable|disable`

Toggles the workspace-level MCP enabled state.

```text
/mcp enable <name>
/mcp disable <name>
```

Notes:

- Acts on the current workspace configuration
- Affects the set of MCP services visible to subsequent session runtimes

---

### `/mcp pause|resume`

Toggles the MCP running state within the current session.

```text
/mcp pause <name>
/mcp resume <name>
```

Notes:

- `enable/disable` operates at the workspace configuration layer
- `pause/resume` operates at the current session runtime layer

---

### `/mcp retry`

Reconnects an enabled MCP service.

```text
/mcp retry <name>
```

---

### `/mcp remove`

Deletes a registered global MCP service.

```text
/mcp remove <name>
```

---

### `/stream`

Shows the model-request streaming state of the current CLI session.

```text
/stream
```

---

### `/stream on|off`

Toggles the model-request streaming behavior of the current CLI session.

```text
/stream on
/stream off
```

Notes:

- The scope is the current CLI session
- Toggling immediately rebuilds the current session runtime
- `on`: subsequent requests use `stream=true`
- `off`: subsequent requests use `stream=false`
- This is not a provider protocol switch command

---

## 3. Sessions

### `/status`

Shows the current session runtime status.

```text
/status
```

---

### `/session`

Shows the current session metadata.

```text
/session
```

---

### `/save`

Persists the current session state.

```text
/save
```

---

### `/sessions`

Lists the saved sessions in the current session store.

```text
/sessions
```

---

### `/resume` / `/load`

Resumes a saved session.

```text
/resume <id>
/load <id>
```

Notes:

- `/load` is an alias for `/resume`

---

### `/fork`

Forks a new branch from an existing session.

```text
/fork [new-id]
/fork <source-id> <new-id>
```

---

### `/history`

Shows the lineage from the root to the target session.

```text
/history [id]
```

---

### `/tree`

Shows the current session tree.

```text
/tree [id]
```

---

### `/events`

Shows recent session ledger events.

```text
/events [n]
```

---

### `/replay`

Replays the recent conversation, aggregated by turn.

```text
/replay [n]
```

---

### `/team`

Views the current agent team board, or manages the team snapshots persisted in the workspace.

```text
/team
/team list
/team status [team-id]
/team messages [team-id] [limit]
/team resume [team-id]
```

Notes:

- `/team`: reads the current session event ledger and aggregates it via `TeamBoardRenderSupport` into the "team board for the current session"
- `/team list`: lists the known teamIds in `<workspace>/.ai4j/teams/state/*.json`
- `/team status [team-id]`: reads the most recently persisted `AgentTeamState` and renders a text board; when `team-id` is omitted, the most recently persisted team is used by default
- `/team messages [team-id] [limit]`: reads `<workspace>/.ai4j/teams/mailbox/<teamId>.jsonl`, used to view recent team collaboration messages
- `/team resume [team-id]`: reopens a "persisted-snapshot view" board; it does not restart the team runtime, nor does it replay live team execution
- `CLI` / `ACP`: returns text-based results
- `TUI`: `/team` opens the current board; `/team resume ...` opens the persisted board snapshot
- The current experimental delivery team writes its data to `<workspace>/.ai4j/teams` by default
- Only Team tasks / Team messages are aggregated; plain delegate tasks are not mixed in

---

### `/compacts`

Views the recent compaction history.

```text
/compacts [n]
```

In addition to the time and summary, the current output carries compaction diagnostic fields, such as:

- `strategy`
- `compactedToolResultCount`
- `deltaItemCount`
- `checkpointReused`
- `fallbackSummary`

---

### `/compact`

Compacts the current session memory.

```text
/compact
/compact <summary>
```

Additional notes:

- A manual compact updates the current checkpoint directly
- On success, it cleans up the pending loop artifact left over from the previous round and resets the auto-compact breaker
- `<summary>` can be supplied as an additional summary instruction for this compaction, rather than replacing the entire checkpoint schema

---

### `/checkpoint`

Shows the current structured checkpoint summary.

```text
/checkpoint
```

Key checkpoint fields currently displayed include:

- `goal`
- `constraints`
- `done / in-progress / blocked`
- `keyDecisions`
- `nextSteps`
- `criticalContext`
- `processSnapshots`

---

## 4. Processes

### `/processes`

Lists metadata for currently active and resumed processes.

```text
/processes
```

---

### `/process status`

Views a single process's metadata.

```text
/process status <process-id>
```

---

### `/process follow`

Views a process's metadata and follows its buffered logs.

```text
/process follow <process-id> [limit]
```

---

### `/process logs`

Reads a process's buffered logs.

```text
/process logs <process-id> [limit]
```

---

### `/process write`

Writes text to an active process's stdin.

```text
/process write <process-id> <text>
```

---

### `/process stop`

Stops an active process.

```text
/process stop <process-id>
```

---

## 5. TUI / Palette

### `/help`

Prints the current command help.

```text
/help
```

---

### `/theme`

Views or switches the current TUI theme.

```text
/theme
/theme <name>
```

---

### `/commands`

Lists the custom command templates currently available.

```text
/commands
```

---

### `/palette`

An alias for `/commands`, with semantics that lean more toward TUI interaction.

```text
/palette
```

---

### `/cmd`

Executes a custom command template.

```text
/cmd <name> [args]
```

---

### Custom command template files

`/commands`, `/palette`, and `/cmd` are all backed by the same custom command template mechanism (`CustomCommandRegistry`). Once you write a frequently used prompt as a command file, you can inject its body into the current turn via `/cmd <name>`.

**Discovery paths** (later entries override commands with the same name):

| Location | Scope |
| --- | --- |
| `~/.ai4j/commands/` | User-global, visible across all workspaces |
| `<workspace>/.ai4j/commands/` | Current workspace, overrides same-named global commands |

Supported extensions: `.md`, `.txt`, `.prompt`. The command name is the file name minus the extension (`review.md` → `/cmd review`).

**File format**: when the first line starts with `#`, that line (with the `#` stripped) becomes the command description, and the prompt body starts from the second line; when the first line does not start with `#`, the entire file is the body and there is no description. `$key` placeholders in the body are substituted with variables at render time.

A minimal example, `<workspace>/.ai4j/commands/refactor.md`:

```text
# Review and refactor code
Review the code in the current workspace, refactor it following $language idioms, and point out potential issues.
```

Then run `/cmd refactor` inside the session to inject the body into the current turn.

---

### `/clear`

Prints a new screen section, effectively resetting the current terminal view.

```text
/clear
```

---

### `/exit` / `/quit`

Exits the current session.

```text
/exit
/quit
```

---

## 6. Completion and interaction conventions

In the current TUI shell:

- `/`: opens the command palette
- `Tab`: applies the current completion item
- `Ctrl+P`: opens the command palette
- `Ctrl+R`: opens replay
- `/team`: opens the current team board
- `Enter`: submits the input
- `Esc`: interrupts the current task while a turn is active; closes the panel or clears the input when idle

Meanings of the current status bar text:

- `Thinking`: analyzing the current input and context
- `Connecting`: opening a model request or waiting for the first model event
- `Responding`: the model is continuously producing output
- `Working`: a tool or process is still running
- `Retrying`: the request is being retried
- `Waiting`: no new progress within a short interval
- `Stalled`: no new progress for a longer interval; the status bar will prompt `press Esc to interrupt`

Current command completion covers:

- Root commands
- `/provider` second-level actions
- `/provider add|edit` parameters
- `/provider add|edit --protocol` values
- `/model` candidates
- `feature` / `on|off` candidates for `/experimental`
- `/skills` candidates
- `/stream on|off`

---

## 7. Top-level CLI commands

Beyond the in-session slash commands, `ai4j-cli` itself exposes a set of top-level subcommands. Their entry point is `Ai4jCli`:

| Command | Purpose |
| --- | --- |
| `ai4j-cli code` | Starts a coding session (one-shot or interactive REPL); the most common entry point |
| `ai4j-cli tui` | Equivalent to `code --ui tui`; starts a richer text-UI shell |
| `ai4j-cli acp` | Starts the coding session as an ACP stdio server (IDE / headless integration) |
| `ai4j-cli run` | Runs a single-agent Agent Blueprint YAML once (one-shot, no session) |
| `ai4j-cli trust` | Manages the trust list for workspace hooks (see [Lifecycle Hooks and Workspace Trust](/docs/products/coding-agent/lifecycle-hooks)) |
| `ai4j-cli extension` | Inspects / assembles / runs AI4J extension packages on the classpath |

When no subcommand is supplied and flags like `--model` are passed directly, the input is treated as a `code` command, for example:

```bash
ai4j-cli --provider openai --model gpt-5-mini --prompt "Investigate why tests fail"
```

This is equivalent to `ai4j-cli code ...`.

---

### 7.1 `ai4j-cli run` — one-shot Agent Blueprint entry

`run` (corresponding to `AgentBlueprintRunCommand`) runs a declarative Agent Blueprint YAML once. It does not enter an interactive session, making it suitable for scripting, CI, or one-off tasks:

```bash
ai4j-cli run agent.yaml --input "Answer based on the knowledge base" --provider openai --protocol responses
```

Usage:

```text
ai4j-cli run <agent.yaml> --input <text> [options]
ai4j-cli run <agent.yaml> --prompt <text> [options]
```

`--input` and `--prompt` are aliases; pick one — it is required (or supply it via env).

Options:

| Option | Description |
| --- | --- |
| `--input` / `--prompt <text>` | User input for this run (required) |
| `--provider <name>` | Overrides `model.provider` in the YAML |
| `--protocol <chat\|responses>` | Overrides the protocol |
| `--model <name>` | Overrides `model.model` in the YAML |
| `--profile <name>` | References host-side provider profile metadata |
| `--api-key <key>` | Host runtime key; prefers env / config |
| `--base-url <url>` | Runtime base URL compatible with the provider |
| `--workspace <path>` | Workspace for provider/profile configuration lookup |
| `--allow-sandbox-declaration` | Accepts the `sandbox.enabled` declaration in the YAML without actually creating a sandbox |
| `--verbose` | Prints the full stack trace on unexpected failure |
| `-h` / `--help` | Prints help |

Environment variables: `AI4J_AGENT_INPUT`, `AI4J_PROVIDER`, `AI4J_PROTOCOL`, `AI4J_MODEL`, `AI4J_API_KEY`, `AI4J_BASE_URL`, plus provider-specific keys (e.g. `OPENAI_API_KEY` / `ZHIPU_API_KEY` / `MINIMAX_API_KEY`).

:::note YAML does not store secrets
`AgentFactory` is supplied by the host: the YAML file holds no secrets, installs no plugins, and creates no real sandbox session. Runtime credentials come from the host's env / config.
:::

---

### 7.2 `ai4j-cli extension` — extension package inspection and execution

`extension` (corresponding to `CliExtensionCommand`) inspects, assembles, and runs AI4J extension packages discovered on the classpath (matching extension API version `2.4.2`, groupId `io.github.lnyo-cly`).

```text
ai4j-cli extension list
ai4j-cli extension inspect <id> [--runtime]
ai4j-cli extension plan <id> [--enable] [activation options]
ai4j-cli extension check <id> --enable [activation options]
ai4j-cli extension init <directory> --id <extension-id> --package <java-package> [options]
ai4j-cli extension validate <id>|--all
ai4j-cli extension run --enable <extension-id> [--allow-command <command>] <command> [arguments...]
ai4j-cli extension resource --enable <extension-id> [--allow-skill <name>|--allow-prompt <name>] <skill|prompt> <name>
```

Subcommands:

| Subcommand | Purpose |
| --- | --- |
| `list` | Lists discovered extension manifests |
| `inspect <id>` | Shows manifest, permissions, config prefix, and source class; `--runtime` additionally lists contributed tools / commands / skills / prompts / guardrails / lifecycle hooks |
| `plan <id>` | Previews the enable / expose / allow activation state without actually wiring into the host |
| `check <id> --enable` | Validates and fails when a requested activation resource is not ready (pass/fail gate) |
| `init <directory>` | Generates a Maven Java 8 plugin skeleton locally |
| `validate <id>\|--all` | Validates manifests, runtime resources, and authoring contracts |
| `run --enable <id> <command>` | Executes a command from an explicitly enabled extension |
| `resource --enable <id> <skill\|prompt> <name>` | Prints the resource content from an enabled extension |

Activation options (for `plan` / `check` / `run` / `resource`): `--expose-tool`, `--allow-command`, `--allow-skill`, `--allow-prompt`, `--allow-guardrail`, `--strict`.

:::warning Running extension commands requires explicit --enable
Classpath discovery does not auto-enable extensions. Executing extension commands or reading resources always requires `--enable <id>` first, to avoid implicitly executing arbitrary extension code.
:::

`init` options: `--id` (required, e.g. `weather-pack`), `--package` (required, e.g. `com.example.ai4j.weather`), `--name`, `--group-id` (defaults to `--package`), `--artifact-id` (defaults to `--id`), `--version` (default `1.0.0`), `--class-name`, `--vendor`.

---

## 8. Full reference for `code` / `tui` CLI flags

Below is the complete flag list supported by the `code` / `tui` commands (`CodeCommandOptionsParser`). All flags accept both `--name value` and `--name=value` forms; boolean flags can be written as just `--name` (equivalent to true).

### Model and protocol

| Flag | Default | Description |
| --- | --- | --- |
| `--model <name>` | — | Model name, **required** (unless supplied by profile / env) |
| `--provider <name>` | openai | Provider, e.g. openai / zhipu / minimax / doubao / dashscope |
| `--protocol <chat\|responses>` | Derived from provider/baseUrl | Does not accept `auto` |
| `--api-key <key>` | — | API key; prefers env / config |
| `--base-url <url>` | — | Base URL compatible with the provider |

### Workspace and prompts

| Flag | Default | Description |
| --- | --- | --- |
| `--workspace <path>` | Current directory | Workspace root directory |
| `--workspace-description <text>` | — | Workspace description text |
| `--system <text>` | — | Appends to the system prompt |
| `--instructions <text>` | — | Appends instructions |
| `--prompt <text>` | — | One-shot prompt; supplying it triggers one-shot mode |
| `--allow-outside-workspace` | false | Whether tools may write outside the workspace |
| `--ui <cli\|tui>` | cli | The `tui` command forces `tui` |

### Sampling and generation

| Flag | Default | Description |
| --- | --- | --- |
| `--max-steps <n>` | 0 (unlimited) | Maximum number of steps in the agent loop |
| `--temperature <0..2>` | — | Sampling temperature |
| `--top-p <0..1>` | — | Nucleus sampling |
| `--max-output-tokens <n>` | — | Maximum output tokens per request |
| `--parallel-tool-calls` | false | Whether parallel tool calls are allowed |
| `--stream` | true | Whether to stream model requests |

### Sessions

| Flag | Default | Description |
| --- | --- | --- |
| `--no-session` | false (falls back to env/property when unspecified) | true = in-memory session only, not persisted |
| `--auto-save-session` | true | Whether to auto-save the session |
| `--session-id <id>` | — | Specifies the session id |
| `--resume <id>` / `--load <id>` | — | Resumes a saved session (`--load` is an alias) |
| `--fork <id>` | — | Forks a new branch from an existing session |
| `--session-dir <path>` | `<workspace>/.ai4j/sessions` | Session storage directory |

:::warning Session flags are mutually exclusive
`--resume` and `--fork` cannot be used together; `--no-session` cannot be combined with `--resume` or `--fork`.
:::

### Approval and compaction

| Flag | Default | Description |
| --- | --- | --- |
| `--approval <safe\|manual\|auto>` | safe | Approval mode; see [Tools and the approval mechanism](/docs/products/coding-agent/tools-and-approvals) |
| `--auto-compact` | true | Whether auto-compaction is enabled |
| `--compact-context-window-tokens <n>` | 128000 | Context window used for compaction |
| `--compact-reserve-tokens <n>` | 16384 | Tokens reserved during compaction |
| `--compact-keep-recent-tokens <n>` | 20000 | Most-recent tokens retained during compaction |
| `--compact-summary-max-output-tokens <n>` | 400 | Maximum output tokens for the compaction summary |

### Display

| Flag | Default | Description |
| --- | --- | --- |
| `--theme <name>` | — | TUI theme |
| `--verbose` | false | Verbose output |
| `-h` / `--help` | — | Prints help |

Most flags can also be set equivalently via environment variables (`AI4J_*`) or Java properties (`ai4j.*`), e.g. `AI4J_MODEL`, `AI4J_WORKSPACE`, `AI4J_STREAM`, `AI4J_APPROVAL`, `AI4J_SESSION_DIR`, etc.

---

## 9. `/sandbox` and `/extension` slash commands

These two slash commands are used inside the CLI/TUI to switch the execution environment at runtime and to inspect extensions; both are implemented (no longer planned capabilities).

### `/sandbox`

Manages the sandbox binding for the current session (resolver is `CliSandboxCommand`). Command actions:

```text
/sandbox                                Show the current sandbox binding state
/sandbox status                         Same as above
/sandbox enable <provider> [options]    Create/bind a sandbox and route bash exec through it
/sandbox attach <provider> <id> [options]  Bind an existing sandbox
/sandbox disable                        Release the current sandbox binding and return to local execution
```

Options for `enable` / `attach`:

| Option | Description |
| --- | --- |
| `<provider>` | Sandbox provider; currently supports `daytona` |
| `<id>` (attach) | The sandbox id or name to bind |
| `--workspace` / `--sandbox-name <name>` | Sandbox name / workspace |
| `--sandbox-id <id>` | Explicit sandbox id |
| `--image` / `--snapshot <snapshot>` | Sandbox image / snapshot |
| `--delete-on-close` | Delete the sandbox when the CLI closes or on disable |
| `--keep-on-close` | Keep the sandbox (default) |
| `--create-if-missing` / `--no-create-if-missing` | Whether to create the sandbox when the attach target is missing |

:::note Credentials are not passed on the command line
Sandbox credentials must come from environment variables or local configuration; slash-command parameters are **not accepted** — this avoids leaking keys into shell history.
:::

Once a sandbox is bound, `bash action=exec` is routed to `SandboxSession.execute(...)`, and the result carries `executionEnvironment`, `sandboxSessionId`, and `sandboxProviderId`. For the full boundary, see [Sandbox Routing](/docs/products/coding-agent/sandbox-routing).

### `/extension` / `/extensions`

Inspects / runs extension packages on the classpath from within a session; it is the in-session counterpart to the top-level `ai4j-cli extension`:

```text
/extensions                              List discovered extension plugins
/extension list                          List extensions
/extension inspect <id>                  View manifest and runtime resources
/extension plan <id> [activation options]   Preview activation state
/extension check <id> --enable [options]    pass/fail activation gate
/extension validate <id>|--all           Validate extension contracts
/extension run --enable <id> <command> [args]   Run an extension command
/extension resource --enable <id> <skill|prompt> <name>  Read an extension resource
```

`/extension` ships with completion: second-level actions (list / inspect / plan / check / validate / run / resource), resource types (skill / prompt), and activation options (`--enable` / `--extension` / `--expose-tool`, etc.) are all in the completion candidates.

---

## 10. Further reading

If you are not just looking something up but want to understand the usage behind these commands, see:

1. [CLI / TUI usage guide](/docs/products/coding-agent/cli-and-tui)
2. [Configuration system](/docs/products/coding-agent/configuration)
3. [MCP and ACP](/docs/products/coding-agent/mcp-and-acp)
4. [Sessions, streaming, and processes](/docs/products/coding-agent/session-runtime)
5. [Lifecycle hooks and workspace trust](/docs/products/coding-agent/lifecycle-hooks)
