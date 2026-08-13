---
sidebar_position: 6
title: "Provider Profiles and Model Switching"
description: "Explains the real semantics of a provider profile as a switchable, fallback-capable provider runtime binding, covering activeProfile/effectiveProfile resolution, the /provider and /model commands, and session runtime rebinding."
tags: [concept]
---

# Provider Profiles and Model Switching

In the `Coding Agent`, a provider profile is not "a collection of commands" but rather a connection binding that can be switched, can fall back, and affects the current session runtime.

If you read it as just a JSON template, you will miss two key facts:

- profile resolution must be read together with `workspace.json.activeProfile`, `modelOverride`, and CLI override
- some `/provider` and `/model` operations rebuild the current session runtime directly, rather than only editing the config file

---

## 1. Where profile actually enters the activation chain

The current main chain can be condensed to:

```text
providers.json + workspace.json
  -> CliProviderConfigManager.resolve(...)
  -> CliResolvedProviderConfig
  -> DefaultCodingCliAgentFactory.resolveProtocol(...)
  -> createModelClient(...)
  -> CodingAgentBuilder.build()
```

Then at runtime, commands under CLI/TUI or ACP re-trigger this chain:

```text
/provider ... or /model ...
  -> modify providers.json / workspace.json or session config
  -> re-resolve the effective provider config
  -> switchSessionRuntime(...) / applyModelChange(...)
  -> rebind the current session runtime
```

So a provider profile in the current implementation is not "a static asset description" — it is part of a live binding.

---

## 2. A profile actually stores only five fields

The source entry point is:

- `ai4j-cli/.../provider/CliProviderProfile`

The fields are deliberately minimal:

- `provider`
- `protocol`
- `model`
- `baseUrl`
- `apiKey`

This means a profile describes exactly one thing:

- how to form a reusable set of provider connection parameters

It is not responsible for storing:

- whether the current repository is enabled
- MCP server toggles
- skills / agents directories
- compact / approval / session strategies

Those belong to other control planes.

---

## 3. `defaultProfile`, `activeProfile`, `effectiveProfile` must be kept distinct

### `defaultProfile`

Stored in:

- `~/.ai4j/providers.json`

Meaning:

- machine-level default profile

### `activeProfile`

Stored in:

- `<workspace>/.ai4j/workspace.json`

Meaning:

- which profile the current repository wants to bind

### `effectiveProfile`

Lives in:

- `CliResolvedProviderConfig`

Meaning:

- the profile name that actually participates in evaluation after resolution

The current fallback order is:

1. workspace `activeProfile` exists and is resolvable
2. global `defaultProfile` exists and is resolvable
3. otherwise no profile, continue with env / properties / defaults filling in

This means:

- if `activeProfile` points to a name that does not exist, it does not magically take effect
- it falls back, rather than "pretending to succeed"

---

## 4. An explicit `--provider` kicks mismatched profiles out of the resolution chain

This is the most easily overlooked but very important implementation detail.

When `CliProviderConfigManager.resolve(...)` encounters an explicit provider override, it first performs profile alignment:

- `alignProfileWithProvider(activeProfile, explicitProvider)`
- `alignProfileWithProvider(defaultProfile, explicitProvider)`

The result is:

- if the current workspace activates the `zhipu` profile
- but you explicitly pass `--provider openai`

then this `zhipu` profile will no longer contribute its:

- `baseUrl`
- `model`
- `apiKey`

This is the current implementation's key protection against "the provider was switched, but it still inherits the old parameters of another provider".

---

## 5. The `/provider` command does more than write files

The main logic for the CLI/TUI path lives in:

- `CodingCliSessionRunner`

The ACP path has a mirror implementation in:

- `AcpJsonRpcServer`

Both paths currently support:

- `provider use`
- `provider save`
- `provider add`
- `provider edit`
- `provider default`
- `provider remove`

But their runtime consequences are not the same.

---

## 6. The real consequence of `/provider use <name>`

This command is not "preparing for the next launch" — it affects the current session immediately.

The CLI/TUI path currently:

1. validates that the profile exists
2. writes `workspace.json.activeProfile`
3. re-resolves the runtime options
4. calls `switchSessionRuntime(...)`
5. rebuilds the current session host layer with the new provider binding

The ACP path has equivalent semantics, only `AcpJsonRpcServer.switchToProviderProfile(...)` follows its own session rebinding chain.

So:

- `/provider use` is a live switch
- not a deferred config

---

## 7. `/provider save <name>` persists the "currently effective runtime state"

`/provider save <name>` is not a simple copy of hand-written parameters.

It persists the currently effective provider state, i.e.:

- current `provider`
- current `protocol`
- current `model`
- current `baseUrl`
- current `apiKey`

This is well suited for distilling an already-verified runtime state into a profile.

One additional detail:

- if there is no global `defaultProfile` yet
- the current implementation will directly set the first saved profile as the default

---

## 8. `/provider add` and `/provider save` are not the same thing

### `/provider save <name>`

Semantics:

- save from the current runtime state

### `/provider add <name> ...`

Semantics:

- create a new profile from explicit fields

If this command is not given an explicit `--protocol`, it still follows:

- provider
- baseUrl

to derive the default protocol.

So it is more like "declarative creation", not "snapshot the current runtime".

---

