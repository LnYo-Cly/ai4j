---
title: "Agent Concept Map"
description: "The navigation map for every core agent concept in ai4j: 20 concepts organized into 7 capability clusters — the capability triangle (Function Call/MCP/Skill), the memory and context chain, the execution core layers, the security boundary, observability, external protocols, and engineering. Each cluster marks the relationships between concepts, with a one-sentence positioning and a deep link to the detail page."
sidebar_position: 10
tags: [concept]
---

# Agent Concept Map

> This page is ai4j's **concept GPS** — 20 core agent concepts organized into 7 capability clusters. Each cluster marks the relationships between concepts, with a one-sentence positioning and a direct link to the detail page.

## Why this page exists

ai4j's documentation is organized by subsystem (Core SDK / Agent Runtime / Coding Agent / MCP / FlowGram…), and each subsystem has its own concept entry page. But agent concepts **span multiple subsystems** — Function Call lives in Core SDK, Hooks in Agent Runtime, Compaction in Agent + Coding Agent — so readers easily get lost between sections.

This page does not duplicate the content of each detail page. It is only responsible for: **telling you what the 20 concepts are, which layer each one belongs to, how they relate to each other, and which page to start reading from.**

## Concept panorama

```
┌─────────────────────────────────────────────────────────────┐
│             Capability triangle (how the model acts)         │
│   Function Calling ←──→ MCP ←──→ Skill                      │
│     execute code        connect external tools   methodology │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│          Memory & context chain (how state is managed)       │
│   Memory → Context Window → Compaction → Checkpoint         │
│    store facts     manage window      compact      archive & recover │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│           Execution core layers (how work is divided)        │
│   Agent Loop → DAG/Workflow → Subagents → Agent Teams       │
│   single-step loop    orchestrate DAG   dispatch subtasks  multi-agent collaboration │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│            Security boundary (what can and can't be done)    │
│   Sandbox + Hooks + Plugin + Workspace Trust                │
│   isolated execution   event interception   contribute capabilities   trust gate │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│                    Observability (what happened)             │
│   Trace → Replay / Audit                                    │
│   real-time tracing    replay & recovery + tamper-evident   │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│       External protocols & knowledge augmentation (how to   │
│       interact with the outside world)                      │
│   A2A + ACP + MCP Server + RAG                              │
│   agent interconnect   IDE protocol   expose tools   knowledge augmentation │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│               Engineering (how to reach production)          │
│   Session + Prompt + Harness                                │
│   session management   prompt assembly   coding agent host  │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. The capability triangle: Function Calling ↔ MCP ↔ Skill

**This is ai4j's most central conceptual relationship** — all three let the model "do things," but through entirely different mechanisms. Understand this triangle before diving into any one of them.

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Function Calling** | The model calls Java methods you declare (`@FunctionCall` / built-in / SPI), executed inside the host process | Core SDK | [Tools overview](/docs/capabilities/tools/overview) |
| **MCP** | A standard protocol that connects external tool servers (local stdio / remote HTTP); the tools do not live in your process | Core SDK → top-level MCP | [MCP overview](/docs/capabilities/mcp/overview) |
| **Skill** | Rather than executing an action, it gives the model **methodology guidance** (SKILL.md) to read on demand; it controls the "how to do it" knowledge | Core SDK | [Skills overview](/docs/capabilities/skills/overview) |

:::tip How to choose between the three
The three are not mutually exclusive; they can be used together. The core distinction:
- **Function Calling** = model → your code (in-process)
- **MCP** = model → external tool server (cross-process)
- **Skill** = model → methodology document (no execution, just knowledge)

For the detailed comparison table and decision framework, see [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp).
:::

---

## 2. Memory & context chain

**The full path of state from "remember" to "compact" to "recover."** These 4 concepts are progressive — later concepts depend on earlier ones.

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Memory / Chat Memory** | Session-level fact storage (system/user/assistant/tool-call/tool-output/summary); storage and retention policies are decoupled | Core SDK | [Memory overview](/docs/capabilities/chat-memory/overview) |
| **Context Window Management** | Manages the size of the context window that enters the model (ContextBudget limits entries/characters/pinned prefix) | Agent Runtime | [Context Window Management](/docs/agent/memory/context-window-management) |
| **Compaction** | Compresses the context (ContextProjector trims by strategy / microcompact tool results / auto-compact circuit breaker) | Agent + Coding Agent | [Memory Compact Context](/docs/agent/memory/memory-compact-context) · [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint) |
| **Checkpoint / Resume** | Structured archive + crash recovery (ResumeCache skips completed side effects + hash-chained tamper-evident audit) | Agent + Coding Agent | [Replay, Recovery & Audit](/docs/agent/observability/replay-recovery-audit) · [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint) |

:::note Cross-layer note
Compaction and Checkpoint are both implemented in the Agent Runtime layer and the Coding Agent layer, with different concerns at each layer:
- **Agent layer**: ContextProjector + ResumeCache (context/recovery for general agents)
- **Coding Agent layer**: CodingSessionCompactor + CodingSessionCheckpoint (pipeline specific to coding sessions)

Start from the concept page in the Agent layer, then jump to the Coding Agent layer for the engineering implementation.
:::

---

## 3. Execution core layers

**A 4-level progression from "single-agent single-step loop" to "multi-agent collaboration."** Each level is a superset of the previous one.

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Agent Loop (ReAct / CodeAct)** | A single agent's think→act→observe loop; ReAct uses tool calls, CodeAct uses code execution | Agent Runtime | [Minimal React Agent](/docs/agent/runtimes/minimal-react-agent) · [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime) |
| **DAG / Workflow Orchestration** | Orchestrates multiple agent steps into a directed acyclic graph (StateGraph); declare nodes + edges + conditional branches | Agent Runtime | [Workflow StateGraph](/docs/agent/runtimes/workflow-stategraph) |
| **Subagents** | The main agent delegates subtasks to isolated subagents (independent memory + tool + session) | Agent Runtime | [Subagent Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy) |
| **Agent Teams** | Multiple agents form a team that coordinates task assignment, parallel execution, and result aggregation through a TaskBoard | Agent Runtime | [Agent Teams](/docs/agent/orchestration/agent-teams) |

---

## 4. Security boundary

**Four gates that control what an agent can and cannot do.** Together these concepts form ai4j's security perimeter.

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Sandbox** | Isolated code execution environment (E2B / Daytona / CubeSandbox); the agent runs code inside a remote sandbox | Agent Runtime | [Sandbox SPI](/docs/agent/governance/sandbox-spi) · [CubeSandbox](/docs/agent/governance/cubesandbox-provider) |
| **Lifecycle Hooks** | Intercepts, approves, or observes agent behavior at PreToolUse / PostToolUse / Stop and other event points | Agent + Coding Agent | [Plugin Lifecycle Hooks](/docs/agent/governance/plugin-lifecycle-hooks) · [Lifecycle Hooks](/docs/products/coding-agent/lifecycle-hooks) |
| **Plugin / Extension** | Third parties package jars to contribute tool/command/skill/prompt, gated by a discover→enable→expose three-stage pipeline | Core SDK (extension-api) | [Extension overview](/docs/extending/overview) · [Extend ai4j](/docs/extending/extend-ai4j) |
| **Workspace Trust** | Pauses for a y/n prompt on first entry into an untrusted directory; managed via `~/.ai4j/trusted-dirs.txt`; `ai4j cli trust` command | Coding Agent | [Lifecycle Hooks & Trust](/docs/products/coding-agent/lifecycle-hooks) |

:::warning Security model boundary
- **Sandbox** controls **execution isolation** (code runs remotely, not on your machine)
- **Hooks** control **behavior interception** (check + approve before/after the agent executes)
- **Plugin** controls **capability contribution** (not exposed unless given)
- **Workspace Trust** controls **first-time trust** (config is not loaded for untrusted directories)

The four are orthogonal — an agent can be sandbox-isolated + hook-intercepted + limited to an allowlist of tools + restricted to run only in trusted directories, all at once.
:::

---

## 5. Observability

**What happened during agent execution, whether it can be traced back, and whether it can be recovered.**

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Agent Trace / Observability** | The runtime publishes a unified event stream (MODEL_REQUEST / TOOL_CALL / TOOL_RESULT); trace consumes it, folds it into spans, and exports to OTel / Langfuse / JSONL | Agent Runtime | [Trace & observability](/docs/agent/observability/trace-observability) |
| **Replay / Audit** | Node-level I/O replay (live/mock), crash resume (ResumeCache), and tamper-evident hash-chained audit log | Agent Runtime | [Replay, Recovery & Audit](/docs/agent/observability/replay-recovery-audit) |

:::note The event stream is the foundation
Both Trace and Replay are **consumers** of the runtime event stream, not instrumentation — the events are already published; trace/replay only decides how to consume them. This means you can add trace export or replay recovery at any time without modifying agent code.
:::

---

## 6. External protocols & knowledge augmentation

**How the agent interacts with the outside world — with other agents, with the IDE, with tool servers, with knowledge bases.**

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **A2A (Agent-to-Agent)** | A JSON-RPC + SSE protocol that exposes an ai4j agent as a service other agents can discover and call | Agent Runtime | [A2A](/docs/agent/observability/a2a) |
| **ACP (Agent Client Protocol)** | Newline-delimited JSON-RPC (not LSP framing) that lets an IDE / desktop shell drive a coding session (create/load/prompt/permission confirmation) | Coding Agent | [ACP integration](/docs/products/coding-agent/acp-integration) · [Programmatic integration](/docs/getting-started/programmatic-integration) |
| **MCP Server** | Exposes ai4j's tools as an MCP server (streamable-HTTP / SSE / stdio); other MCP clients can discover and call them | MCP (top-level) | [Build Your MCP Server](/docs/capabilities/mcp/build-your-mcp-server) |
| **RAG** | Ingestion → chunking → embedding → vector store → retrieval → rerank → citation: a complete knowledge augmentation pipeline | Core SDK | [Search and RAG overview](/docs/capabilities/rag/overview) |

---

## 7. Engineering

**Three engineering concepts that take you from SDK calls to a production-grade agent application.**

| Concept | One sentence | Layer | Detail page |
|---|---|---|---|
| **Session Management** | AgentSession as a stateful long-running container (sessionId + independent memory + event log + snapshot/restore) | Agent + Coding Agent | [Session Runtime](/docs/agent/session-runtime) · [Coding Session Runtime](/docs/products/coding-agent/session-runtime) |
| **Prompt / System Prompt** | Field semantics of systemPrompt (runtime instruction merge) vs instructions (kept independent) + the prompt assembly pipeline of the coding agent | Agent + Coding Agent | [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions) · [Prompt Assembly](/docs/products/coding-agent/prompt-assembly) |
| **Harness / Coding Agent** | A complete terminal coding agent host (CLI/TUI + ACP + sandbox-routing + tools + approvals + compaction) | Coding Agent | [Coding Agent overview](/docs/products/coding-agent/overview) · [Programmatic integration](/docs/getting-started/programmatic-integration) |

---

## How to use this page

1. **First time learning about agents**: start with the [capability triangle](#1-the-capability-triangle-function-calling--mcp--skill) to understand how the model acts.
2. **Need to manage state**: walk the [memory & context chain](#2-memory--context-chain), from Memory to Checkpoint.
3. **Need to orchestrate complex tasks**: walk the [execution core layers](#3-execution-core-layers), from Agent Loop to Agent Teams.
4. **Going to production**: check the [security boundary](#4-security-boundary) + [observability](#5-observability) + [engineering](#7-engineering).
5. **Need to integrate with the outside world**: see [external protocols](#6-external-protocols--knowledge-augmentation).

## Further reading

- [Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp) — detailed disambiguation of the capability triangle
- [Agent Runtime overview](/docs/agent/overview) — the full entry point to the agent subsystem
- [Extend ai4j](/docs/extending/extend-ai4j) — aggregate entry for plugin/Skill/Prompt/custom provider
- [Programmatic integration](/docs/getting-started/programmatic-integration) — aggregate entry for SDK/RPC/event stream/TUI
- [Feature Map](/docs/getting-started/feature-map) — feature maturity map
