---
sidebar_position: 5
title: "Configuration System"
description: "Explains the three layers of Coding Agent configuration (global provider assets, global MCP definitions, workspace bindings), per-field resolution precedence, and which changes trigger a rebind of the current session runtime."
tags: [reference]
---

# Configuration System

The configuration layer of `Coding Agent` does not end at "filling in the provider and API Key."

In the current implementation, configuration actually controls three things:

- Which provider / protocol / model this session ultimately connects to
- Which layer each of these values comes from: CLI, environment, global assets, or workspace bindings
- After a configuration change, whether it only mutates JSON or rebuilds the current session runtime

If you want to understand this system from the source, the entry points most worth reading directly are:

- `ai4j-cli/.../provider/CliProviderConfigManager`
- `ai4j-cli/.../mcp/CliMcpConfigManager`
- `ai4j-cli/.../config/CliWorkspaceConfig`
- `ai4j-cli/.../factory/DefaultCodingCliAgentFactory`
- `ai4j-cli/.../runtime/CodingCliSessionRunner`
- `ai4j-cli/.../acp/AcpJsonRpcServer`

---

## 1. Start with the real configuration chain

On the CLI / TUI path, a configuration resolution roughly goes through this chain:

```text
Ai4jCli / CodeCommand
  -> CodeCommandOptionsParser.parse(...)
  -> CliProviderConfigManager.resolve(...)
  -> CliMcpConfigManager.resolve(...)
  -> DefaultCodingCliAgentFactory.resolveProtocol(...)
  -> buildWorkspaceContext(...)
  -> buildCodingOptions(...)
  -> CodingAgentBuilder.build()
```

The ACP path follows the same base rules; it just enters from `AcpCommand` / `AcpJsonRpcServer` instead of the terminal runner.

This chain has an important consequence:

- The "effective value" of provider / protocol / model is not produced by a single file directly
- It is the result computed jointly by the resolver, workspace config, global config, and runtime overrides

---

## 2. The current configuration surface is split into three layers

### 2.1 Global provider assets

Path:

```text
~/.ai4j/providers.json
```

Determined by `CliProviderConfigManager.globalProvidersPath()`.

It stores:

- `defaultProfile`
- `profiles`

In other words, "which reusable provider profiles exist on this machine," not which one a given repo is currently bound to.

### 2.2 Global MCP definitions

Path:

```text
~/.ai4j/mcp.json
```

Determined by `CliMcpConfigManager.globalMcpPath()`.

It answers:

- Which MCP servers are defined on this machine

It does not directly answer:

- Which MCP servers are enabled for the current repo

### 2.3 Workspace bindings

Path:

```text
<workspace>/.ai4j/workspace.json
```

Both `CliProviderConfigManager` and `CliMcpConfigManager` land in this file.

It currently carries repo-level bindings and toggles, for example:

- `activeProfile`
- `modelOverride`
- `enabledMcpServers`
- `skillDirectories`
- `agentDirectories`
- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

The core idea here is:

- Global files store "asset definitions"
- The workspace file stores "per-repo selection and local overrides"

---

## 3. `workspace.json` is not a runtime state directory

Currently `<workspace>/.ai4j/` typically contains more than one file:

```text
<workspace>/.ai4j/
  workspace.json
  sessions/
  teams/
    state/
    mailbox/
```

Keep them clearly distinct:

- `workspace.json`: human-maintained repo-level configuration
- `sessions/`: session snapshots and event ledgers
- `teams/state`: team runtime-state snapshots
- `teams/mailbox`: member message streams

That is:

- `workspace.json` is the control plane
- `sessions/` and `teams/` are runtime artifacts

If you conflate these, it is easy to mistake "persisted state" for "static configuration."

---

## 4. Provider resolution is not a whole-object merge, but per-field evaluation

`CliProviderConfigManager.resolve(...)` does not first merge several JSON blobs into one big object and then read from it.

It walks the precedence chain separately for each field.

### 4.1 `provider`

The current order is:

1. CLI explicit `--provider`
2. `activeProfile.provider`
3. `defaultProfile.provider`
4. `AI4J_PROVIDER`
5. `ai4j.provider`
6. Default value `openai`

### 4.2 `baseUrl`

The current order is:

1. CLI explicit `--base-url`
2. `activeProfile.baseUrl`
3. `defaultProfile.baseUrl`
4. `AI4J_BASE_URL`
5. `ai4j.base-url`

### 4.3 `protocol`

The current order is:

