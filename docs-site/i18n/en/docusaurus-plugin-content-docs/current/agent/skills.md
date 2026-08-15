---
sidebar_position: 8
title: "Agent Skills"
description: "Explains ai4j-agent Skills: how the SDK discovers and scopes SKILL.md, the workspace-safe vs user-home roots, request-scoped AgentSkillResolver for tenants, and why Skills never bypass tool authorization."
tags: [how-to]
---

# Agent Skills

A Skill is a reusable instruction asset. It tells an Agent how to approach a class of work; it does not grant a tool, data access, or an approval bypass.

AI4J's Agent integration follows the same split that makes Skills safe in local and service hosts:

- the SDK discovers, catalogs, scopes, and reads `SKILL.md` content;
- the application decides which Skills the current request is allowed to see;
- the application continues to own authentication, tenant policy, tools, approvals, and UI.

The Coding Agent has its own workspace integration. See [Coding Agent Skills](/docs/products/coding-agent/skills) when that is the runtime you are using.

## Start with the right integration

For a local developer Agent, use the workspace convenience method:

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;

import java.nio.file.Paths;

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .skills(Paths.get(System.getProperty("user.dir")))
        .build();
```

`skills(workspace)` searches only these workspace-owned roots:

- `<workspace>/.ai4j/skills`
- `<workspace>/.agents/skills`

It does not read the process user's home directory. That is the safe default for both repositories and service processes.

For a local developer tool that deliberately needs process-user Skills, opt in explicitly:

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .skillsIncludingUserHome(Paths.get(System.getProperty("user.dir")))
        .build();
```

:::warning Do not use for multi-tenant services
Do not use `skillsIncludingUserHome(...)` for a tenant service. It can expose the server process's local Skill roots, which are not tenant policy.
:::

## Use a host resolver for services

`AgentSkillResolver` runs once at the start of each Agent run. It is the service-safe extension point for a database, configuration center, or other host-owned Skill source.

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillResolver;
import io.github.lnyocly.ai4j.agent.skill.AgentSkillScope;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;

import java.nio.file.Paths;
import java.util.List;

// tenantSkillService, currentTenantId(), currentUserId(), and namesOf(...)
// are application code, not SDK services.
AgentSkillResolver resolver = new AgentSkillResolver() {
    @Override
    public AgentSkillScope resolve(AgentRequest request) {
        List<SkillDescriptor> allowed = tenantSkillService.findEnabledSkills(
                currentTenantId(), currentUserId(), request);

        return AgentSkillScope.builder()
                .workspaceRoot(Paths.get(System.getProperty("user.dir")))
                .providedSkills(allowed)
                .enabledSkillNames(namesOf(allowed))
                .build();
    }
};

Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .skillResolver(resolver)
        .build();
```

The resolver's result is a request-scoped snapshot. Re-resolve it on the next run when an administrator changes a Skill or its grant. Do not cache a tenant's authorization decision globally in the SDK.

For an in-memory Skill from a tenant store, use a stable virtual location and provide the body directly:

```java
SkillDescriptor refundRunbook = SkillDescriptor.builder()
        .name("invoice-refund")
        .description("Handle a verified invoice-refund request.")
        .content("# Invoice refund\n\nVerify the invoice before requesting a refund.")
        .skillFilePath("tenant://acme/invoice-refund/SKILL.md")
        .source("tenant")
        .disableModelInvocation(true)
        .build();
```

`skillFilePath` is a stable identifier for the scoped reader. A `tenant://...` path is not read from the local filesystem.

## Automatic and explicit activation

Skills have two activation paths.

| Path | When to use it | Runtime behavior |
| --- | --- | --- |
| Automatic catalog | The model may choose a Skill for a task. | The stable system prompt gets metadata only. The model reads the relevant body through the scoped Skill reader on demand. |
| Explicit selection | A user or host chooses a named Skill for this run. | The selected body is a request-scoped user overlay. It is not stored in long-lived memory or appended to the stable system-prompt prefix. |

Set `disableModelInvocation(true)` for a manual-only Skill. It remains available to a host-selected request but is not offered for automatic model invocation.

```java
import io.github.lnyocly.ai4j.agent.AgentRequest;

AgentRequest request = AgentRequest.builder()
        .input("Refund invoice INV-42")
        .selectedSkills(java.util.Collections.singletonList("invoice-refund"))
        .build();

agent.run(request);
```

:::warning selectedSkills is not an authorization credential
Treat `selectedSkills` as a request from your UI, not proof of authorization. The resolver must return only Skills the authenticated tenant and user may activate. Unknown, disabled, or unauthorized names must not be added to the scope.
:::

## Prompt and tool boundary

The automatic catalog is kept small for prompt-cache reuse. The Agent exposes the scoped reader as `read_file` unless the host already owns that name; in that case it uses `read_skill_file` and preserves the host tool unchanged.

Reading a Skill does not grant the instructions inside it any extra authority. A Skill that says "call `refund_invoice`" still needs that tool to be registered and permitted by the host's existing tool, approval, sandbox, and guardrail policies.

## What belongs where

| Concern | Owner |
| --- | --- |
| `SKILL.md` parsing, workspace discovery, descriptors, prompt catalog, scoped reading | AI4J SDK |
| Per-run `AgentSkillScope` and explicit selection input | Application host |
| Tenant storage, versioning, publish state, user/role grants, audit records | Application business layer |
| Slash-command picker or a "my Skills" UI | Application frontend/backend |
| Tool registration, authorization, approval, sandboxing, and guardrails | Agent host/runtime configuration |

This separation is intentional. A reusable SDK cannot infer a tenant's authorization model or safely expose a server process's global Skills to application users.

## Do not confuse these concepts

- A file-based Skill is a method or instruction resource.
- A Tool is an executable capability exposed by the host.
- MCP is a protocol for connecting or publishing external capabilities.
- `A2ASkill` describes an agent capability in an A2A AgentCard; it is not a local `SKILL.md` file. Use `A2ASkillMapper` to publish an authorized ai4j `SkillDescriptor` as an A2A skill on the AgentCard (see [A2A — Publish ai4j Skills](/docs/agent/observability/a2a#publish-ai4j-skills-to-the-agentcard)).

Read [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp) for the Core distinction and [A2A](/docs/agent/observability/a2a) for the AgentCard capability model.

## Next pages

1. [Core Skills overview](/docs/capabilities/skills/overview)
2. [Discovery and loading](/docs/capabilities/skills/discovery-and-loading)
3. [Loading and activation](/docs/capabilities/skills/loading-and-activation) — three loading paths and the scoped executor
4. [Tools and registry](/docs/agent/tools-and-registry)
4. [Coding Agent Skills](/docs/products/coding-agent/skills)