## 9. When `/provider edit` rebinds the current session

`/provider edit <name> ...` does not blindly rebuild the current runtime.

In the current implementation, a session runtime rebind is triggered only when you edit:

- the current `effectiveProfile`

If you just modify a profile that is not currently in effect, it only updates the config file and does not affect the current session.

This is very important, because it guarantees:

- the profile library can be maintained in the background
- without every single edit interrupting the current session

---

## 10. `/provider remove` and `/provider default` have different boundaries

### `/provider remove <name>`

What gets deleted is not just a map entry.

The current implementation also cascades cleanup to:

- `providers.json.defaultProfile`
- `workspace.json.activeProfile`

provided they happen to point to this deleted profile.

### `/provider default <name|clear>`

It only affects the global default binding.

It does not directly overwrite:

- an `activeProfile` already declared by a workspace

In other words:

- `default` affects "the case where there is no repository-level binding"
- `active` is the repository-level hard binding

---

## 11. `/model` and profile are deliberately layered

`/model <name>` does not modify the profile.

It writes:

- `workspace.json.modelOverride`

And the priority of `CliProviderConfigManager.resolve(...)` specifies:

- `modelOverride` takes precedence over `model` inside the profile

The engineering implication is clear:

- profile stores the long-term stable provider + baseline model
- repository-level experimental models use `modelOverride`
- once mature, write it back into the profile

This avoids:

- a single repository experiment polluting the profile shared by all repositories

---

## 12. `/provider` and `/model` in ACP are not a stripped-down version

ACP currently also supports:

- `providers`
- `provider`
- `model`

These commands are preserved in `AcpSlashCommandSupport` and executed by the runtime command handler in `AcpJsonRpcServer`.

Among them:

- `/provider ...` still goes through the logic of switching, saving, and editing profiles
- `/model ...` still changes the effective model for subsequent turns of the current ACP session via `applyModelChange(...)`

So ACP is not only able to "view state" — it can also switch provider/model directly inside the host session, only the form becomes a headless slash command.

---

## 13. Protocol defaults and protocol support boundaries are two separate layers

### 13.1 Default protocol derivation

The current rule of `CliProtocol.defaultProtocol(...)` is:

- `openai` + official host -> `responses`
- `openai` + custom compatible host -> `chat`
- `doubao` / `dashscope` -> `responses`
- other providers -> `chat`

### 13.2 Providers that actually allow `responses`

The current runtime explicitly supports only:

- `openai`
- `doubao`
- `dashscope`

This restriction is validated in both the CLI/TUI and ACP paths.

So do not conflate these two things:

- "how the default protocol is derived"
- "whether this provider combination is actually allowed at runtime"

---

## 14. The three most common profile shapes

### OpenAI official host

```json
{
  "provider": "openai",
  "protocol": "responses",
  "model": "gpt-5-mini",
  "apiKey": "${OPENAI_API_KEY}"
}
```

### OpenAI-compatible custom host

```json
{
  "provider": "openai",
  "protocol": "chat",
  "model": "deepseek-chat",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "${DEEPSEEK_API_KEY}"
}
```

What matters most here is not the field shape, but:

- `provider=openai` does not imply it must go through `responses`

### Zhipu / custom coding endpoint

```json
{
  "provider": "zhipu",
  "protocol": "chat",
  "model": "glm-4.7",
  "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
  "apiKey": "${ZHIPU_API_KEY}"
}
```

The key to this kind of profile is:

- provider, protocol, and baseUrl must all be aligned with each other

---

## 15. Recommended workflow

A more robust way to work is usually:

1. maintain a small number of stable profiles globally, e.g. `openai-main`, `zhipu-main`
2. bind only one `activeProfile` per repository
3. prefer `/model` for repository-level model experiments
4. once a profile is truly stable, write it back or edit the global profile

This is equivalent to splitting:

- machine-level reusable assets
- repository-level local experiments

into two layers of governance.

---

## 16. The 5 most common pitfalls

### 16.1 Assuming the old profile still acts as fallback after explicitly switching provider

The current implementation proactively cuts off field inheritance from mismatched profiles.

### 16.2 Treating `/model` as profile editing

It modifies `workspace.json.modelOverride`, not the profile.

### 16.3 Using an OpenAI-compatible host while keeping the official OpenAI protocol expectation

A custom `baseUrl` changes the default protocol derivation.

### 16.4 Editing a non-current effective profile and expecting the current session to change immediately

It currently does not.

### 16.5 Hard-coding the API key into the repository

:::warning
A profile can persist a key field, but in real team usage you should still prefer routing sensitive values through environment variables or locally controlled configuration.
:::

---

## 17. The conclusions most worth remembering on this page

- a profile is a provider runtime binding, not an ordinary JSON template
- `activeProfile`, `defaultProfile`, `effectiveProfile` have distinct semantics
- an explicit `--provider` removes mismatched profiles from the resolution chain
- `/provider use` and provider switching under ACP both rebind the current session runtime
- `/model` is a repository-level override, not profile editing

---

## 18. Further reading

1. [Configuration](/docs/coding-agent/configuration)
2. [Command Reference](/docs/coding-agent/command-reference)
3. [CLI / TUI Usage Guide](/docs/coding-agent/cli-and-tui)
4. [ACP Integration](/docs/coding-agent/acp-integration)