1. CLI explicit `--protocol`
2. `activeProfile.protocol`
3. `defaultProfile.protocol`
4. `AI4J_PROTOCOL`
5. `ai4j.protocol`
6. If empty or `auto`, fall back to `CliProtocol.defaultProtocol(...)`

### 4.4 `model`

The current order is:

1. CLI explicit `--model`
2. `workspace.json.modelOverride`
3. `activeProfile.model`
4. `defaultProfile.model`
5. `AI4J_MODEL`
6. `ai4j.model`

The most important point here is:

- `modelOverride` takes precedence over the model baked into the profile

This lets a repo stay bound to a given provider profile while experimenting with a different model, without polluting the global profile.

### 4.5 `apiKey`

The current order is:

1. CLI explicit `--api-key`
2. `activeProfile.apiKey`
3. `defaultProfile.apiKey`
4. `AI4J_API_KEY`
5. `ai4j.api.key`
6. Provider-specific env, e.g. `OPENAI_API_KEY`

This shows the current implementation supports both:

- A generic key entry point
- A provider-specific env-var entry point

---

## 5. An explicit `--provider` affects whether the profile still participates in resolution

This is an easily overlooked but critical detail in the current implementation.

When `CliProviderConfigManager.resolve(...)` encounters an explicit `providerOverride`, it first runs:

- `alignProfileWithProvider(activeProfile, explicitProvider)`
- `alignProfileWithProvider(defaultProfile, explicitProvider)`

This means:

- If the current `activeProfile` is `zhipu`
- but you explicitly pass `--provider openai`

then this `zhipu` profile will no longer serve as a fallback for `baseUrl`, `model`, or `apiKey`.

It is treated as "no longer matching the current provider."

This behavior is sensible, because otherwise you could get:

- provider switched to `openai`
- while still secretly inheriting another provider profile's URL or key

That would make runtime behavior highly unpredictable.

---

## 6. `activeProfile`, `defaultProfile`, and `effectiveProfile` are not the same thing

`CliResolvedProviderConfig` retains all of:

- `activeProfile`
- `defaultProfile`
- `effectiveProfile`

They respectively mean:

- `activeProfile`: the profile name the workspace wants to bind to
- `defaultProfile`: the machine-level global default profile name
- `effectiveProfile`: the profile name actually used in resolution in the current context

The rule for `effectiveProfile` is:

- Prefer the workspace `activeProfile`, provided it actually exists
- Otherwise fall back to the global `defaultProfile`, provided it also exists
- Otherwise `null`

So:

- A non-existent profile name written in the workspace will not take effect by force
- The system falls back instead of silently fabricating a profile

---

## 7. Loaded configuration is normalized first

Currently, `loadProvidersConfig()`, `loadWorkspaceConfig()`, and `loadGlobalConfig()` do not "just parse the JSON as-is and finish."

They immediately run a normalize pass.

Common effects include:

- Trimming leading and trailing whitespace from fields
- Normalizing empty strings to `null`
- Removing blank profile names
- Clearing a non-existent `defaultProfile`
- `enabledMcpServers`, `skillDirectories`, and `agentDirectories` are deduplicated (drop empties, keep order)
- MCP `http` transport is normalized to `streamable_http`

So this system is closer to a "controlled configuration model" than an "arbitrary JSON store."

---

## 8. The protocol default has a local inference rule, not dynamic probing

`CliProtocol.defaultProtocol(provider, baseUrl)` is currently a static rule:

- `openai` and `baseUrl` is empty or contains `api.openai.com` -> `responses`
- `openai` with a custom compatible host -> `chat`
- `doubao` / `dashscope` -> `responses`
- Other providers -> `chat`

There is no remote capability probe behind this.

That is:

- The default protocol is a local inference result
- It does not ask the provider at runtime "which protocol do you support?"

---

## 9. "Can be inferred by default" does not equal "allowed at runtime"

Currently, `DefaultCodingCliAgentFactory.resolveProtocol(...)` and the provider/protocol validation in the ACP path add another layer of runtime restriction.

At present, `responses` is explicitly allowed only for:

- `openai`
- `doubao`
- `dashscope`

So there are currently two layers of checks:

1. Infer the default protocol
2. Validate whether the current provider supports this protocol

These two layers are separate in the implementation, so when troubleshooting, distinguish:

- Whether the default was inferred wrongly
- Or the default was inferred but runtime rejects this combination

---

## 10. `experimental*` is currently default-on

`CliWorkspaceConfig` has two easily overlooked boolean toggles:

- `experimentalSubagentsEnabled`
- `experimentalAgentTeamsEnabled`

