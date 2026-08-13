---
title: "Tool Whitelist and Security"
description: "AI4J's two layers of tool security: a request-level functions/mcpServices allowlist and BuiltInToolContext workspace read/write boundaries, with a look at high-risk built-ins like bash and the still-missing approval/sandbox governance."
tags: [concept]
---

# Tool Whitelist and Security

The most important principle in tool security is not "can the model call it," but rather "what the model sees by default, and what it is allowed to touch."

The AI4J foundation layer currently ships two layers of defense:

1. Request-level allowlist
2. Built-in tool context constraints

These two layers already matter a great deal, but they are far from a complete governance loop. The docs must draw this boundary clearly.

## 1. First layer of security: nothing exposed by default, only what an explicit allowlist names

What actually defines the tool surface in a request is:

- `functions(...)`
- `mcpServices(...)`

These then flow into:

```java
ToolUtil.getAllTools(functionList, mcpServerIds)
```

It only resolves the names you pass in explicitly; it does not hand a tool to the model just because some tool class happens to be on the classpath.

This is AI4J's current core default-secure mental model:

- Closed by default
- Open only when explicitly chosen

## 2. Why this allowlist layer cannot be skipped

Because on a real tool surface you frequently have all of these at once:

- Read-only tools
- File-writing tools
- Shell tools
- Third-party MCP write tools

If everything is exposed by default, the model faces an oversized side-effect surface instead of "the minimum capability set needed to finish the current task."

From a tool-governance standpoint, the best default is never "auto-discover and open everything," but rather "minimum necessary exposure."

## 3. Second layer of security: `BuiltInToolContext`

AI4J adds an additional host context layer for built-in coding tools:

- `tool/BuiltInToolContext.java`

Its most important fields today are:

- `workspaceRoot`
- `allowOutsideWorkspace`
- `allowedReadRoots`
- `defaultReadMaxChars`
- `defaultCommandTimeoutMs`

This means built-in tools do not execute inside a completely unbounded host; instead they depend on the current context object to decide:

- The workspace root directory
- Which directories are readable
- Whether going outside the workspace is allowed
- Default limits on file reads and command execution

## 4. The read path and the write path do not follow the same rules

This is one of the implementation details most worth getting right.

### Write path

Things like:

- `write_file`
- `apply_patch`
- the `cwd` of `bash`

all ultimately go through:

```java
context.resolveWorkspacePath(path)
```

Its semantics are:

- Relative paths are resolved against the workspace root
- Absolute paths are also normalized
- If `allowOutsideWorkspace == false`, the target path must still land inside the workspace root

In other words, write-related built-in tools are not allowed to escape the workspace by default.

#### The write path's second gate: `WorkspacePathGuard`

`BuiltInToolContext.resolveWorkspacePath(...)` is only the workspace boundary check. Before persisting to disk, the coding-agent layer (`ai4j-coding`) write executors (`WriteFileToolExecutor` / `EditToolExecutor` / `ApplyPatchToolExecutor`) additionally pass through `WorkspacePathGuard.resolveForWrite(...)`, which layers three defenses on top of the workspace boundary:

- **Symlink-loop resolution**: follows symbolic links for up to `MAX_SYMLINK_DEPTH = 8` hops and uses a visited-set to detect cycles. After resolving the canonical path it **re-checks** the workspace boundary — because a symlink can point outside the workspace, a plain normalize will not catch it. Hitting a loop or exceeding the depth throws an exception, closing off symlink-based workspace escapes.
- **Sensitive-directory blocklist**: if any segment of the canonical path is `.ssh` or `.aws`, or lands under `.git/hooks/**`, the write is rejected outright (guards against hook backdoors and credential-directory tampering). Note this blocks `.git/hooks` precisely and does not affect reads or writes to other `.git` entries such as `.gitignore`.
- **`excludedPaths` write denial**: paths listed in `WorkspaceContext.getExcludedPaths()` (typically `.git`, `target`) are refused for writing even when they sit inside the workspace.

