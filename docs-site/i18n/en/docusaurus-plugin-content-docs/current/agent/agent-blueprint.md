---
sidebar_position: 9
title: "Agent Blueprint YAML"
description: "Describe a single Agent's model, instructions, tools, memory, compact, sandbox, and workflow parameters with declarative YAML. Support loading, validation, and templating, then create a runnable Agent once the host supplies its dependencies."
tags: [reference]
---

# Agent Blueprint YAML

`AgentBlueprint` is the declarative single-Agent configuration model for `ai4j-agent`. The problem it solves:

> Once the Java API can assemble an Agent dynamically, how do you save, share, template, and validate a single Agent's model, instructions, plugins, tools, memory, compact toggle, sandbox toggle, and workflow parameters?

P1-A provides the foundation: **Java DTO + YAML loader + validator + fixture tests**. P1-B builds on this with `AgentFactory`, which converts a Blueprint into an `AgentBuilder` / `Agent` once the host explicitly supplies dependencies such as `AgentModelClient`. P1-C adds `ai4j-cli run <agent.yaml>`, so a single-Agent Blueprint can be run once directly from the terminal.

:::note
`AgentFactory` still does not read the provider key, local profiles, the plugin directory, or a real sandbox. It only performs deterministic mapping; all sensitive configuration and external system connections are provided by the host application.
:::

## 1. Suitable scenarios

| Scenario | Suitable? |
| --- | --- |
| Quickly creating a very simple Agent in Java code | Continue using the Java API directly |
| Saving an Agent config to a file for others to reuse | Suitable |
| Generating Agent config from a UI / template / scaffolding tool | Suitable |
| Checking config up front for missing fields, illegal workflows, or illegal compact thresholds | Suitable |
| Creating an Agent from YAML where the host provides the model client | Suitable — P1-B provides `AgentFactory` |
| Running a single-Agent YAML directly from the terminal | Suitable — P1-C provides `ai4j-cli run <agent.yaml> --input <task>` |
| Connecting to a real VM / container / remote sandbox | P1-A/P1-B only declare, validate, and guard fields; real execution belongs to the later Sandbox SPI |

## 2. Minimal YAML

```yaml
version: ai4j.agent/v1
id: minimal-agent

model:
  provider: openai-compatible
  model: gpt-4.1-mini

workflow:
  mode: react
  maxTurns: 3
```

What this config expresses:

- Use the `ai4j.agent/v1` Blueprint contract.
- The stable ID of this Agent is `minimal-agent`.
- The model comes from the generic `openai-compatible` provider.
- The workflow uses `react`, with at most 3 turns.

:::warning
There is no provider token here. The Blueprint is not responsible for storing keys; keys should still come from environment variables, host configuration, or an external secret store.
:::

## 3. A more complete example

```yaml
version: ai4j.agent/v1
id: coding-assistant
name: Coding Assistant

model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1
  options:
    temperature: 0.2

instructions:
  system: |
    You are a careful coding agent.
  developer: Prefer small, verifiable changes.
  variables:
    language: zh-CN

plugins:
  - id: ask-user
  - id: todo
    enabled: true
    config:
      limit: 20

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
    config:
      timeoutSeconds: 60

session:
  memory:
    enabled: true
    scope: project
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
      - failed_commands
      - test_results

sandbox:
  enabled: false

workflow:
  mode: react
  maxTurns: 20
```

This example is still a single-Agent Blueprint, not a Team Blueprint and not a workflow graph. Team, handoff, nodes/edges, and FlowGram export should all be left to later phases.

## 4. Load, validate, and create the Agent in Java

### 4.1 Loading and validation

The core package of P1-A/P1-B is:

```text
io.github.lnyocly.ai4j.agent.blueprint
```

Typical usage:

```java
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationIssue;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidationReport;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintValidator;

import java.nio.file.Paths;

AgentBlueprintLoader loader = new AgentBlueprintLoader();
AgentBlueprint blueprint = loader.load(Paths.get("agent.yaml"));

AgentBlueprintValidator validator = new AgentBlueprintValidator();
AgentBlueprintValidationReport report = validator.validate(blueprint);

if (!report.isValid()) {
    for (AgentBlueprintValidationIssue issue : report.getErrors()) {
        System.out.println(issue.getPath() + " " + issue.getCode() + " " + issue.getMessage());
    }
    throw new IllegalArgumentException("Invalid Agent Blueprint");
}
```

