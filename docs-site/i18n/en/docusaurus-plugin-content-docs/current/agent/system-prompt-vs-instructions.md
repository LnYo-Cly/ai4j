---
sidebar_position: 2
title: "System Prompt vs Instructions"
description: "Clarifies the source-level semantics of systemPrompt vs instructions in ai4j-agent: both are AgentContext configuration that re-enters the model every step, but they map differently on the Chat and Responses paths, and must be layered deliberately under CodeAct."
tags: [concept]
---

# System Prompt vs Instructions

This page is not about prompt engineering in general. It is about the actual source-level semantics of these two fields in the AI4J Agent.

Get this wrong and you will also misunderstand:

- Why the same Agent behaves differently under different runtimes
- Why the request shape differs between Chat and Responses
- Why the `systemPrompt` you see in a trace is not exactly the string you wrote
- Why prompt rules do not change after `newSession()`

## 1. Start with six key design decisions

### 1.1 Neither field is "ad-hoc text for the current turn" — both are part of AgentContext

`AgentBuilder.build()` writes both into `AgentContext`:

- `instructions`
- `systemPrompt`

That is, both belong to the "Agent wiring configuration", not to temporary variables recomputed dynamically at every step.

The direct consequence:

- Every turn, `buildPrompt(...)` puts them back into the prompt
- They do not change automatically with memory

### 1.2 `systemPrompt` is implicitly expanded by the runtime; `instructions` is not

The key logic in `BaseAgentRuntime.buildPrompt(...)` is:

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

Then:

```java
.systemPrompt(systemPrompt)
.instructions(context.getInstructions())
```

So:

- Your `systemPrompt` is not the complete system text sent to the model
- The runtime's own policy prompt is appended after `systemPrompt`
- `instructions` stays a separate field and does not participate in this merge

### 1.3 Both fields re-enter the model on every step

Many people instinctively assume:

- `systemPrompt` is "used once at initialization"
- `instructions` is "injected once for this turn"

The current implementation does not work that way.

`BaseAgentRuntime.runInternal(...)` on every turn:

1. `buildPrompt(...)`
2. `executeModel(...)`

And `buildPrompt(...)` on every turn pushes:

- `systemPrompt`
- `instructions`

into `AgentPrompt` again.

This means:

- The longer you write, the more steps run, the higher the repeated token cost
- Stuffing dynamic context into these two fields is very wasteful

### 1.4 `newSession()` only swaps memory, not these two

The implementation of `Agent.newSession()` only replaces:

- `memory`

It does not replace:

- `systemPrompt`
- `instructions`
- `runtime`
- `modelClient`
- `toolRegistry`

So a session isolates state, not instruction templates.

If you want to change prompt rules, you should `build()` a new Agent, not just open a new session.

### 1.5 The `systemPrompt` recorded in a trace is already the merged version

When `AgentTraceListener` records `MODEL_REQUEST`, it takes:

- `prompt.getSystemPrompt()`
- `prompt.getInstructions()`

And this `prompt` is already the `AgentPrompt` built by the runtime.

Therefore, the `systemPrompt` you see in the trace:

- usually already contains the policy text injected by the runtime, not the original text you first passed to the Builder.

### 1.6 Chat and Responses do not map these two fields equivalently

This is not an implementation detail — it directly affects how you should design your prompt structure.

- Chat path: both end up as system messages
- Responses path: `systemPrompt` goes into top-level `instructions`, and `instructions` becomes a leading system item

So "these two fields are roughly the same, put things wherever" is the wrong conclusion.

## 2. Where these two fields actually live in the object model

In source, the chain is clear:

```text
AgentBuilder
  -> AgentContext
  -> AgentPrompt
  -> ChatModelClient / ResponsesModelClient
```

The three most important objects are:

| Object | Responsibility here |
| --- | --- |
| `AgentContext` | Stores the original `systemPrompt` / `instructions` you configured |
| `AgentPrompt` | The prompt snapshot actually submitted to the model client at each runtime step |
| `ChatModelClient` / `ResponsesModelClient` | Translates these two fields into the underlying protocol |

`AgentPrompt` itself models these two fields separately:

- `systemPrompt`
- `instructions`

This shows the framework authors did not treat them as the same thing at the abstraction layer.

## 3. What actually happens inside `BaseAgentRuntime`

### 3.1 The most important line is `mergeText(...)`

