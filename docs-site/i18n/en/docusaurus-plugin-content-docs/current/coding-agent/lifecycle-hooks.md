---
sidebar_position: 13
title: "Lifecycle Hooks and Workspace Trust"
description: "Explains Claude-Code-style shell-command lifecycle hooks (PreToolUse/PostToolUse/UserPromptSubmit/Stop/PreCompact/SessionStart/SessionEnd): their configuration, intercept vs. observe routing, and exit code protocol, plus the workspace trust gate and the ai4j-cli trust command that let these hooks load safely."
tags: [how-to]
---

# Lifecycle Hooks and Workspace Trust

The AI4J CLI lets you attach **external shell commands** to lifecycle events of the coding agent — i.e. Claude-Code-style end-user hooks.

But hooks execute arbitrary shell commands, so they must sit behind a trust gate: **workspace directories that the user has not explicitly trusted never get their hooks loaded.** These two things are a single paired design and must be understood together.

---

## 1. Seven hook events, split into two tiers

Hooks are declared in the `hooks` field of the workspace configuration `<workspace>/.ai4j/workspace.json` and parsed by `CliHooksConfig`. Seven event types are currently supported, split into two tiers by "can it halt the flow?":

| Event | Claude Code equivalent | Interceptable (can block) | Implementation entry |
| --- | --- | --- | --- |
| `preToolUse` | PreToolUse | Yes (can block / modify) | `CliHookInterceptor.beforeToolCall` |
| `postToolUse` | PostToolUse | Yes (can block feedback) | `CliHookInterceptor.afterToolCall` |
| `userPromptSubmit` | UserPromptSubmit | Yes (can block / modify) | `CliPromptInterceptor.beforePrompt` |
| `stop` | Stop | No (observe only) | `CliLifecycleHookBridge` (AFTER_TURN) |
| `preCompact` | PreCompact | No (observe only) | `CliLifecycleHookBridge` (ON_COMPACT) |
| `sessionStart` | SessionStart | No (observe only) | `CliLifecycleHookBridge` (SESSION_START) |
| `sessionEnd` | SessionEnd | No (observe only) | `CliLifecycleHookBridge` (SESSION_END) |

The key distinction:

- **Intercepting hooks** (`preToolUse` / `postToolUse` / `userPromptSubmit`) can change the flow: they can block the call or modify its inputs.
- **Observing hooks** (`stop` / `preCompact` / `sessionStart` / `sessionEnd`) only run for side effects: their output is discarded and any thrown exception is swallowed. **Observing hooks must never interrupt normal execution.**

Separating these two tiers is the real landing of the Claude-Code hook model in AI4J — it is not a single set of callbacks.

---

## 2. How to configure a hook

Hooks are declared in the `hooks` field of `<workspace>/.ai4j/workspace.json`. Each hook is an object with:

- `command`: the shell command to execute (required)
- `match`: the tool name to match (only meaningful for `preToolUse` / `postToolUse`)

The `match` semantics live in `CliHookEntry.matches(...)`:

- Omitted / empty string / `"*"`: matches all tools
- Otherwise requires an exact tool-name match (e.g. `"bash"`)

A minimal configuration example:

```json
{
  "hooks": {
    "preToolUse": [
      { "command": "python ~/.ai4j/hooks/guard.py", "match": "bash" }
    ],
    "userPromptSubmit": [
      { "command": "python ~/.ai4j/hooks/prompt_guard.py" }
    ],
    "sessionStart": [
      { "command": "echo 'session started' >> ~/.ai4j/session.log" }
    ]
  }
}
```

This configuration will:

- Run `guard.py` only when the model is about to call the `bash` tool
- Run `prompt_guard.py` every time the user submits a prompt
- Append a line to the log on session start (observing hook; does not affect the flow)

---

## 3. How the command is actually executed

The actual process spawning is done by `ProcessHookCommandRunner`:

- Unix uses `sh -c <command>`
- Windows uses `cmd /c <command>`
- Writes the event JSON (tool call / prompt / lifecycle event) to the child process's stdin
- Captures exit code, stdout, and stderr and returns them

In other words, hooks are **real child processes**, not in-JVM callbacks. This lets users write hooks in any language (python / node / bash scripts) without writing Java.

The decision logic (how the exit code maps to allow / block / modify) is deliberately placed in `CliHookInterceptor` / `CliPromptInterceptor`; the runner only "runs the command, collects the result", so tests can swap in a fake runner.

---

## 4. Exit code protocol (intercepting hooks)