`load(...)` turns the YAML into a Java object; `validate(...)` produces stable errors and warnings. The two are kept separate so that a UI, CLI, test, or Factory can decide how to surface or block on the same report.



### 4.2 Creating the Agent with AgentFactory

P1-B adds `AgentFactory` and `AgentFactoryContext`:

```java
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprint;
import io.github.lnyocly.ai4j.agent.blueprint.AgentBlueprintLoader;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactory;
import io.github.lnyocly.ai4j.agent.blueprint.AgentFactoryContext;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;

AgentBlueprint blueprint = new AgentBlueprintLoader().load(Paths.get("agent.yaml"));

AgentModelClient modelClient = createModelClientFromYourHostConfig();

Agent agent = new AgentFactory().create(
    blueprint,
    AgentFactoryContext.builder()
        .modelClient(modelClient)
        .build()
);
```

The key point here: `AgentFactory` does not create the provider client. The host application must itself obtain configuration from environment variables, a configuration center, a Spring Bean, a CLI profile, or a secret store, and then explicitly pass in `AgentModelClient`.

Fields mapped in P1-B:

| Blueprint field | Agent mapping |
| --- | --- |
| `model.model` | `AgentBuilder.model(...)` |
| `model.options.temperature` | `AgentBuilder.temperature(...)` |
| `model.options.topP` / `top_p` | `AgentBuilder.topP(...)` |
| `model.options.maxOutputTokens` / `max_output_tokens` | `AgentBuilder.maxOutputTokens(...)` |
| `instructions.system` | `AgentBuilder.systemPrompt(...)` |
| `instructions.developer` | `AgentBuilder.instructions(...)` |
| `workflow.mode=react` | ReAct runtime |
| `workflow.mode=codeact` | CodeAct runtime |
| `workflow.maxTurns` | `AgentOptions.maxSteps` |

Fields not mapped or not executed in P1-B:

| Field | P1-B behavior |
| --- | --- |
| `model.profile` | Kept only as host metadata; the Factory does not read local profiles. |
| `plugins[]` | Does not install, scan, or auto-enable plugins. |
| `tools[]` | The concrete tool registry is still supplied by the host through `AgentFactoryContext`. |
| `session.memory` / `session.compact` | Does not auto-create an external memory store or compact strategy. |
| `sandbox.enabled=true` | Reports `blueprint.sandbox.unsupported` by default; even if the host explicitly sets `allowSandboxDeclaration(true)`, it only lets the declaration pass — it does not create a real sandbox. |

### 4.3 Running an Agent YAML with the CLI

P1-C adds `ai4j-cli run`, used to run a single-Agent Blueprint directly:

```bash
ai4j-cli run agent.yaml --input "Answer using the knowledge base"
```

You can also explicitly override the runtime provider / protocol / model:

```bash
ai4j-cli run agent.yaml \
  --input "Summarize this task" \
  --provider openai \
  --protocol responses \
  --model gpt-4.1-mini
```

The boundaries of this command match `AgentFactory`: the YAML holds no token; the CLI host resolves the runtime model client from `--api-key`, environment variables, a provider profile, or workspace configuration, then passes it to `AgentFactoryContext`. `model.profile` can be used as the CLI host's profile name, but `AgentFactory` itself does not read the secret.

Common parameters:

| Parameter | Description |
| --- | --- |
| `<agent.yaml>` | Path to the Blueprint YAML file. |
| `--input` / `--prompt` | The user input for this run; required. |
| `--provider` | Overrides `model.provider` in the YAML. `openai-compatible` is handled by the CLI host's OpenAI-compatible runtime. |
| `--protocol` | `chat` or `responses`. |
| `--model` | Overrides `model.model` in the YAML. |
| `--profile` | Use a CLI host provider profile; fails if it does not exist or is incompatible with an explicit `--provider` — it will not silently fall back to the default profile. |
| `--base-url` | Custom base URL for OpenAI-compatible or another provider. |
| `--allow-sandbox-declaration` | Only allows the `sandbox.enabled=true` declaration to pass; does not create a real sandbox. |

If the YAML declares `sandbox.enabled=true`, the CLI fails by default with `blueprint.sandbox.unsupported`. This is by design, to prevent users from mistakenly believing a VM / container has already been created.

## 5. Loader entry points

`AgentBlueprintLoader` supports:

| Method | Description |
| --- | --- |
| `load(String yaml)` | Load from YAML text |
| `load(InputStream inputStream)` | Load from an input stream |
| `load(Path path)` | Load from a path |
| `load(File file)` | Load from a file |

