---
title: "Dynamic Workflow Plugin"
description: "Explains the ai4j-plugin-dynamic-workflow sample plugin: the model writes complex tasks as a deterministic workflow script, the plugin only returns a host-mediated request envelope, and actual execution is optionally taken over by the ai4j-agent runtime; the built-in Nashorn executor disables Java interop by default."
tags: [integration]
---

# Dynamic Workflow Plugin

:::tip Production-grade reference plugin
`ai4j-plugin-dynamic-workflow` is AI4J's **flagship reference plugin** — it showcases all four extension capabilities (Tool + Command + Skill + Prompt) at once, has zero runtime dependencies (only `ai4j-extension-api`), and is security-designed (host-mediated request envelope; no JS execution, no agent spawning, no file access). It ships with 9 unit tests + 3 live end-to-end smoke tests (MiniMax M3 → script → envelope → agent execution → E2B sandbox), is Java 8 compatible, and runs on GitHub Actions CI.

**GitHub**: [LnYo-Cly/ai4j-plugin-dynamic-workflow](https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow)
:::

`ai4j-plugin-dynamic-workflow` is AI4J's dynamic workflow sample plugin, recommended to be maintained as a standalone GitHub repository and released independently rather than merged into the `ai4j-sdk` reactor. It follows the ecosystem pattern of Claude Code-style dynamic workflows: **the model first writes a complex task as an inspectable workflow script, and the host then decides how to dispatch the script to subagents, worktrees, approvals, and model routing for execution**.

The first version of this AI4J plugin deliberately stays within the `ai4j-extension-api` boundary: the plugin only contributes tool, command, Skill, and Prompt resources, and returns a host-mediated JSON envelope; it does not execute JavaScript, spawn sub-agents, manipulate git worktrees, or bypass host approvals within the plugin process. Actual execution is optionally taken over by the host-side `ai4j-agent` dynamic workflow runtime.

## 1. Why this shape

This time we compared two directions for implementing a dynamic workflow:

| Implementation direction | Suitable as a reference | Not to copy directly |
| --- | --- | --- |
| Small-core implementation | Small, clear core primitives: the `workflow` tool, `export const meta`, `agent()` / `parallel()` / `pipeline()` / `phase()`, deterministic script constraints | It still depends on a specific host's in-memory subagent session and Node `vm`, and cannot be dropped directly into the AI4J Java plugin API |
| Production-grade implementation | Background execution, `/workflows` management, model tiers, resume journal, worktree isolation, saved workflows, built-in deep research/review | Both the code volume and host assumptions are heavier; it would over-couple a first-version AI4J plugin |

So the first AI4J version adopts the minimal core semantics as the public contract: `workflow` is a tool entry point, the script has deterministic constraints, and actual execution is taken over by the host runtime. Background execution, resume, model tiers, and worktree isolation fit better as host capabilities of the subsequent `ai4j-agent` / `ai4j-coding` rather than being stuffed into an extension-api-only plugin.

## 2. Repository and dependency import

The standalone repository is recommended to be named:

```text
https://github.com/LnYo-Cly/ai4j-plugin-dynamic-workflow
```

This way it can showcase the AI4J plugin ecosystem without being tied to the SDK monorepo's release cadence. The `ai4j-sdk` side keeps only this documentation entry point and the plugin contract description; the plugin source, CI, version numbers, and release notes are maintained by the standalone repository.

Once the plugin is released, import the standalone artifact directly:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-plugin-dynamic-workflow</artifactId>
  <version>0.1.0</version>
</dependency>
```

Use `0.1.0-SNAPSHOT` for snapshots or local validation. The plugin still depends on `ai4j-extension-api`; the standalone repository's POM should explicitly declare the compatible AI4J extension API version, for example:

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-extension-api</artifactId>
  <version>2.4.2</version>
</dependency>
```

Do not assume it has already been included in `ai4j-bom`; only omit the version number once the plugin rejoins the SDK release train or is explicitly included in the BOM.

## 3. Enable and expose

Plain Java:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .exposeTool("workflow");
```

Strict authorization:

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .requireExplicitResourceActivation()
        .allowCommand("workflow")
        .allowSkill("dynamic-workflow-orchestration")
        .allowPrompt("dynamic-workflow-script")
        .exposeTool("workflow");
```

Spring Boot:

```yaml
ai:
  extensions:
    enabled:
      - dynamic-workflow
    tools:
      expose:
        - workflow
```

Spring Boot strict authorization:

