---
sidebar_position: 11
title: "Sandbox Routing"
description: "Explains how ai4j-coding routes the Coding Agent's bash exec to a live SandboxSession (P3 first slice), covering the current API, unrouted tools, the relationship with approval, and the security boundary of non-sensitive sandbox summaries."
tags: [integration]
---

# Sandbox Routing

This page explains how `ai4j-coding` wires the Coding Agent's execution tools to the Sandbox SPI of `ai4j-agent`.

Current state first: **the P3 first slice already supports routing foreground `exec` of `bash` to a live `SandboxSession`**. This is not a full cloud Runner, nor a filesystem-level isolation platform; it simply switches one-off shell commands from the local `LocalShellCommandExecutor` to `SandboxSession.execute(...)`.

## 1. What problem it solves

By default, the built-in tools of the Coding Agent execute in the host workspace:

```text
bash action=exec
  -> BashToolExecutor
  -> LocalShellCommandExecutor
  -> host shell
```

Once a sandbox is bound, `bash action=exec` becomes:

```text
bash action=exec
  -> BashToolExecutor
  -> SandboxShellCommandExecutor
  -> SandboxSession.execute(SandboxCommand)
```

This lets the host application send high-risk commands, project runs, and test commands to an external VM / container / microVM / remote sandbox for execution, rather than executing them directly on the local machine.

## 2. Current actual API

The host application first creates a live `SandboxSession` through its own `SandboxProvider`, then hands it to `CodingAgentBuilder`:

```java
SandboxSession sandboxSession = provider.createSession(SandboxSpec.builder()
        .providerId("my-sandbox")
        .workspaceId("/workspace/session-123")
        .label("purpose", "coding-agent")
        .build());

CodingAgent agent = CodingAgents.builder()
        .modelClient(modelClient)
        .model("your-model")
        .workspaceContext(WorkspaceContext.builder()
                .rootPath(projectRoot)
                .build())
        .sandbox(sandboxSession)
        .build();
```

Afterward, when a new coding session is created, AI4J does two things:

1. Binds a non-sensitive sandbox summary on the underlying `AgentSession`: `session.getDelegate().getSandboxBinding()`.
2. Switches the `exec` executor of the built-in `bash` to `SandboxShellCommandExecutor`.

## 3. Return result of `bash exec`

When a command enters the sandbox, the JSON result of `bash` carries the execution location:

```json
{
  "command": "mvn test",
  "workingDirectory": "/workspace/session-123",
  "executionEnvironment": "sandbox",
  "sandboxSessionId": "sandbox-session-id",
  "sandboxProviderId": "my-sandbox",
  "stdout": "...",
  "stderr": "...",
  "exitCode": 0,
  "timedOut": false
}
```

When no sandbox is bound, `executionEnvironment` is `local`, and the local shell is still used. That is:

```text
No sandbox = current local execution semantics unchanged
With sandbox = bash exec goes through SandboxSession.execute(...)
```

## 4. What is not done yet

The P3 first slice is deliberately narrow, to avoid getting the routing boundary wrong in one shot. The following capabilities are not yet routed to the sandbox automatically:

| Tool / capability | Current status |
| --- | --- |
| `read_file` | Still uses the local `WorkspaceFileService` |
| `write_file` | Still uses the local write executor |
| `apply_patch` | Still applies patches in the local workspace |
| `bash start/status/logs/write/stop/list` | Still managed by the local `SessionProcessRegistry` |
| browser / screenshot | Not yet wired to a sandbox provider in `ai4j-coding` |
| git / project run / test runner | Currently still behave as shell commands or follow-up capabilities |

Follow-up slices should continue to deliver:

1. Sandbox file read/write abstraction;
2. Applying patches inside the sandbox workspace;
3. Mapping long-running process lifecycle to the provider;
4. Browser/screenshot/artifact collection.

:::tip CLI `/sandbox` command is implemented
The `/sandbox` status display and switch inside the CLI/TUI **has landed** (parser `CliSandboxCommand`); it is no longer a planned capability. You can manage the sandbox binding of the current session within a session using `/sandbox status`, `/sandbox enable <provider>`, `/sandbox attach <provider> <id>`, and `/sandbox disable`. For command details see [Command Reference](/docs/coding-agent/command-reference).
:::

## 5. Relationship with approval

Sandbox routing does not replace approval.

Even when a command has been routed to a VM / container / remote environment, the host should still decide whether to allow execution through `ToolExecutorDecorator`, CLI approval, the ACP permission gateway, or the `ai4j-agent` permission policy.

The two layers have different responsibilities:

| Layer | Question it answers |
| --- | --- |
| approval / permission | Whether this tool call may execute |
| sandbox routing | If it may execute, where it executes |

## 6. Security boundary

`AgentSessionSandboxBinding` only stores non-sensitive summaries such as providerId, sandboxSessionId, workspaceId, profile, image, and labels.

It does not store `SandboxSpec.config`, and fields in the label whose names carry sensitive meaning such as token/key/password/credential/cookie are also filtered out.

:::danger Sensitive credentials must not be persisted
Therefore, real provider tokens, cookies, API keys, and tenant connection strings should only exist in the host application or the provider implementation, and must not be written into documentation examples, test fixtures, or session snapshots.
:::

## 7. Verification entry point

The minimum regression for the current first slice is:

```bash
mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test
```

The full coding runtime regression is:

```bash
mvn -pl ai4j-coding -am -DskipTests=false test
```

## 8. Further reading

1. [Agent Sandbox SPI](/docs/agent/sandbox-spi)
2. [Tools and the approval mechanism](/docs/coding-agent/tools-and-approvals)
3. [Sessions, streaming, and processes](/docs/coding-agent/session-runtime)
4. [AI4J Agent SDK Roadmap](/docs/agent/sdk-roadmap)