```java
// io.github.lnyocly.ai4j:ai4j-coding:2.4.2
// Unified validation before write executors persist: workspace boundary + symlinks + blocklist + excludedPaths
Path safe = WorkspacePathGuard.resolveForWrite(workspaceContext, rawPath);
Files.write(safe, content.getBytes(StandardCharsets.UTF_8));
```

This defense layer applies only to the write/patch path; `read_file` goes through the read-only rules of `resolveReadablePath(...)` and is not constrained by the blocklist.

### Read path

`read_file` uses:

```java
context.resolveReadablePath(path)
```

Its semantics are slightly looser:

- Reads inside the workspace are allowed
- If a path hits `allowedReadRoots`, those extra read-only roots are also allowed

This is why `read_file` can read certain skill directories but `write_file` cannot.

## 5. How skill read-only roots get into the tool context

This chain is something a lot of docs leave out, but it is exactly where skills meet tool security.

`Skills.createToolContext(...)` will:

1. First run skill discovery
2. Obtain `DiscoveryResult.allowedReadRoots`
3. Construct the `BuiltInToolContext`
4. Write these skill roots into `allowedReadRoots`

So a skill is not simply "telling the model there is a SKILL.md here"; it also registers those directories as:

- Readable on demand
- But read-only by default

This is also the key design that lets the skill system do lazy loading without breaking the workspace write boundary.

## 6. Which built-in tools carry the most risk

### `read_file`

Relatively low risk, but it can still leak:

- Workspace source code
- Skill directory contents
- Accidentally exposed sensitive text

### `write_file` / `apply_patch`

The risk is in:

- Modifying workspace contents
- Producing destructive changes

Although they are bounded by the workspace root by default, that is not the same as "safe for your business."

### `bash`

This is the built-in tool that currently demands the most conservative handling.

:::danger bash has the largest host-capability surface of any built-in
Its `cwd` is constrained to the workspace, but the command itself can still:

- Read and write workspace files
- Spawn child processes
- Make network requests
- Produce long-running background processes

So `bash` is not a "lightweight file tool"; it is one of the built-ins with the largest host-capability surface.
:::

#### bash's multi-action API: foreground `exec` + background process management

`bash` is not a single-argument command runner. Its `action` parameter is enumerated as `exec`/`start`/`status`/`logs`/`write`/`stop`/`list`, routed by `BuiltInToolExecutor.runBash(...)`, with background processes managed by `BuiltInProcessRegistry`. This API lets the model both run self-terminating commands and drive interactive/long-running processes:

| action | Purpose | Key parameters |
|--------|------|----------|
| `exec` (default) | Run a self-terminating command synchronously; killed on timeout | `command`, `cwd`, `timeoutMs` |
| `start` | Launch a background/interactive process and return a `processId` | `command`, `cwd` |
| `status` | Query a snapshot of a process (`status`/`pid`/`exitCode`/`startedAt`/`endedAt`) | `processId` |
| `logs` | Read a process's accumulated output, with `offset`-cursor paging | `processId`, `offset`, `limit` |
| `write` | Write text to a background process's stdin | `processId`, `input` |
| `stop` | Stop a background process (`destroy` first, then `destroyForcibly` after a `processStopGraceMs` grace period) | `processId` |
| `list` | List snapshots of all background processes managed by the current context | — |

Output from background processes is captured by a ring buffer inside `BuiltInProcessRegistry` (capacity bounded by `BuiltInToolContext.maxProcessOutputChars`; on overflow it drops from the head and advances `startOffset`), and the `nextOffset`/`truncated` returned by `logs` supports incremental pulls. The process boundary matches `cwd` — the `cwd` of `start` is likewise validated through `resolveWorkspacePath(...)`, so it cannot escape the workspace.

From a security-surface standpoint, this means `bash`'s capability surface is much larger than "run one command": `start` can leave behind background processes that hold resources, read/write the workspace long-term, or talk to the network. Granting the model `bash` means simultaneously granting background-process governance; the host should treat `start`/`write`/`stop` as side-effecting actions on the same level as `exec` and fold them into approval and audit.

