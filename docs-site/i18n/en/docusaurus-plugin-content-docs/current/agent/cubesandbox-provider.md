---
sidebar_position: 10
title: "CubeSandbox Provider"
description: "CubeSandboxProvider is AI4J's first real remote sandbox adapter, mapping SandboxProvider/SandboxSession to the CubeSandbox CubeAPI control plane and the envd execution API."
tags: [integration]
---

# CubeSandbox Provider

`CubeSandboxProvider` is the first real remote sandbox adapter that AI4J ships inside `ai4j-agent`. It maps `SandboxProvider` / `SandboxSession` to the CubeAPI control plane and the envd process execution API of the open-source [TencentCloud/CubeSandbox](https://github.com/TencentCloud/CubeSandbox).

Its positioning is deliberate:

- **AI4J ships no built-in VM**: the isolated environment is provided by the CubeSandbox cluster you deploy.
- **The SDK is only an adapter**: it is responsible for the Java contract mapping of create / connect / execute / close.
- **Not auto-enabled**: CubeSandbox is only contacted when the host explicitly constructs a `CubeSandboxProvider` and calls `createSession(...)` or `connect(...)`.
- **Never persists secrets**: the API key is read only from the provider builder or environment variables, never from `SandboxSpec.config`, so session snapshots or YAML configuration cannot accidentally persist a secret.

## 1. Suitable scenarios

| Scenario | Suitable? |
| --- | --- |
| Spin up a remote Linux sandbox per Agent / Coding Agent session | Yes |
| Move shell / project tests / artifact collection off the local machine into an isolated environment | Yes, currently via `SandboxSession.execute(...)` |
| Connect to an existing CubeSandbox sandbox and run commands | Yes, using `provider.connect(...)` |
| Replace the local file/git/browser tools of `ai4j-coding` directly | Not yet; this needs coding tool routing |
| Out-of-the-box use with no local CubeSandbox cluster | No; you must first deploy CubeSandbox or wire into an existing CubeAPI |

## 2. Dependencies and configuration

The Java side adds no third-party dependencies. The adapter uses Java 8, the JDK HTTP client, and the project's existing JSON dependency.

Prefer environment variables for the real connection details:

```bash
export CUBE_API_URL="http://127.0.0.1:3000"
export CUBE_API_KEY="..."              # optional; also accepts E2B_API_KEY
export CUBE_TEMPLATE_ID="your-template"
export CUBE_SANDBOX_DOMAIN="cube.app"
export CUBE_ENVD_PORT="49983"
```

Compatible variables:

| Variable | Description |
| --- | --- |
| `CUBE_API_URL` / `E2B_API_URL` | CubeAPI address, default `http://127.0.0.1:3000` |
| `CUBE_API_KEY` / `E2B_API_KEY` | Control-plane auth token; sent as `Authorization: Bearer <key>` |
| `CUBE_TEMPLATE_ID` | Default template ID |
| `CUBE_SANDBOX_DOMAIN` | Sandbox domain suffix, default `cube.app` |
| `CUBE_ENVD_PORT` | envd data-plane virtual port, default `49983`; if your template/deployment exposes it on the Go SDK's `49999`, override with `49999` |
| `CUBE_TIMEOUT` | Sandbox TTL, default 300 seconds |
| `CUBE_REQUEST_TIMEOUT` | Control-plane request timeout, default 30 seconds |

## 3. Create and execute commands

```java
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxSession;

CubeSandboxProvider provider = new CubeSandboxProvider();
CubeSandboxSession session = provider.createSession(SandboxSpec.builder()
        .providerId("cubesandbox")
        .image(System.getenv("CUBE_TEMPLATE_ID"))
        .workspaceId("/workspace")
        .label("task", "demo")
        .config("allowInternetAccess", Boolean.FALSE)
        .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .commandId("hello")
            .command("printf ai4j-cubesandbox-ok")
            .workingDirectory("/workspace")
            .timeoutMillis(30000L)
            .build());

    System.out.println(result.getExitCode());
    System.out.println(result.getStdout());
} finally {
    session.close(); // a sandbox created via createSession is DELETE-destroyed by default
}
```

`SandboxCommand.command(...)` runs in the command shape used by the official CubeSandbox SDK:

```text
/bin/bash -l -c <command>
```

The return value is mapped to `SandboxResult.exitCode/stdout/stderr/durationMillis/artifacts/events`.

## 4. Connect to an existing sandbox

If the sandbox was already created by an external system, you can simply connect:

```java
CubeSandboxSession session = provider.connect("sandbox-id", SandboxSpec.builder()
        .providerId("cubesandbox")
        .workspaceId("/workspace")
        .build());

try {
    SandboxResult result = session.execute(SandboxCommand.builder()
            .command("pwd")
            .build());
} finally {
    session.close(); // an existing sandbox opened via connect(...) is NOT destroyed remotely by default
}
```

This differs from the `createSession(...)` lifecycle:

| Method | Remote sandbox source | Default `close()` behavior |
| --- | --- | --- |
| `createSession(...)` | AI4J calls `POST /sandboxes` to create it | Calls `DELETE /sandboxes/{id}` to destroy it |
| `connect(...)` | An externally pre-existing sandbox | Only closes the local connection; does not destroy the remote instance |


## 5. CLI: connect to an existing CubeSandbox session

`ai4j-cli` can attach the current coding session to a CubeSandbox session that **already exists**:

```text
/sandbox attach cubesandbox <sessionId> [workspaceId]
# or
/sandbox attach cube <sessionId> [workspaceId]
```

On execution, the CLI calls `CubeSandboxProvider.connect(sessionId, SandboxSpec)`:

- On success `/sandbox status` reports `mode=attached-live`, `runtime=live-session`.
- Subsequent `bash action=exec` calls from the coding agent enter CubeSandbox through the live `SandboxSession.execute(...)`.
- `disable` closes the local session handle, but `connect(...)` mode does not destroy the externally pre-existing remote sandbox.
- If the CubeAPI address, authentication, network, or sessionId is invalid, attach fails and keeps the previous runtime; it never falls back to local execution.

The CLI only attaches; it does not create or authenticate:

- It does not deploy CubeSandbox automatically.
- It does not create templates.
- It does not write the API key into workspace configuration or session snapshots.
- It does not pretend a sandbox execution succeeded when the provider bridge is missing.

## 6. Supported `SandboxSpec.config` keys

`SandboxSpec.config` is intended only for non-sensitive, task-level configuration:

| key | Effect |
| --- | --- |
| `templateId` / `templateID` | template ID; can also be expressed via `SandboxSpec.image` |
| `envVars` | environment variable map injected when the sandbox is created |
| `metadata` | metadata map written when the sandbox is created; sensitive keys are filtered |
| `allowInternetAccess` | when `false`, public network access is disabled |
| `network` | passed through to the CubeSandbox network configuration |
| `timeoutSeconds` / `timeout` | sandbox TTL in seconds |
| `requestTimeoutMillis` / `requestTimeout` | control-plane request timeout |
| `closeDestroysSandbox` / `destroyOnClose` | whether closing a session created via `createSession(...)` destroys it |
| `sandboxDomain` / `domain` | sandbox host domain suffix |
| `envdPort` / `envdHTTPPort` / `dataPort` | envd data-plane virtual port, default `49983` |
| `user` | envd Basic auth user, default `root` |
| `connectEnvelopeLimitBytes` | Connect message size limit, default 64MiB |

:::danger
Never put `apiKey`, tokens, cookies, or connection strings into `SandboxSpec.config` or labels. `CubeSandboxConfig.withSpecOverrides(...)` deliberately ignores `apiKey`; any key in labels or metadata that contains `secret/token/key/password/passwd/credential/authorization/cookie` is filtered out.
:::

## 7. Protocol mapping

| AI4J behavior | CubeSandbox behavior |
| --- | --- |
| `provider.health()` | `GET /health` |
| `createSession(...)` | `POST /sandboxes`, payload includes `templateID`, `timeout`, optional `envVars/metadata/allowInternetAccess/network` |
| `connect(...)` | `POST /sandboxes/{sandboxID}/connect` |
| `session.execute(...)` | envd `POST /process.Process/Start`, `application/connect+json` |
| `session.close()` | for a newly created session, `DELETE /sandboxes/{sandboxID}` by default |

The envd Connect stream uses a 5-byte envelope: 1 byte flags + 4 bytes big-endian size. stdout/stderr are decoded from the base64 fields in the CubeSandbox envd events.

AI4J targets envd port `49983` by default, which is the default port in the CubeSandbox envd/BYOI/files documentation. Some CubeSandbox SDK/templates reach the Jupyter/code-interpreter gateway via `49999`; if your template exposes `/process.Process/Start` on `49999`, set `CUBE_ENVD_PORT=49999` or `spec.config.envdPort=49999`.

## 8. Local and live verification

Deterministic protocol-level test:

```bash
mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

This test starts a local HTTP server stub and uses an `envdBaseUrl` override to route both control-plane (`POST /sandboxes`, `DELETE /sandboxes/{id}`, `POST /sandboxes/{id}/connect`) and data-plane (`envd /process.Process/Start`) requests to the same stub. It verifies: the full Create + Execute + Destroy flow; Bearer/Basic/X-Access-Token authentication; Connect envelope frame parsing; base64 stdout decoding; exitCode extraction; Connect end-stream error frame handling; the partial-envelope failure path; secret filtering (apiKey never appears in the create payload/metadata/labels); connect-existing mode (close does not delete the remote sandbox); config merging; provider selection; env var compatibility (CUBE_* / E2B_*); envdBaseUrl override; envdPort configuration; timeout merging; spec field protection; and more. It needs no real keys.

The real CubeSandbox smoke test is explicitly opt-in:

```bash
export AI4J_CUBESANDBOX_LIVE=true
export CUBE_API_URL="..."
export CUBE_TEMPLATE_ID="..."
# if the deployment enables auth, also set CUBE_API_KEY or E2B_API_KEY
mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test
```

:::warning
If the live environment variables are missing, the test is skipped via a JUnit `Assume`; never write real keys into logs, docs, PRs, or Harness materials.
:::

## 9. Current boundaries

- Only command execution is implemented so far; higher-level capabilities such as file upload/download, the Jupyter code API, snapshots, browser, and artifact download are not yet included.
- `ai4j-coding` currently routes foreground `bash exec` to a `SandboxSession`; file/git/browser/long-running processes/artifacts are not yet fully wired to the sandbox, so further work is needed before the coding agent reaches the "every tool runs in the remote sandbox" experience.

## 10. Related docs

- [Agent Sandbox SPI](/docs/agent/sandbox-spi)
- [Approval / Permission Policy](/docs/agent/approval-permission-policy)
- [Coding Agent Sandbox Routing](/docs/coding-agent/sandbox-routing)
