---
sidebar_position: 7
title: "CodeAct Runtime"
description: "CodeActRuntime replaces native tool-calling with a code/final JSON protocol, bringing model-generated executable code into the main loop, with CodeExecutor bridging tools and deciding the closing path."
tags: [concept]
---

# CodeAct Runtime

`CodeActRuntime` is not "ReAct plus another code tool", but rather a different execution chain.

It changes the stable intermediate representation of model output from:

- `tool_calls`

to:

- `{"type":"code","language":"...","code":"..."}`
- `{"type":"final","output":"..."}`

This means the essence of CodeAct is not native function-calling, but rather a prompt protocol enforced by the runtime, which then bridges code and tools through `CodeExecutor`.

## 1. Start with 3 key design decisions

To understand `CodeActRuntime`, first grasp the 3 points that actually determine behavior.

### 1.1 It is not the native tool-calling path

When `CodeActRuntime.buildPrompt(...)` builds an `AgentPrompt`, it does not stuff the tool schemas into the `tools` field of the prompt.

What it actually does is:

- Merge the `systemPrompt`
- Inject a block of `runtimeInstructions(...)`
- Concatenate the tool names and descriptions into a text guide

In other words, CodeAct does not have the model emit provider-native `tool_calls` directly; instead it has the model:

1. First emit a JSON code message
2. Then the runtime interprets that code message
3. Then `CodeExecutor` invokes tools inside the host

The fundamental difference from ReAct is not "whether it calls tools", but rather "whether there is a code-level intermediate representation between the model and the tools".

### 1.2 Code execution is not an extra side feature, but the core of the main loop

In `runInternal(...)`, whenever the model emits `type=code`, the execution chain enters:

```text
parseMessage
  -> build AgentToolCall(name=code)
  -> codeExecutor.execute(CodeExecutionRequest)
  -> memory.addSystemMessage(CODE_RESULT / CODE_ERROR)
  -> next step or final output
```

So CodeAct is not "the model writes some code and runs it on the side"; the runtime brings code execution itself into the main loop.

### 1.3 The final output does not necessarily pass through a model summary

The default is `CodeActOptions.reAct = false`.

This means that after code executes successfully, the runtime prefers to return directly:

- `result`
- `stdout`
- `toolOutput`

In many scenarios the final answer never goes back to the model for polishing; the code result becomes `AgentResult.outputText` directly.

So to tell whether an output is "summarized by the model" or "returned directly by the executor", you must look at `reAct` and the execution result structure, not just the final text.

## 2. What problem does it actually solve

ReAct is suitable for:

- The model deciding whether to call a tool
- Calling a tool once or a few times
- Feeding tool results directly back to the model to continue reasoning

But there is a class of tasks where a pure-text tool loop gradually becomes unstable:

- Calling multiple tools in a loop within the same turn
- Aggregating, sorting, or transforming between tool results
- Constructing temporary intermediate variables rather than just stitching natural language
- Wanting to "execute first, then decide how to answer"

The value of CodeAct is converting such tasks from "the model reasons directly" to "the model generates an executable procedure".

## 3. The real boundary with ReAct

| Runtime | Model's primary output | How tools enter the chain | Who decides the final answer |
| --- | --- | --- | --- |
| `ReActRuntime` | Text + native tool calls | Runtime executes the tool call directly | Usually the model |
| `CodeActRuntime` | JSON code/final message | Code calls tools indirectly via `CodeExecutor` | Could be the executor, or the model |

So when to switch to CodeAct is not about "whether the task is complex", but about:

- Whether you need "tool calls"
- Or "an explicit executable procedure between tool calls"

If the latter matters more, you should enter CodeAct.

