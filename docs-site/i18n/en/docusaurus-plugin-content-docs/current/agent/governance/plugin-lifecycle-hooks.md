---
sidebar_position: 7
title: "Plugin Lifecycle Hooks"
description: "Explains the Agent lifecycle hooks in ai4j-extension-api: how plugins observe events such as BEFORE_TURN/BEFORE_MODEL_REQUEST/BEFORE_TOOL_CALL/ON_COMPACT, the event payload, exception strategy, and the distinction from Guardrails."
tags: [integration]
---

# Plugin Lifecycle Hooks

`ai4j-extension-api` now supports Agent lifecycle hooks. They let plugins do more than contribute Tools, Commands, Skills, Prompts, or Guardrails — a plugin can also observe the Agent's runtime behavior.

This is a foundational capability of the plugin ecosystem, suitable for:

- runtime audit and trace enrichment
- tool call statistics
- prompt / model request observation
- preparation ahead of memory / compaction strategies
- future integration points for sandbox, runner, and CLI plugin experiences

:::info
The first version of lifecycle hooks is **observation-first**: hooks can observe events and payloads, but they are not mutable interceptors for prompts / tools / model responses.
:::

## 1. How a plugin registers a hook

Plugins continue to implement `Ai4jExtension` and register inside `apply(ExtensionContext context)`:

```java
public final class AuditExtension implements Ai4jExtension {
    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("audit-pack")
                .name("Audit Pack")
                .version("1.0.0")
                .vendor("example")
                .capability(ExtensionCapability.LIFECYCLE)
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        context.lifecycle().register(new AgentLifecycleHook() {
            @Override
            public String name() {
                return "audit.lifecycle";
            }

            @Override
            public void onEvent(AgentLifecycleEvent event) {
                System.out.println(event.getType() + " step=" + event.getStep());
            }
        });
    }
}
```

On the consumer side, plugins are still enabled through `ExtensionRegistry`:

`ai4j-cli extension inspect <id> --runtime` now prints a `lifecycleHooks=` line, so plugin authors can verify that hooks were packaged and discovered before wiring them into an Agent.

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("audit-pack");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .extensions(registry)
        .build();
```

Older plugins that do not implement lifecycle hooks require no changes.

## 2. Current event types

| Event | When triggered |
| --- | --- |
| `BEFORE_TURN` | After each Agent step begins |
| `AFTER_TURN` | Before each Agent step wraps up |
| `BEFORE_MODEL_REQUEST` | After the prompt is built and before the model is called |
| `AFTER_MODEL_RESPONSE` | After the model returns |
| `BEFORE_TOOL_CALL` | Before a tool or CodeAct code execution |
| `AFTER_TOOL_CALL` | After a tool or CodeAct code execution |
| `ON_COMPACT` | After `AgentSession.compact(...)` produces a compaction result |
| `SESSION_START` | Reserved; not auto-triggered in the first version |
| `SESSION_END` | Reserved; not auto-triggered in the first version |

`SESSION_START` / `SESSION_END` are currently only reserved event types. Because the current Agent does not yet have a stable explicit close/end lifecycle, the first version does not guess at trigger points.

## 3. What the event payload contains

`AgentLifecycleEvent` provides:

| Field | Description |
| --- | --- |
| `type` | The lifecycle event type |
| `runtime` | The source, e.g. `react`, `codeact`, `session` |
| `sessionId` | Carries a session id when sourced from `AgentSession` |
| `step` | The current step |
| `message` | A lightweight label, e.g. the runtime name or tool name |
| `payload` | The context object for the corresponding event, e.g. `AgentPrompt`, `AgentModelResult`, `AgentToolCall`, `CompactResult` |
| `attributes` | Extension attributes; in the first version, used for plugin-defined context |

:::warning Payloads may contain sensitive data
The payload is a runtime object; plugins should not persist the full original directly. In particular, prompts, raw model responses, and tool arguments may contain user input, business data, or configuration content.
:::

## 4. Exception strategy

Hooks use `record-and-continue` by default:

1. The dispatcher catches the exception thrown by the hook.
2. The Agent publishes an `AgentEventType.ERROR`.
3. The main Agent loop continues.

This is designed so that bugs in third-party plugins do not directly break the core Agent flow.

If the ability to "abort on hook failure" is needed later, it should be added as an explicit, separate strategy rather than as the default behavior.

## 5. Distinction from Guardrails

| Capability | Role |
| --- | --- |
| Guardrail | Makes policy decisions, e.g. whether to allow a tool call |
| Lifecycle Hook | Observes the runtime, e.g. recording model requests, tool results, compaction state |

If you need to block or allow an action, use a Guardrail.
If you need to record, tally, audit, or forward events to an external system, use a Lifecycle Hook.

## 6. Current limitations

The first version does not do the following:

- Does not modify prompts, tool arguments, or model responses.
- Does not provide a hook allowlist; hooks take effect with extension enable.
- Does not provide a plugin marketplace or remote install protocol.
- Does not directly integrate a sandbox provider.
- Does not hardcode any OpenAI-compatible relay platform name as an SDK concept.

These capabilities may be extended in subsequent Blueprint, Sandbox SPI, and CLI/TUI plugin experiences.
