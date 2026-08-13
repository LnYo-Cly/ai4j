---
sidebar_position: 7
title: "Using and Organizing Skills"
description: "Explains the real position of Skills in the Coding Agent as file-based workflow knowledge: discovery chain, scanning and dedup rules, SKILL.md field parsing, and how skills affect both the prompt and the read-only path boundary."
tags: [concept]
---

# Using and Organizing Skills

In AI4J, a `Skill` is not "a special tool," nor is it "a large template that auto-injects a complete prompt."

Along the `Coding Agent` path, its more precise definition is:

- A human-readable workflow description that lives on the file system
- Discovered as a summary at session startup
- Read on demand by the model from `SKILL.md` when a task actually matches

So it solves "how to reuse experience," not "how to execute actions."

---

## 1. The real position of Skills in the Coding Agent

Compressing the startup chain into a single line makes it easiest to follow:

```text
DefaultCodingCliAgentFactory.buildWorkspaceContext(...)
  -> CodingAgentBuilder.build()
  -> CodingSkillDiscovery.enrich(workspaceContext)
  -> Skills.discoverDefault(workspaceRoot, skillDirectories)
  -> WorkspaceContext.availableSkills + allowedReadRoots
  -> CodingContextPromptAssembler.mergeSystemPrompt(...)
  -> The model first sees the skills summary, then read_file's the relevant SKILL.md on demand
```

The key point here is not "how many skills were discovered," but that two things happen at once:

- `availableSkills` is written back into `WorkspaceContext` for prompt assembly and `/skills` display
- `allowedReadRoots` is also written back into `WorkspaceContext`, so skill root directories can be read by `read_file`

This shows that in the current implementation the skill system is not a pure documentation scanner, but part of the `workspace` permission model.

---

## 2. How the discovery chain actually runs

`CodingSkillDiscovery.discover(...)` itself is very thin; it just hands `WorkspaceContext` off to `Skills.discoverDefault(...)`.

The real discovery logic lives in `ai4j/skill/Skills.java`:

1. Normalize the workspace root
2. Resolve skill roots
3. Scan for skills under each root
4. Build a `SkillDescriptor`
5. Deduplicate by name
6. Collect `allowedReadRoots`

The default root order is:

1. `<workspace>/.ai4j/skills`
2. `~/.ai4j/skills`
3. `skillDirectories` configured in `workspace.json`

The resolution rules for `skillDirectories` are straightforward:

- Absolute paths are used as-is
- Relative paths are resolved against the workspace root

An important consequence:

- Skills inside the workspace and skills under the user home can coexist naturally
- Team-shared skill bundles can be mounted in via `skillDirectories`
- But whether they actually take effect still depends on the name-dedup result, not "later-mounted always overrides earlier-mounted"

---

## 3. The scanning rules are more conservative than many assume

`Skills.discoverFromRoot(...)` does not recursively walk the entire directory tree.

Its rules are:

### 3.1 The root itself is a skill

If a `SKILL.md` or `skill.md` exists directly under a root directory, that root itself is treated as a skill.

Once this rule hits, the subdirectories of the current root are not scanned again as skill containers.

This means:

- A directory is either treated as "the skill body"
- Or as "a skill container"

The current implementation never consumes it as both at once.

### 3.2 Otherwise, only the first level of subdirectories is checked

If the root itself is not a skill, this round only checks its direct subdirectories for `SKILL.md` / `skill.md`.

It does not recurse into deeper levels.

So:

- `skills/sql-review/SKILL.md` is discovered
- `skills/backend/sql-review/SKILL.md` is not discovered by default

If you want hierarchical organization, you can only do it via "multiple roots," not via "deeply nested directories."

---

## 4. Which fields in `SKILL.md` actually get read

The current descriptor construction logic is in `Skills.buildDescriptor(...)`.

It only extracts a few stable fields:

- `name`
- `description`
- `skillFilePath`
- `source`

The resolution priority for `name` is:

1. `name` in the front matter
2. The first markdown heading
3. The parent directory name of the skill file

The resolution priority for `description` is:

1. `description` in the front matter
2. The first body paragraph
3. The default copy `No description available.`

The design behind this is pragmatic:

- Front matter is the most stable structured metadata
- Omitting front matter does not disable the skill entirely
- But missing `name` / `description` directly lowers the chance of a skill being matched and displayed correctly

A minimal, most-robust example is still recommended:

```md
---
name: sql-review
description: Review SQL changes with indexing, execution-plan, and migration risk checks.
---

# SQL Review

Use this skill when the task involves SQL schema or query review.
```

---

## 5. The dedup rules decide "who wins"

`Skills.discover(...)` normalizes skill names with `trim + lowercase`, then keeps the descriptor for the first occurrence of a name.

This means deduplication is:

- Case-insensitive
- First-win, not last-win
- Content is not merged; only the first descriptor is kept

Combined with the default root order, the actual effect today is usually:

1. Workspace skills take precedence
2. Home skills come next
3. Extra mounted directories come last

If you put skills with the same name in multiple roots, the later one is typically shadowed silently rather than producing an error.

This is completely different from MCP conflict handling.

- Same-named skill: usually the first one is kept
- Same-named MCP tool: the server is treated as an error outright

The reason is simple:

- Skills are prompt-layer knowledge
- MCP is execution-layer capability

Execution-layer conflicts must be stricter.

---

## 6. What the model actually sees in the prompt

`CodingContextPromptAssembler.mergeSystemPrompt(...)` does not splice each skill's body directly into the system prompt.

