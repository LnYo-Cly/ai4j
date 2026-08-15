---
sidebar_position: 8
title: "Extending ai4j"
description: "Brings the four extension lines that let agents and the SDK do more into a single entry point: the plugin package contribution contract (discover/enable/allow/expose), the on-demand Skill methodology, plugin Prompt resources, and the provider/model/service extension for adding new LLM backends, marking each entry point and security boundary."
tags: [concept]
---

# Extending ai4j

> **An ai4j "extension" = adding a capability that an agent or the SDK does not ship with out of the box.** But ai4j splits this into four lines, and each line has a different entry point, cost, and security boundary — pick the wrong line and you will change the wrong place.

This page is an aggregation entry point: it does not repeat the content of each topic page, it only tells you **what the four extension lines are, what problem each one solves, and which page to start reading from**. Each line below has a one-sentence definition and a real doc entry point.

---

## Start with a summary table

| Extension line | In one sentence | Entry mechanism | Visible to the model? |
| --- | --- | --- | --- |
| **Plugin package (Plugin)** | Pack a tool / command / Skill / Prompt / Guardrail into a jar and contribute it to an agent | `ai4j-extension-api` + `ServiceLoader` + three-stage gate | A tool becomes visible only after `exposeTool` |
| **Skill** | A methodology resource the model reads on demand ("how to do this kind of task") | `.ai4j/skills` scan + `SKILL.md` | The model reads the summary first, then `read_file` |
| **Prompt** | A reusable prompt resource contributed by a plugin, entering the agent's `available_prompts` | Plugin `prompts().register(...)` | No; read on demand by the host or agent |
| **Provider / Model / Service extension** | Add a new LLM backend or top-level capability surface to the SDK | Modify the `PlatformType` + `AiService` + `Registry` main chain | Not applicable; this is a protocol-layer extension |

The boundaries between these four lines are the easiest thing to conflate in the ai4j docs. Each is expanded below.

---

## 1. Plugin packages: the contribution contract (`ai4j-extension-api`)

Third-party developers pack tools, commands, Skills, Prompts, and Guardrails into an ordinary Maven jar; after the user adds it to the classpath, they then **discover, enable, authorize, and expose** it. It is not an app store, and it is not a remote downloader — the stable path is jar + `ServiceLoader` + explicit gate.

The key boundary is the **three-stage gate**: `discover()` only discovers without executing, `enable(...)` registers resources without exposing them to the model, and `exposeTool(...)` is what hands a specific tool to the agent tool registry. By design, this deliberately does not do "installed means automatically available."

→ [Plugin Packages](/docs/extending/plugins/plugin-packages) (concept and gate) | [Plugin Recipes](/docs/extending/plugins/plugin-recipes) (copy-ready wiring recipes)

---

## 2. Skill: on-demand methodology capability

`Skill` is not "a little explanatory text added for the model"; it is a formal **context governance mechanism**: at session startup only the summary is discovered (name + description), and when a task actually matches, the model reads the body of `SKILL.md` via `read_file`. It belongs to the Core SDK (`Skills.java`), not only to the Coding Agent product feature set.

The most common carrier is `SKILL.md`. It solves "how methodology is reused," not "how actions are executed" — so it does not take on execution itself, nor does it automatically fill the context.

→ [Skills overview](/docs/capabilities/skills/overview) | [Discovery and Loading](/docs/capabilities/skills/discovery-and-loading) | [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp) | [Coding Agent Skills usage and organization](/docs/products/coding-agent/skills)

---

## 3. Prompt: reusable prompt resources

ai4j has no standalone "slash prompt template engine." **Prompt is one kind of plugin resource**: inside `apply(...)`, the plugin uses `context.prompts().register(...)` to register an `ExtensionPromptResource` (pointing to a markdown resource inside the jar); once enabled it is materialized into a read-only file and enters the agent's `<available_prompts>` list, to be read on demand by the host or the Coding Agent.

That is, ai4j's Prompt and Skill share the same "summary first, read on demand" context governance approach, differing only in resource type. ai4j has **two official reference plugins**, each with a different focus:

- **`ask-user`** — the minimal plugin (host-mediated user clarification tool + command + Skill + Prompt), suitable for learning the basic skeleton of an extension.
- **`dynamic-workflow`** — 🚀 **the production-grade flagship reference** (contributing 4 kinds of capability at once + a host-mediated workflow envelope + zero runtime dependencies + a live closed-loop test). Suitable for learning how to build a complete, safe, releasable ai4j plugin. See [Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin).

