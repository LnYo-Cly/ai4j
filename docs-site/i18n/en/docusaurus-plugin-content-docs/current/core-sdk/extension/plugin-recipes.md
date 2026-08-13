---
title: "Plugin Recipes"
description: "Assembly recipes for plugin consumers: after the jar is on the classpath, how to use CLI plan/check for pre-wiring checks, with enable/allow/expose configuration for Java, Spring Boot, Agent, Coding Agent, and multi-plugin combinations, distinguishing command and tool exposure semantics."
tags: [how-to]
---

# Plugin Recipes

This page solves the assembly problem for plugin consumers: **after the plugin jar is on the classpath, how to inspect, enable, authorize, expose, and wire it into Java, Spring Boot, Agent, Coding Agent, or the CLI.**

If you do not yet know what a plugin package is, see [Plugin Packages](/docs/core-sdk/extension/plugin-packages) first. If you want to author a third-party plugin, see [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook).

## 1. First split by resource type

AI4J plugin packages can contribute several kinds of resources. They are not controlled by a single switch.

| Resource | How it enters | Who uses it | Model-visible |
| --- | --- | --- | --- |
| Tool | `exposeTool(...)` / `ai.extensions.tools.expose` | Agent / Coding Agent tool loop | Yes |
| Command | `allowCommand(...)` / `ai.extensions.commands.allow` | Explicit CLI or host invocation | No |
| Skill | `allowSkill(...)` / `ai.extensions.skills.allow` | Coding Agent context resource | No — the model first sees the resource name and a readable path |
| Prompt | `allowPrompt(...)` / `ai.extensions.prompts.allow` | Host or Coding Agent reads on demand | No, unless the host injects it explicitly |
| Guardrail | `allowGuardrail(...)` / `ai.extensions.guardrails.allow` | Pre-check before tool execution | No |

When wiring a plugin, write these five resource categories as one recipe, not just "enable the plugin." That way consumers can judge which capabilities enter the model context and which are only for the host or CLI.

## 2. Standard wiring order

### 2.1 Add the dependency

A plugin is a normal Maven / Gradle dependency. Using the official `ask-user` plugin as an example:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
</dependency>
```

If you are not using `ai4j-bom`, you must specify the version explicitly:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>${ai4j.version}</version>
</dependency>
```

AI4J does not auto-download plugins remotely, and it does not modify your `pom.xml` for you. Discovery, inspection, and activation only happen after the dependency lands on the classpath.

### 2.2 Inspect the classpath first

```bash
ai4j-cli extension list
ai4j-cli extension inspect ask-user --runtime
ai4j-cli extension validate ask-user
```

These three commands answer different questions:

| Command | What it answers |
| --- | --- |
| `list` | Which plugins were discovered on the current classpath |
| `inspect --runtime` | Which tools, commands, Skills, Prompts, and Guardrails the plugin actually contributes |
| `validate` | Whether the manifest, resource paths, tool schema, and `apply(...)` registration logic can be consumed reliably by AI4J |

`inspect --runtime` and `validate` temporarily invoke the plugin's `apply(...)` to collect registration info, but they do not expose tools to the model and do not execute commands.

### 2.3 Write an activation plan

Under strict mode, preview once with the CLI first:

```bash
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
```

You should see:

```text
enabled=true
explicitResourceActivation=true
tools:
- name=ask_user state=active reason=exposeTool allowlist
commands:
- name=ask-user state=active reason=resource allowlist
skills:
- name=ask-user-collaboration state=active reason=resource allowlist
prompts:
- name=ask-user-question state=active reason=resource allowlist
```

If a resource name is wrong, plan shows `not registered by extension`. This is the cheapest stage to fix it.

For CI or pre-release smoke tests, use `check`:

```bash
ai4j-cli extension check ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
```

`plan` only previews; it prints inactive reasons and still returns 0. `check` first runs validation, then verifies that the tools, commands, Skills, Prompts, and Guardrails explicitly requested by this command are active. If validation reports an error or any requested resource is inactive, `check` returns non-zero. Plugin resources you did not request will not fail `check`.

## 3. Recipe A: Plain Java wiring for `ask-user`