:::tip All code on this page is runnable
The wiring examples below come from
[`CodeActDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActDocExamplesTest.java),
which uses an inline scripted client to validate the code→execute→finalize protocol, with zero network.
The Nashorn executor is only available on JDK 8/11; on higher JDK it is skipped automatically.
:::

Minimal wiring — `reAct=false`: the model emits code, the executor runs it, and the result becomes the output directly:

```java
Agent agent = Agents.codeAct()
        .modelClient(modelClient)
        .model("gpt-4o-mini")
        .codeExecutor(new NashornCodeExecutor())           // Use Nashorn on JDK 8/11; otherwise GraalVM/Python
        .systemPrompt("You are a code execution assistant. Output {type:code,language,code} for the executor to run.")
        .codeActOptions(CodeActOptions.builder().reAct(false).build())
        .options(AgentOptions.builder().maxSteps(4).build())
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("Use JavaScript to compute 17*3+5").build());
// reAct=false: the model emits {"type":"code","language":"javascript","code":"return 17*3+5"}
// -> execution yields 56 -> used directly as the output, no second pass from the model
```

`reAct=true` is two stages: first emit code to execute, then emit `{"type":"final","output":"..."}` for the model to close — suitable for scenarios where the execution result needs the model to re-explain or keep reasoning.

The JSON protocol the model emits has only two types:

| type | Meaning |
| --- | --- |
| `code` | Triggers the executor; `language` + `code` are bridged to `CodeExecutor` |
| `final` | Closes; `output` becomes the final answer |

Execution results are fed back into memory as `CODE_RESULT: ...` / `CODE_ERROR: ...`, forming a self-repair loop.

## 4. The real lifecycle of `runInternal()`

From the source, `CodeActRuntime.runInternal(...)` can be broken into 6 phases.

### 4.1 Phase 1: Initialization and the step loop

At the start it will:

- Read `AgentOptions.maxSteps`
- Read `CodeActOptions.reAct`
- Validate `memory`
- Validate `codeExecutor`
- Write the user input into `AgentMemory`

Then it enters the step loop.

One thing to note here:

- When `maxSteps <= 0`, there is no step cap

:::warning
So CodeAct's default is not a production-safe conservative configuration. As with ReAct, if you do not explicitly cap the step count, failure recovery or idle spinning can continue indefinitely.
:::

### 4.2 Phase 2: Build the prompt, but do not register tools

`buildPrompt(...)` only puts these fields into `AgentPrompt`:

- `model`
- `items`
- `systemPrompt`
- `instructions`
- Sampling parameters
- `reasoning`
- `store`
- `user`
- `extraBody`

It does not inject `tools`.

Instead, `runtimeInstructions(...)` assembles a tightly constrained protocol that requires the model to:

- Output only a single JSON object
- Output `type=code` when execution is needed
- Output `type=final` when done
- Use a specific language
- Call tools by tool name or `callTool(...)`

So CodeAct is "tool documentation as text + execution bridging", not "exposing provider-native tools".

### 4.3 Phase 3: Parse the model output

After the model returns text, the runtime calls `parseMessage(...)`:

- First extract the first JSON object from the text
- Then read `type/language/code/output`

There is a critical fault-tolerance boundary here:

- The contract requires the model to output only JSON
- But the implementation allows "some surrounding text, as long as a JSON can be extracted it keeps running"

If no JSON can be extracted, `message == null`, and the runtime treats the original text directly as the final output.

This means:

- The fallback for a parse failure is not a protocol retry
- But rather a direct retreat to "a plain-text Agent"

So if the model frequently drifts from the JSON protocol, CodeAct's behavior degrades quickly.

### 4.4 Phase 4: When `type=code`, enter the execution bridge

When `message.type == "code"` and `message.code != null`, the runtime will:

1. Construct a logical `AgentToolCall`
   - `name = "code"`
   - `callId = code_execution_<step>`
2. Emit a `TOOL_CALL` event
3. Construct a `CodeExecutionRequest`
4. Call `codeExecutor.execute(...)`

The purpose of this `AgentToolCall(name=code)` is not for the model to use; it exists to bring CodeAct's execution behavior into the unified tool event / trace system.

In other words, at the observation layer CodeAct looks like "it executed a code tool", but this tool is actually a runtime-internal bridge.

### 4.5 Phase 5: Write the execution result back into memory

The execution result is first assembled into JSON by `buildToolOutput(...)`, possibly containing:

- `result`
- `stdout`
- `error`

Then the runtime writes it as a system message:

- Success: `CODE_RESULT: {...}`
- Failure: `CODE_ERROR: {...}`

This step is critical, because what the model sees next is not a JVM object, but execution feedback already converted to the text protocol.

CodeAct's "self-repair" capability is fundamentally built on this feedback chain.

### 4.6 Phase 6: Decide whether to end directly or run another turn

Once the execution result is out, the runtime takes two different branches.

#### `reAct = false`

The runtime tries to return directly:

1. `resolveDirectOutput(...)`
2. `resolveFallbackOutput(...)`

As long as either is non-empty, it ends immediately and does not call the model again.

#### `reAct = true`

The runtime does not treat the successful execution result as the final answer directly; instead it:

- Sets `finalizeRequested = true`
- Leaves `CODE_RESULT` in memory
- Has the model output `type=final` on the next turn

If the model keeps producing `type=code` while in finalize mode, the runtime inserts another system message:

```text
FINALIZE_MODE: Do not output code. Use the latest CODE_RESULT ...
```

This shows that `reAct=true` is not merely "run one more turn"; the runtime actively tightens the protocol, forcing the model to switch from the execution phase to the summary phase.

## 5. `runtimeInstructions(...)` exposes the real execution boundary

This method is more truthful than much of the documentation, because it tells the model directly what is allowed.

### 5.1 The Java 8 path is actually JavaScript mode

When the executor is `NashornCodeExecutor`, the runtime explicitly requires:

- Use `js`
- Syntax compatible with Nashorn ES5
- No `Promise`
- No `async/await`
- No template strings
- No `let/const`
- No arrow functions

This shows that CodeAct under Java 8 is not "a feature-reduced Python mode", but rather another explicit language contract.

### 5.2 Higher Java versions default to Python mode

When the executor is not Nashorn, the runtime by default requires the model to output Python.

So in a Java 17+ environment, CodeAct's main mental model is closer to:

- Python as the execution intermediate language
- Java as the host and tool bridge

### 5.3 Tool capability is exposed as text, not as schema

`runtimeInstructions(...)` iterates the current `toolRegistry` and concatenates the tool names and descriptions into:

```text
Available tools: toolA - ..., toolB - ...
```

This means CodeAct's tool discovery does not rely on the provider validating schemas, but on prompt protocol constraints.

Its advantage is:

- A more flexible execution bridge

Its cost is:

- Parameter correctness depends more on the model following the text description
- And more on executor fault tolerance

## 6. What `CodeExecutionRequest` actually bridges in

Each execution passes this information into `CodeExecutor`:

- `language`
- `code`
- `toolNames`
- `toolExecutor`
- `user`

So the executor is not "just running a string of code"; it runs inside an environment that carries a host tool bridge.

This means what the code can do depends on two layers:

1. Which tool names the runtime provides
2. How the executor exposes those tools as callable objects

So CodeAct's capability boundary cannot be read from the model alone, nor from the runtime alone; you must look at `CodeExecutor` at the same time.

## 7. The output selection logic is finer than it appears

Many people assume "whatever the code returns is the final result", but the current implementation is finer-grained.

### 7.1 `resolveDirectOutput(...)` is conservative

It uses `result` directly only when all of the following hold:

- `execResult.isSuccess()`
- `result` is non-empty
- Non-empty after `trim`
- Not `"undefined"`
- Not `"null"`

So "code executed successfully" does not equal "there is definitely a direct output".

### 7.2 `reAct=false` does not necessarily end immediately

If both direct output and fallback output are empty, the runtime does not return immediately; it continues to the next turn.

That is, in non-summary mode, the loop can still continue after a successful execution.

This is an easily overlooked point:

- `reAct=false` does not mean "must terminate after one execution"
- It only means "if there is already a returnable result, prefer to terminate directly"

### 7.3 `stdout` and `toolOutput` can also become the final answer

This explains why some CodeAct outputs look like raw logs or structured JSON rather than natural-language summaries.

## 8. How failure recovery takes shape

CodeAct's recovery capability does not come from an external retry framework; it comes from the runtime writing failures back into memory.

The chain after a failure is:

```text
exec error
  -> CODE_ERROR system message
  -> model sees error on next step
  -> model emits patched code
