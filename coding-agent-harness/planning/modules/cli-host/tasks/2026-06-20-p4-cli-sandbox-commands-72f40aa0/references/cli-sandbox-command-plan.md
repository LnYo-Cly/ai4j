# CLI `/sandbox` command implementation plan

> Task：`MODULES/cli-host/2026-06-20-p4-cli-sandbox-commands-72f40aa0`  
> Date：2026-06-20  
> Scope：`ai4j-cli` first slice on top of P3 coding sandbox routing

## 1. Product intent

本切片要解决的是“用户在 CLI/TUI 中能不能看见和切换 sandbox 状态”。它不是云端 sandbox 产品，也不是 provider adapter。完成后，用户至少可以清楚知道：

- 当前会话是 `direct-host`，还是 attached 到一个外部 sandbox session 摘要。
- 如果 attached，provider/session/workspace 是什么。
- 当前 CLI 是否已经把这个 binding 传给 coding runtime。
- 如果没有真实 provider bridge，哪些命令不会被伪装成 sandbox 成功。

## 2. Command contract

| Command | Behavior | Mutates runtime | Notes |
| --- | --- | --- | --- |
| `/sandbox` | 等价 `/sandbox status` | no | 展示 mode、provider/session/workspace、routing boundary |
| `/sandbox status` | 展示当前 sandbox binding | no | direct-host 时说明 bash exec 使用 host executor |
| `/sandbox attach <providerId> <sessionId> [workspaceId]` | 记录当前 CLI session 的 attached sandbox 摘要并 rebind runtime | yes | 不创建真实 sandbox；不保存 secret；无 provider bridge 时必须清晰失败/说明 |
| `/sandbox disable` | 清除 binding，rebind 回 direct-host | yes | 后续 shell 回到 local executor |

建议输出示例：

```text
sandbox:
- mode=direct-host
- routing=bash exec uses local host runtime
- attach=/sandbox attach <providerId> <sessionId> [workspaceId]
```

```text
sandbox:
- mode=attached
- provider=cubesandbox
- session=sbx_123
- workspace=project_abc
- routing=bash exec is routed through SandboxSession when a provider bridge is available
- boundary=this command does not create or authenticate a sandbox provider
```

## 3. Implementation seams

### 3.1 CLI state model

Create a small CLI-owned value object, likely under `io.github.lnyocly.ai4j.cli.sandbox` or `io.github.lnyocly.ai4j.cli.runtime`:

- `CliSandboxBinding`
  - `providerId`
  - `sessionId`
  - `workspaceId`
  - `attachedAtEpochMs`
  - `source` such as `cli-attach`
- Optional `CliAttachedSandboxSession implements SandboxSession`
  - returns non-sensitive ids/spec/status
  - only executes if backed by a real bridge; otherwise fails loudly or is not passed into runtime

Implementation must choose one of two honest behaviors:

| Option | Behavior | Trade-off |
| --- | --- | --- |
| A. Status-only binding | `/sandbox attach` records state but does not pass fake `SandboxSession` into runtime | No false execution; but P3 route is not exercised |
| B. Attached handle with explicit unsupported execute | passes a `SandboxSession` that fails loudly on `execute` without provider bridge | Proves runtime rebind path and prevents local fallback; may surprise if user expected working backend |

Recommended for P4：Option B if tests can make the error clear; otherwise Option A with explicit docs residual. Never claim successful remote execution without a real provider bridge.

### 3.2 Factory overload

Add a default overload to `CodingCliAgentFactory` instead of changing existing signatures destructively:

```java
default PreparedCodingAgent prepare(CodeCommandOptions options,
                                    TerminalIO terminal,
                                    TuiInteractionState interactionState,
                                    Collection<String> pausedMcpServers,
                                    SandboxSession sandboxSession) throws Exception {
    return prepare(options, terminal, interactionState, pausedMcpServers);
}
```

Then override in `DefaultCodingCliAgentFactory` and pass `sandboxSession` to `CodingAgentBuilder.sandbox(sandboxSession)` when non-null.

### 3.3 Runner wiring

In `CodingCliSessionRunner`:

1. Add field for current binding/session handle.
2. Dispatch `/sandbox` before `/commands` area, near `/mcp` or `/stream`.
3. Attach/disable should call `switchSessionRuntime(session, options)` or an overload that injects current sandbox.
4. `renderStatusOutput(...)` should include `sandbox=direct-host` or `sandbox=attached(provider/session/workspace)`.
5. `renderCommandLines(...)` and `buildCommandPaletteItems()` should mention sandbox.

### 3.4 Slash completion

In `SlashCommandController`:

- Add `/sandbox` to `BUILT_IN_COMMANDS` and `EXECUTABLE_ROOT_COMMANDS`.
- Add `SANDBOX_ACTIONS = ["status", "attach", "disable"]`.
- Add `suggest(...)` branch for `/sandbox`.
- Add command palette item(s), at minimum `/sandbox status` and `/sandbox attach `.

## 4. Testing matrix

| Surface | Test | Expected |
| --- | --- | --- |
| root completion | `SlashCommandControllerTest` | `/sandbox ` appears as argument command |
| action completion | `SlashCommandControllerTest` | `/sandbox ` suggests `status`, `attach`, `disable` |
| attach parsing | `CodingCliSessionRunnerArgumentParsingTest` or new unit | provider/session/workspace parse with Windows-safe shell split where relevant |
| factory wiring | `DefaultCodingCliAgentFactoryTest` | sandbox overload calls builder with sandbox without breaking normal prepare path |
| status render | new/extended runner test if seam exists | direct-host and attached output do not overclaim |
| docs | `npm --prefix docs-site run build` | docs build passes |

## 5. Docs updates

Prefer editing existing docs over adding a new docs-site page to avoid `.gitignore` surprises:

- `docs-site/docs/coding-agent/sandbox-routing.md`
  - add CLI `/sandbox` command section
  - explain direct-host vs attached
  - explain no real provider creation in P4
- CLI command reference / TUI page if present
  - add command table entry
- `docs-site/docs/agent/sdk-roadmap.md`
  - update P4 status if implementation completes

## 6. Regression / governance updates

If implementation touches `ai4j-cli/**` only:

- RG-004 evidence in task `progress.md`
- Cadence Ledger already maps CLI changes to RG-004/RG-007; update shared log if a new SRB row is recorded

If docs-site changes:

- RG-008 evidence in task `progress.md`
- Update `docs/05-TEST-QA/Regression-SSoT.md` notes only if the fixed gate description changes or new evidence row is needed

## 7. Out-of-scope follow-ups

- Real `SandboxProvider` discovery/attach/resume SPI.
- CubeSandbox/container/VM/browser provider plugin.
- Remote Agent Runner protocol and event stream.
- Persistent sandbox binding across saved sessions.
- `/sandbox create/list/destroy/logs` product surface.
