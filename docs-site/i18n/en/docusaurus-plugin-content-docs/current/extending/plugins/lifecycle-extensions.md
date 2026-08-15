---
title: "Lifecycle Extensions"
description: "Explains the sixth plugin capability, ExtensionCapability.LIFECYCLE: plugins register an AgentLifecycleHook via context.lifecycle().register(hook) to receive AgentLifecycleEvent at session/turn/model/tool/compact event points, used for observation, telemetry, and auditing — it contributes no tools or resources."
tags: [how-to]
---

# Lifecycle Extensions

`LIFECYCLE` is the sixth capability of `ExtensionCapability`. Unlike the first five (`TOOL` / `COMMAND` / `SKILL` / `PROMPT` / `GUARDRAIL`), it contributes no tools or resources; instead, it lets a plugin **receive notifications** at key points during agent execution. It addresses needs such as observation, telemetry, auditing, and external state synchronization.

Typical use cases:

- Log every model request / tool call to an external audit system
- Initialize plugin-held session state when a session starts, and clean it up when the session ends
- Take snapshots or collect metrics before and after context compaction
- Observe only — when you do not need to intercept decisions (interception is the job of Guardrail)

If you want to allow or deny a tool execution **before** it runs, that is the responsibility of `GUARDRAIL`, not `LIFECYCLE`. A lifecycle hook is a one-way notification and cannot block the flow.

