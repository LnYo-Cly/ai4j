---
sidebar_position: 4
title: "AI4J Agent SDK Roadmap"
description: "Technical roadmap for ai4j-agent: phased evolution from the P0 runtime kernel (Session/Memory/Compact/Plugin/Permission) through P1 Blueprint YAML, P2 Sandbox SPI, P3 Coding sandbox routing, P4 CLI, and P5 Remote Runner."
tags: [reference]
---

# AI4J Agent SDK Roadmap

This page explains how `ai4j-agent` will evolve from a "working Agent runtime" into a more complete Java Agent SDK.

First, a boundary note: this is a technical roadmap, not a sign that every capability is already shipped. Capabilities available today include `Agent`, `AgentBuilder`, `AgentRuntime`, `AgentSession`, memory, runtime, workflow, team, trace, and so on; the directions below are what will be strengthened incrementally going forward.

## 1. The Most Important Architectural Judgment

`ai4j-agent` does not need yet another top-level concept called `AgentHost`, `Host Kernel`, or `ai4j-runtime`.

A better mental model is:

```text
ai4j
  Foundation model SDK: Chat / Responses / Provider / Streaming / Tool Schema

ai4j-agent
  General-purpose Agent SDK: Agent / Runtime / Session / Memory / Compact / Plugin / Event

ai4j-extension-api
  Plugin contracts: Tool / Command / Hook / Prompt / Skill / UI / SandboxProvider

ai4j-coding
  Coding Agent capability pack: file / shell / git / browser / workspace / diff / project run

ai4j-cli
  Terminal product: CLI / TUI / ACP host / session command
```

In other words, `ai4j-agent` is the main entry point of the general-purpose Agent SDK; `ai4j-coding` and `ai4j-cli` are more specific product layers built on top of it.

## 2. What Already Exists Today

`ai4j-agent` is not a blank module. It already provides:

- `Agent` / `AgentBuilder` / `AgentRuntime` / `AgentSession` / `AgentContext`
- `AgentMemory` / `MemoryCompressor`
- `ReActRuntime` / `CodeActRuntime` / `DeepResearchRuntime`
- `AgentToolRegistry` / `ToolExecutor`
- workflow / subagent / team orchestration
- trace / event publisher
- extension bridge

What follows is not "tear it down and rewrite", but rather building on these foundations to fill out long-running execution, declarative assembly, plugin lifecycle, and sandbox execution boundaries.

## 3. P0: First Strengthen the Agent SDK Kernel

P0 does not chase flashy APIs; it first stabilizes the foundational state model for long-horizon agents.

### P0-A: Promote `AgentSession` to a Runtime Container

Today `AgentSession` is closer to a lightweight state-derivation entry point. Next it needs to become the runtime container for a long-horizon Agent task:

```text
AgentSession =
  sessionId
  + event log
  + memory
  + compact state
  + plugin state
  + tool execution state
  + sandbox binding
  + artifact state
  + checkpoint
  + resume / fork / rewind
```

The first version should not do everything at once. The P0-A foundation has already landed in `AgentSession` and the `io.github.lnyocly.ai4j.agent.session` package:

- session id / metadata
- session snapshot
- session event log
- in-memory session store
- `AgentBuilder.sessionStore(...)` and `Agent.resumeSession(...)`
- compatible with the existing `Agent.run(...)`

For usage details, see [Agent Session Runtime](/docs/agent/session-runtime).

### P0-B: Separate Memory, Compact, and Context Projector into Layers

The P0-B foundation has already landed: `ContextBudget`, `ContextProjector`, `ContextReport`, `CompactPolicy`, `CompactResult`, `AgentSession.compact(...)`, and the session snapshot compact state. For usage details, see [Memory Compact Context Projector](/docs/agent/memory-compact-context).

This layer must clearly separate three tiers:

| Layer | Responsibility |
| --- | --- |
| `SessionEventLog` | Complete event history: user input, model output, tool calls, tool results, errors, artifacts, etc. |
| `Memory` | Stable information that spans turns or sessions, e.g. preferences, project conventions, past experience |
| `ModelContext` / `WorkingContext` | The context actually sent to the model for the current turn |

Core P0-B objects include:

- `AgentSessionStore`
- `AgentSessionEventLog`
- `ContextProjector`
- `CompactPolicy`
- `CompactResult`
- `ContextBudget`
- `ContextReport`

Compact results should be as structured as possible, not just a natural-language summary. At minimum, preserve:

- Completed items
- Incomplete items
- Key decisions
- Modified files or artifacts
- Failed commands
- Test results
- User confirmations
- sandbox state
- open questions

### P0-C: Plugin Lifecycle and Tool Execution Lifecycle

Plugins should not only contribute tools; they should also be able to participate in the Agent run lifecycle.

The P0-C foundation has already landed: `ai4j-extension-api` adds the `io.github.lnyocly.ai4j.extension.lifecycle` public contract, and `ai4j-agent` fires observation-style hooks in the ReAct/Base runtime, the CodeAct runtime, and `AgentSession.compact(...)`. For usage details, see [Plugin Lifecycle Hooks](/docs/agent/plugin-lifecycle-hooks).

