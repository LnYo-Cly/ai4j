---
sidebar_position: 3
title: "Agent usage paths and scenario selection"
description: "Helps you pick an abstraction layer by system structure: ReAct is the default starting point, CodeAct swaps the intermediate representation, Workflow adds explicit nodes, SubAgent does controlled delegation, Teams introduces a collaboration model; with a decision table and common misjudgements."
tags: [concept]
---

# Agent usage paths and scenario selection

This page does not answer "which capability is more powerful", but rather "which abstraction layer the task at hand should land on".

In `ai4j-agent`, `ReAct`, `CodeAct`, `Workflow`, `SubAgent`, and `Teams` are not five feature buttons on the same layer, but five different control boundaries:

- `ReAct` / `CodeAct` are runtime-level differences
- `Workflow` is an orchestration layer above the Agent
- `SubAgent` is a controlled delegation capability of a single Agent
- `Teams` is an independent collaboration runtime model

If this boundary is not clear from the start, the most common outcomes are:

- You only needed ReAct, but jumped to a Team too early
- You should have built a Workflow, but crammed the flow into the prompt
- You only needed to delegate one specialized subtask, but reworked the whole system into multi-member collaboration

## 1. Pick by "what changes in the system structure" first

The shortest way to decide is not to read the feature list, but to look at what changes in the main control plane of your source code once you bring in a new capability.

| Entry point | Control plane it actually changes | Problems it fits |
| --- | --- | --- |
| `ReAct` | `AgentRuntime` stays a standard `BaseAgentRuntime` loop | Single agent, multi-step reasoning, a small number of tools |
| `CodeAct` | runtime becomes a closed loop of "model produces code, then executes it" | Complex tool orchestration, structured intermediate programs |
| `Workflow / StateGraph` | Adds explicit nodes and edges above the Agent | Fixed node flows, approvals, routing, rollback |
| `SubAgent` | Injects a controlled handoff into a single Agent's tool surface | The main Agent is still the only scheduler, but needs specialized sub-roles |
| `Teams` | Introduces planner / task board / message bus / synthesizer | Long-term multi-role collaboration, task claiming and reassignment |

The most important row in this table is:

- `Workflow` and `Teams` are not "enhanced editions" of `ReAct`

What they change is the system structure, not a few extra lines in the prompt.

## 2. `ReAct`: the default starting point, not the low-end version

Fits:

- A single goal
- A small number of tools
- No need for explicit node transitions
- No need for a code execution environment
- No need for multi-role collaboration

Why it should be the default starting point:

- The default runtime of `AgentBuilder.build()` is already `ReActRuntime`
- `ReActRuntime` reuses `BaseAgentRuntime` almost entirely
- Foundational semantics such as memory, tool result write-back, event publishing, and parallel tool calls are all in place

So for many tasks the optimal solution is not "move up to a higher abstraction", but rather:

- Get the tool surface, prompt, and memory policy right at the ReAct layer first

Signals that you should no longer stay in ReAct are typically:

- The tool call sequence starts to look like a program
- The output is no longer just natural language, but depends on intermediate code or artifacts from fixed stages
- The prompt starts to carry a lot of flow descriptions like "do A first, then B, fall back to C on failure"

Further reading:

- [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)

## 3. `CodeAct`: what you need is an intermediate program, not more text links

Fits:

- The model writes a piece of temporary code first
- The code calls tools multiple times
- Complex processing logic is better handed off to code than left to multi-turn pure-text reasoning by the model

The essential difference from ReAct is not "an extra code executor", but that the execution semantics changed:

- ReAct is "the model emits a tool call directly"
- CodeAct is "the model produces code first, and the code then drives tools and computation"

So the following signals usually indicate you should upgrade from ReAct to CodeAct:

- In a single turn you need to process the same batch of data multiple times
- You need loops, filtering, formatting, aggregation
- The control logic between tools starts to look more like a script than a prompt

But do not treat CodeAct as the default answer for every complex task. Its costs are:

- The execution environment's security boundary matters more
- The runtime semantics are more complex
- The troubleshooting surface for code execution failures and tool failures is larger

Further reading:

