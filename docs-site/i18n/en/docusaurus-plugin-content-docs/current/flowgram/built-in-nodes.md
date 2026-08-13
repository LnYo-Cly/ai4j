---
title: "Built-in Nodes"
description: "Distinguishes Flowgram runtime kernel nodes (START/END/LLM/CONDITION/LOOP) from the executor nodes registered by the starter (HTTP/VARIABLE/CODE/TOOL/KNOWLEDGE), and clarifies the shared value-resolution model and the inputs and outputs of each node."
tags: [concept]
---

# Built-in Nodes

The most important thing on this `Built-in Nodes` page is not to enumerate node names, but to make clear which capabilities belong to the runtime kernel and which belong to the executors registered by the starter.

If you do not understand this boundary, you will easily put extension points in the wrong place when you later build custom nodes.

## 1. First, separate the two kinds of built-in nodes

What we currently call "built-in nodes" actually spans at least two layers.

### 1.1 Runtime kernel nodes

These types are understood directly by `FlowGramRuntimeService`:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

They represent flow structure and core control semantics.

### 1.2 Starter-registered nodes

These types are typically injected through `FlowGramNodeExecutor`:

- `VARIABLE`
- `HTTP`
- `CODE`
- `TOOL`
- `KNOWLEDGE`

They represent business capabilities and integration capabilities.

This distinction matters because:

- The former must be understood by the runtime kernel
- The latter can in principle be replaced, extended, or removed

## 2. The input-resolution model shared by all executor nodes

Many nodes look like they have different input structures, but they all share one core capability: value resolution.

### 2.1 `FlowGramNodeValueResolver`

Several executors on the starter side use `FlowGramNodeValueResolver` to parse input values. It supports several expression kinds:

- `REF`
- `CONSTANT`
- `TEMPLATE`
- `EXPRESSION`

### 2.2 What it can reference

During resolution, values can be taken from these root objects:

- `locals`
- `inputs` / `taskInputs` / `$inputs`
- the current node's already-resolved inputs
- outputs of previous nodes

This means node configuration is not dead JSON, but a lightweight expression layer with runtime reference capability.

### 2.3 Why this matters

This explains how Flowgram node configuration can:

- pull results from earlier nodes
- do template interpolation
- pass local variables
- let an HTTP / Tool / Code node reuse upstream output

Without this resolver layer, nodes could only pass data between each other through hardcoded fields.

## 3. `START` / `END`: structural boundaries, not business nodes

### `START`

The point of `START` is not "to do work", but rather to:

- define the root-graph entry point
- receive the task inputs
- provide the initial context for the whole execution chain

The root graph must have exactly one `Start`, otherwise validation fails outright.

### `END`

The point of `END` is to:

- define the formal closing point of the workflow
- produce the final result

The root graph needs at least one `End`. If execution finishes without reaching any `End`, the runtime treats the whole task as failed.

## 4. `LLM`: a constrained intelligent node

The `LLM` node is driven by `Ai4jFlowGramLlmNodeRunner`.

### Input requirements

At minimum it needs:

- a model name: `modelName` / `model` / `modelId`
- a prompt: `prompt` / `message` / `input`

It also optionally supports:

- `systemPrompt`
- `instructions`
- `temperature`
- `topP`
- `maxOutputTokens`

### Default behavior

- an Agent is created dynamically on each node execution
- the default runtime is `ReActRuntime`
- default `maxSteps(1)`
- default `stream(false)`

### Output structure

- `result`
- `outputText`
- `rawResponse`
- `metrics`

So this node is better suited to "single-step intelligent processing" than to carrying an entire complex business flow.

## 5. `CONDITION` / `LOOP`: control-flow nodes

The value of these two node types is that they keep "flow structure" in the graph, rather than stuffing it into a prompt or into code.

### `CONDITION`

It is responsible for choosing the next edge based on a condition, so it belongs to runtime-level control semantics and is not appropriate to demote to an ordinary executor.

### `LOOP`

`LOOP` is not just a label. The runtime recursively executes its block subgraph and applies the same structural validation to the loop subgraph.

This shows that loop is a first-class semantic in the current implementation, not a front-end display effect.

## 6. `VARIABLE`: lightweight assignment and field shaping

`FlowGramVariableNodeExecutor` behaves more specifically than its name suggests.

### Input form

It reads an `assign` list, where each entry is typically:

- `left`
- `right`

### Run behavior

- first resolves `assign` with `FlowGramNodeValueResolver`
- treats each `left` as an output key
- writes the corresponding `right` into outputs

### An easily overlooked detail

If in the end no assign produces output, and the current node has inputs, it copies the current inputs wholesale into the outputs.

This means `VARIABLE` is not only "define a new variable" — it can also act as a lightweight field pass-through / reshaping node.

## 7. `HTTP`: the external-call node inside a workflow

`FlowGramHttpNodeExecutor` is a typical "integration node".

### Required inputs

At minimum it needs:

- `api.url`

### Default behavior

- default method `GET`
- default timeout `10000ms`
- default retry at least `1` time

### Configurable items

- `headersValues`
- `paramsValues`
- `timeout.timeout`
- `timeout.retryTimes`
- `body.bodyType`
- `body.json`
- `body.rawText`

### Request body support

Currently it mainly supports:

