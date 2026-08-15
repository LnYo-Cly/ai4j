---
title: "Package Map"
description: "Maps the source layout of the ai4j module using a package-cluster mental model, identifies the responsibilities of primary capability surfaces and supporting-layer packages, and suggests a recommended order for reading the source."
tags: [concept]
---

# Package Map

This page covers the package-level mental model of the `ai4j/` module.

Its goal is not to list every class, but to answer three questions first:

- How the `Core SDK` code is broadly layered in the source
- Which packages you should look at first when reading the source
- Which packages are primary capability surfaces and which are more of a supporting layer

## 1. Start by remembering the source root path

The module corresponding to `Core SDK` is:

- `ai4j/`

The main source root path is:

- `ai4j/src/main/java/io/github/lnyocly/ai4j/`

So all packages discussed in this chapter live under this root path by default.

## 2. The package clusters worth looking at first

Inside the `ai4j` module, the most important packages are better understood as "clusters" rather than by individual directory name:

- `service` + `service.factory`: unified entry point, configuration objects, platform enums, service factory and registry
- `platform`: concrete adapter implementations for each provider
- `tool` + `tools`: local tool declaration, bridging, built-in tools and execution semantics
- `skill`: Skill description, discovery and loading
- `mcp`: MCP client / gateway / server / transport
- `memory`: foundational session context capability
- `rag` + `vector` + `rerank` + `websearch` + `document`: the knowledge-augmentation chain

## 3. What the supporting-layer packages are roughly responsible for

Beyond the primary capability surfaces, `ai4j` contains a set of more support-oriented packages:

- `config`: platform configuration objects
- `network`: HTTP, connections and underlying network capabilities
- `auth`: authentication-related capabilities
- `interceptor`: request/response interception extension points
- `annotation`: annotation-driven capability exposure
- `listener`: streaming or event listening helpers
- `convert`: object conversion and adaptation helpers
- `exception`: unified exception definitions
- `constant`, `token`: constants and tokenization/token-related helpers

These packages matter, but they are usually not the first landing point on your first read of the source.

## 4. How to make sense of each primary package cluster

### 4.1 `service` + `service.factory`

This is the unified entry-point layer of the `Core SDK`.

If you want to look first at:

- `Configuration`
- `PlatformType`
- `AiService`
- `AiServiceRegistry`

start by descending from here.

### 4.2 `platform`

This is the provider landing layer.

It answers:

- How each platform such as OpenAI, DashScope, Doubao, or Ollama is adapted
- Which capability interfaces already have concrete implementations

### 4.3 `tool` + `tools`

This is the local invocable capability surface.

It is not just a list of tools; it includes:

- tool declaration
- schema exposure
- execution semantics
- tool security boundaries

### 4.4 `skill`

This is the descriptive-asset layer.

Its focus is not execution, but rather:

- which skill to discover
- when to load it
- how to bring resources such as `SKILL.md` into the model context

### 4.5 `mcp`

This is the protocolized external-capability layer.

It sits at the same level as `tool`, not as a subdirectory of `tool`, because it also involves:

- transport
- client
- gateway
- server
- tool/resource/prompt exposure

### 4.6 `memory`

This is the foundational session context of the base layer, not the full state machine of the upper-layer runtime.

### 4.7 `rag` + `vector` + `rerank` + `websearch` + `document`

This is the knowledge-augmentation main line.

If you trace the whole chain, you will typically see:

- ingestion and chunking
- embedding
- vector store
- rerank
- online search
- citations / trace-related semantics

## 5. Recommended order for reading the source

If you want to read the source, the suggestion is:

1. `service` / `service.factory`
2. `platform`
3. `tool` / `tools`
4. `skill`
5. `mcp`
6. `memory`
7. `rag` / `vector` / `rerank` / `websearch`

This lets you build the main line of "entry-point layer -> capability surface -> knowledge augmentation" first, so you do not conflate the `Core SDK` with the upper-layer runtime.

## 6. Boundary with upper-layer modules

If, as you read, you start to see:

- runtime step loop
- subagent / team orchestration
- workspace-aware tools
- CLI / TUI / ACP
- Flowgram node-graph execution

that is usually no longer the `Core SDK` layer itself, but rather:

- `ai4j-agent`
- `ai4j-coding`
- `ai4j-cli`
- `ai4j-flowgram-spring-boot-starter`