```yaml
ai:
  extensions:
    enabled:
      - dynamic-workflow
    explicit-resource-activation: true
    tools:
      expose:
        - workflow
    commands:
      allow:
        - workflow
    skills:
      allow:
        - dynamic-workflow-orchestration
    prompts:
      allow:
        - dynamic-workflow-script
```

## 4. What capabilities it contributes

| Type | Name | Description |
| --- | --- | --- |
| Extension id | `dynamic-workflow` | Plugin ID used for classpath discovery and enable |
| Tool | `workflow` | Dynamic workflow request tool that an agent can call |
| Command | `workflow` | Workflow request entry point triggered manually by the CLI / host |
| Skill | `dynamic-workflow-orchestration` | Instructions on when to use the workflow and how to write the script |
| Prompt | `dynamic-workflow-script` | Prompt template for generating a deterministic workflow script |

The manifest also declares:

```text
vendor: ai4j
permission: agent.workflow.request
configPrefix: ai4j.extensions.dynamic-workflow
```

:::warning
`agent.workflow.request` is a host policy hint; it does not automatically grant permission to execute JavaScript, create worktrees, access the network, or call a provider. Actual execution is still determined by host policy, the Agent / Coding Agent factory, and the tool exposure configuration.
:::

## 5. Tool input

Input schema of `workflow`:

```json
{
  "type": "object",
  "properties": {
    "script": {
      "type": "string",
      "description": "Raw JavaScript workflow script. First statement should be: export const meta = { name, description, phases }."
    },
    "args": {
      "description": "Optional JSON value exposed to the workflow script as args."
    },
    "background": {
      "type": "boolean",
      "description": "Whether the host may run the workflow out of band."
    },
    "maxAgents": {
      "type": "integer",
      "minimum": 1,
      "maximum": 1000,
      "description": "Optional host-enforced maximum number of subagents."
    },
    "tokenBudget": {
      "type": "integer",
      "minimum": 1,
      "description": "Optional host-enforced token budget."
    }
  },
  "required": ["script"]
}
```

Example:

```json
{
  "script": "export const meta = { name: 'auth_audit', description: 'Audit routes for missing auth checks', phases: [{ title: 'Scan' }, { title: 'Verify' }] }\n\nphase('Scan')\nconst findings = await parallel([\n  () => agent('Audit ' + args.files[0] + ' for missing auth checks.', { label: 'audit user route' }),\n  () => agent('Audit ' + args.files[1] + ' for missing auth checks.', { label: 'audit admin route' })\n])\n\nphase('Verify')\nreturn await agent('Synthesize and verify these findings:\n' + findings.join('\n\n'), { label: 'final review' })",
  "args": {
    "files": ["src/routes/user.ts", "src/routes/admin.ts"]
  },
  "background": true,
  "maxAgents": 16
}
```

## 6. Tool output

The plugin returns a JSON envelope the host can recognize:

```json
{
  "type": "ai4j.dynamic_workflow.request",
  "source": "tool",
  "tool": "workflow",
  "status": "pending_host_workflow_execution",
  "hostAction": "execute_dynamic_workflow",
  "scriptRuntime": "host_mediated",
  "blocking": "host_decides",
  "argumentsRaw": "{... original tool arguments ...}"
}
```

`argumentsRaw` keeps the original argument string passed in by the model and truncates it after 64 KiB; when truncated, it adds:

```json
{"argumentsTruncated": true}
```

This is consistent with the envelope approach of the `ask-user` plugin: the plugin layer guarantees that the output is always valid JSON; if the host wants to actually execute the workflow, it must parse, validate, and run the `script` according to its own security policy.

## 7. Host runtime (optional execution)

`ai4j-agent` provides an optional host runtime. When not enabled, the `workflow` tool still only returns a pending envelope; once enabled, the host parses the envelope into a `DynamicWorkflowRequest`, then executes the script and returns `ai4j.dynamic_workflow.execution_result`.

### 7.1 AgentBuilder wiring

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("dynamic-workflow")
        .exposeTool("workflow");

Agent workerAgent = Agents.react()
        .modelClient(modelClient)
        .model("MiniMax-M3")
        .build();

Agent hostAgent = Agents.react()
        .modelClient(modelClient)
        .model("MiniMax-M3")
        .extensions(registry)
        .dynamicWorkflow(
                new AgentDynamicWorkflowBridge(workerAgent),
                DynamicWorkflowRuntimeOptions.builder()
                        .timeoutMs(30000L)
                        .maxAgents(16)
                        .allowJavaInterop(false)
                        .build()
        )
        .build();
