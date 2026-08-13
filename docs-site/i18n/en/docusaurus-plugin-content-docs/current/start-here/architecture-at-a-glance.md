---
title: "Architecture at a Glance"
description: "Build a mental model of AI4J with a four-layer diagram: Start Here, Core SDK, upper modules (Spring Boot/Agent/Coding Agent/FlowGram), and Solutions, and clarify the boundaries between the three most easily confused concepts: Function Call, Skill, and MCP."
tags: [concept]
---

# Architecture at a Glance

This page is not about details. Its goal is to first help you build a coherent big picture.

If you can keep this diagram in mind, then when you later read `Core SDK`, `Agent`, `Coding Agent`, and `FlowGram`, you won't feel these modules are just "a pile of features".

## 1. First, remember this four-layer diagram

```text
Start Here
  Handles orientation, path selection, and the first successful path

Core SDK
  Unified model calls / Tools / Skills / MCP / Memory / Search & RAG / Extension
  Corresponding main module: ai4j

Upper Modules
  Spring Boot         -> ai4j-spring-boot-starter
  Agent               -> ai4j-agent
  Coding Agent        -> ai4j-coding + ai4j-cli
  FlowGram            -> ai4j-flowgram-spring-boot-starter

Solutions
  Scenario solutions, examples, end-to-end composition patterns
```

The most important point: `Core SDK` is the backbone of the whole system. Every other module grows upward from this layer.

## 2. What each layer is responsible for

### 2.1 Start Here

It only handles three things:

- Explaining what AI4J is
- Helping you choose a reading path
- Walking you through the first successful path

It does not replace the full reference documentation.

### 2.2 Core SDK

This is the single foundation layer of AI4J, and the most important layer in the entire docs site.

It answers:

- How model calls are unified
- How local tools are declared and executed
- How `Skill`s are organized and loaded
- How `MCP` connects external capabilities
- How session memory, RAG, and web augmentation fit in
- How providers, services, and the network stack are extended

If you don't understand this layer clearly, then `Agent`, `Coding Agent`, and `FlowGram` will easily read as "a scattered collection of capabilities".

### 2.3 Upper Modules

The upper modules don't start from scratch. They solve more specific problems on top of `Core SDK`:

- `Spring Boot`: wires the foundation capabilities into container, configuration, and auto-configuration
- `Agent`: adds runtime, memory, tool loop, orchestration, and tracing
- `Coding Agent`: adds workspace-aware tools, session runtime, and CLI / TUI / ACP hosting
- `FlowGram`: adds backend execution and platform integration for visual node workflows

### 2.4 Solutions

`Solutions` does not define foundational concepts. It tells you how these capabilities combine by scenario to ship.

So the reading order should be:

1. First build structural sense
2. Then read the foundation
3. Then look at the upper modules
4. Finally look at the examples

## 3. How modules map to real repository paths

From the repository structure, the main paths read like this:

```text
ai4j-sdk                    Parent POM / multi-module release entry
ai4j                        Core SDK
ai4j-spring-boot-starter    Spring Boot integration layer
ai4j-agent                  General Agent runtime
ai4j-coding                 Coding Agent runtime
ai4j-cli                    CLI / TUI / ACP host
ai4j-flowgram-spring-boot-starter
                            FlowGram backend integration layer
ai4j-flowgram-demo          FlowGram example project
ai4j-bom                    Version alignment
docs-site                   Docs site source
```

In other words, the layering in the docs is not an "abstract concept" — every layer maps to a module you can find directly in the current repository.

## 4. The three most easily confused concepts

This is the most critical boundary question on the whole site.

### 4.1 Function Call

`Function Call` answers "how local tools are exposed for the model to call".

It belongs to:

- [Core SDK / Tools](/docs/core-sdk/tools/overview)

### 4.2 Skill

`Skill` answers "what instructions, templates, and methodology resources the model reads on demand".

It belongs first to:

- [Core SDK / Skills](/docs/core-sdk/skills/overview)

In `Coding Agent`, it further becomes a productized skill discovery and loading capability.

### 4.3 MCP

`MCP` answers "how the model connects to external capability systems through a standard protocol".

It belongs to:

- [MCP](/docs/mcp/overview)

:::note Boundary between MCP and Tools
Note: `MCP` and `Tools` are closely related, but `MCP` is not a subdirectory of `Tools`. `MCP` also covers protocol-layer concerns such as transport, gateway, server publish, and tool exposure semantics.
:::

## 5. By goal, where you should enter

If your goal is:

- Send your first model request: start at `Start Here -> Quickstart -> Core SDK / Model Access`
- Integrate Spring Boot: start at `Spring Boot`
- Build an agent runtime: start at `Agent`
- Turn a local repository directly into a coding assistant: start at `Coding Agent`
- Build a visual node platform: start at `FlowGram`

But no matter which path you take, you cannot get around `Core SDK`.

## 6. Next step

If you already know what kind of thing you want to do, continue to [Choose Your Path](/docs/start-here/choose-your-path).

If you want to fully digest the foundation storyline first, the next page to read is [Core SDK / Overview](/docs/core-sdk/overview).
