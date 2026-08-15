---
sidebar_position: 8
title: "Agent Approval / Permission Policy"
description: "AgentPermissionPolicy performs permission checks before tool execution: ALLOW / DENY / REQUIRE_APPROVAL. It can be reused by regular Agents, Blueprints, the CLI approval UI, and the later Sandbox SPI."
tags: [concept]
---

# Agent Approval / Permission Policy

`AgentPermissionPolicy` is the pre-tool-execution policy layer of `ai4j-agent`. The problem it solves is clear:

> The model has already decided to call a tool, but does the host program allow it to execute right now?

This is not a real sandbox, nor does it create VMs, containers, or remote environments. It simply fixes the pre-execution permission check into a small, testable Java API, reused by regular Java Agents, later Blueprints, the CLI/TUI approval UI, and the Sandbox SPI.

## 1. When you need it

If your Agent has tool-calling capability, you should think about this layer.

| Scenario | Suitable |
| --- | --- |
| Only calls the model once, exposes no tools | Not needed |
| Only exposes pure query tools, e.g. weather lookup, read-only cache reads | Optional |
| Exposes file writes, command execution, HTTP requests, DB mutations, workflow triggers | Needed |
| Want users to approve dangerous tools in a CLI/TUI | Needed; P0-D provides the policy foundation first |
| Want to integrate a real remote sandbox | Needed, but the real sandbox belongs to the later Sandbox SPI |

Without a `permissionPolicy(...)` configured, the existing `ToolExecutor` behavior is unchanged.

## 2. Minimal example

The following example allows ordinary tools to execute but denies `bash`:

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;

import java.util.Collections;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .toolRegistry(toolRegistry)
        .toolExecutor(toolExecutor)
        .permissionPolicy(
                AgentPermissionPolicies.denyTools(
                        Collections.singleton("bash"),
                        "local shell is disabled for this agent"))
        .executionEnvironment(AgentExecutionEnvironment.LOCAL)
        .build();
```

When the model calls `bash`:

1. The runtime first validates that the tool call structure is well-formed.
2. After structural validation passes, control enters `AgentPermissionToolExecutor`.
3. The policy returns `DENY`.
4. The delegate `ToolExecutor` does not execute.
5. The runtime wraps the exception into an observable `TOOL_ERROR` tool result, which subsequent model turns can see.

## 3. API surface

The core types introduced by P0-D live in:

```text
io.github.lnyocly.ai4j.agent.permission
```

| Type | Purpose |
| --- | --- |
| `AgentPermissionPolicy` | Policy interface; takes a single tool call request and returns a decision |
| `AgentPermissionRequest` | Policy input, containing the tool call and execution-environment metadata |
| `AgentPermissionDecision` | Policy output, expressing allow, deny, or approval-required |
| `AgentPermissionDecisionType` | `ALLOW` / `DENY` / `REQUIRE_APPROVAL` |
| `AgentExecutionEnvironment` | `LOCAL` / `SANDBOX` / `REMOTE_SANDBOX` metadata |
| `AgentPermissionToolExecutor` | Pre-execution gate that wraps the real `ToolExecutor` |
| `AgentPermissionException` | Thrown when the policy denies |
| `AgentApprovalRequiredException` | Thrown when the policy requires approval |
| `AgentPermissionPolicies` | Factory for common policies |

## 4. Custom policies

You can implement your own policy, for example to allow the browser tool only in a remote sandbox environment:

```java
AgentPermissionPolicy policy = request -> {
    if ("browser".equals(request.getToolName())
            && request.getEnvironment() != AgentExecutionEnvironment.REMOTE_SANDBOX) {
        return AgentPermissionDecision.deny("browser must run in remote sandbox");
    }
    return AgentPermissionDecision.allow();
};
```

The policy can see:

- `request.getToolName()`
- `request.getArguments()`
- `request.getCallId()`
- `request.getToolCall()`
- `request.getEnvironment()`

:::note
`getEnvironment()` is only metadata. Setting it to `REMOTE_SANDBOX` does not automatically create a remote sandbox, nor does it automatically route the tool to a remote machine.
:::

## 5. Semantics of `REQUIRE_APPROVAL`

`REQUIRE_APPROVAL` means:

> The current tool call is not permanently forbidden, but it cannot proceed until the host or user approves it.

P0-D only provides this state; it does not implement interactive waiting. The current runtime wraps it into a tool error result. The benefits of doing so:

- The Java API can stabilize first.
- A CLI/TUI can later capture `AgentApprovalRequiredException` and show an approval dialog or command-line confirmation.
- Blueprint YAML can later map `approval: safe | ask | deny` onto the same set of policies.
- A real sandbox provider can also reuse the same policy rather than inventing a separate approval semantics.

## 6. Relationship with `ToolExecutor`

`ToolExecutor` is the tool execution boundary of `ai4j-agent`:

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

P0-D's implementation wraps it:

```text
AgentRuntime
  -> AgentToolCallSanitizer
  -> AgentPermissionToolExecutor
  -> delegate ToolExecutor
