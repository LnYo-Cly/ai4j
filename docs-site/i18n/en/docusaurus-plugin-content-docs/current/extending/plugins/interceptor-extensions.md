---
title: Interceptor Extensions
description: Interceptor extensions declare ExtensionCapability.INTERCEPTOR and register tool-call interceptors (allow/block/modify/routeTo), prompt interceptors (allow/block/modify), and model-request interceptors (scalar overrides) through context.interceptors() — letting a plugin act on control flow at key execution points instead of only observing events.
tags: [how-to]
---

# Interceptor Extensions

`INTERCEPTOR` is the seventh `ExtensionCapability` member. Unlike `LIFECYCLE` (observe-only event
notification) and `GUARDRAIL` (allow/deny only), interceptors return **control-flow decisions**
the runtime honors: rewriting tool arguments, routing dangerous calls into a sandbox, intercepting
user input, and adjusting model parameters per request.

Typical scenarios:

- Compliance/safety plugins: rewrite tool arguments (e.g. force a working directory), route
  `bash`-style tools to a Daytona/E2B sandbox
- Injection defense / PII redaction: rewrite or block the prompt before it reaches memory or
  the model
- Multi-tenant policy: rewrite `systemPrompt` per tenant, cap `temperature` or
  `maxOutputTokens`
- Audit hardening: `afterToolCall` inspects tool output for leaked secrets and can replace what
  is fed back to the model

