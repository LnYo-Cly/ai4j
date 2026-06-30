---
sidebar_position: 11
---

# Tool Interceptor (block / modify / route-to-sandbox)

`ToolInterceptor` is the **control-flow** hook — the Claude-Code / pi "PreToolUse" interception
capability, in-process. It runs before a tool executes and the runtime honors its decision. This is
distinct from the observe-only [lifecycle hooks](/docs/agent/plugin-lifecycle-hooks) (which only
notify); an interceptor can veto, rewrite, or redirect a tool call.

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