Fits when you build your own `Agent` or `CodingAgent` and do not rely on Spring Boot auto-configuration.

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .requireExplicitResourceActivation()
        .allowCommand("ask-user")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .exposeTool("ask_user");
```

Wire into an Agent:

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

Wire into a Coding Agent:

```java
CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .workspaceContext(workspaceContext)
        .extensions(registry)
        .build();
```

The effect of this configuration:

- `ask_user` enters the model-visible tool list.
- The `ask-user` command can be invoked explicitly by the CLI or host.
- The `ask-user-collaboration` Skill and `ask-user-question` Prompt are readable as read-only resources.
- Commands, Skills, and Prompts you did not list will not enter the strict runtime just because of `enable("ask-user")`.

## 4. Recipe B: Spring Boot wiring for `ask-user`

Fits projects that want the starter to create the `ExtensionRegistry` and `ExtensionRuntimeSnapshot`.

```yaml
ai:
  extensions:
    enabled:
      - ask-user
    explicit-resource-activation: true
    tools:
      expose:
        - ask_user
    commands:
      allow:
        - ask-user
    skills:
      allow:
        - ask-user-collaboration
    prompts:
      allow:
        - ask-user-question
```

Then inject the registry into your own Agent bean:

```java
@Bean
public Agent agent(ModelClient modelClient, ExtensionRegistry extensionRegistry) {
    return Agents.react()
            .modelClient(modelClient)
            .model("glm-4.5-flash")
            .extensions(extensionRegistry)
            .build();
}
```

The starter only assembles the registry and snapshot. It does not auto-create an Agent, and it does not decide the model, memory, tool policy, or business recovery flow for you.

## 5. Recipe C: CLI for pre-wiring checks

Fits when you want a human verification step before handing a third-party plugin to the host application.

```bash
ai4j-cli extension validate ask-user
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
ai4j-cli extension check ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict
ai4j-cli extension resource --enable ask-user \
  --allow-skill ask-user-collaboration \
  skill ask-user-collaboration
ai4j-cli extension resource --enable ask-user \
  --allow-prompt ask-user-question \
  prompt ask-user-question
ai4j-cli extension run --enable ask-user \
  --allow-command ask-user \
  ask-user "Should I continue with this file rewrite?"
```

These commands still do not mean the Agent can already invoke the tool. Whether the Agent can invoke it depends solely on whether the host exposes the tool to the model via `.exposeTool(...)` or `ai.extensions.tools.expose`.

## 6. Recipe D: Assembling multiple plugins together

For multiple plugins, do not write "enable everything." The recommendation is one `enable` line per plugin, then aggregate allowlists by resource type.

Assume three plugins:

| Plugin | What it does | Exposure strategy |
| --- | --- | --- |
| `ask-user` | Lets the agent ask the host to question the user | Expose the `ask_user` tool, authorize command / Skill / Prompt |
| `weather-pack` | Weather lookup | Expose the `weather.search` tool, authorize the `weather-check` command |
| `repo-policy-pack` | Constrains coding agent tool calls | Authorize the `repo-policy.safe-write` guardrail, do not expose a tool |

Plain Java:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .enable("weather-pack")
        .enable("repo-policy-pack")
        .requireExplicitResourceActivation()
        .exposeTool("ask_user")
        .exposeTool("weather.search")
        .allowCommand("ask-user")
        .allowCommand("weather-check")
        .allowSkill("ask-user-collaboration")
        .allowPrompt("ask-user-question")
        .allowGuardrail("repo-policy.safe-write");
```

Spring Boot:

```yaml
ai:
  extensions:
    enabled:
      - ask-user
      - weather-pack
      - repo-policy-pack
    explicit-resource-activation: true
    tools:
      expose:
        - ask_user
        - weather.search
    commands:
      allow:
        - ask-user
        - weather-check
    skills:
      allow:
        - ask-user-collaboration
    prompts:
      allow:
        - ask-user-question
    guardrails:
      allow:
        - repo-policy.safe-write
```

Run plan three times, once per plugin, before wiring:

```bash
ai4j-cli extension plan ask-user --enable \
  --expose-tool ask_user \
  --allow-command ask-user \
  --allow-skill ask-user-collaboration \
  --allow-prompt ask-user-question \
  --strict

ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --strict

ai4j-cli extension plan repo-policy-pack --enable \
  --allow-guardrail repo-policy.safe-write \
  --strict
```

