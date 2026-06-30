---
sidebar_position: 11
---

# Interception Hooks (block / modify / route-to-sandbox + observe events)

ai4j covers every Claude-Code-equivalent hook event: **interception** (can veto/rewrite) for tool
calls and prompts, plus **observe** side-effect hooks for turn/compact/session boundaries. Two
control-flow interfaces plus the existing observe-only [lifecycle hooks](/docs/agent/plugin-lifecycle-hooks).

- **`ToolInterceptor`** — `beforeToolCall` (Claude Code "PreToolUse": block / modify / route to sandbox) and `afterToolCall` ("PostToolUse": block the result).
- **`PromptInterceptor`** — before the user's input reaches the model ("UserPromptSubmit": block / modify the prompt).
- **`AgentLifecycleHook`** — observe-only side-effects for Stop / PreCompact / SessionStart / SessionEnd (now directly registerable via `AgentBuilder.lifecycleHook(...)`).

This is the layer library users need to build policy, safety, or prompt-shaping into their own agent
systems. It aligns ai4j with pi and Claude Code, and one decision — `routeTo` — surpasses them.

## The four decisions

```java
public interface ToolInterceptor {
    ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context);
}
```

| Decision | What happens | Equivalent |
| --- | --- | --- |
| `allow()` | proceed | — |
| `block(reason)` | veto; the reason is fed back to the model as the tool result so it can adjust | Claude Code PreToolUse exit-code-2 |
| `modify(newCall)` | rewrite the call (name/arguments), execute the rewritten one | Claude Code JSON input-modify |
| `routeTo(spec, command)` | **run the command in a sandbox** (Daytona/E2B) instead of locally; output fed back | pi redirect-to-sandbox — but ai4j has a real Sandbox SPI |

Register it on the agent builder:

```java
Agent agent = Agents.react()
        .anthropicMessages(key, baseUrl).model("glm-5.1")
        .toolExecutor(executor)
        .toolInterceptor((call, ctx) -> {
            if (isDestructive(call)) {
                return ToolCallDecision.block("destructive command blocked");
            }
            return ToolCallDecision.allow();
        })
        .build();
```

## Example: block dangerous commands

```java
.toolInterceptor((call, ctx) -> {
    String cmd = extractCommand(call);
    if (cmd != null && cmd.contains("rm -rf")) {
        return ToolCallDecision.block("refusing rm -rf; the model will see this and retry safely");
    }
    return ToolCallDecision.allow();
})
```

On `block`, the runtime skips execution and returns `TOOL_BLOCKED: {"reason": "..."}` to the model as
the tool result — the model sees the reason and adjusts, exactly like Claude Code's exit-2 deny.

## Example: route a dangerous command to a sandbox (beyond pi)

pi can "redirect to sandbox" but has no sandbox SPI — ai4j routes to Daytona/E2B. Configure a
`SandboxProvider`, then route:

```java
Agent agent = Agents.react()
        .anthropicMessages(key, baseUrl).model("glm-5.1")
        .toolExecutor(localExecutor)
        .sandboxProvider(new E2BSandboxProvider())     // or DaytonaSandboxProvider
        .toolInterceptor((call, ctx) -> {
            String cmd = extractCommand(call);
            if (cmd != null && isDangerous(cmd)) {
                // run it isolated in a sandbox instead of on the host
                return ToolCallDecision.routeTo(
                        SandboxSpec.builder().providerId("e2b").build(),
                        SandboxCommand.builder().command(cmd).build());
            }
            return ToolCallDecision.allow();
        })
        .build();
```

The runtime creates a sandbox session from the spec, runs the command, and feeds
`SANDBOX_RESULT: {"exitCode":0,"stdout":"..."}` back to the model. The interceptor owns the
tool→command mapping (it knows its tools); the runtime owns session creation/execution.

## PostToolUse (afterToolCall)

`ToolInterceptor` has a second, default method that runs **after** a tool executes, with its output.
Return `block(reason)` to replace the result fed back to the model — e.g. the output leaked a secret,
or a post-edit lint failed. The default is `allow()` (no-op), so `beforeToolCall` lambdas still work.