:::warning
`LifecycleHookRegistry` and `AgentLifecycleHook` are currently annotated `@Experimental(since = "2.4.3")`; their signatures and behavior may change in later minor versions. Pin the exact version when you depend on them. See [Extension overview - Plugin SPI stability matrix](/docs/extending/overview#32-plugin-spi-stability-matrix).
:::

## 1. Declare the capability and register a hook

A plugin must declare the `LIFECYCLE` capability in its manifest, otherwise calling `context.lifecycle().register(...)` inside `apply(...)` throws `ExtensionException("did not declare capability: lifecycle")`.

```java
public ExtensionManifest manifest() {
    return ExtensionManifest.builder()
            .id("audit-pack")
            .name("Audit Pack")
            .version("1.0.0")
            .vendor("Example")
            .capability(ExtensionCapability.LIFECYCLE)
            .build();
}

public void apply(ExtensionContext context) {
    // ponytail: register only, no network/IO; defer side effects to onEvent and trigger on demand
    context.lifecycle().register(new AuditHook());
}
```

`AgentLifecycleHook` has only two methods:

```java
public interface AgentLifecycleHook {
    String name();
    void onEvent(AgentLifecycleEvent event) throws Exception;
}
```

- `name()` is the unique identifier of the hook. It cannot be duplicated within the same plugin, otherwise `snapshot()` fails fast (`duplicate lifecycle hook id`). Naming rules match other public IDs: it must start with an alphanumeric character and may contain only letters, digits, dots, underscores, and hyphens.
- `onEvent(...)` may throw. A thrown exception **does not interrupt the agent**; the dispatcher converts it into an `AgentEvent` of type `ERROR` and publishes it (see Section 4).

A minimal hook implementation:

```java
public class AuditHook implements AgentLifecycleHook {
    public String name() {
        return "audit.tool-call";
    }

    public void onEvent(AgentLifecycleEvent event) {
        if (event.getType() == AgentLifecycleEventType.AFTER_TOOL_CALL) {
            // Observe only, do not block; record to the external audit store
            auditStore.record(event.getSessionId(), event.getStep(), event.getPayload());
        }
    }
}
```

## 2. Event types

The `AgentLifecycleEventType` enum covers the ten nodes of a single agent execution:

| Event type | When it fires |
| --- | --- |
| `SESSION_START` | agent session starts |
| `SESSION_END` | agent session ends |
| `BEFORE_TURN` | before each turn of the agent loop begins |
| `AFTER_TURN` | after each turn of the agent loop ends |
| `BEFORE_MODEL_REQUEST` | before issuing a model request |
| `AFTER_MODEL_RESPONSE` | after receiving a model response |
| `BEFORE_TOOL_CALL` | before executing a tool call |
| `AFTER_TOOL_CALL` | after executing a tool call |
| `BEFORE_COMPACT` | before context compaction |
| `ON_COMPACT` | after context compaction |

Inside a hook, use `event.getType()` to determine the current node and handle only the events you care about; return early for the rest. The dispatcher invokes all registered hooks for every event type, so ignoring uninteresting events is the norm.

## 3. Event payload

`AgentLifecycleEvent` is built with a builder, populated by the agent runtime at dispatch time. Field meanings:

| Field | Meaning |
| --- | --- |
| `type` | Event type, required |
| `runtime` | agent runtime identifier (e.g. `react`), nullable |
| `sessionId` | agent session id, sourced from `AgentContext`, nullable |
| `step` | step count of the agent loop |
| `message` | human-readable message, nullable |
| `payload` | event-specific payload object; structure varies by event type |
| `attributes` | additional key-value pairs (immutable map) |

`attributes` returns an unmodifiable `Map<String, Object>`; when empty it is an empty map rather than `null`. When reading `payload` inside a hook, interpret it according to what the event type expects; defensively check the type rather than casting blindly across event points.

## 4. How the agent dispatches

On the agent side, `AgentLifecycleHookDispatcher` distributes events to the list of hooks collected from `ExtensionRegistry`. Key behaviors:

- Only hooks registered by an enabled (`enable(...)`) plugin enter the runtime; hooks registered by a disabled plugin are never dispatched, even if registered.
- The dispatcher wraps each hook in its own `try/catch`: a single hook throwing an exception **does not interrupt** the agent and does not affect other hooks.
- The exception thrown by a hook is wrapped in an `AgentLifecycleHookError` (carrying the hook name, event, and original exception), then published as an `AgentEvent` with `AgentEventType.ERROR` via `AgentEventPublisher`. The host can observe these errors through existing agent event listeners.
- Fields such as `payload` and `message` are filled by the dispatcher per node; `sessionId` is taken from `AgentContext`.

In other words, a lifecycle hook is a "best-effort observation point": you can trust that it gets invoked, but not that it returns normally — a failure simply becomes an error event and never stops the agent.

## 5. Wiring on the consumer side

A lifecycle hook needs no `exposeTool(...)` or `allow*(...)`: it is not a model-visible tool, nor an explicitly authorized resource. It enters the runtime when its plugin is enabled via `enable(...)`. This is a key difference from skill / prompt / guardrail / tool.

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("audit-pack");   // the registered lifecycle hook enters the agent runtime

Agent agent = Agent.builder()
        // ... model / tools configuration ...
        .extensions(registry)
        .build();
```

`ExtensionRegistry.getLifecycleHooks()` (or `snapshot().getLifecycleHooks()`) returns all hooks contributed by currently enabled plugins; the agent hands them to `AgentLifecycleHookDispatcher` at construction time.

## 6. Common mistakes

| Mistake | Consequence | Fix |
| --- | --- | --- |
| Registering a hook without declaring `LIFECYCLE` in the manifest | `snapshot()` throws `did not declare capability: lifecycle` | Add `.capability(ExtensionCapability.LIFECYCLE)` to the manifest |
| Registering hooks with duplicate names in the same plugin | `snapshot()` throws `duplicate lifecycle hook id` | Keep each hook's `name()` unique |
| Doing long blocking IO inside `onEvent(...)` | Slows the agent at every event point | Make it async or bound the timeout; a hook should return quickly |
| Putting interception logic inside a lifecycle hook | The hook cannot block the flow, so interception is ineffective | Use `GUARDRAIL` for interception instead |
| Issuing network requests in `apply(...)` instead of `onEvent(...)` | `validate` / `inspect --runtime` will trigger side effects | Put side effects in `onEvent(...)`; let `apply(...)` only register |

## 7. Further reading

1. [Plugin Packages](/docs/extending/plugins/plugin-packages)
2. [Plugin Author Cookbook](/docs/extending/plugins/plugin-author-cookbook)
3. [Extension SPI Internals](/docs/extending/plugins/extension-spi)
4. [Agent Tools and Registry](/docs/agent/tools-and-registry)