```

So the quality of failure recovery depends mainly on 4 things:

- Whether `maxSteps` is large enough
- Whether the error message is specific enough
- Whether the system prompt asks the model to fix rather than give up
- Whether the executor compresses exceptions into readable text

This is also why CodeAct is often more sensitive to "protocol clarity" than ReAct.

## 9. Keep the boundary with security isolation distinct

`CodeActRuntime` is responsible for:

- The code protocol
- Loop advancement
- Result feedback

It is not responsible for:

- Process isolation
- File system restrictions
- Network permission governance
- CPU / memory quotas

These should be borne by `CodeExecutor` and its host environment.

:::note
So the built-in executors solve "runnable", not "ready for production directly".
:::

## 10. What tasks it fits, and what it does not

Fits:

- Batch tool calls
- Aggregation and structured transformation of tool results
- Tasks with many intermediate variables
- Tasks where "execute first, then summarize" beats "think while answering"

Does not fit:

- Only 1 to 2 simple tool calls needed
- Not allowed to introduce a code execution environment
- Scenarios with extremely strict tool parameter constraints that must rely on the provider's native schema validation

If the task is essentially still a plain tool loop, sticking with ReAct is often more stable.

## 11. Recommended source entry points

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeActOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/GraalVmCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`

## 12. Recommended verification test cases

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`

## 13. Further reading

1. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory/memory-and-state)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