```java
.toolInterceptor(new ToolInterceptor() {
    @Override
    public ToolCallDecision beforeToolCall(AgentToolCall c, AgentContext ctx) {
        return ToolCallDecision.allow();
    }
    @Override
    public ToolCallDecision afterToolCall(AgentToolCall c, String output, AgentContext ctx) {
        return containsSecret(output) ? ToolCallDecision.block("output redacted: secret detected")
                                      : ToolCallDecision.allow();
    }
})
```

The tool still ran (block is post-execution); the model receives `TOOL_BLOCKED: <reason>` instead of
the raw output.

## Prompt interception (UserPromptSubmit)

`PromptInterceptor` runs before the user's input is committed to the conversation — block a harmful
prompt or rewrite it before the model sees it.

```java
Agent agent = Agents.react()
        .anthropicMessages(key, baseUrl).model("glm-5.1")
        .promptInterceptor((input, ctx) -> {
            if (looksLikeInjection(input)) {
                return PromptDecision.block("possible prompt injection");
            }
            return PromptDecision.allow();
        })
        .build();
```

`PromptDecision` mirrors `ToolCallDecision`: `allow()` / `block(reason)` / `modify(newInput)`. On
`block`, the agent returns immediately with `PROMPT_BLOCKED: <reason>` and the model is never called.

## Observe events (Stop / PreCompact / SessionStart / SessionEnd)

The remaining Claude-Code events are **side-effects**, not decisions (lint after edit, audit log on
turn end, snapshot on compact). These ride on the existing observe-only `AgentLifecycleHook`, now
directly registerable without the extension SPI:

```java
Agent agent = Agents.react()
        .modelClient(modelClient).model("m")
        .lifecycleHook(new AgentLifecycleHook() {
            @Override public String name() { return "audit"; }
            @Override public void onEvent(AgentLifecycleEvent e) {
                if (e.getType() == AgentLifecycleEventType.AFTER_TURN) {
                    metrics.turnEnded(e.getStep());
                }
            }
        })
        .build();
```

Event mapping: `AFTER_TURN`→Stop, `ON_COMPACT`→PreCompact, `SESSION_START`/`SESSION_END`→SessionStart/End.

## Supported events

| Claude Code event | Type | ai4j mechanism |
| --- | --- | --- |
| PreToolUse | interception (block/modify/routeTo) | `ToolInterceptor.beforeToolCall` |
| PostToolUse | interception (block result) | `ToolInterceptor.afterToolCall` |
| UserPromptSubmit | interception (block/modify prompt) | `PromptInterceptor.beforePrompt` |
| Stop | observe | `AgentLifecycleHook` → `AFTER_TURN` |
| PreCompact | observe | `AgentLifecycleHook` → `ON_COMPACT` |
| SessionStart / SessionEnd | observe | `AgentLifecycleHook` → `SESSION_START/END` |

## File-configured hooks (CLI)

End users configure all of the above via the workspace config JSON — no Java. The CLI bridges each
event to external shell commands (any language): exit `2` blocks, `{"decision":"modify",...}` JSON
rewrites, anything else continues.

```json
"hooks": {
  "preToolUse":     [{ "command": "python guard.py",   "match": "bash" }],
  "postToolUse":    [{ "command": "python scan.py",    "match": "bash" }],
  "userPromptSubmit":[{ "command": "python pii.py" }],
  "stop":           [{ "command": "python audit.py" }],
  "preCompact":     [{ "command": "python snapshot.py" }],
  "sessionStart":   [{ "command": "python on_start.py" }],
  "sessionEnd":     [{ "command": "python on_end.py" }]
}
```

## Interceptor vs observe-only lifecycle hooks

| | `AgentLifecycleHook` (observe) | `ToolInterceptor` (control) |
| --- | --- | --- |
| Return | `void` | `ToolCallDecision` |
| Can veto | no | yes (`block`) |
| Can rewrite | no | yes (`modify`) |
| Can redirect to sandbox | no | yes (`routeTo`) |
| Use for | telemetry, audit, trace | policy, safety, prompt-shaping, sandbox routing |

They coexist — register both; observe hooks see what happens, interceptors decide what happens.

## Where this fits

- Observe/audit layer (tracing, replay, hash-chain audit): [Replay, Recovery & Audit](/docs/agent/replay-recovery-audit).
- The sandbox the `routeTo` decision targets: [Sandbox SPI](/docs/agent/sandbox-spi).
- The veto-by-exception policy mechanism (a coarser sibling): [Approval & Permission Policy](/docs/agent/approval-permission-policy).