For intercepting hooks, the exit code and stdout determine the flow. This protocol matches Claude Code's PreToolUse:

| Exit case | Result |
| --- | --- |
| **exit 2** | block (refuse to execute); reason taken from stderr, or stdout if empty |
| **exit 0 + stdout JSON** `{"decision":"block","reason":"..."}` | block |
| **exit 0 + stdout JSON** `{"decision":"modify","name":"...","arguments":"..."}` (tool hook) | modify the inputs of this tool call |
| **exit 0 + stdout JSON** `{"decision":"modify","input":"..."}` (prompt hook) | modify the user input |
| **exit 0 / other** | continue evaluating the next hook (provisional allow) |
| **the hook itself throws / crashes** | fail-closed: block (with a reason) |

The merge rule is: **the first block wins; otherwise the first modify wins; otherwise the call is allowed.**

:::warning Hook crash = block
Intercepting hooks are safety hooks. A crashing hook must not let a tool "slip through" — so when a hook throws, it is treated as fail-closed block, not allow. If you write a hook that can fail intermittently, make sure it exits with 0/1 (soft error, flow continues) instead of throwing.
:::

---

## 5. Why observing hooks must never block

`CliLifecycleHookBridge` routes `stop` / `preCompact` / `sessionStart` / `sessionEnd` to external commands, but its semantics are pure side effect:

- The command's stdout is discarded
- Exceptions thrown by the command are swallowed (`catch (Exception ignored)`)

This is by design. In Claude Code these events are "post-hoc notification / context injection", not "decision points". If you need to block a tool call, use `preToolUse`, not `stop`.

Typical observing-hook uses:

- `stop`: send a notification at end of a turn, write an audit log
- `preCompact`: back up a context snapshot before compact
- `sessionStart` / `sessionEnd`: record session-lifecycle metrics

---

## 6. Workspace Trust Gate

Hooks execute arbitrary shell commands, so loading them must pass a trust gate: `WorkspaceTrustGate`.

### 6.1 Trust flow

`DefaultCodingCliAgentFactory.attachToolHooks(...)` calls the trust gate before wiring hooks. The flow is:

1. If the workspace declares no hooks → `NO_HOOKS`, pass through (no trust needed).
2. If the directory is already in `~/.ai4j/trusted-dirs.txt` → `TRUSTED`, pass through.
3. Otherwise, print the full hook configuration for review and prompt `y/n`:
   - Enter `y`: persist the directory into `trusted-dirs.txt`, return `TRUSTED`
   - Enter `n`: return `UNTRUSTED`, **hooks will not be loaded this run**
   - Read EOF / read failure: return `UNTRUSTED` (fail-closed)

In other words: **workspace hooks only take effect when the user has explicitly confirmed in the terminal, or has previously trusted this directory.**

### 6.2 ANSI escapes are stripped when hooks are displayed

`WorkspaceTrustGate.sanitizeForDisplay(...)` strips ANSI escape sequences (including the OSC sequences that set the terminal title) before printing hook commands.

This is an anti-injection measure: a malicious workspace.json cannot hide the real command inside display text via terminal control codes. The command string you see in the review prompt is exactly the string the child process will execute — it cannot be disguised by escape codes.

### 6.3 Where the trust record lives

The trust record is managed by `TrustedDirsStore` and stored in `~/.ai4j/trusted-dirs.txt`:

- One normalized absolute path per line
- Lines starting with `#` are comments
- A missing file = no directory is trusted
- Paths are compared after normalization via `toAbsolutePath().normalize()`

---

## 7. The `ai4j-cli trust` command

Manage trust directories manually with `ai4j-cli trust` (backed by `TrustCommand`). This is especially useful in CI / automation scenarios — you can pre-trust a workspace and avoid the interactive `y/n` prompt.

```text
ai4j-cli trust --dir <path>      Trust a workspace directory
ai4j-cli trust --revoke <path>   Revoke trust for a directory
ai4j-cli trust --list            List all trusted directories
```

Examples:

```bash
# Pre-trust a CI workspace so hooks load with no interaction
ai4j-cli trust --dir /home/runner/work/my-repo

# Revoke trust (next entry into that workspace re-triggers the review)
ai4j-cli trust --revoke /home/runner/work/my-repo

# See which directories are trusted on this machine
ai4j-cli trust --list
```

Notes:

- `--dir` and `--add` are equivalent; `--revoke` and `--remove` are equivalent
- Paths are normalized to absolute before being written
- `--revoke` on a directory that was never trusted does not error; it just reports "was not trusted"
- The trust record always lives in `~/.ai4j/trusted-dirs.txt`