- [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
- [CodeAct custom sandbox](/docs/agent/runtimes/codeact-custom-sandbox)

## 4. `Workflow / StateGraph`: you need explicit nodes, not a model pretending to be a flowchart

Fits:

- The task is naturally a multi-node process
- There is a fixed division of work between nodes
- There is conditional routing, looping, approvals, failure rollback
- You want the flow structure to be visible, testable, and editable, not buried in the prompt

The most important boundary here is:

- Workflow is not a runtime variant
- It is an orchestration layer above the Agent

So when your problem is already "how do nodes connect, which edge to pick, how to pass state", continuing to describe the flow inside a ReAct prompt usually only makes the system more fragile.

Typical signals:

- You can already draw the flowchart
- You want to know explicitly which node you are currently stuck on
- You need fixed node input/output semantics

Cases where jumping straight to Workflow is not advisable:

- Just ordinary Q&A plus tools
- The tool allowlist and basic behavior are not yet settled
- There is no clear node boundary in reality, the task is just somewhat complex

Further reading:

- [Workflow StateGraph](/docs/agent/runtimes/workflow-stategraph)
- [Weather workflow cookbook](/docs/agent/orchestration/weather-workflow-cookbook)

## 5. `SubAgent`: the main Agent is still the only brain, it just starts delegating in a controlled way

Fits:

- The main Agent is still in charge of unified scheduling
- You just want to hand certain subtasks to a specialized sub-agent
- You need a handoff policy, depth limits, deny/allow rules

The key difference from a Team is:

- SubAgent is still a tool-surface extension within a single-Agent system
- Sub-agents are not equal long-term collaboration members

If your system still has the shape of "one main Agent plus several specialized delegates", prefer SubAgent over jumping straight to a Team.

Further reading:

- [Subagent Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy)

## 6. `Teams`: not "more agents", but a collaboration organization model

Fits:

- Multiple roles exist long-term
- A planner / synthesizer / task board is needed
- Members need to message each other, claim tasks, release them, reassign them
- A single main Agent's handoff model can no longer express the business structure

The essential change a Team brings is:

- The system is no longer just "one agent occasionally delegating to others"
- It introduces an explicit collaboration runtime model

So you should not start with a Team just because "it might get complex later". Signals that genuinely fit a Team usually are:

- You already have a clear long-term role division
- Tasks need a shared task board rather than ad-hoc handoffs
- You need to record the collaboration process between members, not just care about the final answer

Further reading:

- [Agent Teams](/docs/agent/orchestration/agent-teams)
- [Agent Teams API Reference](/docs/agent/orchestration/agent-teams-api-reference)

## 7. A more practical decision table

| Task shape | Better-fitting entry point | Layer you should not jump to first |
| --- | --- | --- |
| One-off task + a few tools | ReAct | Teams |
| Multi-step data processing, best written as temporary code | CodeAct | Teams |
| Explicit nodes, conditional edges, approval flows | Workflow / StateGraph | Teams |
| Main Agent occasionally delegates specialized subtasks | SubAgent | Teams |
| Long-term multi-role collaboration, shared task board | Teams | Forcing it with ReAct / SubAgent |

There is only one principle behind this table:

- Pick the smallest correct abstraction layer that meets the current need

Not the strongest, not the most complete, but the smallest correct one.

## 8. A more stable evolution order

In real engineering, a more stable order is usually:

1. First get a single Agent with a clear tool allowlist working
2. Then decide whether to upgrade to CodeAct or to Workflow
3. Once delegation is clearly needed, bring in SubAgent
4. Only when the collaboration structure is already clear, bring in Teams
5. Throughout, fill in tracing, session, and regression coverage

The benefits of doing it this way:

- The foundational loop stabilizes first
- The tool boundary stabilizes first
- You introduce only one new layer of complexity at a time

## 9. Common misjudgements

### 9.1 "The task is complex, so go straight to a Team"

Complex does not mean collaboration. The real need behind many complex tasks is just:

- CodeAct
- Workflow

Not a multi-role organization model.

### 9.2 "There are two phases, so it has to be a Workflow"

If the two phases are just one sequential pass with no clear state branch, routing, or failure recovery, CodeAct or a single Agent may be a better fit.

### 9.3 "Delegation is needed, so it must be a Team"

If there is only one main scheduler and the subtasks are just tool-style delegation, SubAgent is usually the better fit.

### 9.4 "ReAct is too simple, not advanced enough"

ReAct is not a demo layer, it is the default runtime semantics. What many production tasks actually need to change is:

- prompt
- tool executor
- memory policy

Not upgrading the whole system to a higher abstraction layer.

## 10. Recommended reading order

1. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
2. [Agent architecture overview](/docs/agent/architecture)
3. [Runtime implementation deep dive](/docs/agent/runtimes/runtime-implementations)
4. [Workflow StateGraph](/docs/agent/runtimes/workflow-stategraph)
5. [SubAgent and Handoff Policy](/docs/agent/orchestration/subagent-handoff-policy)
6. [Agent Teams](/docs/agent/orchestration/agent-teams)
