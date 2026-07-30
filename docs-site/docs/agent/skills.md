---
sidebar_position: 8
---

# Agent Skills

Skill is a reusable instruction asset. It tells a model how to approach a class of work; it does not grant a capability or execute an action.

This page is the canonical boundary for using file-based `SKILL.md` resources with AI4J agents. It deliberately separates what the SDK provides today from what an application must own.

## What is available now

The Core SDK provides the file-based Skill primitives in `io.github.lnyocly.ai4j.skill.Skills`:

- discovers `SKILL.md` files from `<workspace>/.ai4j/skills`, `~/.ai4j/skills`, and caller-supplied roots;
- turns each file into a lightweight `SkillDescriptor` (`name`, `description`, path, and source);
- creates a prompt catalog instead of placing every Skill body in the system prompt;
- returns allowed read roots for `BuiltInToolContext` so a host can keep Skill reads scoped.

The generic Agent runtime on `main` does **not** automatically discover, authorize, or inject Skills for every ReAct or CodeAct run. There is no published `AgentBuilder.skills(...)` convenience API to rely on yet. The application remains responsible for selecting Skills and connecting the catalog to its own prompt and read-tool policy.

The Coding Agent has its own workspace integration. See [Coding Agent Skills](/docs/coding-agent/skills) when that is the runtime you are using.

## P2-B branch semantics (not on `main` yet)

:::warning
The APIs in this section are implemented on the separate P2-B feature branch. They are not part of `main` or a published release at the time of writing. Do not compile against or document them as generally available until that branch is merged and released.
:::

The P2-B Agent integration deliberately narrows the convenience defaults:

- `AgentBuilder.skills(workspace)` discovers only `<workspace>/.ai4j/skills` and `<workspace>/.agents/skills`.
- `AgentBuilder.skillsIncludingUserHome(workspace)` is the explicit local-development opt-in for process-user Skill roots; it must not become the default in a tenant service.
- `AgentBuilder.skillResolver(AgentSkillResolver)` is the service-safe integration point. A tenant host supplies the authorized catalog and roots instead of exposing a process user's filesystem.
- Automatically discoverable Skill metadata remains in the cache-stable system prompt. Explicitly selected, manual-only Skill content is a request-scoped user overlay rather than a permanent system-prompt addition.
- When the host already owns `read_file`, the SDK preserves it and exposes the scoped Skill reader as `read_skill_file`; it does not silently replace the host's tool or its policy chain.

These semantics intentionally differ from the Core SDK's legacy `Skills.discoverDefault(...)` helper, which also considers `~/.ai4j/skills`. The explicit split avoids accidentally leaking a server process's local Skills across tenants.

## Minimal host-owned integration

Use the Core SDK primitives at the request boundary after the application has decided which roots are allowed for the current user, tenant, and workspace. This example uses APIs present on the current branch; it does not call the pending P2-B builder APIs above.

```java
import io.github.lnyocly.ai4j.skill.Skills;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

Path workspaceRoot = Paths.get(System.getProperty("user.dir"));
Skills.DiscoveryResult discovery = Skills.discoverDefault(
        workspaceRoot,
        Collections.<String>emptyList()
);

String systemPrompt = Skills.appendAvailableSkillsPrompt(
        "Follow the application's safety and tool rules.",
        discovery.getSkills()
);
BuiltInToolContext readContext = Skills.createToolContext(workspaceRoot, discovery);
```

The example only creates the catalog and its read boundary. The host must pass `systemPrompt` to its model request and use `readContext` in the file-reading path it owns. Do not treat `allowedReadRoots` as a replacement for the host's permission checks or tool guardrails.

## Runtime contract

For one agent run, resolve the catalog once and keep the prompt catalog and read roots paired. Re-resolve it for the next run if the application supports live administration or filesystem updates. This keeps a model from seeing one catalog while its reader is authorized against another one.

The model should receive only Skill metadata first:

1. match the task against the catalog;
2. read the smallest relevant `SKILL.md` through the scoped reader;
3. follow the instructions only within the host's existing tool, approval, and sandbox boundaries.

Do not preload every Skill body into the stable system prompt. It increases context cost, weakens prompt-cache reuse, and makes unrelated instructions harder to distinguish. In the pending P2-B integration, a manually selected Skill body belongs in a request-scoped user overlay while the automatic catalog remains stable in the system prompt.

## Ownership boundaries

| Concern | Owner |
| --- | --- |
| `SKILL.md` discovery, descriptors, prompt catalog, read roots | Core SDK |
| Per-request Skill selection and filesystem roots | Application host |
| Tenant/user visibility, admin updates, versions, and audit records | Application business layer |
| Slash-command picker or a "my Skills" UI | Application frontend/backend |
| Tool permissions, approval, sandboxing, and guardrails | Agent host/runtime configuration |

This split is intentional. A reusable SDK cannot infer a tenant's authorization model or safely expose a process user's global Skills to all service users.

## Do not confuse these concepts

- A file-based Skill is a method or instruction resource.
- A Tool is an executable capability exposed by the host.
- MCP is a protocol for connecting or publishing external capabilities.
- `A2ASkill` describes an agent capability in an A2A AgentCard; it is not a local `SKILL.md` file.

Read [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp) for the Core distinction and [A2A](/docs/agent/a2a) for the AgentCard capability model.

## Next pages

1. [Core Skills overview](/docs/core-sdk/skills/overview)
2. [Discovery and loading](/docs/core-sdk/skills/discovery-and-loading)
3. [Tools and registry](/docs/agent/tools-and-registry)
4. [Coding Agent Skills](/docs/coding-agent/skills)