What it does:

1. Build the workspace prompt
2. Append built-in tool rules
3. Call `Skills.buildAvailableSkillsPrompt(...)`
4. Put only the skill summary list into the prompt

The information currently spliced in is of this kind:

- skill name
- file path
- description
- "Do not read every skill up front; only read_file the corresponding `SKILL.md` when there is an obvious match"

This point is critical.

The current skill mechanism is not:

- Dumping every experience bundle into the model's context at startup

But rather:

- Exposing a "catalog of available skills" at startup
- Letting the model decide at runtime whether to read a given skill's body

The direct payoffs:

- The first-turn prompt does not bloat linearly with the number of skills
- Skill bodies can be written longer without polluting the context every time
- The model is more likely to treat skills as "knowledge packs read on demand" rather than "global rules permanently bound to the system"

---

## 7. Why skills affect the file-read boundary

`CodingSkillDiscovery.enrich(...)` does not only write `availableSkills`; it also writes the `allowedReadRoots` returned by discovery back into `WorkspaceContext`.

And `WorkspaceContext` has two path semantics:

- `resolveWorkspacePath(...)`
- `resolveReadablePath(...)`

The distinction matters:

- `resolveWorkspacePath(...)` by default allows only paths inside the workspace
- `resolveReadablePath(...)` also accepts `allowedReadRoots` in addition to the workspace

So the real permission model for skill root directories today is:

- Read is allowed
- Write is not automatically allowed
- Execute is not automatically allowed either

That is why team-shared skills can live in a directory outside the workspace yet still be opened normally by `read_file`.

It also shows:

- Skills are an excepted read path on the "knowledge input surface"
- Not "granting the model arbitrary external file access"

---

## 8. What the `/skills` command actually exposes

The `/skills` output in the CLI/TUI is not the skill body; it is the discovery result.

The current `CodingCliSessionRunner.renderSkillsOutput(...)` shows:

- count
- workspaceConfig path
- roots
- each skill's `name / source / path / description`

`/skills <name>` shows for a single skill:

- name
- source
- path
- description
- roots

There are two practical implications:

1. `/skills` is more an "index view" than a "reading view"
2. If you can see a skill summary but the model still does not use it, the problem is usually not discovery but matching and prompt strategy

---

## 9. The most common failure paths

### 9.1 Skill placed too deep

Discovery today scans only the root or the first level of subdirectories.

A deeper `SKILL.md` is not discovered by default.

### 9.2 `name` and `description` not written clearly

Although there is a heading / paragraph fallback, match quality drops noticeably, especially when multiple skills cover similar topics.

### 9.3 Same-named skill shadowed silently

If skills with the same name exist in workspace, home, and shared roots, the later-discovered one does not error; it simply never enters the final list.

### 9.4 Treating skills as a privilege-escalation lever

A skill only extends readable roots; it does not automatically open write access, nor does it bypass the write boundary enforced by `allowOutsideWorkspace`.

### 9.5 Treating skills as automatic executors

A skill itself performs no actions. Actual execution still relies on:

- `read_file`
- `bash`
- `write_file`
- `apply_patch`
- or MCP tools

---

## 10. When to use a Skill, and when not to

The most reliable criterion: are you capturing "how to do it," or exposing "a capability."

### Scenarios better suited to Skills

- Code review standards
- SQL review checklists
- Engineering conventions for a class of frameworks
- Multi-step troubleshooting playbooks
- Documentation writing or release processes

### Scenarios better suited to Tools / MCP

- Genuinely needing to execute commands
- Genuinely needing to read and write external systems
- Genuinely needing to call databases, browsers, search, or internal platform APIs

### Scenarios better suited to Agent Definitions

- Needing a fixed model, fixed system prompt, fixed handoff policy
- Needing to turn a class of workers into delegable subagents

One-line mnemonic:

- Skills define "how to do it"
- Tools/MCP provide "the hands that do it"
- Agent Definitions fix "the identity and boundary of that class of worker"

---

## 11. Which entry points to look at first when extending or troubleshooting

If you want to extend the skill system further, the entry points worth looking at first are:

- `ai4j-coding/.../CodingSkillDiscovery`
- `ai4j/.../skill/Skills`
- `ai4j-coding/.../workspace/WorkspaceContext`
- `ai4j-coding/.../prompt/CodingContextPromptAssembler`
- `ai4j-cli/.../runtime/CodingCliSessionRunner.renderSkillsOutput`

The recommended troubleshooting order is:

1. Run `/skills` to see whether it was discovered
2. Check whether the root path is actually in `roots`
3. Check whether `name` conflicts with another skill
4. Check whether the skill file sits at the root or the first level of subdirectories
5. Check whether the model only saw the summary but did not proceed to `read_file`

---

## 12. The conclusions most worth remembering from this page

- In the current implementation a Skill is "file-based workflow knowledge," not an executor
- `CodingSkillDiscovery` does not just scan; it populates both `availableSkills` and `allowedReadRoots` at once
- Discovery by default looks only at the root itself or the first level of subdirectories; it does not recurse deeply
- Same-named skills are kept first-win; later ones are typically shadowed silently
- The prompt carries only summaries by default, not full skill bodies
- Skills can extend readable paths, but they do not extend write or execute permissions

---

## 13. Further reading

1. [Tools and the approval mechanism](/docs/coding-agent/tools-and-approvals)
2. [MCP integration](/docs/coding-agent/mcp-integration)
3. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
4. [Coding Agent Architecture](/docs/coding-agent/architecture)
