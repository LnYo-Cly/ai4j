---
sidebar_position: 7
title: "Agent, Tool, Knowledge Base, and MCP Integration"
description: "Explains the capability wirings FlowGram ships built-in today (LLM/TOOL/KNOWLEDGE) versus those that can only be extended (Agent/MCP have no dedicated built-in nodes), and traces the real wiring behind the TOOL node reusing the tool bus and the KNOWLEDGE node reusing the RAG abstraction."
tags: [concept]
---

# Agent, Tool, Knowledge Base, and MCP Integration

This page is dedicated to a question that is too easily hand-waved:

- Which capability wirings FlowGram currently ships built-in
- Which capabilities are merely "extensible" but have no built-in node yet

The most important conclusion, up front:

:::note Agent and MCP have no dedicated built-in nodes
- `LLM`, `TOOL`, `KNOWLEDGE` already have official wirings today
- `Agent` has no dedicated built-in node
- `MCP` has no dedicated built-in node

Do not write "theoretically connectable" as "currently supported out of the box".
:::

## 1. The `LLM` node: wire models through the AI4J service registry

The `LLM` node does not bypass AI4J to talk directly to model services.

The starter registers these by default:

- `Ai4jFlowGramLlmNodeRunner`
- `RegistryBackedFlowGramModelClientResolver`

### 1.1 How the model client is resolved

`RegistryBackedFlowGramModelClientResolver` looks up the service identifier in this order:

- `serviceId` from the node inputs
- `aiServiceId` from the node inputs
- `ai4j.flowgram.default-service-id`

If none of the three is present, it throws immediately.

This means the FlowGram LLM node is not "just fill in a modelName". It depends on both:

- Which chat service
- Which model under that service

### 1.2 What you gain from this

The benefits are concrete:

- FlowGram does not maintain its own provider adapters
- Model switching still goes through the unified AI4J service registry layer
- The frontend node protocol stays decoupled from the underlying provider

### 1.3 What belongs in an LLM node

A good fit:

- Summarization, rewriting, extraction, classification
- Generation after retrieval
- Single-node intelligence steps

Not a good fit:

- Stuffing the entire flow graph into one giant prompt
- Pushing multi-step tool strategy back inside an LLM node

## 2. The `TOOL` node: turn the tool bus into a workflow capability

The `TOOL` node is currently driven by:

- `FlowGramToolNodeExecutor`

### 2.1 Input contract

Required at minimum:

- `toolName`

Optional:

- `argumentsJson`

If `argumentsJson` is not supplied explicitly, the executor serializes the remaining inputs as a whole into JSON and uses it as the tool arguments.

### 2.2 How the execution chain runs

The current logic is:

1. Try the built-in demo tool first
2. Otherwise route through `ToolUtilExecutor`
3. Build an `AgentToolCall`
4. After execution, write both the raw output and the parsed result back to the node outputs

### 2.3 Why the output is more useful than it looks

The current output includes not only:

- `toolName`
- `rawOutput`
- `result`

If the tool output can be parsed into a JSON map, it additionally provides:

- `data`
- Flattened fields from the map

This lets downstream nodes reference tool fields directly, instead of manually parsing a string layer first.

### 2.4 When to prefer TOOL over HTTP

Prefer `TOOL` when:

- You already have AI4J / Java tool capabilities
- You want to reuse unified tool governance
- You want clearer parameter and return semantics

Prefer `HTTP` when:

- The capability is already a remote HTTP service
- You only need the thinnest possible remote call bridge

## 3. The `KNOWLEDGE` node: collapse the RAG pipeline into a proper node

The `KNOWLEDGE` node is currently implemented by:

- `FlowGramKnowledgeRetrieveNodeExecutor`

### 3.1 It is not registered unconditionally

The starter auto-registers `FlowGramKnowledgeRetrieveNodeExecutor` only when both:

- `AiServiceRegistry`
- A single `VectorStore`

are present.

This means it is a "conditional built-in capability", not a default that is available in every environment.

### 3.2 The input contract is explicit

Required at minimum:

- `serviceId`
- `embeddingModel`
- `dataset` or `namespace`
- `query`

Optional:

- `topK`, default `5`
- `finalTopK`, defaults to `topK`
- `delimiter`, default `\n\n`
- `filter`

### 3.3 Under the hood it is not bound to any one vector store

From the implementation, the node internally goes through:

- `IEmbeddingService`
- `DenseRetriever`
- `RagService`
- `VectorStore`
- `Reranker`
- `RagContextAssembler`

This means the backend can be a different vector store, without the node protocol having to change.

### 3.4 The output is far richer than "return a context"

Currently it returns at least:

- `matches`
- `hits`
- `context`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`
- `count`

So the `KNOWLEDGE` node is not "fetch a piece of text and hand it to the LLM". It already preserves retrieval evidence, rerank traces, and citation information.

### 3.5 Recommended way to consume these outputs

A saner layering is:

- `context` goes to the downstream `LLM` node
- `citations` / `sources` go to the user-visible sources panel
- `trace` / `retrievedHits` / `rerankedHits` go to an internal debug panel

This way you can ship product-grade answers without losing the debug evidence chain.

## 4. `Agent` currently has no dedicated built-in node

This must be stated very clearly.

The starter currently ships no default:

- `AGENT` node
- ReAct-specific node
- CodeAct-specific node
- Agent Team-specific node

So the sentence "FlowGram can run Agents" is inaccurate without extra context.

### 4.1 More realistic wiring today, option 1: custom node

The most recommended approach is:

- Implement `FlowGramNodeExecutor` yourself
- Inside the node, invoke a ReAct Agent, CodeAct Runtime, Agent Team, or another Agent runtime

The benefits:

- The input/output contract stays under your control
- The frontend does not need to understand Agent internals
- The platform can still apply unified permission, logging, and task governance

### 4.2 More realistic wiring today, option 2: HTTP to an external Agent service

If your Agent is already a standalone service, you can also:

- Call it directly via the `HTTP` node

This is cheap to adopt, but the trade-offs are clear:

- Weaker type constraints
- Weaker node-level observability
- Harder for the platform to sense finer-grained Agent internal state

## 5. `MCP` also has no dedicated built-in node

To be equally clear:

- There is no "out-of-the-box MCP node" today
- There is no starter-level auto-configuration that "auto-converts an MCP server into FlowGram nodes"

This does not mean MCP cannot be wired in. It means you decide the integration layer yourself.

### 5.1 Recommended wiring, option 1: wrap as a Tool first, then go through the `TOOL` node

This is the most stable approach today.

The path is:

1. Wrap the MCP call as a Java tool capability on the server side
2. Let the `TOOL` node call it

Advantages:

- The frontend node protocol stays stable
- The platform does not need to understand the MCP protocol directly
- Permission, logging, timeout, and retry governance is easier to unify

### 5.2 Recommended wiring, option 2: build a custom node directly

If you genuinely need stronger control over MCP parameters and behavior, you can:

- Customize a `FlowGramNodeExecutor`
- Call the MCP client directly inside the executor

This is more flexible, but you also own:

- The node schema
- Error handling
- Timeout / retry
- Security boundaries

## 6. A few more stable composition patterns

The compositions that are relatively stable today are usually:

- `KNOWLEDGE -> LLM`
- `TOOL -> LLM`
- `HTTP -> VARIABLE -> LLM`
- `custom agent node -> END`

What these share: they keep complex capabilities behind nodes, rather than exposing complex protocols directly to the frontend canvas.

## 7. When to turn a capability into a node, and when not to

### Better as a node

- The input/output needs to stay stable long-term
- The capability will be reused across multiple flows
- You want observation, audit, and governance at the node level

### Not necessarily a node

- It is a one-off experiment
- The capability does not have stable inputs/outputs yet
- You are not sure whether it belongs to Tool, HTTP, or Agent behavior

The more platform-oriented a node is, the more it should be contract first, not implementation first.

## 8. How to describe the current capability map accurately

An accurate description is:

- `LLM` node: built-in, reuses the AI4J model service registry
- `TOOL` node: built-in, reuses the tool call chain
- `KNOWLEDGE` node: conditionally built-in, reuses the RAG abstraction
- `Agent`: extensible, but no dedicated built-in node
- `MCP`: extensible, but no dedicated built-in node

This framing lets users clearly distinguish:

- What the starter has already done for them
- Where they still need to implement it themselves

## 9. One design judgment call that matters most

The FlowGram line should not expose every lower-layer protocol verbatim to the frontend canvas.

A better approach is usually:

- Collapse complex capabilities like provider, Tool, RAG, MCP, and Agent into backend contracts first
- Then expose them to the frontend as stable node types

This way the canvas faces platform nodes, not a patchwork of backend protocols.