Currently supported:

```text
BEFORE_TURN
AFTER_TURN
BEFORE_MODEL_REQUEST
AFTER_MODEL_RESPONSE
BEFORE_TOOL_CALL
AFTER_TOOL_CALL
ON_COMPACT
```

`SESSION_START` and `SESSION_END` are reserved as event types but are not fired automatically in the first version. The reason is that the Agent does not yet have a stable explicit close/end lifecycle.


### P0-D: Tool Approval and Permission Policy

The P0-D foundation has already landed: `ai4j-agent` adds the `io.github.lnyocly.ai4j.agent.permission` package, providing `AgentPermissionPolicy`, `AgentPermissionRequest`, `AgentPermissionDecision`, `AgentPermissionToolExecutor`, and `AgentExecutionEnvironment`.

This layer provides the host-side policy gate before tool execution:

```text
Valid tool call
  -> permission policy
  -> allow / deny / require approval
  -> delegate ToolExecutor or TOOL_ERROR
```

For usage details, see [Agent Approval / Permission Policy](/docs/agent/approval-permission-policy).

The boundaries must be stated clearly:

- `executionEnvironment` is policy metadata only; it does not create a sandbox.
- `REQUIRE_APPROVAL` is only a steady state; interactive approval belongs to the CLI/TUI or host application.
- Real VM / container / microVM / remote execution environments belong to the later Sandbox SPI.
- Entering a sandbox does not mean automatically unlocking dangerous tools; they must still pass through the permission policy.


The capabilities a plugin can contribute can be extended to:

- Tool
- Command
- Prompt
- Skill
- Guardrail
- UI contribution
- Memory provider
- Compact strategy
- SandboxProvider
- RemoteAgentRunnerProvider

This part must consider the boundaries of both `ai4j-agent` and `ai4j-extension-api`: public contracts go into the extension API, while runtime orchestration remains controlled by `ai4j-agent`. The first version of hooks is observation-first, not a mutable interceptor for prompt/tool/model responses.

## 4. P1: Agent Blueprint YAML

A Java API is well suited for building an Agent dynamically, but users also need a declarative, shareable, template-friendly way to configure one.

The first version can ship single-Agent Blueprints only:

```yaml
version: ai4j.agent/v1
id: coding-assistant
name: Coding Assistant

model:
  provider: openai-compatible
  profile: default
  model: gpt-4.1

instructions:
  system: |
    You are a careful coding agent.

plugins:
  - id: ask-user
  - id: todo
  - id: browser

tools:
  - ref: coding.file
  - ref: coding.shell
    approval: safe
  - ref: coding.git

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

The goal of P1 is not to build a full low-code platform, but to first provide:

- YAML schema / Java DTO
- loader
- validator
- fixture tests
- `AgentFactory`: after the host explicitly supplies dependencies such as `AgentModelClient`, turns the Blueprint into an `AgentBuilder` / `Agent`

The P1-A/P1-B/P1-C foundation has already landed: the `io.github.lnyocly.ai4j.agent.blueprint` package provides `AgentBlueprint`, `AgentBlueprintLoader`, `AgentBlueprintValidator`, `AgentBlueprintValidationReport`, `AgentFactory`, `AgentFactoryContext`, plus deterministic YAML / Factory tests; `ai4j-cli` provides `ai4j-cli run <agent.yaml> --input <task>`, so users can run a single-Agent Blueprint straight from the terminal. For usage details, see [Agent Blueprint YAML](/docs/agent/agent-blueprint).

Team Blueprint, Workflow Blueprint, and FlowGram export can be deferred.

## 5. P2: Sandbox SPI

The sandbox here is not simply "yet another shell tool".

Two categories of capability must be distinguished:

| Type | Description |
| --- | --- |
| Local permission sandbox | File-write, network, and approval restrictions similar to a CLI, not necessarily a VM |
| Real remote sandbox | Cloud VM / container / microVM that can install dependencies, run a project, open a browser, and persist artifacts |

AI4J should focus on the abstraction first, rather than officially maintaining a pile of concrete providers. The current route is: stabilize the SPI first, then keep a small number of officially verified real providers. Daytona has landed as the first real provider under P2-C; other providers should not be squeezed into the core concepts.

P2-A has landed the minimal contract, P2-B has added `AgentSessionSandboxBinding` so that session snapshot/store/event log can retain non-sensitive sandbox summaries, and P2-C provides `DaytonaSandboxProvider`. The P2-A minimal contract includes:

- `SandboxProvider`
- `SandboxSession`
- `SandboxSpec`
- `SandboxCommand`
- `SandboxResult`
- `SandboxArtifact`
- `SandboxEvent` / `SandboxEventType`
- `SandboxStatus`

For usage details, see [Agent Sandbox SPI](/docs/agent/sandbox-spi).

Principles:

- The sandbox binds to the execution environment of `AgentSession` or a coding session.
- Execution-style tools such as file / shell / git / browser / project run automatically sense the sandbox.
- Without a sandbox, local execution is preserved.
- With a sandbox, execution moves into the sandbox.
- Daytona is the first official real provider and can be tried directly; CubeSandbox / Docker / E2B / K8s / company-internal sandboxes should still be integrated via plugins, the application side, or standalone provider packages.

## 6. P3: `ai4j-coding` Wired into the Sandbox

The first P3 slice has landed: `CodingAgentBuilder.sandbox(SandboxSession)` binds a live sandbox to a newly created coding session and routes `bash action=exec` to `SandboxSession.execute(...)` via `SandboxShellCommandExecutor`. For usage details, see [Coding Agent Sandbox Routing](/docs/coding-agent/sandbox-routing).

`ai4j-agent` owns the general runtime; `ai4j-coding` owns workspace-aware coding tools.

So the areas the sandbox affects the most are:

- file
- shell
- git
- browser
- test runner
- project run
- artifact collection

Wiring must preserve one compatibility principle:

```text
No sandbox = current local execution semantics unchanged
With sandbox = execution-style tools automatically route to the sandbox
```

Approval is still controlled by the coding tool policy / host policy; entering a sandbox must not default to unlocking every dangerous capability.

## 7. P4: The CLI `/sandbox` Experience

The CLI/TUI layer should surface a clear, visible, switchable sandbox state.

Recommended commands:

```text
/sandbox
/sandbox status
/sandbox enable <provider>
/sandbox disable
/sandbox attach
```

The TUI should at least let the user know:

- Whether a sandbox is currently enabled
- What the provider is
- Where the workspace lives
- Whether the most recent command ran locally or in the sandbox
- Whether the sandbox is recoverable or has been destroyed

If you later need to test the interactive experience, you can drive the CLI with tmux to verify input commands and output rendering.

## 8. P5: Remote Agent Runner

The remote Runner is not a Maven module that must be added right now, but it is a future product direction.

The target scenario is: a developer wants to quickly build something resembling a cloud-based Agent product:

```text
User Web/App/CLI
  ↓