The current logic in `DefaultCodingCliAgentFactory` is:

- When the field is `null`, treat it as `true`

So omitting these two fields does not turn the experimental capabilities off; it leaves them on by default.

They do not control provider connection; they control which experimental tools are injected into the current runtime:

- The experimental subagent tool surface
- The experimental team tool surface

---

## 11. Which changes trigger a rebind of the current session runtime

This is the biggest difference between the `Coding Agent` configuration system and ordinary SDK configuration docs.

On the current CLI / TUI path, commands like these do not merely edit files:

- `/provider use`
- `/provider add|edit|default|remove`
- `/model`
- `/stream on|off`
- `/experimental ...`
- `/mcp enable|disable|pause|resume|retry|remove`

These operations usually flow through:

```text
Mutate configuration or in-memory state
  -> resolveConfiguredRuntimeOptions(...)
  -> switchSessionRuntime(...)
  -> rebuild the current session host layer with new options / new MCP runtime
```

So this configuration system is essentially a live control plane.

It is not "save the config and wait for the next restart."

---

## 12. Which configuration is mutable in ACP

ACP mode does not replicate all CLI configuration capabilities.

Currently, the `configOptions` exposed by `AcpJsonRpcServer` has only two entries:

- `mode`
- `model`

The corresponding methods are:

- `session/set_mode`
- `session/set_config_option`

Where:

- `mode` controls the approval mode within an ACP session
- `model` controls the effective model for subsequent turns

This shows ACP currently leans toward "session-time control" rather than being a "full configuration file editor."

---

## 13. Which values are not in `workspace.json` at all

Many people intuitively assume that, since these are workspace bindings, `baseUrl` and `apiKey` should land there too.

But currently they do not.

`workspace.json` primarily carries:

- Profile bindings
- Model override
- MCP enablement
- Skill / agent roots
- Experimental toggles

Whereas the following values do not exist as persistent workspace fields:

- `apiKey`
- `baseUrl`
- Provider profile details

They still belong to:

- CLI override
- env / property
- Global profile assets

:::warning Do not commit credentials into the repo
This boundary is deliberate, because mixing sensitive credentials with repo bindings carries high risk.
:::

---

## 14. The most common failure paths

### 14.1 `activeProfile` points to a non-existent name

Result:

- It does not take effect as the profile you intended
- It falls back to `defaultProfile` or the env-var chain

### 14.2 After an explicit `--provider`, the original profile no longer matches

Result:

- The old profile's `baseUrl` / `model` / `apiKey` may no longer participate in evaluation

### 14.3 Custom OpenAI-compatible host, but forgetting the protocol default changes

Result:

- `openai + custom baseUrl` defaults to `chat`, not `responses` as on the official OpenAI host

### 14.4 Assuming that editing a config file requires restarting the entire CLI

Result:

- Many slash command paths actually rebuild the current session runtime directly
- Without understanding this, you may misjudge "why the config took effect immediately"

### 14.5 Mixing runtime strategy with connection configuration

Result:

- Mixing `provider/profile/model` issues with `auto-compact` / outer loop / tool approval issues makes troubleshooting painful

---

## 15. What does not belong on this page

This page covers:

- "Who to connect to"
- "Where it is resolved from"
- "Whether the runtime rebinds after a change"

It does not cover another set of equally important runtime strategy parameters:

- auto-compact
- compact window
- reserve tokens
- keep recent tokens
- approval behavior
- outer loop stop / continue behavior

These belong more to:

- `CodingAgentOptions`
- session / runtime behavior layer

Do not conflate provider/profile configuration with loop / compact strategy into a single mental model on one page.

---

## 16. The takeaways most worth remembering from this page

- The current configuration surface is split into three layers: global provider assets, global MCP definitions, and workspace bindings
- Configuration resolution is per-field evaluation, not a whole-object merge
- An explicit `--provider` drops non-matching profiles out of the resolution chain
- `modelOverride` takes precedence over the profile's model
- The protocol default is inferred by local rules, not by remote probing
- Many configuration commands rebuild the current session runtime directly, rather than waiting for the next launch to take effect

---

## 17. Further reading

1. [Provider profiles and model switching](/docs/coding-agent/provider-profiles)
2. [CLI / TUI usage guide](/docs/coding-agent/cli-and-tui)
3. [Command reference](/docs/coding-agent/command-reference)
4. [MCP integration](/docs/coding-agent/mcp-integration)
5. [Compact and checkpoint mechanism](/docs/coding-agent/compact-and-checkpoint)