- `JSON`
- `raw-text`

### Output structure

- `statusCode`
- `body`
- `headers`
- `contentType`

So it is better suited to "wiring the flow into an external system" than to carrying complex business orchestration itself.

### 7.1 SSRF protection: `HttpNodeSsrfGuard` {#ssrf-guard}

Before sending a request, the HTTP node first runs it through an `HttpNodeSsrfGuard` (`ssrfGuard.validate(fullUrl)`) to prevent the workflow from being coaxed into accessing the internal network. This is enabled by default and needs no extra configuration.

How it works:

- It parses the host out of the URL and calls `InetAddress.getAllByName(host)` to do a real DNS lookup.
- It then judges against the concrete resolved IP, not just the host string — this is what blocks DNS rebinding: a domain that looks public but resolves to an internal IP will be intercepted.
- Hitting any of the ranges below throws `HttpNodeSsrfGuard.SsrfBlockedException` (fail-closed):
  - loopback: `127.0.0.0/8`, `::1`
  - private networks: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, IPv6 `fc00::/7`
  - link-local: `169.254.0.0/16` (including the cloud metadata endpoint `169.254.169.254`), `fe80::/10`
  - carrier-grade NAT: `100.64.0.0/10`
- If the host cannot be resolved (`UnknownHostException`), it does not intercept; it lets the HTTP client fail naturally.

### 7.2 Disabling SSRF protection (internal-network scenarios only)

Only when your HTTP node genuinely needs to reach internal services should you explicitly disable it:

```yaml
ai4j:
  flowgram:
    http-node:
      allow-private-network: true
```

Once set to `true`, the guard emits a warn log and then lets the request through. This switch is injected into `FlowGramHttpNodeExecutor` via `FlowGramProperties.httpNode.allowPrivateNetwork`; in non-Spring scenarios it can also be set with the system property `ai4j.flowgram.http-node.allow-private-network=true`. The production default is `false` — do not turn it on lightly.

:::warning Disabling means giving up this layer of protection
`allow-private-network=true` is equivalent to re-exposing the workflow's HTTP node to SSRF. Use it only in a trusted internal-network topology where restrictions are already enforced upstream (gateway / network policy).
:::

## 8. `CODE`: a lightweight programmable node

`FlowGramCodeNodeExecutor` currently defaults to running scripts on top of `NashornCodeExecutor`.

### Input requirements

At minimum it needs:

- `script.content`

Optional:

- `script.language`, default `javascript`

### Execution constraints

- timeout is fixed at `8000ms`
- the runtime injects `params`
- it supports returning a `main(__flowgram_input)` result
- it also supports reading the global `ret`
- `async main()` is currently explicitly unsupported

### Output structure

- the script return value is parsed as JSON if possible
- if stdout exists, it is also written to `stdout`

This node fits stable, local, short-lived rule processing; it is not meant to carry a large business-script platform.

## 9. `TOOL`: wiring the tool bus into the workflow

`FlowGramToolNodeExecutor` is positioned to expose existing tool capabilities as a node.

### Required input

- `toolName`

### Parameter source

It reads, in priority order:

- `argumentsJson`

If that is absent, it serializes the rest of the input wholesale into the arguments JSON.

### Execution path

- first it tries to execute a built-in demo tool
- otherwise it goes through `ToolUtilExecutor`

### Output structure

- `toolName`
- `rawOutput`
- `data`
- `result`

If the tool output is itself a JSON map, it further flattens the fields into outputs.

This lets downstream nodes reference tool-result fields directly, instead of parsing a string themselves every time.

## 10. `KNOWLEDGE`: the RAG retrieval node

`FlowGramKnowledgeRetrieveNodeExecutor` is the node type where Flowgram and the AI4J RAG foundation are most deeply combined.

### Registration condition

This node does not exist unconditionally. The starter auto-registers it only when both of the following are present:

- `AiServiceRegistry`
- a single `VectorStore`

### Required inputs

- `serviceId`
- `embeddingModel`
- `dataset` or `namespace`
- `query`

### Optional inputs

- `topK`, default `5`
- `finalTopK`, default equals `topK`
- `delimiter`, default `\n\n`
- `filter`

### Output structure

- `matches`
- `hits`
- `context`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`
- `count`

It is not as simple as "return a context string"; it preserves retrieval, rerank, citation, and trace information, which suits workflow nodes that need an evidence chain.

## 11. When you should move on to writing a custom node

After going through these built-in nodes, then judge whether you really need to extend.

Cases where a custom node is more appropriate:

- the logic is a stable rule and does not fit being stuffed into an LLM
- you need to integrate an enterprise internal system
- the input/output schema needs to stay fixed long-term
- you want this capability to become a platform-level reusable node

Cases where you should not rush to customize:

- you just have not composed the existing nodes well yet
- the real problem is a front/back-end schema mismatch
- you only want a one-off simple field conversion

## 12. The single most important principle

In Flowgram, nodes should not be classified by "what they look like", but rather by "whether they carry control semantics or business capability".

That is also why:

- `START` / `END` / `CONDITION` / `LOOP` stay in the runtime
- `HTTP` / `CODE` / `TOOL` / `KNOWLEDGE` go through executors

Once you grasp this layering, the extension path that follows will not go off track.