→ [Ask User Plugin](/docs/extending/plugins/ask-user-plugin) (template that also contributes a Prompt) | [Dynamic Workflow Plugin](/docs/extending/plugins/dynamic-workflow-plugin) (scripted Prompt + Skill template)

---

## 4. Provider / Model / Service extension: adding a new LLM backend

This line **is not the same thing** as a plugin package. It modifies the SDK code main chain, formally bringing a new model platform or top-level capability surface into the platform distribution system. Today ai4j models providers with **explicit enum drive**, not "register an implementation and it is automatically visible."

The three granularities, ordered from lightest to heaviest cost:

- **Model Extension** — add a model name, request field, or capability variant under the same provider; the main work is in the request object and the provider adaptation layer, without touching `PlatformType`.
- **Provider Extension** — add a new model platform (new `PlatformType` + configuration + factory branch + Registry + starter); it must touch the entire chain at once.
- **Service Extension** — add a top-level capability contract (such as a new `IXxxService`), which widens the SDK's entire public API surface; the highest cost.

:::warning Boundary caveat
Plugin packages **cannot** be used to add a provider. To wire a new LLM backend, you still go through the code main chain; plugin packages only take responsibility for exposing runtime resources (tools / commands / Skills / Prompts / Guardrails) to the agent.
:::

→ [Provider Extension](/docs/extending/code-level/provider-extension) | [Model Extension](/docs/extending/code-level/model-extension) | [Service Extension](/docs/extending/code-level/service-extension) | [Extension overview](/docs/extending/overview)

---

## Minimal runnable example: giving an Agent one more tool

The most common "extension" action is wiring a plugin tool into the general Agent loop. Below, the official `ask-user` plugin demonstrates the three steps discover → enable → expose (Java 8 style).

Add the dependency:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-ask-user</artifactId>
  <version>2.4.2</version>
</dependency>
```

Enable and expose it to the Agent:

```java
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;

// 1. discover: discover the plugin from the classpath (does not execute the tool, does not expose it to the model)
// 2. enable:   call the plugin's apply(...) to register the tool / command / Skill / Prompt / Guardrail
// 3. exposeTool: hand the specified tool to the model-visible tool registry
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("ask-user")
        .exposeTool("ask_user");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-4.5-flash")
        .extensions(registry)
        .build();
```

At runtime, the exposed `ExtensionToolSpec` is converted into an ordinary `Tool`, and the `ExtensionToolExecutor` is routed to the existing `ToolExecutor` — the Agent main loop never has to know the plugin implementation class.

:::note Spring Boot
Spring Boot projects can accomplish the same thing through configuration: `ai.extensions.enabled` + `ai.extensions.tools.expose`; the starter will auto-configure the `ExtensionRegistry` / `ExtensionRuntimeSnapshot`, but will not auto-create the Agent. See [Plugin Recipes](/docs/extending/plugins/plugin-recipes).
:::

---

## Boundary in one sentence

- **Plugin package** = a pluggable **contribution contract** (the jar contributes resources, the gate controls visibility).
- **Skill** = an on-demand **methodology resource** (tells the model "how to do this kind of task").
- **Prompt** = a **reusable prompt resource** contributed by a plugin (enters `available_prompts`, read on demand).
- **Provider / Model / Service extension** = modifying the SDK **code main chain** to add a new LLM backend or capability surface.

When you hit "the existing SDK is not enough," first judge whether you are dealing with **resource reuse** (go through plugin packages / Skills) or a **platform boundary / model variant / new capability** (go through the provider/model/service extension). For the decision order see [Extension overview §5 Extension decision order](/docs/extending/overview).

---

## Further reading

- [Extension overview](/docs/extending/overview) — full landscape and decision order of the four extension lines
- [Plugin Packages](/docs/extending/plugins/plugin-packages) — plugin package concept and three-stage gate
- [Skills overview](/docs/capabilities/skills/overview) — Skill as a context governance layer
- [Tools](/docs/capabilities/tools/overview) — tools are the model's execution capability inside the host (for the Skill / Tool / MCP comparison see [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp))
- [Agent Runtime](/docs/agent/overview) — the host into which plugin tools wire the general Agent loop
- [Coding Agent](/docs/products/coding-agent/overview) — using plugin tools + Skills + Prompts inside a coding session
