---
sidebar_position: 9
title: "Agent Sandbox SPI"
description: "Introduces the ai4j-agent Sandbox SPI: how the SandboxProvider/SandboxSession contract hands execution off to an isolated environment, how AgentSessionSandboxBinding keeps only a non-sensitive summary, the three official real providers (Daytona, E2B, CubeSandbox), and how AgentBuilder/CodingAgentBuilder consume the sandbox."
tags: [integration]
---

# Agent Sandbox SPI

`io.github.lnyocly.ai4j.agent.sandbox` is the real sandbox execution environment abstraction for `ai4j-agent`. The problem it solves:

> Once the agent has decided to execute a shell, file, browser, or project command, how does the host hand that execution off to a real isolated environment and get back stdout, stderr, artifacts, and events?

P2-A delivers the Java 8 SPI and data model; P2-B binds a non-sensitive sandbox summary to `AgentSession`; later phases shipped three official real providers: Daytona (P2-C), E2B (P2-D), and CubeSandbox (PR #218). You can still wire the same SPI to Docker/K8s, an in-house VM/microVM, or your own remote execution platform.

## 1. What it is not

The Sandbox SPI is not just another ordinary tool, nor is it a security promise.

| Not | Explanation |
| --- | --- |
| Not a `run_in_sandbox` tool | It is a provider/session contract for the tool execution environment, not a tool the model sees directly. |
| Not a local permission policy | A permission policy decides whether execution is allowed; the Sandbox SPI decides where it runs and how results come back. |
| Not a built-in VM | AI4J does not bundle Docker, K8s, a browser, or remote machines inside P2-A. |
| Not a way to bypass approval | Entering a sandbox does not automatically unlock dangerous capabilities; you still go through `AgentPermissionPolicy`. |

## 2. Minimal API

P2-A adds the package:

```text
io.github.lnyocly.ai4j.agent.sandbox
```

Core types:

| Type | Role |
| --- | --- |
| `SandboxProvider` | Implemented by the host or a plugin; creates a `SandboxSession`. |
| `SandboxSession` | An isolated execution environment that can run commands, list artifacts, cancel commands, and close. |
| `SandboxSpec` | Declares provider, profile, image, workspace, labels, config. |
| `SandboxCommand` | A single execution request, carrying command, cwd, stdin, timeout, env, metadata. |
| `SandboxResult` | A single execution result, carrying exitCode, stdout, stderr, timeout/cancel, artifact, event. |
| `SandboxArtifact` | Sandbox artifact metadata, e.g. logs, screenshots, archives. |
| `SandboxEvent` / `SandboxEventType` | Provider-neutral events, used later for the session event log / UI display. |
| `SandboxStatus` | `CREATED` / `RUNNING` / `CLOSED` / `FAILED`. |
| `SandboxException` | Checked exception thrown when a provider/session operation fails. |

## 3. Minimal provider example

The example below only illustrates the shape of the contract; it does not represent real isolation:

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

public class MySandboxProvider implements SandboxProvider {
    @Override
    public String getProviderId() {
        return "my-sandbox";
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        return spec == null
                || spec.getProviderId() == null
                || "my-sandbox".equals(spec.getProviderId());
    }

    @Override
    public SandboxSession createSession(SandboxSpec spec) {
        return new MySandboxSession(spec);
    }
}
```

A real provider should create the isolated environment inside `createSession(...)`, for example:

- Allocate a VM / container / microVM.
- Prepare a workspace or restore a snapshot.
- Set timeouts, network, filesystem, image, and resource limits.
- Return only a non-sensitive session summary; never write secrets into `SandboxSpec` or logs.

## 4. Running a command

```java
SandboxSpec spec = SandboxSpec.builder()
        .providerId("my-sandbox")
        .profile("default")
        .image("java8")
        .workspaceId("task-123")
        .label("project", "ai4j")
        .build();

SandboxSession session = provider.createSession(spec);

SandboxResult result = session.execute(SandboxCommand.builder()
        .command("mvn -pl ai4j-agent -DskipTests=false test")
        .workingDirectory("/workspace")
        .timeoutMillis(120000L)
        .environment("CI", "true")
        .metadata("tool", "project-test")
        .build());

if (Integer.valueOf(0).equals(result.getExitCode())) {
    System.out.println(result.getStdout());
}

for (SandboxArtifact artifact : result.getArtifacts()) {
    System.out.println(artifact.getPath());
}
```


## 5. P2-B: Binding to AgentSession

P2-B adds `AgentSessionSandboxBinding` on top of the P2-A SPI. It is not a live provider, nor does it start a VM; it only binds the current sandbox's **non-sensitive summary** to `AgentSession`:

```java
SandboxSession sandbox = provider.createSession(spec);

AgentSession session = agent.newSession()
        .bindSandbox(sandbox);

AgentSessionSnapshot snapshot = session.snapshot();
System.out.println(snapshot.getSandboxBinding().getProviderId());
System.out.println(snapshot.getSandboxBinding().getWorkspaceId());
```

This binding flows into:

- `AgentSession.getSandboxBinding()`
- `AgentSession.snapshot()`
- `AgentSession.restore(snapshot)`
- `AgentSessionStore.save/load(...)`
- The session event log: `SANDBOX_BOUND`, `SANDBOX_UPDATED`, `SANDBOX_CLEARED`

You can update or clear the state:

```java
session.updateSandboxStatus(SandboxStatus.CLOSED);
session.clearSandbox();
```

### Security boundary

`AgentSessionSandboxBinding` keeps only summary fields: providerId, sandboxSessionId, status, profile, image, workspaceId, labels, boundAt, updatedAt.

It does not keep `SandboxSpec.config`, because provider config may contain tokens, cookies, API keys, connection strings, or tenant information. Labels whose key carries a sensitive meaning such as `secret`, `token`, `key`, `password`, `credential`, `cookie`, or `authorization` are also filtered out.

:::warning Secrets never enter the snapshot
In other words, P2-B lets a session "know which sandbox it is bound to" without dragging the real sandbox provider's secrets into the snapshot, store, event log, or doc examples.
:::

## 6. Relationship to the Permission Policy

The two sit at different layers:

```text
Agent / Coding Tool
  -> AgentPermissionPolicy: whether execution is allowed
  -> SandboxProvider: where it runs
  -> SandboxSession: how it runs and how results come back
```

Recommended rules:

1. Tool execution still passes through `AgentPermissionPolicy` first.
2. The policy can branch on `AgentExecutionEnvironment.SANDBOX` / `REMOTE_SANDBOX`.
3. But `AgentExecutionEnvironment` is only metadata; real routing waits for P3, where `ai4j-coding` wires into `SandboxSession`.
4. No provider should ever write tokens, cookies, or API keys into `SandboxSpec.config`, `SandboxEvent.message`, or artifact names.

## 7. Relationship to the Agent Blueprint

The P1 YAML already has a declaration field:

```yaml
sandbox:
  enabled: true
  provider: my-sandbox
  profile: default
  config:
    image: java8
```

P2-A still does not let the Blueprint auto-create a sandbox. Later P2-B/P3 turns the declaration into a safe `SandboxSpec` and binds it to an `AgentSession` or coding session when the host explicitly allows it.

## 8. Relationship to `ai4j-coding`

P2-A lands only inside `ai4j-agent`. What actually affects the coding agent is the next phase:

| Tool | Without sandbox | Goal once a sandbox exists |
| --- | --- | --- |
| file | local workspace | sandbox workspace |
| shell | host shell | `SandboxSession.execute(...)` |
| git | local git | sandbox git command |
| browser | host browser capability | browser capability exposed by the provider |
| project run/test | local command | sandbox command + artifact |

This part belongs to P3 `ai4j-coding` sandbox routing and is not implemented in P2-A.

## 9. Fake provider testing

P2-A's deterministic tests use an inline fake provider and verify:

- The provider can create a `SandboxSession` from a `SandboxSpec`.
- `SandboxCommand` carries command, cwd, timeout, env, metadata.
- `SandboxResult` returns exitCode, stdout, stderr, artifact, and events.
- DTOs return defensive copies; callers cannot mutate internal state.
- A closed session refuses further execution.

Local regression command:

```bash
mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test
```

## 10. FAQ

### With the Sandbox SPI, can I run remote commands right away?

No. P2-A is only the interface and data model. You still need a provider implementation, and later the file/shell/git/browser tools have to be routed to the sandbox inside `ai4j-coding`.

### Will AI4J officially bundle many providers?

It will not bundle a pile of providers. The more stable path is: AI4J ships a small, stable SPI and keeps a handful of officially verified real providers. Three official real providers have already landed: **Daytona, E2B, CubeSandbox**; Docker/K8s, in-house platforms, and so on can continue to be wired in by plugins, business teams, or follow-up tasks.

| Provider | providerId | Execution model | Detailed docs |
| --- | --- | --- | --- |
| Daytona | `daytona` | Daytona API to create/stop sandboxes + toolbox execute API to run commands | this page [§11](#11-p2-c--daytona-provider) |
| E2B | `e2b` | E2B control API (`X-API-Key`) to create/destroy + per-sandbox execution host using Connect `process.Process/Start` streaming | this page [§12](#12-p2-d--e2b-provider) |
| CubeSandbox | `cubesandbox` | CubeAPI control plane of the open-source TencentCloud/CubeSandbox + envd `process.Process/Start` data plane | [CubeSandbox Provider](/docs/agent/governance/cubesandbox-provider) |

### Should each user get their own sandbox, or share one?

By default, isolate the writable execution environment per user/task/session. You can share images, dependency caches, or read-only templates, but do not let multiple users share the same writable sandbox.

### Can a sandbox replace permission approval?

:::note A sandbox does not replace permission approval
No. A sandbox reduces the risk of the execution environment; the permission policy governs "whether execution is allowed at all". The two should stack, not substitute for each other.
:::


## 11. P2-C: Daytona provider

P2-C adds a real, usable Daytona integration on top of the generic SPI:

```text
io.github.lnyocly.ai4j.agent.sandbox.daytona
```

Core classes:

| Type | Role |
| --- | --- |
| `DaytonaSandboxProvider` | `SandboxProvider` implementation, `providerId=daytona`. |
| `DaytonaSandboxConfig` | Reads Daytona connection, creation, startup, and cleanup config from environment variables and `SandboxSpec.config`. |
| `DaytonaSandboxClient` | Java 8 `HttpURLConnection` client that calls the Daytona API and the toolbox execute API. |
| `DaytonaSandboxSession` | Turns a `SandboxCommand` into a Daytona process execute request and returns a `SandboxResult`. |

### Minimal usage

Prefer putting the key in an environment variable rather than in code, YAML, or logs:

```bash
export DAYTONA_API_KEY="..."
# Optional; when omitted, the default Daytona API URL is used
export DAYTONA_API_URL="https://app.daytona.io/api"
```

On the Java side, declare only the provider, workspace, and non-sensitive policy:

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;

SandboxSession session = new DaytonaSandboxProvider().createSession(
        SandboxSpec.builder()
                .providerId("daytona")
                .workspaceId("ai4j-demo")
                .config("createIfMissing", Boolean.TRUE)
                .config("deleteOnClose", Boolean.TRUE)
                .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("printf ai4j-daytona-ok")
            .timeoutMillis(30000L)
            .build());
    System.out.println(result.getExitCode());
    System.out.println(result.getStdout());
} finally {
    session.close();
}
```

### Configuration sources

| Config | Source | Notes |
| --- | --- | --- |
| `DAYTONA_API_KEY` / `apiKey` | env / `SandboxSpec.config` | Daytona API key; in production, prefer env. |
| `DAYTONA_API_URL` / `apiUrl` | env / config | Daytona API address; uses the SDK default when omitted. |
| `DAYTONA_TOOLBOX_PROXY_URL` / `toolboxProxyUrl` | env / config | Optional; when omitted the provider queries the toolbox proxy URL. |
| `DAYTONA_ORGANIZATION_ID` / `organizationId` | env / config | Optional organization/tenant header. |
| `DAYTONA_TARGET` / `target` | env / config | Optional Daytona target. |
| `sandboxId` | config | Attach to an existing sandbox. |
| `sandboxName` / `name` / `workspaceId` | config / spec | Name of the sandbox to attach to or create. |
| `createIfMissing` | config | Whether to create on attach 404; defaults to `true`. |
| `deleteOnClose` | config | Whether to delete the sandbox on `close()`; defaults to `false`. |
| `snapshot` / `image` | config / spec | Daytona snapshot/image. |
| `env` | config | Non-sensitive environment variables to inject when creating the sandbox. |
| `connectTimeoutMillis`, `readTimeoutMillis`, `startTimeoutMillis`, `pollIntervalMillis` | config | HTTP and startup polling timeouts. |

### Current boundaries

- Supports create-or-attach, start/poll, process execute, and `deleteOnClose` cleanup.
- `SandboxCommand`'s `command`, `workingDirectory`, `stdin`, `environment`, `timeoutMillis` map onto the Daytona toolbox execute request.
- `cancel(...)` currently returns `false`; the artifact list is currently empty and should be wired in later through a Daytona artifact/file API.
- Live smoke belongs to `live-provider-opt-in`, runs explicitly via `-P live-provider-tests`, and reads keys only from environment variables.

Local deterministic regression:

```bash
mvn -pl ai4j-agent -am "-Dtest=DaytonaSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

Real Daytona smoke (requires environment variables):

```bash
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=DaytonaSandboxLiveSmokeTest" -DskipTests=false -DfailIfNoTests=false test
```

## 12. P2-D: E2B provider

P2-D adds a second real, usable E2B integration on top of the generic SPI:

```text
io.github.lnyocly.ai4j.agent.sandbox.e2b
```

E2B's execution model differs from Daytona's: it creates/destroys sandboxes through the E2B control API (`X-API-Key`), then runs commands through each sandbox's execution host (`Authorization: Bearer`) using the Connect server-streaming `process.Process/Start` protocol. These protocol details are encapsulated by the provider; the caller just needs `SandboxSession.execute(...)`.

Core classes:

| Type | Role |
| --- | --- |
| `E2BSandboxProvider` | `SandboxProvider` implementation, `providerId=e2b`. |
| `E2BSandboxConfig` | Reads E2B connection, template, timeout, and cleanup config from environment variables and `SandboxSpec.config`. |
| `E2BSandboxClient` | Java 8 `HttpURLConnection` client: control API (create/delete) + Connect frame codec (`buildProcessFrame` / `parseConnectStream`). |
| `E2BSandboxSession` | Maps a `SandboxCommand` to `sh -c` execution (optional stdin pipe) and returns a `SandboxResult`. |

### Minimal usage

Prefer putting the key in an environment variable rather than in code, YAML, or logs:

```bash
export E2B_API_KEY="e2b_..."
# Optional; when omitted, SDK defaults are used (domain e2b.app / template base / execution port 49983)
```

On the Java side, declare only the provider, template, and non-sensitive policy:

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;

SandboxSession session = new E2BSandboxProvider().createSession(
        SandboxSpec.builder()
                .providerId("e2b")
                .config("templateID", "base")
                .config("timeoutSeconds", Integer.valueOf(300))
                .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("printf ai4j-e2b-ok")
            .timeoutMillis(30000L)
            .build());
    System.out.println(result.getExitCode());   // 0
    System.out.println(result.getStdout());     // ai4j-e2b-ok
} finally {
    session.close();   // destroys the sandbox by default
}
```

### Configuration sources

| Config | Source | Notes |
| --- | --- | --- |
| `E2B_API_KEY` / `apiKey` | env / `SandboxSpec.config` | E2B API key; in production, prefer env. |
| `E2B_DOMAIN` / `apiDomain` | env / config | E2B domain, default `e2b.app`. |
| `E2B_API_URL` / `apiUrl` | env / config | Control API address; derived as `https://api.<domain>` when omitted. |
| `E2B_TEMPLATE_ID` / `templateId` / `templateID` / `image` | env / config / spec | Template, default `base`. |
| `E2B_ENVD_PORT` / `envdPort` | env / config | Execution host port, default `49983`. |
| `E2B_TIMEOUT` / `timeoutSeconds` | env / config | Sandbox lifetime in seconds, default `300`. |
| `E2B_SANDBOX_URL` / `sandboxUrl` | env / config | Optional; overrides the derived execution host (e.g. to go through a self-hosted proxy). |
| `E2B_ACCESS_TOKEN` / `envdAccessToken` | env / config | Optional; envd access token for the secure flow (sent as `X-Access-Token`). When omitted, the API key is used as the Bearer. |
| `useShellWrap` | config | Whether to wrap the command with `sh -c`; defaults to `true`. |
| `deleteOnClose` | config | Whether to delete the sandbox on `close()`; defaults to `true`. |
| `env` | config | Non-sensitive environment variables to inject. |
| `connectTimeoutMillis`, `readTimeoutMillis` | config | HTTP timeouts. |

### Execution model and boundaries

- By default the command is wrapped as `sh -c <command>`, supporting pipes, redirects, and multi-statement commands (aligned with Daytona's shell semantics).
- When `SandboxCommand.stdin` is non-empty, it is piped in via `printf '%s' '<stdin>' | ( <command> )` while preserving the exit code. With `useShellWrap=false`, the command is split on whitespace and exec'd directly (stdin not supported).
- stdout / stderr are base64-streamed output; the exit code comes from Connect `end.exitCode`, and when it is 0 it is parsed from the `"exit status N"` status string.
- `cancel(...)` currently returns `false` (process `SendSignal` not wired); `listArtifacts()` is currently empty (filesystem API not wired).
- Live smoke belongs to `live-provider-opt-in`, runs explicitly via `-P live-provider-tests`, and reads keys only from environment variables.

Local deterministic regression:

```bash
mvn -pl ai4j-agent -am "-Dtest=E2BSandboxClientTest,E2BSandboxProviderTest,E2BSandboxConfigTest" -DskipTests=false -DfailIfNoTests=false test
```

Real E2B smoke (requires environment variables):

```bash
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=E2BSandboxLiveSmokeTest" -DskipTests=false -DfailIfNoTests=false test
```

## 13. CubeSandbox provider

The third official real provider adapts the open-source [TencentCloud/CubeSandbox](https://github.com/TencentCloud/CubeSandbox):

```text
io.github.lnyocly.ai4j.agent.sandbox.cubesandbox
```

Core classes:

| Type | Role |
| --- | --- |
| `CubeSandboxProvider` | `SandboxProvider` implementation, `providerId=cubesandbox`. |
| `CubeSandboxConfig` | Reads connection, template, port, and timeout config from environment variables (`CUBE_*`, compatible with `E2B_*`) and `SandboxSpec.config`; deliberately ignores `apiKey` and never reads keys from `spec.config`. |
| `CubeSandboxClient` | Java 8 `HttpURLConnection` client: CubeAPI control plane (`POST /sandboxes`, `DELETE`, `POST /connect`, `GET /health`) + envd Connect frame codec. |
| `CubeSandboxSession` | Maps a `SandboxCommand` to envd `process.Process/Start` (default `/bin/bash -l -c <command>`) and returns a `SandboxResult`. |

Compared with Daytona / E2B, CubeSandbox adds an entry point to **attach to an existing session**:

```java
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;

CubeSandboxProvider provider = new CubeSandboxProvider();

// 1) Create a new sandbox (DELETE / destroy on close by default)
SandboxSession created = provider.createSession(SandboxSpec.builder()
        .providerId("cubesandbox")
        .image(System.getenv("CUBE_TEMPLATE_ID"))
        .workspaceId("/workspace")
        .build());

// 2) Connect to an externally existing sandbox (on close only the local handle is closed; the remote is not destroyed)
SandboxSession attached = provider.connect("existing-sandbox-id", SandboxSpec.builder()
        .providerId("cubesandbox")
        .workspaceId("/workspace")
        .build());
```

`ai4j-cli` exposes this attach capability as `/sandbox attach cubesandbox <sessionId> [workspaceId]`, letting a running coding session attach on the fly to an existing CubeSandbox.

:::note Full capability list on the dedicated page
CubeSandbox's environment variables, supported `SandboxSpec.config` keys, protocol mapping table, envd port (default `49983`, some templates use `49999`), local and live verification commands, and current boundaries are all on the [CubeSandbox Provider](/docs/agent/governance/cubesandbox-provider) page.
:::

Local deterministic regression:

```bash
mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

## 14. How the runtime consumes SandboxProvider

The Sandbox SPI is only the interface and data model; what actually lets the agent / coding agent use a sandbox is the wiring at the builder layer. Two consumption paths:

### 14.1 AgentBuilder registers a provider

`AgentBuilder.sandboxProvider(SandboxProvider)` attaches a provider to the agent so the runtime / session can create sessions on demand:

```java
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("glm-5.1")
        .sandboxProvider(new E2BSandboxProvider())   // register the provider
        .build();
```

The provider itself does not auto-start a sandbox. The host decides when to call `provider.createSession(spec)` to get a live `SandboxSession`, then `agentSession.bindSandbox(session)` binds it into the current session (see [§5](#5-p2-b--binding-to-agentsession)). After binding, only the non-sensitive summary enters the snapshot / event log; keys stay on the provider side.

### 14.2 CodingAgentBuilder configures sandbox routing

`ai4j-coding`'s `CodingAgentBuilder` wraps one more layer of sandbox routing on top of the agent so the built-in `bash` tool automatically goes through the sandbox:

```java
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgentBuilder;

CodingAgent agent = new CodingAgentBuilder()
        .workspaceRoot(workspacePath)
        .modelClient(modelClient)
        .model("glm-5.1")
        .sandbox(liveSandboxSession)        // wrapped as CodingSandboxRuntime
        .build();
```

Wiring details (inside `CodingAgentBuilder` / `CodingAgent`; callers generally do not touch these):

1. `.sandbox(SandboxSession)` or `.sandboxRuntime(CodingSandboxRuntime)` wraps the live session into `CodingSandboxRuntime`; `CodingSandboxRuntime` holds only the live `SandboxSession` and does **not** persist keys (the persisted summary is still handled by `ai4j-agent`'s `AgentSessionSandboxBinding`).
2. `createBuiltInToolExecutor(...)` — when `sandboxRuntime != null`, routes the `bash` entry in the built-in tool table to `BashToolExecutor` and replaces the local shell executor with `SandboxShellCommandExecutor`; when `sandboxRuntime == null` it returns `null` and falls back to local execution.
3. `SandboxShellCommandExecutor.execute(...)` turns each foreground shell request into a `SandboxCommand`, calls `SandboxSession.execute(...)`, and stdout/stderr/exitCode/artifact come straight from the sandbox.
4. `CodingAgent.bindSandbox(session)` — after the build, automatically binds the live `SandboxSession` to the `AgentSession`, so `/sandbox status`, the snapshot, and the event log all see the current sandbox summary.

Current routing scope: foreground `bash exec` is in the sandbox; `file`, `git`, `browser`, long-running processes, and artifact download are not yet fully wired to `SandboxSession`, so the coding agent still needs follow-up slices before "every tool runs in the remote sandbox".

## 15. Next steps

Recommended follow-up order:

1. P2-B: the non-sensitive summary of `SandboxSpec` / `SandboxSession` has been bound to the `AgentSession` snapshot / event log.
2. P2-C / P2-D / PR #218: three official real providers — Daytona, E2B, CubeSandbox — have landed, leaving room for the provider registry / plugin-contributed providers to extend later.
3. P3: `ai4j-coding`'s `bash exec` now routes execution based on the sandbox binding; file/git/browser/project runner should still be split into smaller slices along the boundary.
4. P4: surface `/sandbox status`, provider, workspace, most recent execution location, and artifacts in the CLI/TUI.
