---
title: "Skill vs Tool vs MCP"
description: "Distinguish AI4J's three foundation concepts with definitions, a comparison table, and source entry points: Skill governs methodology context, Tool governs in-host execution, and MCP governs external protocol integration. Clarifies common confusions about projection versus ownership."
tags: [concept]
---

# Skill vs Tool vs MCP

These three terms are the part of the AI4J documentation most easily conflated.

The reason is straightforward:

- All three can ultimately influence "what the model can do next"
- At the provider request layer, all three may ultimately be projected into some model-consumable capability

But engineering-wise they are absolutely not the same layer. The moment you conflate them, you lose all of the following at once:

- Documentation boundaries
- Security boundaries
- Protocol boundaries
- Runtime governance boundaries

## 1. Start with the shortest possible definitions

- `Skill`
  A methodology resource that tells the model "how this kind of task should be done"
- `Tool`
  An execution capability that lets the model "actually do it" inside the current host
- `MCP`
  A protocol layer that lets the host bring external system capabilities in through a standard integration

These three concepts collaborate, but they have different responsibilities.

## 2. Pull the three layers apart with one table

| Dimension | Skill | Tool | MCP |
| --- | --- | --- | --- |
| Primary responsibility | Methodology and context governance | In-host execution capability | External capability protocol integration |
| Main carrier | `SKILL.md` | `Tool.Function` / `@FunctionCall` / built-in tools | `McpClient` / `McpGateway` / MCP server |
| Does it directly execute actions | No | Yes | Eventually it may, but only after the protocol layer is wired in |
| Typical question | "How should this kind of task be done" | "What can the current host do" | "How do external systems get wired in" |
| Primary security boundary | Which instructional assets get read | Which tools are exposed, which local actions are allowed | Which services are wired in, service visibility and authentication |
| Is it part of the AI foundation | Yes | Yes | Yes |

If you only remember one table, remember this one.

## 3. What are the respective source entry points

### Skill

- `skill/Skills.java`
- `skill/SkillDescriptor.java`

What it focuses on:

- Discovery
- Description
- Directory hints
- Read-only root

### Tool

- `annotation/FunctionCall.java`
- `annotation/FunctionRequest.java`
- `annotation/FunctionParameter.java`
- `tool/ToolUtil.java`
- `tool/BuiltInToolExecutor.java`

What it focuses on:

- Tool declaration
- Tool allowlist
- Local execution routing

### MCP

- `mcp/client/McpClient.java`
- `mcp/gateway/McpGateway.java`
- `mcp/transport/*`
- `mcp/server/*`

What it focuses on:

- transport
- Multi-service governance
- Protocol handshake
- Service publishing and integration

Just by looking at these code entry points, you can see that the three layers have completely different concerns.

## 4. Why everyone keeps conflating them

Because from the model's perspective, they do jointly influence the next action:

- skill changes the model's decision path
- tool provides the local execution surface
- MCP brings the external execution surface in

But the fact that "they all influence behavior" does not mean they belong to the same abstraction layer.

A more accurate statement is:

- Skill determines how the model thinks and chooses
- Tool determines what can be executed inside the host
- MCP determines what can be wired in from outside the host

## 5. Why MCP should not be understood as hanging under `Tools`

This is one of the most critical structural issues.

Conceptually, MCP is not a subset of `Tools`, for two reasons:

1. `Tool` solves "how to expose capabilities to the model request in the current host"
2. `MCP` solves "how to wire external services into the host through a standard protocol"

It is only at the final step that tool-style capabilities inside MCP get projected into a `Tool.Function`-style schema, so they "look like tools".

This phenomenon must be kept distinct from the ownership relationship.

A more accurate understanding is:

- MCP tools ultimately get projected onto the tool surface
- But MCP itself remains a protocol integration layer

Just like:

- A remote HTTP API may ultimately be wrapped as a local SDK method
- But you would not therefore say "an HTTP API is a Java method"

## 6. Why Skill is also part of the AI foundation

This is another point that is often underestimated.

`Skill` of course belongs to the AI foundation, except it sits not in the execution layer, but in:

- The methodology reuse layer
- The context governance layer

What it solves:

- Which SOPs are worth exposing
- How instructional assets are lazily loaded
- How to avoid permanently stuffing every long document into the system prompt

So it is a foundation, just not an "execution foundation" — it is a "methodology and context foundation".

## 7. How the three typically collaborate in a real system

The most common combination is:

1. Use `Skill` to tell the model the process and policy
2. Use a local `Tool` to provide in-host actions
3. Use `MCP` to wire in external capabilities such as the browser, GitHub, databases, and search

For example, a coding-agent task:

- skill tells the model to first read the task, then locate the code, then run regression
- local tools provide `read_file`, `apply_patch`, `bash`
- MCP additionally wires in GitHub, the browser, and third-party search

So the common relationship is:

- `Skill` determines the method
- `Tool` and `MCP` determine the execution surface

## 8. A more practical decision table

| The problem you are solving | What to prefer |
| --- | --- |
| Give the model a stable task methodology | `Skill` |
| Expose a local Java function or host built-in capability | `Tool` |
| Wire in GitHub, a browser, a database, or an internal API | `MCP` |
| Have the model think through the process first, then call local capabilities | `Skill + Tool` |
| Have the model both follow a process and use external services | `Skill + MCP` or `Skill + Tool + MCP` |

If a requirement involves both "how to do it" and "what to do", it is usually not an either-or choice, but a composition problem.

## 9. Where these three concepts are most commonly written wrong

### Writing skill as a permission system

A skill should encode methodology, and should not carry:

- Permission grants
- Action execution
- External service connection

### Writing tool as an approval system

A tool is an execution surface, not an approval and governance surface. Approval should be done by a higher-layer runtime or the host.

### Writing MCP as "a remote tool list"

This is far too narrow. MCP also includes:

- transport
- client lifecycle
- gateway
- resources
- prompts
- server publish

## 10. From the model interface's view, why they "look like the same layer"

Because when everything finally enters the model, a convergence occurs:

- skill enters the context first as a directory hint or body content
- tool enters the provider `tools` list
- MCP tools are also projected into the provider `tools` list

So in front of the model, they all ultimately seem to influence the "actionable space right now".

But this is only an interface-layer convergence, not a conceptual-layer merger.

## 11. The most robust mental model for design

Remember it like this:

- `Skill`: guide the model
- `Tool`: execute inside the host
- `MCP`: wire the host to the outside

And going one step further:

- `Skill` primarily changes the decision path
- `Tool` primarily changes the local capability surface
- `MCP` primarily changes the external capability surface

This mental model works well when reading documentation, doing design, and splitting security boundaries.

## 12. The conclusion this page most wants you to remember

`Skill`, `Tool`, and `MCP` in AI4J all belong to the AI foundation, but each in a different dimension:

- Skill is the methodology and context governance layer
- Tool is the in-host execution layer
- MCP is the out-of-host protocol integration layer

MCP can ultimately be projected as tool schema, but conceptually it is still not a sub-page of `Tools`; likewise, a skill is not "a tool that cannot execute", but an entirely different kind of foundation capability.