#### bash output charset resolution (Windows GBK fallback)

`bash` stdout/stderr must first be decoded into a string against some `Charset` before it is returned. `BuiltInToolExecutor` (along with the background-process `BuiltInProcessRegistry` and `ai4j-coding`'s `ShellCommandSupport.resolveShellCharset()`) resolves this charset in the following order:

1. **Explicit override wins**: the system property `ai4j.shell.encoding` (or the environment variable `AI4J_SHELL_ENCODING`) is adopted immediately as long as it names a charset the JVM supports — commonly used to pin a Windows console to UTF-8.
2. **Platform fallback**: when nothing is specified explicitly, non-Windows always uses UTF-8; on Windows it tries `native.encoding` → `sun.jnu.encoding` → `file.encoding` → `Charset.defaultCharset()` in order — on a Chinese Windows this usually resolves to **GBK**.

So the same `bash` tool returns UTF-8 text by default on Linux/macOS and decodes as GBK by default on Chinese Windows. If the model sees garbled output, it is almost always because the console's actual encoding differs from the inference above; in that case pin it explicitly with `-Dai4j.shell.encoding=UTF-8` (or `AI4J_SHELL_ENCODING=UTF-8`) — no code change required.

## 7. The boundary of `readOnlyCodingToolNames()` must be stated clearly

`BuiltInTools` currently exposes:

```java
readOnlyCodingToolNames()
```

It groups:

- `bash`
- `read_file`
- `glob`
- `grep`

into a read-only set (matching the source `READ_ONLY_CODING_TOOL_NAMES = {BASH, READ_FILE, GLOB, GREP}`).

But note: this is more of a classification aid than a complete policy engine. The set by itself does not automatically stop `bash` from running side-effecting commands.

Real side-effect governance still has to be decided by the upper-layer runtime:

- Whether to give this tool to the model at all
- Whether approval is required
- Whether the current session is allowed to execute it

## 8. Why local tools and MCP tools have different security surfaces

### Local tool risk surface

- Workspace filesystem
- Local process capabilities
- The current host environment

### MCP tool risk surface

- External account permissions
- Remote side-effect APIs
- Multi-service, multi-tenant visibility

So tool security cannot look only at locally annotated functions. Remote MCP must likewise be governed through per-service allowlists, and usually requires external authentication and audit as well.

## 9. What the Core SDK already provides today

The current foundation layer already provides:

- Request-level tool allowlist
- Request-level MCP service allowlist
- Workspace / readable-root boundaries for built-in tools
- Linkage between skill directories and read-only roots

These are the first security boundary, and they are already valuable.

## 10. What it does not yet do for you

The current Core SDK is not directly responsible for:

- Human approval
- Per-user permission decisions
- Command-level allow/deny policy
- Third-party account authorization management
- High-risk action audit
- OS / container-level sandboxing

:::warning
`bash` in particular is currently closer to "a host shell constrained by the workspace path" than to an isolation-grade execution sandbox.
:::

## 11. The safest usage guidance

Based on the current implementation, a sensible default policy is usually:

- Expose the minimum tool set first
- Avoid `bash` whenever you can
- Open skill reads only on read-only roots
- Govern write tools and remote side-effect tools separately
- In multi-tenant scenarios, bind the MCP allowlist to user identity

## 12. The conclusion worth remembering from this page

AI4J's tool security today is not "auto-discover everything, then patch it after the fact"; it is:

- First, narrow the model's visible surface with an allowlist
- Then narrow the built-in host boundary with `BuiltInToolContext`

This already forms the foundation layer's first line of defense; but approval, authentication, audit, and true process isolation remain concerns for the upper-layer runtime and host governance.

## Further reading

- → [BuiltInTools API Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/tool/BuiltInTools.html) (built-in tool contracts such as `allCodingToolNames()` / `readOnlyCodingToolNames()`)