:::warning
`InterceptorRegistry` and the three interceptor interfaces are currently annotated
`@Experimental(since = "2.5.1")` — signatures may evolve across minor versions; pin an exact
version when upgrading. See [Extension layering — SPI stability matrix](/docs/extending/overview#32-plugin-spi-stability-matrix).
:::

## 1. Declare the capability and register interceptors

The manifest must declare `INTERCEPTOR`; otherwise `context.interceptors().register*(...)` inside
`apply(...)` throws `ExtensionException("did not declare capability: interceptor")`.

```java
public ExtensionManifest manifest() {
    return ExtensionManifest.builder()
            .id("safety-pack")
            .name("Safety Pack")
            .capability(ExtensionCapability.INTERCEPTOR)
            .build();
}

public void apply(ExtensionContext context) {
    context.interceptors().registerToolCall(new SandboxBashInterceptor());
    context.interceptors().registerPrompt(new PiiRedactor());
    context.interceptors().registerModelRequest(new TenantScope());
}
```

Three interceptor interfaces, each mapped to one native interception surface of the agent
runtime:

| Extension interface | Native counterpart | Decision types |
|--------------------|--------------------|----------------|
| `ExtensionToolCallInterceptor` | `ToolInterceptor` (PreToolUse / PostToolUse) | `allow` / `block` / `modify` / `routeTo` |
| `ExtensionPromptInterceptor` | `PromptInterceptor` (UserPromptSubmit) | `allow` / `block` / `modify` |
| `ExtensionModelRequestInterceptor` | `ModelRequestHook` | `allow` / `modify` (no block — vetoing a model call is a guardrail's job) |

## 2. Tool-call interceptor

`beforeToolCall` runs before the tool executes; `afterToolCall` re-evaluates with the tool output
(default: allow). The request carrier is `ExtensionToolCallRequest` with four portable fields
(name / arguments / callId / type).

```java
public class SandboxBashInterceptor implements ExtensionToolCallInterceptor {
    public String name() { return "sandbox-bash"; }

    public ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request) {
        if (!"bash".equals(request.getName())) {
            return ExtensionToolCallDecision.allow();
        }
        // Route shell-style tools into a sandbox instead of local execution
        return ExtensionToolCallDecision.routeTo("daytona", "default", extractCommand(request));
    }

    public ExtensionToolCallDecision afterToolCall(ExtensionToolCallRequest request, String output) {
        if (output != null && output.contains("AKIA")) {
            return ExtensionToolCallDecision.block("tool output contained a credential");
        }
        return ExtensionToolCallDecision.allow();
    }
}
```

The four `ExtensionToolCallDecision` verdicts:

- `allow()`: proceed unchanged
- `block(reason)`: veto the call; `reason` is fed back to the model as the tool result so it can
  adjust
- `modify(newName, newArguments)`: rewrite then execute — pass null `newName` to keep the tool
  name, `newArguments` replaces the raw JSON arguments (callId/type/metadata preserved)
- `routeTo(providerId, profile, command)`: redirect execution to a sandbox provider
  (Daytona/E2B/…); the extension owns the tool→command mapping, the runtime owns session
  creation and execution

## 3. Prompt interceptor

`ExtensionPromptInterceptor.intercept(ExtensionPromptRequest)` runs before user input enters
memory / the model:

- `allow()`: pass through
- `block(reason)`: the agent does not run this turn; `reason` becomes the run output
  (`PROMPT_BLOCKED: ...`)
- `modify(modifiedInput)`: the rewritten input proceeds

```java
context.interceptors().registerPrompt(new ExtensionPromptInterceptor() {
    public String name() { return "pii-redactor"; }
    public ExtensionPromptDecision intercept(ExtensionPromptRequest request) {
        String cleaned = Redactor.maskPhones(request.getInput());
        return cleaned.equals(request.getInput())
                ? ExtensionPromptDecision.allow()
                : ExtensionPromptDecision.modify(cleaned);
    }
});
```

## 4. Model-request interceptor

`ExtensionModelRequestInterceptor.intercept(ExtensionModelRequest)` runs before each model
request. The request view exposes six read-only scalar fields (model / systemPrompt /
instructions / temperature / topP / maxOutputTokens) — rewriting conversation history is a
host-side concern and is not open to extensions.

Decisions are expressed via `ExtensionModelRequestDecision` `with*` factories; null fields keep
the original value, and `merge` stacks overrides:

```java
context.interceptors().registerModelRequest(new ExtensionModelRequestInterceptor() {
    public String name() { return "tenant-scope"; }
    public ExtensionModelRequestDecision intercept(ExtensionModelRequest request) {
        return ExtensionModelRequestDecision.withSystemPrompt("You are tenant acme's assistant.")
                .merge(ExtensionModelRequestDecision.withMaxOutputTokens(2048));
    }
});
```

## 5. Composition order and activation semantics

- **Activation**: interceptors follow lifecycle-hook semantics — they take effect as soon as the
  extension is `enable(...)`d; no `exposeTool(...)` / `allow*(...)` per-resource authorization.
- **Host first**: interceptors registered on `AgentBuilder` (`toolInterceptor` /
  `promptInterceptor` / `modelRequestHook`) evaluate before extension interceptors, which run in
  registration order.
- **Chained modification**: every interceptor sees the current effective value — a `MODIFY` feeds
  downstream evaluators; `BLOCK` / `ROUTE_TO` short-circuit.
- **Model request**: the host hook's output is the first extension interceptor's input, and so on.
- **Name uniqueness**: interceptors are unique by `name()` across extensions; duplicates fail fast
  at `snapshot()` (`duplicate ... interceptor id`).

## 6. Enable and assemble

```java
ExtensionRegistry registry = ExtensionRegistry.of(new SafetyPackExtension())
        .enable("safety-pack");

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .toolRegistry(tools)
        .sandboxProvider(daytonaProvider)   // routeTo decisions need a configured sandbox provider
        .extensions(registry)
        .build();
```

`AgentBuilder.extensions(registry)` composes extension interceptors with host interceptors into a
single chain for the runtime; the runtime still only sees the native `ToolInterceptor` /
`PromptInterceptor` / `ModelRequestHook` interfaces — the extension-side contract and runtime-side
adaptation are bridged by `ExtensionInterceptors`, so `ai4j-extension-api` does not depend on
`ai4j-agent`.

:::tip Interceptor vs Guardrail vs Lifecycle
- **Guardrail**: allow/deny only, simplest semantics, per-resource authorization
  (`allowGuardrail`).
- **Interceptor**: can rewrite, route, and intercept input — a decision-making hook, active on
  enable.
- **Lifecycle**: read-only notification; it must never alter control flow.
:::
