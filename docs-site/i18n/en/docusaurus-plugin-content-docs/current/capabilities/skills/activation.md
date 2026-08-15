---
title: "Skill Activation"
description: "Explains the three paths by which skill content enters the context (model-selected read_file, host selectedSkills, host providedContents), the security model of the restricted executor, and why AI4J does not provide a dedicated activate_skill tool."
tags: [concept]
---

# Skill Activation

[Discovery and Loading](/docs/capabilities/skills/discovery) covers "how SKILL.md is discovered and how the directory is generated".
This page covers the step that comes after: **once the directory is generated, how exactly the skill content enters the model context**.

This is more complex than it looks, because the content is not all stuffed in up front — there are three paths, each with different security boundaries and context costs.

## 1. Clear up a common misconception first

Many people's first reaction is: "There should be an `activate_skill(name)` tool that lets the model load skill content by name, instead of relying on generic file reads."

AI4J **deliberately does not provide** this tool. This is not an omission; it is a design decision aligned with the [Agent Skills official spec](https://agentskills.io/). The spec lists activation as two equally valid modes:

- **File-read activation**: the model uses a standard file-read tool to read `SKILL.md` by its path in the directory. This is the mode AI4J uses.
- **Dedicated-tool activation**: register an `activate_skill` tool that returns content by name. The spec says it is "**required** when the model cannot read files directly, and **optional** when it can."

AI4J chose the former; the reasons are unpacked in the final section. First, let's look at how it actually works.

## 2. Path 1: Model-selected (default)

This is the most common path, fully automatic:

```text
Skills.discoverDefault(workspace)
  → generates the <available_skills> directory (only name / description / location)
  → the directory is concatenated into the system prompt
  → after matching a task, the model calls read_file to read the body of a SKILL.md
```

Each entry in the directory looks like this:

```xml
<available_skills>
  <skill>
    <name>code-review</name>
    <description>How to do a thorough code review</description>
    <location>/path/to/.ai4j/skills/code-review/SKILL.md</location>
  </skill>
</available_skills>
```

The directory only tells the model "what skills exist and what each does"; the **content does not enter the prompt**. The model only issues a read after it judges a match.

:::note Why reuse read_file instead of a dedicated tool
The directory explicitly says: "reuse `read_file` instead of asking for a dedicated skill tool".
Reusing an existing tool means zero new tool slots (no schema overhead, no added burden on model selection), while the security boundary is tightened by the restricted executor described in the next section — you get the isolation of a dedicated tool without paying the cost of an additional tool.
:::

## 3. Restricted executor: read_file is tightened in the skill context

Under the model-selected path, `read_file` is not a raw file read. In the Agent runtime it is taken over by `SkillReadFileToolExecutor` (in the `ai4j-agent` module):

- **Accepts only the `read_file` tool name**; all other tools are rejected
- Runs in a **per-run isolated `BuiltInToolContext`**, with the read root scoped to the skill directory (`Skills.createSkillToolContext(skillRoots)`)
- Supports `startLine` / `endLine` / `maxChars` segmented reads, so a long skill does not blow up the context all at once

In other words, the model nominally calls the generic `read_file`, but execution is constrained inside the skill read-only root. This is where AI4J is tighter than a "raw read_file".

:::tip Automatic aliasing on tool-name conflict
If the host has already taken the `read_file` name, the runtime aliases the skill reader to `read_skill_file`; the directory also automatically references the correct name, and the host's original tool is unaffected.
:::

## 4. Path 2: Host-named (`selectedSkills`)

Sometimes the model should not pick for itself — for example, the user explicitly selected a skill in the UI, or business logic must run through a fixed flow.

`AgentRequest.selectedSkills` lets the host **name skills directly**; the content is read out and injected into the context, and **the model does not need to issue any tool call at all**:

```java
agent.run(AgentRequest.builder()
        .input("Run the invoice refund flow")
        .selectedSkills(Collections.singletonList("invoice-refund"))
        .build());
```

Two key details:

- **manual-only skills can only take this path**. A skill marked `disable-model-invocation: true` in `SKILL.md` does not appear in the model directory (the model cannot see or self-select it), but the host can name it. This is the front door for "the host forces a flow on".
- **The content goes through memory items, not the system prompt**. The named skill's content is injected via the memory overlay, **leaving the system prompt prefix untouched** — so when the directory is stable, the system prompt can be reused by the prompt cache, and content changes only affect the items.

## 5. Path 3: Host injects content directly (`providedContents`)

A skill does not have to come from disk. `AgentSkillRuntimeSupport` supports `providedContents` — the host pushes the content in directly, without reading any file at all:

- When a skill comes from the classpath / a JAR / a database / a remote service, this path is the only viable option
- The model still reads via `read_file(path=...)`, but the executor sees the path is in `providedContents` and returns the injected content directly, **never touching disk**

This path currently uses the skill's file path as the addressing key — which is semantically mismatched for classpath/JAR sources, and is a known point for improvement.

## 6. Comparison of the three paths

| Path | Who decides | How content enters the context | Fits |
| --- | --- | --- | --- |
| Model-selected | Model | Directory enters system prompt → model calls restricted `read_file` | General case; many skills, matched by task |
| Host-named | Host code | `selectedSkills` → content goes through memory items | User UI selection; manual-only skills |
| Host-injected | Host code | `providedContents` → executor returns directly | classpath/JAR/DB sources; no filesystem |

The executors for all three paths are restricted, and the skill read-only roots are uniformly funneled through `createSkillToolContext`.

## 7. Multi-tenant isolation

`AgentSkillResolver` decides which roots each run scans. A custom resolver uses only **explicit roots** by default — the comment states: "this way tenant policy does not accidentally expose server-side global Skills".

That is, in a server-side deployment, each tenant/request can only see the roots the resolver gives it; the global `~/.ai4j/skills` does not leak automatically. This brings skill discovery under tenant isolation, rather than being globally visible by default.

## 8. Why there is no dedicated `activate_skill` tool

This is the design decision most asked about in the docs. The full reasoning:

1. **It cannot replace generic reads**. A skill directory contains `references/*.md`, `scripts/`, and `assets/` alongside `SKILL.md` (the spec calls these tier-3). In every reference implementation (Spring AI, Claude SDK, LangChain4j), tier-3 still goes through file reads. A dedicated tool is **a net-new parallel path, not a replacement**.
2. **The security gain is roughly zero**. Anthropic's official wording: the skill tool is a "context filter, not a sandbox" — as long as `Read`/`Bash` are still around, files can be read all the same. And AI4J already has a root allowlist + symlink rejection + realpath validation, which is effectively tighter than Claude Code's default combination.
3. **It is less cache-friendly**. The dedicated tool's description is generated dynamically at runtime (aggregating every skill's name+description); adding or removing a skill changes the tool schema and breaks the prompt cache. The directory, by contrast, sits in the system prompt prefix with a stable ordering, which is more cache-friendly.
4. **Its only irreplaceable value** is: when the runtime provides no filesystem tool at all (`supportsSkillReadFile=false`), the model can still self-select a skill. That branch is currently covered by the host's `selectedSkills` fallback. **If real users show up for that branch, build it then**; otherwise it stays deferred under "do not build interfaces without a scenario".

:::note Two real gaps (more valuable than adding a skill tool)
- **The content has no compaction-protection marker**: when skill content is compacted by context compaction, compaction cannot tell it apart from an ordinary file read. The spec warns this "silently degrades agent performance, with no visible error". Improvement: add a `skill: true` marker or a `<skill_instructions>` wrapper to the return of `SkillReadFileToolExecutor`.
- **Virtual skill addressing**: `providedContents` uses a fake file path as the key, which is semantically mismatched for classpath/JAR sources (see Path 3).
:::

## 9. Further reading

- [Agent / Skills](/docs/agent/skills) — skill wiring on the Agent runtime side (resolver, selectedSkills, tool aliasing)
- [Tool Exposure Semantics](/docs/capabilities/mcp/tool-exposure-semantics) — the relationship between the restricted executor and the tool allowlist
- [Skills overview](/docs/capabilities/skills/overview) — where skills sit in the architecture