Invalid YAML throws `AgentBlueprintLoadException` with the error code `blueprint.yaml.invalid`. The exception message only conveys the parse failure; it must not contain the provider token or sensitive local paths.

## 6. Field reference

### 6.1 `version`

```yaml
version: ai4j.agent/v1
```

Required. P1-A supports only `ai4j.agent/v1`. If the version is extended in the future, compatibility should be handled by the loader / validator rather than silently redefining old fields.

### 6.2 `id` / `name`

```yaml
id: coding-assistant
name: Coding Assistant
```

`id` is required; it is the stable identifier used in configuration, logs, the UI, and template references. P1-A requires it to contain only letters, digits, dots, underscores, and hyphens.

`name` is optional; it is a display name.

### 6.3 `model`

```yaml
model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1
  options:
    temperature: 0.2
```

| Field | Description |
| --- | --- |
| `provider` | Required. Prefer a generic provider name such as `openai-compatible`. Do not write some relay platform's brand as an SDK concept. |
| `profile` | Optional. Denotes a host-side configuration profile. P1-A only keeps the string; it does not read local configuration. |
| `model` | Optional, but at least one of `model` and `profile` is required. |
| `options` | Optional, reserved for model parameters such as temperature and topP. |

### 6.4 `instructions`

```yaml
instructions:
  system: |
    You are a careful coding agent.
  developer: Prefer small, verifiable changes.
  variables:
    language: zh-CN
```

`instructions` only declares the instruction text and variables. P1-A performs no prompt assembly and does not inject variables into the model request; that belongs to later Factory / runtime assembly logic.

### 6.5 `plugins`

```yaml
plugins:
  - id: ask-user
  - id: todo
    enabled: true
    config:
      limit: 20
```

`plugins[].id` is required. `enabled` and `config` are optional.

P1-A does not install plugins, scan the classpath, or execute plugin lifecycle hooks. It only expresses plugin intent as a DTO and validates the basic structure.

### 6.6 `tools`

```yaml
tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
```

`tools[].ref` is required. `approval` describes the intended tool execution policy and can be mapped to `AgentPermissionPolicy` in later P1-B.

P1-A only emits a warning for unknown approval values; it does not reject them outright. The reason is that the approval policy also depends on the host, CLI, sandbox, and business scenario.

### 6.7 `session.memory`

```yaml
session:
  memory:
    enabled: true
    scope: project
```

The memory fields declare whether memory is enabled and the scope or store of that memory. P1-A does not create a concrete `AgentMemoryStore`. If `enabled: true` but no `scope` or `store` is given, the validator emits a warning.

### 6.8 `session.compact`

```yaml
session:
  compact:
    enabled: true
    trigger:
      contextRatio: 0.75
    strategy: structured-summary
    preserve:
      - instructions
      - open_decisions
      - changed_files
```

`contextRatio` must be in the range `(0, 1]`. `preserve` lists the categories of information you want to keep during compaction. P1-A only validates the structure; it does not invoke the compact runtime.

### 6.9 `sandbox`

```yaml
sandbox:
  enabled: false
```

Or:

```yaml
sandbox:
  enabled: true
  provider: remote-vm
```

P1-A does not create a real sandbox. If `enabled: true`, you must provide `provider` or `profile`; otherwise the validator reports an error.

The sandbox field here is the declarative entry point for the later Sandbox SPI — it is not a normal tool, and it is not "turn it on and remote execution happens automatically." Real routing should be implemented by the later `SandboxProvider`, coding tools, and CLI host.

### 6.10 `workflow`

```yaml
workflow:
  mode: react
  maxTurns: 20
```

`mode` values supported in P1-A:

- `react`
- `codeact`

`maxTurns`, if filled in, must be a positive integer.

## 7. What the validator checks

Errors:

| Code | Path | Meaning |
| --- | --- | --- |
| `blueprint.version.required` | `$.version` | Missing version |
| `blueprint.version.unsupported` | `$.version` | version is not `ai4j.agent/v1` |
| `blueprint.id.required` | `$.id` | Missing id |
| `blueprint.id.invalid` | `$.id` | id does not match the stable slug rule |
| `blueprint.model.required` | `$.model` | Missing model block |
| `blueprint.model.provider.required` | `$.model.provider` | Missing provider |
| `blueprint.model.selector.required` | `$.model.model` | Neither `model` nor `profile` is filled in |
| `blueprint.plugin.id.required` | `$.plugins[n].id` | Plugin missing id |
| `blueprint.tool.ref.required` | `$.tools[n].ref` | Tool missing ref |
| `blueprint.compact.contextRatio.invalid` | `$.session.compact.trigger.contextRatio` | compact ratio not in `(0, 1]` |
| `blueprint.workflow.mode.invalid` | `$.workflow.mode` | Illegal workflow mode |
| `blueprint.workflow.maxTurns.invalid` | `$.workflow.maxTurns` | workflow maxTurns is not positive |
| `blueprint.sandbox.selector.required` | `$.sandbox` | sandbox enabled but no provider/profile |