`extension plan` currently outputs activation state per single plugin. For multi-plugin combinations, checking plugin by plugin makes it easier to spot misspelled resource names or dependencies that never reached the classpath. Once the wiring recipe is fixed, swap the same arguments to `extension check` and put it in CI or pre-release smoke; `plan` fits human preview, `check` fits machine verification.

## 7. The recipe a third-party plugin README should provide

When third-party plugin authors publish a plugin, the README should at least provide the following. Without these, consumers struggle to assemble it safely.

```md
## AI4J integration

### Maven

<dependency>
  <groupId>io.github.lnyocly</groupId>
  <artifactId>weather-ai4j-plugin</artifactId>
  <version>1.0.0</version>
</dependency>

### Extension id

weather-pack

### Contributed resources

| Type | Name | Recommended | Notes |
| --- | --- | --- | --- |
| Tool | weather.search | Yes | Query weather by city |
| Command | weather-check | Optional | Manual CLI lookup |
| Skill | weather-skill | Optional | Weather query workflow |
| Prompt | weather-summary | Optional | Weather summary template |
| Guardrail | weather.network-policy | Optional | Restrict network access for weather tools |

### Activation plan

ai4j-cli extension plan weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict

### CI check

ai4j-cli extension check weather-pack --enable \
  --expose-tool weather.search \
  --allow-command weather-check \
  --allow-skill weather-skill \
  --allow-prompt weather-summary \
  --allow-guardrail weather.network-policy \
  --strict

### Java

ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("weather-pack")
        .requireExplicitResourceActivation()
        .exposeTool("weather.search")
        .allowCommand("weather-check")
        .allowSkill("weather-skill")
        .allowPrompt("weather-summary")
        .allowGuardrail("weather.network-policy");

### Spring Boot

ai:
  extensions:
    enabled:
      - weather-pack
    explicit-resource-activation: true
    tools:
      expose:
        - weather.search
    commands:
      allow:
        - weather-check
    skills:
      allow:
        - weather-skill
    prompts:
      allow:
        - weather-summary
    guardrails:
      allow:
        - weather.network-policy

### Security notes

- Network access:
- File read/write:
- Database or external API access:
- Required environment variables:
- Local verification commands:
```

The point of this README recipe is not length, but letting consumers judge "what got installed" and "what got exposed" separately.

## 8. Common assembly mistakes

| Mistake | Symptom | Fix |
| --- | --- | --- |
| Assuming the plugin takes effect after only adding the Maven dependency | `extension list` sees it, but the Agent has no new tool | You also need `enable(...)` and `exposeTool(...)` |
| Assuming `enable(...)` alone makes the tool callable by the model | The runtime has a tool spec, but the model cannot see it | Specify `exposeTool("tool.name")` |
| Forgetting `allowSkill(...)` under strict mode | The Coding Agent cannot see the plugin's Skill resource | Add it to the allowlist, or confirm the resource is not needed |
| Treating a command as a tool | The CLI can execute it, but the model cannot call it | A command is a human entry point; model invocation requires registering and exposing a tool |
| Copying a non-existent resource name in a multi-plugin combo | Startup or snapshot fail-fast, plan shows `not registered by extension` | Run `inspect --runtime` and `plan --strict` first |
| Plugin README only says "usable after install" | Consumers do not know which capabilities enter the model context | The README must list the resource inventory and activation recipe |

## 9. When not to use a plugin

The following needs are not a good fit for a plugin package:

- Adding a model platform: see [Provider Extension](/docs/core-sdk/extension/provider-extension).
- Adding request fields to an existing provider: see [Model Extension](/docs/core-sdk/extension/model-extension).
- Adding a top-level SDK capability surface: see [Service Extension](/docs/core-sdk/extension/service-extension).
- Only adjusting the HTTP dispatcher or connection pool: see [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack).

Plugins fit delivering runtime resources; they do not fit doing provider auto-registration for the core SDK.

## 10. Recommended reading order

1. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
2. This page: Plugin Recipes
3. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
4. [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook)
5. [Agent Tools and Registry](/docs/agent/tools-and-registry)
6. [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)
