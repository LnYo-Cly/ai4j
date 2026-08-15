---
title: "Architecture and Module Map"
description: "Maps the AI4J repository's Maven module mainline, dependency direction, and code-location entry points onto real engineering, helping you find the right landing point when reading source code or evaluating module boundaries."
tags: [concept]
---

# Architecture and Module Map

This page grounds the documentation's "layering" directly onto the repository's real modules.

If you plan to read source code, write architectural descriptions, or evaluate module boundaries, this page should help you answer three questions:

1. Which modules actually exist in the repository
2. What the rough dependency direction between modules is
3. Where you should enter first

## 1. Start with the real module map

The Maven module mainline under the root `pom.xml` is:

```text
ai4j-sdk
├─ ai4j-extension-api
├─ ai4j-plugin-ask-user
├─ ai4j
├─ ai4j-agent
├─ ai4j-coding
├─ ai4j-cli
├─ ai4j-spring-boot-starter
├─ ai4j-flowgram-spring-boot-starter
├─ ai4j-flowgram-demo
└─ ai4j-bom
```

The repository also contains two important but qualitatively different directories:

- `docs-site/`: the documentation site source, not a Maven business module
- `ai4j-flowgram-webapp-demo/`: the frontend canvas demo directory, currently not in the root Maven modules list

## 2. What each module solves

### 2.1 `ai4j-extension-api`

This is the lightweight common contract module for the plugin ecosystem.

It is responsible for:

- extension manifest
- ServiceLoader discovery
- enable / expose gating
- the neutral spec for tool, command, Skill, Prompt, and Guardrail
- validators reusable by plugin authors

### 2.2 `ai4j-plugin-ask-user`

This is the official sample plugin module.

It demonstrates how a complete plugin package should be organized:

- `Ai4jExtension` implementation
- `META-INF/services` registration
- `ask_user` tool
- `ask-user` command
- Skill / Prompt distributed with the jar
- validator and ServiceLoader regression tests

It does not open a UI or block waiting for user input; it only returns a question-request envelope that the host can recognize.

### 2.3 `ai4j`

This is the sole `Core SDK` module and the foundational capability base for the entire repository.

It is responsible for:

- model access
- `Tools`
- `Skills`
- `MCP`
- `ChatMemory`
- RAG and retrieval augmentation
- provider / service / network extensions

### 2.4 `ai4j-spring-boot-starter`

Puts `ai4j` into the Spring Boot container.

It is responsible for:

- auto-configuration
- configuration binding
- Bean-level extensions

### 2.5 `ai4j-agent`

Adds a general-purpose agent runtime on top of `ai4j`.

It is responsible for:

- `ReAct`, `CodeAct`, `DeepResearch`
- runtime step loop
- tool registry / executor
- agent memory
- subagent / team / workflow / trace

### 2.6 `ai4j-coding`

Adds a runtime for code-repository tasks on top of `ai4j` and `ai4j-agent`.

It is responsible for:

- workspace-aware tools
- outer loop
- compact / checkpoint
- session / process / prompt assembly
- coding-task-related strategies

### 2.7 `ai4j-cli`

This is the product shell of the `Coding Agent`.

It is responsible for:

- CLI
- TUI
- ACP host
- distribution artifacts and interactive entry points

### 2.8 `ai4j-flowgram-spring-boot-starter`

This is the backend starter for the visual node-workflow platform.

It is responsible for:

- Flowgram runtime integration
- built-in node execution support
- task API
- composition with `Spring Boot` and `Agent` capabilities

### 2.9 `ai4j-flowgram-demo`

This is the sample project for the Flowgram starter, used to demonstrate backend integration and debugging paths.

### 2.10 `ai4j-bom`

Used for version alignment across multi-module projects; suitable for teams that want to manage versions centrally when adopting multiple AI4J modules.

## 3. How to read the dependency direction

A fairly clear mainline can be read from the current module `pom.xml` files:

```text
ai4j-plugin-ask-user       -> ai4j-extension-api
ai4j-agent                  -> ai4j
ai4j-coding                 -> ai4j + ai4j-agent
ai4j-cli                    -> ai4j + ai4j-coding
ai4j-spring-boot-starter    -> ai4j
ai4j-flowgram-spring-boot-starter
                             -> ai4j-agent + ai4j-spring-boot-starter
ai4j-flowgram-demo          -> ai4j-flowgram-spring-boot-starter
```

This dependency direction tells you three things:

1. `ai4j` is the true foundational core
2. `ai4j-extension-api` is the lightweight common contract for the plugin ecosystem and does not depend back on the runtime
3. Neither the `Coding Agent` nor `Flowgram` appears out of thin air; they are runtimes or platform layers stacked on top

## 4. Where to go first when locating code

If your goal is to:

- Look at foundational model and capability integration: start with `ai4j`
- Look at the plugin contract and third-party plugin development: see `ai4j-extension-api`
- Look at the official plugin sample: see `ai4j-plugin-ask-user`
- Look at Spring auto-configuration: see `ai4j-spring-boot-starter`
- Look at the general-purpose agent runtime: see `ai4j-agent`
- Look at local code-repository tasks and continuous sessions: see `ai4j-coding`
- Look at the CLI / TUI / ACP product entry points: see `ai4j-cli`
- Look at the visual workflow platform backend: see `ai4j-flowgram-spring-boot-starter`

This is more efficient than blind directory search and better matches the repository's real layering.

## 5. Mapping to the documentation reading order

Recommended reading order for cross-reference:

1. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
2. [Core SDK / Model Access](/docs/core-sdk/model-access/overview)
3. [Core SDK / Tools](/docs/core-sdk/tools/overview)
4. [Core SDK / Skills](/docs/core-sdk/skills/overview)
5. [Core SDK / MCP](/docs/mcp/overview)
6. [Spring Boot / Overview](/docs/spring-boot/overview)
7. [Extension / Plugin Packages](/docs/core-sdk/extension/plugin-packages)
8. [Extension / Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
9. [Agent / Overview](/docs/agent/overview)
10. [Coding Agent / Overview](/docs/coding-agent/overview)
11. [Flowgram / Overview](/docs/flowgram/overview)

If you want to drill further from code structure into package structure next, see [Package Map](/docs/core-sdk/package-map).

## 6. Two neighboring directory types to watch for

Beyond the main Maven modules, the repository has two directories whose role is easy to misjudge when reading:

- `docs-site/`: it carries the documentation system, not production logic
- `ai4j-flowgram-webapp-demo/`: it is the frontend demo surface, used to complement the FlowGram backend experience, and is not part of the Java core runtime

Keeping these two directory types distinct from the Maven module mainline avoids mistaking "documentation site structure" or "frontend demo structure" for backend production layering.
