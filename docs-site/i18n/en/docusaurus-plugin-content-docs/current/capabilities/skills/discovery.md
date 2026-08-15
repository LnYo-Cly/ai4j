---
title: "Skill Discovery"
description: "A deep look at Skills discovery and loading: scanning the workspace and global root directories, recognizing SKILL.md, extracting the name and description, deduplicating by name, and how allowedReadRoots links the skill directories into the host's read-only security boundary."
tags: [concept]
---

# Skill Discovery

A `Skill` in AI4J is not an executable tool, but a methodology resource read on demand.

So what this page really clarifies is not "how to call a skill", but rather:

- Where skills are discovered from
- How the skill catalog is generated
- Why the full body should not be read up front
- How these skill files enter the secure read boundary

:::tip All code on this page is runnable
The discovery examples below come from
[`SkillsDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/SkillsDocExamplesTest.java),
which sets up a temporary workspace with SKILL.md and exercises the full discovery chain. No keys required, runs in an ordinary CI.
:::

## 1. The main entry point is `Skills.java`

A complete discovery run:

```java
// Place under the workspace: <workspace>/.ai4j/skills/code-review/SKILL.md
Skills.DiscoveryResult result = Skills.discoverDefault(workspaceRoot);

List<SkillDescriptor> skills = result.getSkills();          // discovered skills
List<String> readRoots = result.getAllowedReadRoots();       // read-only roots (linked into the security boundary)
```

Each `SkillDescriptor` carries `name` / `description` / `skillFilePath` / `source` / `disableModelInvocation`.

This mechanism is almost entirely concentrated in:

- `ai4j/src/main/java/io/github/lnyocly/ai4j/skill/Skills.java`

The most important methods are:

- `discoverDefault(...)`
- `discover(...)`
- `buildAvailableSkillsPrompt(...)`
- `createToolContext(...)`

The companion data objects are:

- `SkillDescriptor`
- `Skills.DiscoveryResult`

## 2. Which root directories are scanned by default

Internally, `discoverDefault(...)` first calls `resolveSkillRoots(...)`. The current default candidate roots are 4 (in two groups, each supporting both the `.ai4j` and `.agents` namespaces), plus any additional mounted directories:

1. `<workspace>/.ai4j/skills`
2. `<workspace>/.agents/skills`
3. `~/.ai4j/skills`
4. `~/.agents/skills`
5. Additional mounted `skillDirectories` (relative paths resolved against the workspace root)

In other words, both conventions under the workspace and the user home directory — `.ai4j/skills` and `.agents/skills` — are scanned. `.agents/skills` lets AI4J share the same skill directory with other tools that follow the `.agents` convention (such as the Claude / Agents.md ecosystem), without duplication.

Two points matter here:

### Workspace skills and global skills can coexist

This lets you hold both at once:

- Repository-specific skills (`<workspace>/.ai4j/skills` or `<workspace>/.agents/skills`)
- User-level, cross-project skills (`~/.ai4j/skills` or `~/.agents/skills`)

### Relative paths are resolved against the workspace root

If an additional mounted directory passed in is not an absolute path, it is resolved relative to the current workspace root, not relative to the user home or the JVM launch directory.

## 3. How a root is recognized as a skill

`Skills` does not treat an entire directory as a skill just by seeing it.

The current recognition order is:

1. First check whether the directory itself contains:
   - `SKILL.md`
   - `skill.md`
2. If yes, recognize this directory as a skill
3. If no, recursively scan its subdirectories
4. Any subdirectory that contains `SKILL.md` or `skill.md` is treated as a skill, and scanning inside that skill directory stops

This means two layouts are currently supported:

- A single-skill root
- A skill-collection root

## 4. Nested directories are supported; the skill directory itself is a leaf

`discoverFromRoot(...)` recursively scans the skill root, so you can organize directories by team, domain, or business line.

- The root itself can be a skill
- Any directory at any depth under the root can become a skill
- Once a directory contains `SKILL.md` or `skill.md`, that directory acts as a complete skill, and no further sub-skills are discovered inside it
- Symbolic links for the root, directories, and `SKILL.md` files are skipped, preventing discovery from expanding into undeclared read boundaries

Discovery results are stably sorted by normalized path; skills with the same name still follow root priority, first-come-first-served.

## 5. How a skill's name and description are derived

The extraction order in `buildDescriptor(...)` is explicit.

### Name extraction priority

1. `name` in the front matter
2. The first markdown heading
3. The skill directory name

### Description extraction priority

1. `description` in the front matter
2. The first non-heading paragraph in the body
3. The default copy `No description available.`

This means that even if your `SKILL.md` is written simply, as long as:

- The top front matter is reasonably standard
- Or the heading / first paragraph is clear

AI4J can still construct a usable skill catalog.

## 6. What `SkillDescriptor` actually stores

The current `SkillDescriptor` is lightweight and stores:

- `name`
- `description`
- `skillFilePath`
- `source`
- `disableModelInvocation`
- `content` — an optional `SKILL.md` body provided by the host

Here `source` is determined by `resolveSource(...)`:

- skill root located inside the workspace -> `workspace`
- otherwise -> `global`

This field is practical, because it tells you whether a skill is:

- Part of the current project's truth
- Or a user-level shared capability

### `content`: in-memory / host-provided skills

`content` is an optional field that supports two kinds of off-disk skills:

- **In-memory skills**: skills the host constructs at runtime, with no on-disk file. Here `content` holds the `SKILL.md` body directly.
- **Remote / restricted-source skills**: the skill body comes from a remote or controlled source. Here `skillFilePath` is a stable virtual location (resolved by the host's scoped skill reader rather than the local filesystem), and the body is supplied via `content`.

`content` defaults to `null`; in that case the skill goes through the normal disk discovery path and `skillFilePath` points to a real file. When `content` is provided, the host must ensure the body can be read on demand — `Skills.appendAvailableSkillsPrompt(...)` only projects `name` / `description` / `location` into the model catalog; `content` itself is never concatenated into the prompt.

## 7. What the deduplication strategy is

Internally, `discover(...)` deduplicates skills by name. The dedup key is:

- `name.trim().toLowerCase(Locale.ROOT)`

And it is "first-come-first-served":

- A skill discovered earlier in an earlier root is kept
- A later skill with the same name is ignored

This leads to a very practical conclusion:

- The skill name is essentially a global key in the current system

:::warning
Therefore, do not carelessly let workspace and global skills share a name with different semantics.
:::

`disable-model-invocation: true` is retained in the discovery result so the host can select it explicitly, but it does not enter the `<available_skills>` catalog used for automatic model selection. It does not change Tool or MCP permissions.

## 8. Why AI4J does not read the full `SKILL.md` directly

The text generated by `buildAvailableSkillsPrompt(...)` explicitly tells the model:

- First see the available skill catalog
- Do not pre-read every skill
- Only when a task clearly matches, use `read_file` to read the corresponding `SKILL.md`

This is one of the most important principles of this design.

If you concatenate the full body of every skill into the prompt up front, then:

- Skill discovery degenerates into a "big prompt concatenator"
- The value of lazy loading disappears
- Context pollution gets severe quickly

So the essence of the current skill mechanism is simply:

- A catalog of methodology documents exposed
- Lazy loading of the body

## 9. What `buildAvailableSkillsPrompt(...)` actually generates

This method does not return the skill body; it generates a catalog prompt that roughly includes:

- name
- path
- description

wrapped in `<available_skills>`.

It also explicitly tells the model:

- Do not read all skills first
- Read `SKILL.md` only on a match
- Use the minimal relevant skill set

This is the prompt contract of the current AI4J skill system.

## 10. Why `allowedReadRoots` is the key to this mechanism

`DiscoveryResult` does not only return skills; it also returns:

- `allowedReadRoots`

Then `Skills.createToolContext(...)` writes them into:

- `BuiltInToolContext.allowedReadRoots`

This means skill discovery and host tool security are linked:

- The model knows which skills exist
- `read_file` also knows which skill roots are allowed for read-only access

So skills are not a "pure prompt feature"; they are designed together with the host read boundary.

## 11. What `createToolContext(...)` actually does

This method constructs:

- `workspaceRoot`
- `allowedReadRoots`

The corresponding semantics are:

- Normal workspace paths are still constrained by the workspace root
- skill roots are additionally opened as read-only directories

This is exactly why the model can read global skill files outside the workspace, but by default cannot freely write to these directories.

## 11.1 `createSkillToolContext(...)`: tightening skill read permissions to read-only roots

`Skills.createToolContext(...)` opens both the workspace root and the skill roots to `read_file` — the model can read the workspace and the skills. But some scenarios (for example, when the host only wants to expose a scoped skill reader and keep the model entirely away from the current workspace) need a stricter boundary. `Skills.createSkillToolContext(skillRoots)` is designed for exactly this:

- It writes only the passed-in `skillRoots` into `allowedReadRoots`;
- It sets `BuiltInToolContext.restrictReadToAllowedRoots` to `true`;
- `workspaceRoot` is pointed at an unrelated placeholder directory (when there are no skill roots, a nonexistent `.ai4j-skill-read-denied` path), thereby **actively surrendering workspace read permission**.

The semantics of `restrictReadToAllowedRoots = true` inside `BuiltInToolContext.resolveReadablePath(...)` are: the candidate path must fall under some `allowedRoot` (and must also pass the `resolvesWithin` check to prevent symlink bypass), and the workspace root itself no longer counts as an allowed read source. In other words:

| Scenario | workspace readable | skill roots readable | write to workspace |
|------|---------------|------------------|----------|
| `createToolContext(...)` (default) | Yes | Yes (read-only) | Constrained by `resolveWorkspacePath` |
| `createSkillToolContext(...)` (`restrict=true`) | No | Yes (read-only) | Placeholder root, effectively not writable |

This lets skill lazy loading run in a least-privilege context: the model only sees the skill directories, cannot even read the workspace source, let alone write to it.

## 12. Real limitations of the current implementation

### No complex version or dependency model

`SkillDescriptor` carries only basic metadata, without:

- version
- dependencies
- capability graph

### No complex runtime activation model

The Core SDK provides discovery and model catalog projection; UI, tenant authorization, slash commands, business Tool binding, and runtime activation are still decided by the host application.

### No body-level cache layer

The current focus is on the discovery catalog and the read-only boundary; body reading is still delegated to host tools such as `read_file`.

None of these are bugs; they are the current implementation's deliberate choice of a lightweight and clear skill model.

## 13. Directory organization recommendations

### What belongs in repository skills

- Project-specific development conventions
- Repository-specific scripts and workflows
- Methodology that only holds for the current monorepo

### What belongs in global skills

- Cross-project reusable capabilities
- General analysis SOPs
- Personal long-term standard templates

### What is most worth writing carefully at the top of `SKILL.md`

- `name`
- `description`
- `disable-model-invocation: true` (only allow explicit host selection, not auto-published to the model)

Because these two fields are what catalog discovery relies on first.

## 14. The conclusions most worth remembering from this page

AI4J's skill discovery is not "walk the directory and then shove in the body", but a set of:

- Scan roots
- Generate catalog
- Read on demand
- Bring skill roots into the read-only boundary

lightweight lazy-loading machinery.

It is precisely for this reason that skills can reuse methodology without breaking context governance.
