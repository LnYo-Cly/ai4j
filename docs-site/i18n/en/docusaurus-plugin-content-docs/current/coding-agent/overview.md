---
sidebar_position: 1
title: "Coding Agent Overview"
description: "Overview of the AI4J Coding Agent module responsibilities, the three host entry points (CLI/TUI/ACP), and core concepts, helping you decide whether local code repository tasks fit the Coding Agent runtime."
tags: [concept]
---

# Coding Agent Overview

`Coding Agent` is AI4J's runtime and host entry point for local code repository tasks. It is not "a general-purpose agent with a few file tools bolted on", but rather organizes workspace, tools, sessions, approval, MCP, Skills, and CLI/TUI/ACP into a single local development workflow.

If you want to embed a general-purpose agent in a business system, start with [Agent](/docs/agent/overview). If you want AI to read files, run commands, write patches, persist sessions, and accept host approval inside a code repository, read this section.

## In One Sentence

Coding Agent addresses:

> Inside a local code repository context, letting the model complete trackable, recoverable, approvable development tasks through controlled tools.

Its core value is not just "being able to call bash", but rather that workspace, tool policy, session lifecycle, and host protocol live within the same runtime model.

## Module Responsibilities

| Module | Responsibility |
| --- | --- |
| `ai4j-coding` | coding runtime, workspace-aware tools, outer loop, compact, child session |
| `ai4j-cli` | CLI, TUI, ACP host, session store, provider profile, approval UI |
| `ai4j-agent` | underlying Agent runtime |
| `ai4j` | foundation capabilities such as model, Tool, Skill, MCP |

A simple way to remember:

- `ai4j-coding` decides how a task runs.
- `ai4j-cli` decides how a person or external host uses it.

## Suitable Scenarios

| Scenario | Suitable? |
| --- | --- |
| Embedding a general-purpose business agent in a Java project | Prefer `ai4j-agent` |
| Local code repository Q&A, modification, verification | Yes |
| Needing a CLI or TUI as the development entry point | Yes |
| Needing an IDE / desktop app to integrate through a structured protocol | Yes, via ACP |
| Needing files, commands, patches, approval, and session state | Yes |
| Only a single model call or Tool call | Coding Agent not needed |
| Needing a visual workflow canvas | See FlowGram |

## What a Single Run Includes

When Coding Agent is assembled, it simultaneously decides:

- The current workspace and path boundary.
- The visible built-in tools, such as read file, write file, shell, and patch.
- Whether to wire in MCP tools.
- The available Skills and workspace instructions.
- The provider profile, model, baseUrl, and apiKey source.
- The approval policy.
- Whether the session is created, recovered, saved, or forked.

Therefore, CLI, TUI, and ACP are not three separate agents, but rather three host entry points. They share the same core runtime, but differ in interaction style and approval channel.

## Three Entry Points

| Entry point | Suited for | Focus |
| --- | --- | --- |
| CLI | Users who want to run a quick one-shot or REPL | provider, workspace, session, command arguments |
| TUI | People who work in the terminal for long stretches | slash command, state view, interaction density |
| ACP | IDEs, desktop apps, custom frontends | JSON-RPC session, permission request, host-injected capabilities |

:::note More than three top-level subcommands
The table above distinguishes the three entry points CLI/TUI/ACP by **host interaction mode**. But the `ai4j-cli` executable itself exposes more top-level subcommands: beyond the three session-style entry points `code`/`tui`/`acp`, there are also `run` (run an Agent Blueprint YAML once), `extension` (inspect/assemble/run extension packages), and `trust` (manage workspace hook trust directories). See [Command Reference §7](/docs/coding-agent/command-reference) for the full list.
:::

If you are only evaluating features, start with [Quickstart](/docs/coding-agent/quickstart) and [CLI / TUI](/docs/coding-agent/cli-and-tui).

## Core Concepts

| Concept | Description |
| --- | --- |
| Workspace | The current code repository context and file boundary |
| Built-in Tools | Coding-native tools such as read file, write file, shell, and patch |
| Approval | The confirmation mechanism for high-risk tool calls |
| Session | Work state that can be saved, recovered, and forked |
| Compact / Checkpoint | Context compaction and state retention during long tasks |
| Provider Profile | The combination of provider, protocol, model, baseUrl, and key source |
| Skills | Workflow instructions and project experience that the model reads on demand |
| MCP / ACP | MCP wires in tool capabilities; ACP wires in the host application |

## Security and Limits

:::warning Security boundary
Coding Agent has a larger high-risk surface than a normal agent, because it may touch the file system, shell, processes, and external services.
:::

Before going to production or long-term use, confirm that:

- The workspace root directory is correct and the forbidden paths are explicit.
- Write file, shell, patch, and package manager commands have approval rules.
- The session store does not record real secrets.
- MCP tools are not exposed in full by default.
- Subagents or delegation do not cross the original permission boundary.
- Run output and traces do not leak private code or configuration.

Related pages:

- [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
- [Session Runtime](/docs/coding-agent/session-runtime)
- [Security Overview](/docs/security/overview)

## Recommended Reading Order

### Direct Use

1. [Why Coding Agent](/docs/coding-agent/why-coding-agent)
2. [Quickstart](/docs/coding-agent/quickstart)
3. [Install and Release](/docs/coding-agent/install-and-release)
4. [CLI / TUI](/docs/coding-agent/cli-and-tui)
5. [Provider Profiles](/docs/coding-agent/provider-profiles)
6. [Tools and Approvals](/docs/coding-agent/tools-and-approvals)
7. [Session Runtime](/docs/coding-agent/session-runtime)

### Extension Development

1. [Architecture](/docs/coding-agent/architecture)
2. [Runtime Architecture](/docs/coding-agent/runtime-architecture)
3. [Prompt Assembly](/docs/coding-agent/prompt-assembly)
4. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
5. [Command Reference](/docs/coding-agent/command-reference)

If you want to compare Coding Agent with agent SDKs in the JS/TS ecosystem, see [Comparison](/docs/comparison/overview).