Warnings:

| Code | Meaning |
| --- | --- |
| `blueprint.field.unknown` | Unknown top-level field. Currently kept as a warning to allow future version evolution. |
| `blueprint.memory.scope.warning` | memory enabled but no scope/store. |
| `blueprint.tool.approval.unknown` | approval value unknown; a later host policy can interpret it or escalate it to an error. |

## 8. Relationship to the existing Agent API

The current Java runtime still builds Agents using APIs such as `AgentBuilder` / `Agents.react()`. The Blueprint does not yet replace these APIs.

```text
P1-A:
  YAML -> AgentBlueprint DTO -> ValidationReport

P1-B:
  AgentBlueprint DTO + host AgentFactoryContext -> AgentFactory -> Agent / AgentSession
```

This layering has two benefits:

1. The configuration contract stabilizes first, so the later Factory does not need to redefine fields.
2. The UI, CLI, docs, and tests can all surface clear errors to the user based on the validator up front, rather than waiting until runtime to fail.

## 9. Relationship to Approval / Permission Policy

`tools[].approval` can be seen as a later policy mapping entry point. For example:

```yaml
tools:
  - ref: coding.shell
    approval: safe
```

P1-B still does not directly generate an `AgentPermissionPolicy`. The Factory/host can, in later versions or at the business layer, map different approval values to:

- Allow execution
- Deny execution
- Require user approval
- Allow only inside a sandbox

For the underlying policy semantics, see [Agent Approval / Permission Policy](/docs/agent/approval-permission-policy).

## 10. Relationship to Sandbox

The `sandbox` in a Blueprint is a declaration, not an implementation.

```text
sandbox.enabled=true
  != a VM has been created
  != shell/file/git/browser has executed remotely
  != the permission policy has been relaxed automatically
```

A real sandbox still requires, later on:

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`
- coding tool routing
- CLI/TUI status display

So P1-A's sandbox validation only answers one question: "Does this config express a sandbox selector that a later host can resolve?"

## 11. Current boundaries and next steps

Already in place:

- `AgentBlueprint` and field DTOs
- `AgentBlueprintLoader`
- `AgentBlueprintValidator`
- `AgentBlueprintValidationReport`
- `AgentBlueprintValidationIssue`
- `AgentFactory` / `AgentFactoryContext` / `AgentFactoryException`
- YAML fixtures and deterministic JUnit tests

Suggested next steps:

1. P2: Design the Sandbox SPI so the `sandbox` field gets a real provider binding.
2. P3: Make the file/shell/git/browser tools in `ai4j-coding` aware of the sandbox binding.
3. P4: Complete the `ai4j` main command, TUI layout, provider/model switching, `/sandbox` status, and fuller reply rendering in the CLI/TUI.

## 12. Troubleshooting

### Load failure: `blueprint.yaml.invalid`

The YAML syntax is invalid. Check indentation, lists, and colons with a regular YAML linter first.

### Validation failure: `blueprint.model.selector.required`

Fill in at least one of `model.model` or `model.profile`. Writing only the provider is not enough to locate a concrete model.

### `sandbox.enabled=true` is set but there is no remote execution

This is expected. Both P1-B's `AgentFactory` and P1-C's `ai4j-cli run` reject `sandbox.enabled=true` by default, to prevent users from mistakenly believing a VM has been created. `--allow-sandbox-declaration` only lets the declaration pass; it does not create a real remote execution environment. Real remote execution requires the later Sandbox SPI and coding tool routing.

### Why doesn't a wrong `model.profile` fall back to the default profile?

This is by design. `model.profile` or `--profile` means the user has explicitly specified a CLI host profile.
If that profile does not exist, or is incompatible with an explicit `--provider`, `ai4j-cli run` fails immediately, to avoid silently using the default profile and getting the wrong model, base URL, or key.

### Wanting to put the token in the YAML

:::danger
Do not do this. Blueprint files are meant to be committed, shared, and templated; tokens belong in environment variables, host configuration, or a secret store.
:::
