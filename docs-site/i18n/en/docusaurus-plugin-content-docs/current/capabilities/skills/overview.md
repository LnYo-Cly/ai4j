---
title: "Skills Overview"
description: "Overview of the AI4J Skills methodology and context-governance layer: discover SKILL.md, generate a skill catalog, lazily load bodies on demand, and bring skill roots into a read-only boundary — clarifying the division of responsibilities between Skill, Tool, and MCP."
tags: [concept]
---

# Skills Overview

In AI4J, a `Skill` is not "a bit of explanatory text appended to the model." It is a formal context-governance mechanism.

The problems it solves are:

- How methodologies get discovered
- Which instructions deserve to enter the current context
- How to let the model read them on demand, instead of consuming the entire SOP library up front

This is why `Skill` belongs in the foundation layer, rather than being treated merely as a product feature of `Coding Agent`.

:::tip All code on this page is runnable
The discovery / catalog-generation examples below come from
[`SkillsDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/SkillsDocExamplesTest.java);
they need no API keys and run in an ordinary CI.
:::

## 1. Where `Skill` actually sits in the architecture

Looking at the source, the skill main line lives in:

- `ai4j/src/main/java/io/github/lnyocly/ai4j/skill/Skills.java`
- `SkillDescriptor.java`

These belong to the `ai4j/` core module, not `ai4j-coding/`.

This shows that `Skill` is first and foremost a capability of the Core SDK:

- Discover methodology resources
- Give the model an optional catalog
- Read the body only when needed

`Coding Agent` merely turns this capability chain into a more complete product entry point; it does not redefine the skill concept.

## 2. What a Skill actually is

The most common skill carrier is simply:

- `SKILL.md`

But the point of a skill is not its file extension — it is its responsibility:

- It is a methodology resource
- It is an on-demand context asset
- It does not directly carry out execution

You can think of it as "a structured work instruction handed to the model," not "a capability the model can invoke directly."

## 3. Why it belongs to the AI foundation

If we break the AI foundation into layers:

- `Skill` handles context governance and methodology reuse
- `Tool` handles execution capabilities inside the host
- `MCP` handles integration of capabilities outside the host

So when you asked earlier "aren't skills part of the AI foundation?", the answer is yes:

- Yes, and it belongs to the methodology / context layer within the foundation

It is not an appendage of `Tool`, nor a private concept of `Coding Agent`.

## 4. What this mechanism actually solves

Without skills, the common practices are only two:

- Permanently stuffing every process instruction into the system prompt
- Leaving it entirely to the model to guess what to do

Neither is ideal.

The skill system provides a third path:

- Expose the catalog first, not the full body
- Let the model judge whether it matches first
- Read the specific `SKILL.md` only after a match

Hence its core value:

- Reduce prompt pollution
- Improve methodology reuse
- Keep instruction assets governable

## 5. What a Skill is not

These boundaries must be clear.

### Not a Tool

A skill does not perform actions. It only tells the model:

- How this kind of task should be done
- What the ordering is
- Which constraints to watch for

### Not MCP

A skill is not responsible for:

- transport
- external service connections
- multi-service gateways

### Not a runtime

A skill is not responsible for:

- multi-step progression
- approval
- checkpoint
- task state machines

Its responsibility is "instruction," not "execution" or "scheduling."

## 6. Which layers make up the Skill subsystem

From `Skills.java`, the current skill mechanism has at least 4 layers:

### 6.1 Discovery layer

Scans:

- `<workspace>/.ai4j/skills`
- `~/.ai4j/skills`
- caller-supplied extra mount directories

### 6.2 Description layer

Each skill is organized into a `SkillDescriptor`:

- `name`
- `description`
- `skillFilePath`
- `source`
- `disableModelInvocation`

### 6.3 Exposure layer

`buildAvailableSkillsPrompt(...)` does not stuff the body in; instead it gives the model a catalog of available skills first.

Skills marked `disable-model-invocation: true` are retained for the host to select explicitly, but do not appear in this automatic model catalog.

### 6.4 Read-boundary layer

`createToolContext(...)` writes the skill roots into `BuiltInToolContext.allowedReadRoots`, ensuring the model has a clear read-only boundary when reading skill files.

Together, these four layers are what constitute the current AI4J skill system.

:::note Third-party Skills can also go through the extension SPI
Beyond the built-in `.ai4j/skills` directory discovery, third-party jars can likewise inject Skills as resources through the extension SPI (`ServiceLoader` + `ExtensionRegistry`); the gating rules are the same as for Tool / Prompt, and they are not exposed automatically by default. See [Plugin Packages](/docs/extending/plugins/plugin-packages).
:::

## 7. What a typical workflow looks like

A standard skill workflow is usually:

1. The host calls `Skills.discoverDefault(...)`
2. It gets back the available skill list and the read-only roots
3. The skill catalog produced by `buildAvailableSkillsPrompt(...)` is added to the system context
4. The model matches against the task and chooses whether to read some `SKILL.md`
5. After reading, it follows the skill's guidance

This flow is a fundamentally different design from "feeding all SOPs to the model up front."

```java
Skills.DiscoveryResult result = Skills.discoverDefault(workspaceRoot);

// Generate only the skill catalog (name + description), no body
String skillCatalog = Skills.buildAvailableSkillsPrompt(result.getSkills());

// Append the catalog to an existing system prompt
String systemPrompt = Skills.appendAvailableSkillsPrompt(
        "You are a coding agent.", result.getSkills());
```

The catalog contains only `name` / `description` and a hint to "read SKILL.md"; the body (concrete steps, constraints) never leaks into the catalog — the model decides which skill to read after matching.

## 8. Which scenarios fit a Skill best

### Methodology-type tasks

For example:

- Code review conventions
- Documentation writing patterns
- Research procedures
- Multi-stage execution SOPs

### Highly reusable instruction assets

One set of instructions shows up repeatedly across multiple requests and tasks, but there is no need to stuff all of it into the context every time.

### Tasks that require explicit reading before execution

For example:

- A repository's proprietary development conventions
- A team's proprietary release process
- A domain's proprietary analysis template

## 9. The real boundaries of the current design

From the implementation, a few boundaries are worth knowing up front.

### A Skill is not an execution permission

:::note
A skill telling the model "you should do it this way" does not mean the model thereby gains:

- permission to write files
- permission to execute commands
- permission to access remote services

These are still determined by the Tool / MCP exposure surface.
:::

### A Skill is not permanent context

The design emphasis of AI4J is:

- The skill catalog enters the prompt first
- The body is read on demand

It is not about pinning every skill permanently into the system prompt.

### Skill metadata is fairly light

`SkillDescriptor` currently has only:

- name
- description
- file path
- source
- whether only the host may select it explicitly

This is a lightweight catalog mechanism, not a complex version-and-dependency management system.

## 10. Relationship with upper-layer product capabilities

What `Coding Agent` does for skills is product-level wrapping, for example:

- Richer interaction entry points
- Tighter cooperation with the host environment
- Tighter integration with the task flow

But the underlying principles are unchanged:

- The Core SDK handles discovery, description, and read boundaries
- The upper-layer runtime decides when to expose them to the model and how to bind them into the execution flow

## 11. Recommended reading order

1. [Discovery and Loading](/docs/capabilities/skills/discovery-and-loading)
2. [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)
3. [Coding Agent / Skills](/docs/products/coding-agent/skills)

## 12. The conclusion worth remembering from this page

AI4J's `Skill` is the methodology and context-governance layer inside the foundation.

It is not responsible for execution; it is responsible for:

- Making methodology resources discoverable
- Letting the model read on demand
- Keeping the read boundary consistent with the host's tool constraints

This is exactly where it fundamentally differs from Tool and MCP.

## Further reading

- → [Skills API Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/skill/Skills.html) (skill governance entry points such as `discoverDefault(...)` / `buildAvailableSkillsPrompt(...)` / `createToolContext(...)`)