Control-plane backend
  ↓
Remote sandbox
  ↓
ai4j-agent-runner
  ↓
shell / browser / workspace / project run / artifacts
```

Runner responsibilities include:

- Running the `ai4j-agent` loop
- Managing session
- Executing coding tools
- Operating the workspace
- Running shell
- Opening a browser
- Taking screenshots
- Collecting artifacts
- Streaming events

:::note The Runner must come after P0-P4
It must come after P0-P4. Otherwise the SDK kernel, coding tools, sandbox, and product protocols get coupled together prematurely.
:::

## 9. Recommended Implementation Order

| Order | Task | Minimal regression |
| --- | --- | --- |
| 1 | P0-A AgentSession runtime container | `mvn -pl ai4j-agent -DskipTests=false test` |
| 2 | P0-B Memory / Compact / Context Projector | `mvn -pl ai4j-agent "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 3 | P0-C Plugin Lifecycle Hooks | `mvn -pl ai4j-extension-api -DskipTests=false test` + `mvn -pl ai4j-agent -DskipTests=false test` |
| 4 | P0-D Approval / Permission Policy | `mvn -pl ai4j-agent -am "-Dtest=AgentApprovalPermissionPolicyTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 5 | P1-A/P1-B Agent Blueprint YAML loader / validator / AgentFactory | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest,AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 6 | P1-C CLI `run <agent.yaml>` | `mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-cli -am -DskipTests=false test` |
| 7 | P2-A/P2-B Sandbox SPI + AgentSession binding | `mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest,AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test` |
| 8 | P3 Coding Sandbox Routing | `mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-coding -am -DskipTests=false test` |
| 9 | P2-C Daytona SandboxProvider | `mvn -pl ai4j-agent -am "-Dtest=DaytonaSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test` + `mvn -pl ai4j-agent -am -DskipTests=false test`; run live smoke separately and explicitly with `-P live-provider-tests` |
| 10 | P4 CLI Sandbox Commands | `mvn -pl ai4j-cli -am -DskipTests=false -DfailIfNoTests=false test` |
| 11 | P5 Runner Decision | contract tests after module decision |

## 10. What Not to Do Right Now

- Do not add an `ai4j-agent-runner` module all at once.
- Do not bake real cloud-sandbox provider tokens, tenants, or private workspace paths into Blueprints, doc examples, test fixtures, or logs.
- Do not port all compact/checkpoint logic from `ai4j-coding` straight into `ai4j-agent`.
- Do not hardcode the name of any specific OpenAI-compatible relay platform in the docs as if it were an SDK concept.
- Do not write provider tokens into config examples, test fixtures, or documentation.

A steadier route is: first make the long-running kernel of `ai4j-agent` solid, then let Blueprint, Sandbox, the Coding Agent, CLI, and the Runner plug in layer by layer.