```

If the model calls the `workflow` tool, the plugin first returns:

```json
{"type":"ai4j.dynamic_workflow.request","hostAction":"execute_dynamic_workflow","argumentsRaw":"..."}
```

Once `dynamicWorkflow(...)` is enabled, the host tool executor converts it into:

```json
{
  "type": "ai4j.dynamic_workflow.execution_result",
  "status": "completed",
  "output": "...",
  "phases": ["Scan", "Verify"],
  "logs": [],
  "agentCalls": [],
  "trace": []
}
```

### 7.2 Executing the envelope directly

```java
DynamicWorkflowRequest request = DynamicWorkflowRequestParser.parse(envelopeJson);

DynamicWorkflowExecutionResult result =
        new NashornDynamicWorkflowExecutor(new AgentDynamicWorkflowBridge(workerAgent))
                .execute(request);

System.out.println(result.toJson());
```

### 7.3 Currently supported primitives

| Primitive | Current behavior |
| --- | --- |
| `phase(name)` | Records the current phase and writes it into the result `phases` / `trace` |
| `log(message, data)` | Records a structured log entry with the current phase attached |
| `agent(prompt, options)` | Executes through the host-injected `DynamicWorkflowAgentBridge`; the SDK does not hardcode a provider, worktree, or CLI |
| `parallel([...])` | Preserves fan-out grouping and result order; the current Nashorn runtime performs deterministic execution of JS function tasks, while real concurrency / isolation can be extended by a later host bridge or coding-agent worker |
| `pipeline([...], input)` | Runs steps in sequence, passing the previous step's output to the next |

### 7.4 JavaScript runtime boundary

The current built-in runtime is a Java 8-friendly Nashorn executor; it is not Node.js, and it does not expose `fs`, `process`, `fetch`, `import`, or any system API. By default, it also creates the Nashorn engine with `--no-java`, removes global entry points such as `load` / `quit`, and closes over the host bridge inside a closure, exposing only the `phase` / `log` / `agent` / `parallel` / `pipeline` workflow primitives.

:::warning
`DynamicWorkflowRuntimeOptions.allowJavaInterop` defaults to `false`. Only set it to `true` when the script is fully trusted and the host is willing to expose Java interop as an explicit extension surface.
:::

The runtime applies a lightweight normalizer to make common workflow scripts runnable:

- `export const meta = ...` → `var meta = ...`
- `const` / `let` → `var`
- top-level `await agent(...)` / `await parallel(...)` → synchronous call
- simple `() => agent(...)` / `() => log(...)` / `() => phase(...)` → ES5 function

Complex modern JS (e.g. `args.files.map(file => () => agent(...))`, `Promise`, module imports) is not part of the stable contract of the first-version built-in runtime. When such scripts are needed, the recommendation is to have the model generate ES5-compatible workflows, or to replace the runtime on the host side with a custom `DynamicWorkflowExecutor`.

## 8. Command path

Before wiring up, you can check:

```bash
ai4j-cli extension plan dynamic-workflow --enable \
  --expose-tool workflow \
  --allow-command workflow \
  --allow-skill dynamic-workflow-orchestration \
  --allow-prompt dynamic-workflow-script \
  --strict
ai4j-cli extension check dynamic-workflow --enable \
  --expose-tool workflow \
  --allow-command workflow \
  --allow-skill dynamic-workflow-orchestration \
  --allow-prompt dynamic-workflow-script \
  --strict
```

Command entry point:

```bash
ai4j-cli extension run --enable dynamic-workflow --allow-command workflow workflow "Audit this repository for duplicated plugin contracts"
```

The returned envelope will have `source` set to `command` and `hostAction` set to `synthesize_dynamic_workflow`. This means the host can hand the natural-language goal back to an agent to generate the script, or it can directly reject, queue, or convert it into a manual approval.

## 9. Current boundaries and next steps

The first version of the plugin does not implement these host-level capabilities; the SDK host runtime currently also implements only a minimal local execution loop:

- background run manager / `/workflows` TUI
- resume journal
- per-agent model tier routing
- per-agent git worktree isolation
- saved workflow command registry
- built-in deep research / adversarial review commands

These capabilities require `ai4j-agent` / `ai4j-coding` to hold the model client, session, tool registry, approval policy, and workspace context before they can be further enhanced. The plugin package is only responsible for exposing the stable resource surface of "requesting a dynamic workflow" for the ecosystem to use; the host runtime is responsible for executing it after an explicit opt-in.

## 10. Further reading

- [Plugin Packages](/docs/core-sdk/extension/plugin-packages)
- [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes)
- [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
- [Agent / Orchestration](/docs/agent/workflow-stategraph)
- [Coding Agent / Tools and Approvals](/docs/coding-agent/tools-and-approvals)