```

That is:

- `AgentToolRegistry` decides which tools the model can see.
- `AgentToolCallSanitizer` only checks whether the tool call structure is well-formed.
- `AgentPermissionPolicy` decides whether a well-formed call is allowed to execute.
- `ToolExecutor` actually executes the tool.

:::note
Do not push business authorization logic into the sanitizer. The sanitizer should only answer "does this look like an executable call", not "is this permitted by the business".
:::

## 7. Relationship with plugins, guardrails, and subagents

The executor wiring order in the current `AgentBuilder` can be understood as:

```text
base executor
  -> extension tool routing
  -> subagent executor
  -> extension guardrails
  -> permission policy wrapper
```

Because `AgentPermissionToolExecutor` is the outermost wrapper, ordinary tools, extension tools, and subagent tools all pass through the permission policy first, then enter the delegate execution chain.

Note: in the Team runtime there is a path that dynamically replaces a member's executor. If team orchestration needs to force inheritance of the same tool approval policy, you should add dedicated team-scenario tests or harden this within the team task.

## 8. Relationship with Sandbox

P0-D is not the Sandbox SPI.

| Capability | Provided by P0-D |
| --- | --- |
| Decide whether a tool is allowed to execute | Yes |
| Express that approval is required | Yes |
| Mark the execution environment as `LOCAL` / `SANDBOX` / `REMOTE_SANDBOX` | Yes, but only as metadata |
| Create VMs / containers / microVMs | No |
| Upload a workspace to a remote environment | No |
| Execute shell/file/git/browser on a remote host | No |
| Collect sandbox artifacts / screenshots | No |

A real sandbox should later be carried by a contract similar to the following:

```text
SandboxProvider
SandboxSession
SandboxSpec
SandboxCommand
SandboxResult
SandboxArtifact
```

The relationship between permission policy and sandbox is:

```text
permission policy decides whether execution is allowed
sandbox provider decides where and how it executes
```

:::warning
Entering a sandbox does not automatically relax permissions. Even when a tool runs in a remote environment, it should still go through the approval / permission policy.
:::

## 9. Common policies

### 9.1 Allow only an allowlist of tools

```java
.permissionPolicy(AgentPermissionPolicies.allowTools(
        new java.util.LinkedHashSet<String>(java.util.Arrays.asList("weather", "search"))))
```

Any tool outside the allowlist is denied.

### 9.2 Deny a set of dangerous tools

```java
.permissionPolicy(AgentPermissionPolicies.denyTools(
        Collections.singleton("bash"),
        "shell command is disabled"))
```

Tools that are not matched are allowed through.

### 9.3 Require approval for write operations

```java
.permissionPolicy(AgentPermissionPolicies.requireApprovalForTools(
        Collections.singleton("write_file"),
        "file write needs user approval"))
```

The current runtime turns this into an observable tool error; a later CLI/TUI can turn it into a real user-confirmation flow.

## 10. Troubleshooting

### The tool is not intercepted by the policy

First check whether the tool call passes structural validation. For example, `bash` must have valid arguments:

```json
{"command":"echo hi"}
```

If the arguments are `{}`, it is first rejected by `AgentToolCallSanitizer` before ever reaching the permission policy.

### Configured `executionEnvironment(REMOTE_SANDBOX)`, but the tool still executes locally

This is expected. `executionEnvironment` is only policy metadata and is not responsible for routing tools. Real remote execution waits for the Sandbox SPI and coding tool routing.

### Want interactive approval

P0-D only returns the `REQUIRE_APPROVAL` state and exception type. Interactive approval is the responsibility of the CLI/TUI or host application.

The host application can choose to:

- Return an error to the model for the current turn.
- Intercept the exception in its own UI and show a confirmation.
- Write the pending approval into a business queue.
- Re-execute the corresponding tool call once approval is granted.

## 11. Next steps

If you are building a more complete Agent product, continue in this order:

1. Pin down the pre-execution permission semantics with `AgentPermissionPolicy`.
2. Map the tool `approval` field in Blueprints onto a policy.
3. Render `REQUIRE_APPROVAL` as a confirmable interaction in the CLI/TUI.
4. Replace `AgentExecutionEnvironment` with a real sandbox binding summary in the Sandbox SPI.
5. In `ai4j-coding`, route file / shell / git / browser tool execution based on the sandbox binding.