In `BaseAgentRuntime.buildPrompt(...)`:

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions());
```

The meaning of this line is very specific:

- First take your configured `systemPrompt`
- Then concatenate the runtime's own system-level policy
- Join the two with a newline

### 3.2 `instructions` does not participate in the merge

The subsequent Builder call is:

```java
.systemPrompt(systemPrompt)
.instructions(context.getInstructions())
```

So under the current default runtime, AI4J keeps a very explicit layering:

1. Your system-level settings
2. The runtime's system-level policy
3. Your task-level instructions

### 3.3 Why this layering matters

Because the runtime's system policy is not optional decoration.

For example:

- `ReActRuntime` appends `Use tools when necessary. Return concise final answers.`
- `CodeActRuntime` appends a large block of JSON/code protocol constraints

If you pile all task requirements into `systemPrompt`, when the model later misbehaves it is hard to tell which layer is actually taking effect:

- Your system settings
- The runtime's additional policy
- Your task instructions

## 4. The real mapping on the Chat path

`ChatModelClient.toChatCompletion(...)` currently does this directly:

1. If there is a `systemPrompt`, add a system message first
2. If there are `instructions`, add another system message
3. Then convert `items` into the subsequent messages

That is, in the Chat protocol:

- These two fields are ultimately not two top-level attributes
- They are two sequentially adjacent system messages

Their difference at the protocol layer is reduced to:

- Order
- Text content

### 4.1 What this means for prompt design

On the Chat path, the safest habit is:

- Put long-term role, long-term boundaries, and stable policy in `systemPrompt`
- Put the current turn's goal, format requirements, and local constraints in `instructions`

Because even though both ultimately become system messages at the protocol layer, the logical layering still exists, which keeps subsequent tracing and migration clearer.

### 4.2 Do not conclude from "both become system messages" that "they are the same"

The difference remains in at least three places:

1. Different semantic intent
2. Different code fields
3. Different Responses-path mapping

This distinction matters even more if you later switch to the Responses path.

## 5. The real mapping on the Responses path

`ResponsesModelClient.toResponseRequest(...)` handles this differently:

- `systemPrompt` -> `ResponseRequest.instructions`
- `instructions` -> a `systemMessage(...)` inserted at the very front of the input during `buildItems(prompt)`

This means in the Responses protocol, the two genuinely land on two different layers:

### `systemPrompt`

Closer to:

- Top-level, global, request-scoped instructions

### `instructions`

Closer to:

- A leading task description inside the input sequence

### 5.1 Why many people write more reliably on the Responses path

Because this path can more cleanly preserve the protocol-level separation between:

- Global rules
- Task rules

rather than collapsing both into a message sequence.

## 6. Why this matters even more under `CodeActRuntime`

In `CodeActRuntime.buildPrompt(...)`, it still does:

```java
String systemPrompt = mergeText(context.getSystemPrompt(), runtimeInstructions(context));
```

But here `runtimeInstructions(context)` is not a short hint — it is a whole block of protocol constraints, including:

- Only output a single JSON object
- The `type=code` / `type=final` format
- Language constraints
- Tool guide
- Specific notes for certain tools

This means that by the time you reach CodeAct:

- `systemPrompt` is more like the carrier layer for "host policy + user global policy"
- `instructions` is more like "what this task needs to achieve"

If you stuff specific task steps, data details, and short-term context all into `systemPrompt`, they will quickly tangle with the runtime protocol text.

## 7. What each field is best suited to carry

### 7.1 What `systemPrompt` is best for

Suitable for:

- Identity settings
- Long-term style
- Stable priorities
- Risk boundaries
- Tool-usage principles

For example:

```text
You are an enterprise Java assistant.
Do not invent facts.
Prefer tool-backed answers when external evidence is required.
Return concise conclusions first.
```

The common traits of this content:

- Stable across tasks
- Does not depend on the current user input
- Reasonable to repeat at every step

### 7.2 What `instructions` is best for

Suitable for:

- Current task goal
- Output format
- Current-turn constraints
- Failure-handling policy

For example:

```text
Summarize today's weather for Beijing.
Return strict JSON with fields city, summary, advice.
If the tool fails, explain the failure instead of fabricating data.
```

The common traits of this content:

- Strongly tied to the current task
- Can switch with the task
- But still more stable than a single user input

## 8. What truly does not belong in these fields

### 8.1 Do not stuff real-time business data into `systemPrompt`

For example:

- Current query results
- Temporary table data
- Per-turn context summaries

Because they get resent at every step — both expensive and noisy.

### 8.2 Do not rewrite raw user input and stuff it into `instructions`

The user's genuinely dynamic input should go through:

- `AgentRequest.input`
- Subsequent memory items

not through manually re-assembling the user's question into instructions every time.

### 8.3 Do not manually copy the runtime protocol into your own `systemPrompt`

Especially under CodeAct.

The runtime already injects its own protocol constraints. If you duplicate them, you easily get:

- Redundancy
- Conflicts
- System-text bloat in traces

## 9. The most common misjudgments and their consequences

### 9.1 "`systemPrompt` is only used once at the very start"

False.

It re-enters the model on every prompt rebuild.

### 9.2 "A new session will swap in a new set of prompt rules"

False.

`newSession()` only swaps memory, not these two fields.

### 9.3 "The `systemPrompt` I see in the trace is the exact string I wrote"

False.

What you see in the trace is the runtime-merged `AgentPrompt.systemPrompt`.

### 9.4 "On the Chat path both become system messages, so placement doesn't matter"

False.

The moment you switch to Responses, CodeAct, or do trace analysis, this lazy style breaks immediately.

## 10. A more robust practical template

### Global template

```java
String systemPrompt = ""
        + "You are a production-facing assistant.\n"
        + "Do not invent facts.\n"
        + "Use tools when external evidence is required.\n"
        + "Keep answers concise and explicit about uncertainty.";
```

### Task template

```java
String instructions = ""
        + "Summarize today's weather for Beijing.\n"
        + "Return JSON with fields city, summary, advice.\n"
        + "If the tool fails, explain the failure clearly.";
```

### Wiring

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .systemPrompt(systemPrompt)
        .instructions(instructions)
        .build();
```

The benefit of this layering is not "more elegant", but:

- Easier protocol migration
- Easier to read traces
- Easier to reuse across multi-task scenarios

## 11. When you should prefer the Responses path

If you particularly care about this:

> System-level rules and task-level rules must stay separated at the protocol layer too

then `ResponsesModelClient` is usually closer to your intent.

If you care more about chat compatibility and the traditional message-sequence mental model, `ChatModelClient` still works completely fine, but you must be clear that:

- Both ultimately sink into system messages

## 12. Recommended source-reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentContext.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentPrompt.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/ReActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ChatModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ResponsesModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/trace/AgentTraceListener.java`

## 13. Further reading

1. [Model Client Selection](/docs/agent/model-client-selection)
2. [Quickstart](/docs/agent/quickstart)
3. [Architecture](/docs/agent/architecture)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