---

## 8. Where hooks attach on the agent builder

Looking at the wiring chain: once the trust gate passes, the three hook kinds attach to different extension points on `CodingAgentBuilder`:

```text
DefaultCodingCliAgentFactory.attachToolHooks(...)
  -> WorkspaceTrustGate.checkTrust(...)         # not wired if the gate fails
  -> builder.toolInterceptor(CliHookInterceptor)        # preToolUse / postToolUse
  -> builder.promptInterceptor(CliPromptInterceptor)    # userPromptSubmit (if any)
  -> builder.lifecycleHook(CliLifecycleHookBridge)      # observing (if any)
```

Three details to note:

1. **No hooks, no wiring**: when `hasPromptHooks()` / `hasObserveHooks()` is false, the corresponding extension point is not wired at all — no idle overhead.
2. **The trust gate only gates hooks**: a failed trust gate affects only hook loading; it does not stop the session itself from starting. The session still runs normally, just without external hooks.
3. **Intercepting hooks go through ToolInterceptor / PromptInterceptor; observing hooks go through AgentLifecycleHook** — these are two different extension SPIs; don't conflate them.

---

## 9. A complete "block dangerous commands" example

Below is a minimal example of using a `preToolUse` hook to block dangerous bash commands.

`<workspace>/.ai4j/workspace.json`:

```json
{
  "hooks": {
    "preToolUse": [
      { "command": "python ~/.ai4j/hooks/block_rm_rf.py", "match": "bash" }
    ]
  }
}
```

`~/.ai4j/hooks/block_rm_rf.py` (reads the tool-call JSON from stdin; exits 2 if it matches a dangerous pattern):

```python
import sys, json

call = json.load(sys.stdin)
args = json.loads(call.get("arguments", "{}"))
cmd = args.get("command", "")

if "rm -rf" in cmd:
    # exit 2 = Claude-Code-style deny
    sys.stderr.write("blocked: refused destructive command -> " + cmd)
    sys.exit(2)

# Otherwise exit 0: continue evaluation / allow
sys.exit(0)
```

On first entry to that workspace, the CLI prints this hook and asks for `y/n` confirmation; `ai4j-cli trust --dir <workspace>` can pre-skip this step.

---

## 10. Most common pitfalls

### 10.1 Treating observing hooks as intercepting hooks

Output and exceptions from `stop` / `preCompact` / `sessionStart` / `sessionEnd` are swallowed. To halt the flow you must use `preToolUse` / `postToolUse` / `userPromptSubmit`.

### 10.2 Assuming hooks load by default

In an untrusted workspace, hooks are not loaded. This is a safe default, not a bug. To pre-load, run `ai4j-cli trust --dir`.

### 10.3 Letting a safety hook throw

An intercepting hook that throws = fail-closed block. If your hook is only an "optional check", exit with 1 on failure (soft error, flow continues) instead of crashing the process.

### 10.4 Assuming `match` takes a regex

`match` currently supports only exact tool-name matching (or `*` / empty) — no regex, no comma-separated tool lists. To intercept multiple tools, declare multiple hooks.

### 10.5 Relying on interactive input inside a hook command

Hooks are child processes whose stdin is already occupied by the event JSON; you cannot use it to read user input. Any interactive logic should stay in the CLI body.

---

## 11. The takeaways from this page

- Hooks are **external shell commands**, split into **intercepting** (preToolUse / postToolUse / userPromptSubmit; can block/modify) and **observing** (stop / preCompact / sessionStart / sessionEnd; pure side effect) tiers.
- Intercepting hooks follow the Claude-Code exit code protocol: exit 2 = block, stdout JSON can block/modify, crash = fail-closed block.
- Hooks are declared in the `hooks` field of `<workspace>/.ai4j/workspace.json`.
- **Hooks must pass `WorkspaceTrustGate` before loading**: untrusted workspace hooks never take effect. Pre-trust with `ai4j-cli trust --dir`, revoke with `--revoke`; the record lives in `~/.ai4j/trusted-dirs.txt`.
- The trust gate strips ANSI escapes when printing hooks, preventing configuration from hiding the real command via terminal control codes.

---

## 12. Further reading

1. [CLI / TUI usage guide](/docs/coding-agent/cli-and-tui)
2. [Tools and the approval mechanism](/docs/coding-agent/tools-and-approvals)
3. [Command reference](/docs/coding-agent/command-reference)
4. [Configuration system](/docs/coding-agent/configuration)
