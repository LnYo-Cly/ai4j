---
sidebar_position: 10
title: "Cookbook: Weather Analysis Two-Agent Workflow"
description: "Uses a weather-analysis two-agent workflow to clarify SequentialWorkflow's outputText relay, the WorkflowContext side channel, per-node session isolation, and minimal observation via NamedNode, plus when to upgrade to StateGraphWorkflow."
tags: [how-to]
---

# Cookbook: Weather Analysis Two-Agent Workflow

This page is not a "good enough to run" demo. It uses a minimal two-node workflow to clarify several key boundaries in `ai4j-agent`:

- How `SequentialWorkflow` propagates node output by default
- The relationship between `WorkflowAgent` and the `AgentSession` held by each node itself
- Why one node uses `ChatModelClient` while the other uses `ResponsesModelClient`
- Why the cookbook still wraps `NamedNode` for start/end logging

Corresponding test source (live-run verified, requires `DOUBAO_API_KEY`):

- [`WeatherAgentWorkflowTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-agent/src/test/java/io/github/lnyocly/agent/WeatherAgentWorkflowTest.java)

## 0. Wire up the workflow first

Two stages: an analysis node (Chat + tool) → a formatting node (Responses + strict JSON). `SequentialWorkflow` relays the previous node's `outputText` as the next node's input by default.

```java
// Analysis node: Chat mainline + queryWeather tool
Agent weatherAgent = Agents.react()
        .modelClient(new ChatModelClient(aiService.getChatService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You are a weather analyst. Always call queryWeather before answering.")
        .instructions("Use queryWeather with the user's location, type=now, days=1.")
        .toolRegistry(Arrays.asList("queryWeather"), null)
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

// Formatting node: Responses mainline, closes off the analysis into strict JSON
Agent formatAgent = Agents.react()
        .modelClient(new ResponsesModelClient(aiService.getResponsesService(PlatformType.DOUBAO)))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You format weather analysis into strict JSON.")
        .instructions("Return JSON with fields: city, summary, advice.")
        .options(AgentOptions.builder().maxSteps(2).build())
        .build();

// Chain into a workflow: NamedNode is just a logging wrapper; RuntimeAgentNode is the actual execution body
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new NamedNode("WeatherAnalysis", new RuntimeAgentNode(weatherAgent.newSession())))
        .addNode(new NamedNode("FormatOutput", new RuntimeAgentNode(formatAgent.newSession())));

WorkflowAgent runner = new WorkflowAgent(workflow, weatherAgent.newSession());
AgentResult result = runner.run(AgentRequest.builder()
        .input("Get the current weather in Beijing and provide advice.")
        .build());
// result.getOutputText() is the JSON produced by the formatting node
```

`NamedNode` is a thin custom wrapper in the test that only does node start/end logging — `SequentialWorkflow` does not require it; `RuntimeAgentNode` is the real execution node.

## 1. What this example actually proves

What the test does is a two-stage sequential flow:

1. The weather analysis node calls a tool and produces a natural-language analysis
2. The formatting node closes the analysis result into strict JSON

This means it is not proving "an agent can look up the weather". It is proving:

- Two agents can have different model clients and different responsibilities
- `SequentialWorkflow` fits a "previous node text output -> next node text input" structure
- The workflow layer can split reasoning and formatting into explicit nodes instead of mixing them in one prompt

## 2. Why these two nodes are split

The role split in the test code is:

- `WeatherAnalysisAgent`: `ChatModelClient + queryWeather`
- `FormatOutputAgent`: `ResponsesModelClient + strict JSON formatting`

The core benefit of this split is not "it looks elegant", but that the runtime boundary is clearer:

- The analysis node owns tool usage and conclusion forming
- The formatting node owns output protocol convergence

If you cram both steps into one agent, common problems become:

- The tool-call prompt and the formatting prompt pollute each other
- When JSON formatting is unstable, it is hard to tell whether the tool analysis is wrong or the formatting stage lost control
- When you want to swap models, you cannot replace only one of the stages

## 3. Why this workflow chooses `SequentialWorkflow`

The test uses:

- `SequentialWorkflow`

rather than `StateGraphWorkflow`.

The reason is direct:

- There is only a fixed linear two-step flow here
- No conditional branches
- No loops
- No fallback nodes

So it is the minimal correct abstraction layer.

Jumping straight to `StateGraphWorkflow` here would only rewrite a linear problem as a graph structure, with no real benefit.

## 4. The default propagation semantics of `SequentialWorkflow` are why this example holds

The key logic in `SequentialWorkflow.executeNodes(...)` is:

- After a node executes, it gets `lastResult`
- As long as `lastResult.getOutputText()` is non-empty
- The new `AgentRequest.input` for the next node is that text

In other words, the default chain only propagates:

- `result.outputText`

It does not automatically forward the previous node's raw structured data, tool results, or extra fields downstream.

This is exactly why the weather example holds:

- The first node's output is the natural-language analysis for the second node to consume

And this is also its boundary:

- If you want to propagate structured context, not just text, you cannot rely on the default relay alone

## 5. The real role of `WorkflowContext` in this example

Source:

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java`

`WorkflowContext` currently holds only a few things:

- `session`
- `state`
- `eventPublisher`

The most important of these is:

- `state` is a side channel

That is, the main input channel of the workflow remains:

- `AgentRequest.input`

while `WorkflowContext.state` carries:

- routing results
- intermediate structured objects
- metadata shared between nodes

So if you want the weather node to pass a structured object like `{city, temperature, advice}` directly to the formatting node, the more robust approach is not to have the first node emit a long blob of intermediate text, but rather:

- Write the structured object explicitly into `WorkflowContext.state`

## 6. `WorkflowAgent` and the node session are not the same thing

The test has an easily overlooked detail:

```java
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new NamedNode("WeatherAnalysis", new RuntimeAgentNode(weatherAgent.newSession())))
        .addNode(new NamedNode("FormatOutput", new RuntimeAgentNode(formatAgent.newSession())));

WorkflowAgent runner = new WorkflowAgent(workflow, weatherAgent.newSession());
```

Three sessions appear here:

- The weather node's own session
- The formatting node's own session
- A session held by `WorkflowAgent` itself

And the implementation of `RuntimeAgentNode.execute(...)` is:

- It directly calls `session.run(request)` from the session passed at construction

It does not switch to using `WorkflowContext.session`.

This means:

- The session held by `WorkflowAgent` is not a unified execution context shared by all nodes
- Whether each `RuntimeAgentNode` shares memory depends on the session you pass when constructing it

This is a very important boundary in the workflow design: node isolation vs. sharing is not inferred for you by the framework.

## 7. Why the cookbook still wraps a `NamedNode` layer

The test does not drop `RuntimeAgentNode` straight into the workflow; it wraps it in a `NamedNode`:

- Prints `NODE START`
- Prints `NODE END | status=OK / ERROR`

The reason is not decoration, but that the workflow currently has no built-in node-level logging shell out of the box.

This surfaces an engineering fact:

- If you need node-level start/end, status, and error visibility, you should wrap the node yourself or plug into a more explicit event layer

That is also why this example is essentially demonstrating a minimal observable workflow pattern, not just showing "how to chain two nodes together".

## 8. Why one node uses `ChatModelClient` and the other uses `ResponsesModelClient`

This is not arbitrary mixing; it leverages the different strengths of the two clients:

- The weather analysis node needs stable tool-call semantics, so it uses `ChatModelClient`
- The formatting node is closer to "output protocol convergence", so it uses `ResponsesModelClient`

This also states an engineering principle:

- Workflow nodes do not have to bind to the same model protocol

As long as they both obey:

- Input comes from `AgentRequest`
- Output goes back to `AgentResult.outputText`

you are free to pick a more suitable model client at the node level.

## 9. The implicit constraints of this example

If you copy the code without understanding these constraints, it is easy to misuse it.

### 9.1 The first node must produce "text the second node can consume"

Because the default relay only propagates `outputText`, the first node should output:

- Stable, clear text conclusions suitable for formatting

If the first node emits verbose, noisy output mixed with lots of tool detail, the JSON convergence quality of the second node usually degrades.

### 9.2 The second node is not reading tool results, it is reading the first node's conclusion text

Tool results do not automatically pierce through the workflow in structured form to the second node; the second node only sees the previous node's text output.

### 9.3 Whether node memory is shared depends on how sessions are reused

If you pass the same `AgentSession` to multiple `RuntimeAgentNode`s, they share that session's state; if you call `newSession()` separately for each, they are isolated.

The current test chooses:

- Each node has an independent session

This fits a "separation of concerns" two-stage pattern better.

## 10. When this cookbook should be upgraded

This two-agent workflow is well suited as:

- A linear two-stage template
- A tool node + formatting node template
- A minimal node-level logging template

But when the following needs appear, you should upgrade the model:

- Need conditional routing: move up to `StateGraphWorkflow`
- Need to pass structured objects across nodes: use `WorkflowContext.state` explicitly
- Need unified node tracing: add a node-level event bridge or a higher-level observability solution
- Need to share complex long-term state: redesign the session / memory strategy instead of relying only on the default text relay

## 11. A thought worth copying

The most copyworthy thing about this example is not a particular model name or prompt, but these four design moves:

1. Split "tool analysis" and "format convergence" into two nodes
2. Pick different model clients for different nodes
3. Use `newSession()` to isolate node state explicitly
4. Use a wrapper node to add minimal observation info

These four points are its real engineering value.

## 12. Further reading

1. [Workflow StateGraph](/docs/agent/workflow-stategraph)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Model Client Selection](/docs/agent/model-client-selection)
