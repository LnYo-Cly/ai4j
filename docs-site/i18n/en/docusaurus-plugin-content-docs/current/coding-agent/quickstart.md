---
sidebar_position: 2
title: "Coding Agent Quickstart"
description: "The shortest path to get ai4j-cli running: build the fat jar, run a one-shot and a persistent session, and verify session, workspace, and provider/model state for a minimum validation."
tags: [how-to]
---

# Coding Agent Quickstart

This page does one thing:

- Get `ai4j-cli` running via the shortest path

But the "shortest path" should not be a single command line — it should also tell you what actually happens behind each entry point. Otherwise, the moment something behaves differently from what you expect, you won't know where to start debugging.

---

## 1. Know the real entry points first

`Ai4jCli` currently exposes six top-level subcommands:

- `code` — the CLI host for a coding session (most commonly used)
- `tui` — equivalent to `code --ui tui`
- `acp` — starts the coding session as an ACP stdio server
- `run` — runs a single-agent Agent Blueprint YAML (does not enter an interactive session)
- `extension` — inspects / assembles / runs extension packages on the classpath
- `trust` — manages the trust directory for workspace hooks

This quickstart only covers the first three **session-style entry points** (`code` / `tui` / `acp`); `run`, `extension`, and `trust` are one-shot tool-style subcommands — see [Command Reference §7](/docs/coding-agent/command-reference) for full details.

It also has two behaviors that are easy to overlook:

- If you write `ai4j-cli --model ...` directly, it is treated as `code` by default
- `tui` is essentially just `code` with `--ui tui` added on top

So the relationship between the session-style entry points can be compressed to:

```text
ai4j-cli code ...   -> coding session CLI host
ai4j-cli tui ...    -> code --ui tui
ai4j-cli acp ...    -> ACP stdio server
```

This shows that `tui` is not a separate runtime, but rather another host mode of the same command.

---

## 2. Build the artifact in this repo that you can actually run directly

Build command:

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

The artifact most worth using directly right now is:

```text
ai4j-cli/target/ai4j-cli-2.4.3-SNAPSHOT-jar-with-dependencies.jar
```

Why this file instead of the plain jar:

- `ai4j-cli/pom.xml` produces `jar-with-dependencies` via `maven-assembly-plugin`
- The manifest main class is `io.github.lnyocly.ai4j.cli.Ai4jCliMain`
- This artifact bundles the CLI dependencies, making it more suitable for a direct `java -jar`

So the most reliable "fastest path to running" today is:

- Build the fat jar first
- Then run it directly with `java -jar`

The current source tree version is `2.4.3-SNAPSHOT`, so the artifact name above uses that version. When using a released binary, replace the filename with the actual release version; see [Release and Artifacts](/docs/reference/release-and-artifacts) for the current release coordinates.

---

## 3. Minimal one-shot

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.4.3-SNAPSHOT-jar-with-dependencies.jar code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

The key point of this command is not just "it has `--prompt`", but rather:

- When `CodeCommand` sees `--prompt`, it takes the one-shot interaction form
- It does not enter a persistent REPL
- It is not TUI either

Suitable scenarios:

- First-time verification that provider / key / protocol / model are wired up
- A minimal smoke test
- When you don't need to reuse a session

If even this step fails, don't rush to blame session, MCP, skills, or TUI.

Prioritize checking:

- provider / api key
- whether the protocol matches the provider
- whether the model name is correct

---

## 4. Persistent CLI session

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.4.3-SNAPSHOT-jar-with-dependencies.jar code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

The real difference from one-shot is:

- Here `--prompt` is not passed
- `CodeCommand` enters persistent interactive mode

Also keep the current default session semantics in mind:

- A session is persisted by default
- The default session store is at `<workspace>/.ai4j/sessions`
- Only if you explicitly pass `--no-session` does it switch to memory-only

So a persistent CLI session is not a "pure in-memory REPL" — it is a workflow entry point with a session store.

Once inside, the most commonly used commands are typically:

- `/provider`
- `/model`
- `/stream`
- `/skills`
- `/mcp`
- `/sessions`
- `/history`
- `/processes`

---

## 5. Shortest path to the TUI entry point

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.4.3-SNAPSHOT-jar-with-dependencies.jar tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

The most common misunderstanding here is:

- `tui` is not a separate command set
- It is essentially `code --ui tui`

Common keys at the interaction layer include:

- `/` opens the slash command list
- `Tab` accepts completion
- `Ctrl+P` opens the palette
- `Ctrl+R` opens replay
- `Esc` interrupts the current turn or closes a panel

