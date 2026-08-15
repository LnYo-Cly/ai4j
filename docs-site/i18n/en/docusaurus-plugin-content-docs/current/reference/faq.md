---
sidebar_position: 998
title: "FAQ"
description: "A consolidated FAQ for the AI4J docs site: where to start, how the SDK relates to the AI foundation, and how to disambiguate concepts such as Chat/Responses, Function Call/Tool, MCP/Agent, Skill/Tool, and ACP/MCP."
tags: [reference]
---

# FAQ

This page consolidates the most common "which page should I actually read?" and "what's the real difference between these concepts?" questions on the AI4J docs site.

If you're hitting a specific API failure, an unexpected parameter, or a concrete error, look at the troubleshooting section of the relevant topic page first. This page leans toward path guidance and concept clarification.

---

## 1. I'm new to AI4J — where should I start

Recommended order:

1. [Why AI4J](/docs/getting-started/why-ai4j)
2. [Architecture at a Glance](/docs/reference/maps/architecture-at-a-glance)
3. [Quickstart for Java](/docs/getting-started/quickstart-java)
4. [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)

---

## 2. Is AI4J an SDK, or an AI foundation

More precisely:

- It does include an SDK
- but the project is positioned as more than "a wrapper package around model interfaces"
- it is more like an AI foundation for the Java ecosystem

The reason: it weaves these capabilities into one continuous spine:

- Model calls
- `Tool / Function Call`
- `Skill`
- `MCP`
- Spring Boot
- Agent
- Coding Agent
- FlowGram

If you want to understand how this foundation is layered first, read:

- [Why AI4J](/docs/getting-started/why-ai4j)
- [Architecture at a Glance](/docs/reference/maps/architecture-at-a-glance)

---

## 3. I want to jump straight to the local coding agent and skip the SDK

Go straight to:

1. [Coding Agent overview](/docs/products/coding-agent/overview)
2. [Coding Agent quickstart](/docs/products/coding-agent/quickstart)
3. [Release, install, and GitHub Release](/docs/products/coding-agent/install-and-release)
4. [CLI / TUI usage guide](/docs/products/coding-agent/cli-and-tui)

---

## 4. How should I choose between `Chat` and `Responses`

Prefer `Chat` when:

- You want to wire up the most mature standard chat path first
- You're more comfortable with a message-style API
- Your focus is Tool / Function calls

Prefer `Responses` when:

- You need a finer-grained event stream
- You need to consume reasoning / output items / function args
- You're building a next-generation agent runtime

See:

- [Chat vs Responses](/docs/capabilities/models/chat-vs-responses)

---

## 5. How do `Function Call` and `Tool` relate

In the AI4J docs, the most common first Tool path is the local `Function Call`.

Think of it this way:

- `Function Call` is a typical way to expose a local Tool
- `Tool` is a slightly broader concept of an invocable capability

If you just want to grasp the first tool path first, read:

- [First Tool Call](/docs/getting-started/first-tool-call)
- [Core SDK / Tools](/docs/capabilities/tools/overview)

---

## 6. How do `MCP` and `Agent` relate

In short:

- `MCP` answers "how do I plug in external capabilities?"
- `Agent` answers "how do I run the reasoning loop and orchestration?"

MCP can be a source of tools for an agent, but MCP is not the same as Agent.

---

## 7. How do `Agent` and `Coding Agent` relate

`Coding Agent` is not an alias for the general agent framework.

Think of it as:

- `Agent` is the framework layer
- `Coding Agent` is the product layer built for interacting with local code repositories

If you're implementing your own agent in a business system, start with `Agent`.
If you want to use it directly as a local coding assistant, start with `Coding Agent`.

---

## 8. What's the difference between `Skill` and `Tool`

`Skill`:

- Usually a `SKILL.md`
- Task instructions, templates, workflow guidance
- The model has to read it before using it

`Tool`:

- A structured invocable capability
- Has a schema and an executor
- Can be called directly by the model

---

## 9. What's the difference between `ACP` and `MCP`

`ACP`:

- The host integration protocol for `Coding Agent`
- For IDEs / desktop apps / frontends to connect to `ai4j-cli acp`

`MCP`:

- The protocol layer for models to reach external capabilities
- For wiring and publishing Tools / Resources / Prompts

The two are not the same kind of protocol.

---

## 10. I want to add a new model platform — where do I look

Start with:

1. [Provider extension](/docs/extending/code-level/provider-extension)
2. [Model extension](/docs/extending/code-level/model-extension)

If you're only switching to a new model name under an existing provider, you usually don't need to modify SDK source.

---

## 11. I want to integrate a third-party MCP server — where do I start

For 1–2 services, start with:

- [MCP Client Integration](/docs/capabilities/mcp/client-integration)

For unified management of multiple services, start with:

- [Gateway Management](/docs/capabilities/mcp/gateway-management)
- [Tool Exposure Semantics](/docs/capabilities/mcp/tool-exposure-semantics)

---

## 12. How should I choose between `FlowGram` and `Agent`

Prefer `FlowGram` when:

- The task is naturally a node graph
- The frontend draws the flow
- Input/output schemas must be stable

Prefer `Agent` when:

- Multi-turn reasoning is needed
- The model needs to decide the tool-call path itself
- You need SubAgent / Teams / Trace governance

---

## 13. I want to run a FlowGram demo first

Go straight to:

1. [FlowGram overview](/docs/products/flowgram/overview)
2. [FlowGram quickstart](/docs/products/flowgram/quickstart)

---

## 14. What about legacy paths or migration pages in the docs

The site is still being consolidated from the old structure into the new topic-based structure.

If you hit a migration page, follow the canonical entry point it points to and keep reading; don't bounce back and forth between old and new pages at the same time.

---

## 15. Should I start with the FAQ or the glossary

If you're stuck on "which path should I take?", read the FAQ first.

If you're stuck on "what do these terms actually mean?", see:

- [Glossary](/docs/reference/glossary)
