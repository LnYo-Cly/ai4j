# P0-D Agent approval/permission policy design plan

> Task: `MODULES/agent-runtime/2026-06-20-p0-d-agent-approval-and-permission-policy-95b57bb5`  
> Scope: `ai4j-agent` minimal policy foundation; no real sandbox provider; no CLI approval UI.

## 1. Why this exists

The current Agent SDK roadmap needs a stable boundary between model-produced tool calls and real execution. This boundary is required before AI4J can safely grow toward:

- Blueprint YAML fields such as tool approval policy.
- CLI/TUI approval prompts.
- Coding-agent sandbox routing.
- Remote sandbox / runner products.

The correct P0-D deliverable is therefore not a sandbox. It is a small policy gate that can answer:

```text
Given this tool call and this execution environment metadata,
should the agent execute now, deny immediately, or stop for approval?
```

## 2. Product semantics

### In scope

- Host-side permission/approval gate.
- Deterministic Java API.
- Execution environment metadata:
  - `LOCAL`
  - `SANDBOX`
  - `REMOTE_SANDBOX`
- Runtime wiring through `ToolExecutor`.
- Testable blocked execution behavior.

### Out of scope

- Creating containers, VMs, microVMs, browser sandboxes, or remote sessions.
- Persisting approval queues.
- CLI/TUI interactive prompt.
- Blueprint YAML syntax.
- ai4j-coding shell/file/git/browser routing.

## 3. API shape

Recommended minimal types:

```text
AgentExecutionEnvironment
AgentPermissionDecisionType
AgentPermissionDecision
AgentPermissionRequest
AgentPermissionPolicy
AgentPermissionException
AgentApprovalRequiredException
AgentPermissionToolExecutor
AgentPermissionPolicies
```

### Request fields

- tool call object or extracted tool name/arguments.
- execution environment metadata.

### Decision fields

- type: allow / deny / require approval.
- reason: stable string for logs, docs and UI.

## 4. Runtime placement

The policy should wrap final tool execution rather than model request construction.

```text
Routing/default executor
  -> SubAgentToolExecutor if configured
  -> ExtensionGuardrailToolExecutor if configured
  -> AgentPermissionToolExecutor
  -> runtime context
```

This placement keeps the policy close to the execution boundary and makes it cover ordinary tools, extension tools and subagent tool calls as consistently as possible.

## 5. Error behavior

P0-D may surface deny / require-approval as tool execution errors because the runtime already has a `TOOL_ERROR` path. This is acceptable for the foundation, provided tests prove:

- delegate is not executed;
- the error is observable;
- reason text is not lost completely.

Future CLI approval UX can catch `AgentApprovalRequiredException` before converting it into a final user-facing event.

## 6. Tests

Minimum tests:

1. allow policy executes delegate and returns delegate output.
2. deny policy throws before delegate execution.
3. require-approval throws before delegate execution.
4. policy receives `REMOTE_SANDBOX` metadata.
5. `AgentBuilder` wiring triggers policy during runtime tool execution and returns a visible tool error.

## 7. Docs wording

Docs must say explicitly:

- This is not a real sandbox.
- `executionEnvironment` is metadata for policy decisions.
- Real sandbox support belongs to a future `SandboxProvider` / `SandboxSession` SPI.
- Approval UI belongs to CLI/TUI tasks.
- Without a policy, existing tool execution behavior remains unchanged.

## 8. Residuals to track

- AgentTeam dynamic executor wrapping may require a follow-up test to prove permission wrapper still applies after team dispatch.
- Blueprint field names should wait for P1.
- CLI approval prompt and pending approval queue should wait for P4.