But don't mistake these keys for "standalone runtime capabilities".

The real session, tool, approval, MCP, and process management is still handled by the same coding runtime.

---

## 6. Shortest path to the ACP entry point

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.4.3-SNAPSHOT-jar-with-dependencies.jar acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

This command does not start a terminal REPL — it starts an ACP stdio server.

The current convention is:

- `stdin/stdout` carries newline-delimited JSON-RPC
- `stderr` is only for logs and warnings

And it shares the same provider / model / workspace parsing rules as `code`.

This means:

- A provider/protocol issue you can reproduce under the CLI will usually reproduce the same way under ACP
- ACP is not a special entry point that bypasses the configuration system

---

## 7. If you don't write `--protocol` explicitly, where does the default come from

One of the easiest traps in the quickstart is the protocol default value.

The current local rules are:

- `openai` with an empty baseUrl or on the official host defaults toward `responses`
- `openai` on a custom compatible host defaults toward `chat`
- `doubao` / `dashscope` default toward `responses`
- Other providers default to `chat`

So:

- For a quickstart on the official OpenAI host, the safest choice is `responses`
- On an OpenAI-compatible host, don't assume the official OpenAI default still applies

:::tip When unsure about the protocol
If you're unsure, the safest approach is still:

- Pin `--protocol` explicitly during the quickstart phase
:::

---

## 8. After it runs the first time, what to verify immediately

A minimal success does not mean "the whole system is fine".

After the first successful run, do these checks right away:

### 8.1 Whether the session was actually persisted to disk

Check:

```text
<workspace>/.ai4j/sessions
```

### 8.2 Whether the current effective provider / model values are as expected

In a persistent CLI / TUI session, run:

```text
/provider
/model
```

### 8.3 Whether the workspace is bound to the directory you intended

Run:

```text
/session
```

### 8.4 Whether MCP reported any warnings on startup

If you have MCP configured, watch the terminal or `stderr` on startup for:

- `Warning: MCP unavailable: ...`

---

## 9. The 6 most common pitfalls

### 9.1 Using the plain jar instead of the fat jar

The most reliable artifact to run directly right now is `jar-with-dependencies`.

### 9.2 Mistaking `tui` for another agent

It is just an alias entry point for `code --ui tui`.

### 9.3 Assuming that omitting `--prompt` is still one-shot

It's the opposite: omitting `--prompt` is what enters persistent interactive mode.

### 9.4 Forgetting `--workspace`

Although the current directory is used by default, in real work it's best to write it explicitly — especially under ACP and in multi-repo environments.

### 9.5 Carrying over official OpenAI protocol expectations to an OpenAI-compatible host

A custom `baseUrl` affects the default protocol choice.

### 9.6 Assuming a successful quickstart means approval, MCP, session, and team are all fine

A quickstart only proves the minimal main path is wired up; it does not replace full runtime validation.

---

## 10. Recommended shortest validation order

If you're integrating for the first time, the safest order is:

1. Run one-shot first to confirm provider / protocol / model are wired up
2. Then run a persistent CLI session to confirm session store, slash commands, and workspace binding work
3. Then run TUI to confirm the terminal interaction layer fits your usage
4. Finally bring in ACP, MCP, approval, and experimental subagent / team capabilities

This makes troubleshooting clearest, because you're not piling every variable into the first round of validation all at once.

---

## 11. The conclusions most worth remembering from this page

- The session-style entry points are `code`, `tui`, and `acp`; in addition, `ai4j-cli` has three one-shot tool-style subcommands: `run`, `extension`, and `trust` (see [Command Reference](/docs/coding-agent/command-reference))
- `tui` is just a host alias for `code --ui tui`, not a separate runtime
- The most reliable direct artifact for the quickstart is `ai4j-cli-<version>-jar-with-dependencies.jar`
- `--prompt` decides one-shot; omitting `--prompt` is what enters a persistent session
- After the quickstart runs, the next step is to verify session, workspace, and provider/model state — not to pile on more features immediately

---

## 12. Recommended next steps

1. [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui)
2. [Configuration System](/docs/coding-agent/configuration)
3. [Sessions, Streaming, and Processes](/docs/coding-agent/session-runtime)
4. [Tools and Approval Mechanism](/docs/coding-agent/tools-and-approvals)
5. [MCP Integration](/docs/coding-agent/mcp-integration)
6. [ACP Integration](/docs/coding-agent/acp-integration)
