---
sidebar_position: 8
title: "CodeAct: Custom Code Sandbox Executor"
description: "Explains CodeAct's code execution boundary and the CodeExecutor extension point: when to replace the default executor, how to handle tool bridging, and the isolation and security constraints production must add."
tags: [concept]
---

# CodeAct: Custom Code Sandbox Executor

This page is not about "how to get code running"; it's about the real execution boundary of CodeAct in AI4J, and at which layer you should swap in your own sandbox.

If this boundary is missed, two kinds of misjudgment are most common:

- Assuming the default `CodeExecutor` is already a strongly isolated sandbox
- Assuming a custom executor just "swaps the interpreter" and won't affect the agent's finalization semantics

Both of these understandings are wrong.

## 1. Start with 6 key design decisions

### 1.1 CodeAct's only code execution extension point is `CodeExecutor`

The current interface is very narrow:

```java
public interface CodeExecutor {
    CodeExecutionResult execute(CodeExecutionRequest request) throws Exception;
}
```

`CodeActRuntime` doesn't care whether you are:

- a local interpreter
- a container
- a remote sandbox service
- an external job system

It only cares about two things:

1. You receive a `CodeExecutionRequest`
2. You return a standard `CodeExecutionResult`

### 1.2 The default executor is not a "strong security sandbox", just the default host implementation

The default `CodeExecutor` chosen by `AgentBuilder` is:

- Java 8 -> `NashornCodeExecutor`
- Higher versions -> `GraalVmCodeExecutor`

This default implementation is positioned to:

- Let CodeAct run out of the box
- Give the model code execution + tool bridging capability

It is not:

- container-level isolation
- syscall-level isolation
- a multi-tenant strong-security execution environment

### 1.3 Default language capability is tightly bound to the Java version

The language boundary of the current default executors is very specific:

- `NashornCodeExecutor` only accepts JavaScript
- `GraalVmCodeExecutor` currently only accepts Python

If the language doesn't match, it directly returns an error result instead of switching automatically.

So the statement "CodeAct supports Python and JS" is incomplete on its own; you must add the precondition:

- It depends on which `CodeExecutor` is currently injected

### 1.4 Tool call bridging happens inside the executor, not outside the runtime

`CodeActRuntime` packs:

- `toolNames`
- `toolExecutor`
- `user`

into the `CodeExecutionRequest`.

The default executor then bridges them into:

- `callTool(...)`
- a helper function for each tool name

In other words, CodeAct's "calling tools from code" is not the runtime directly interpreting Python/JS; it's the executor itself embedding the tool bridging into the execution environment.

### 1.5 The `CodeExecutionResult` contract directly changes agent finalization semantics

`CodeExecutionResult` has only 3 fields:

- `stdout`
- `result`
- `error`

But these 3 fields aren't just for your logging; they directly affect:

- whether this execution is judged success or failure
- what the runtime ultimately returns
- what the model sees in the next turn when `reAct=true`

### 1.6 `CodeActOptions.reAct` doesn't change the executor interface, it changes the post-execution finalization path

The default value of `CodeActOptions.reAct` is:

- `false`

This means default CodeAct leans toward "execute-then-finalize".

Only when:

- `reAct = true`

does the execution result go back to the model, letting the model produce a final natural-language answer.

## 2. How the current execution chain actually flows

To understand a custom sandbox, first look at the default chain:

```text
CodeActRuntime
  -> model produces {"type":"code", ...} or {"type":"final", ...}
  -> construct CodeExecutionRequest
  -> CodeExecutor.execute(...)
  -> CodeExecutionResult
  -> runtime decides to finalize directly or go back to the model
```

The key steps are:

1. The model first emits the code/final JSON protocol
2. `CodeActRuntime` parses it into a `CodeActMessage`
3. If it is `type=code`
4. The runtime constructs an `AgentToolCall` named `code`
5. Then it truly delegates code execution to `CodeExecutor`
6. The execution result is converted into `CODE_RESULT` or `CODE_ERROR`
7. Decide whether to end immediately or continue for another turn

So when you replace `CodeExecutor`, you're essentially replacing the "code execution engine" of this chain, not replacing the entire CodeAct runtime.

## 3. What the default executors actually do

### 3.1 `NashornCodeExecutor`

Main characteristics:

- Supports only JavaScript
- Runs Nashorn via the JDK `ScriptEngine`
- Uses a single-thread pool + `Future.get(timeout)` for timeout
- Injects tool bridging as:
  - `callTool(name, args)`
  - a JS helper function for each tool name
- Supports returning the final result via `return` or `__codeact_result`

It is:

- in-interpreter execution
- host-thread-level timeout

Not:

- process-level isolation
- filesystem / network / syscall isolation

### 3.2 `GraalVmCodeExecutor`

Main characteristics:

- Currently supports only Python
- Executes via the GraalPy `Context`
- Also uses a single-thread pool + timeout
- Injects `tools.call(...)` bridging
- Auto-generates a Python helper function for each tool name
- Extracts the result from `__codeact_result` or the function return value

Its security boundary is likewise:

- in-host execution
- not a strong-security sandbox

### 3.3 An easily overlooked detail of the default executors

Both default executors rewrite the tool name based on `user`:

- `user_<user>_tool_<name>`

provided `user` is non-empty.

This shows the current CodeAct tool bridging doesn't just pass parameters; it also folds user context into tool name resolution logic.

If you write a custom executor, be explicit about whether you want to keep this semantic.

## 4. What you actually get inside `CodeExecutionRequest`

Current fields include:

- `language`
- `code`
- `toolNames`
- `toolExecutor`
- `user`
- `timeoutMs`

These fields answer:

| Field | Real meaning |
| --- | --- |
| `language` | what language the model claims to execute |
| `code` | the code text the model generated |
| `toolNames` | the set of tool names currently allowed to be exposed to the code environment |
| `toolExecutor` | the host's actual tool execution entry |
| `user` | the current user context |
| `timeoutMs` | the expected timeout budget for this execution turn |

The two most important fields are actually:

- `toolNames`
- `toolExecutor`

Because they determine whether you're building a "pure compute sandbox" or a "host-style executor with tool bridging".

## 5. Why the `CodeExecutionResult` contract is so critical

### 5.1 Success or failure is determined by `error`, not by exceptions

The definition of `CodeExecutionResult.isSuccess()` is:

```java
return error == null || error.isEmpty();
```

That is, the runtime's first signal for success or failure isn't whether an exception was thrown, but the `error` you return.

:::warning
If you swallow an exception but forget to fill in `error`, the runtime will misjudge this execution as successful.
:::

### 5.2 `stdout` and `result` are not the same thing

The semantics of these two fields should be kept distinct:

- `stdout`
  - process output
- `result`
  - the value you ultimately want the runtime to consume

If you shove every result into `stdout`, the runtime may only fall back to weaker behavior when `reAct=false`.

### 5.3 `error` isn't just for logs

When execution fails, `CodeActRuntime` constructs:

- `CODE_ERROR: ...`

and writes it back to memory.

This means `error` directly shapes how the next-turn model understands the execution failure.

## 6. The real difference between `reAct=false` and `reAct=true`

### 6.1 `reAct=false`

This is the default mode.

After the executor returns, the runtime finalizes directly as much as possible.

The priority is roughly:

1. First try to take the `result` of a successful execution
2. Otherwise fall back to `stdout`
3. If that fails, fall back to the tool output JSON
4. On failure, fall back to `CODE_ERROR`

So under `reAct=false`, the quality of your executor's return value directly determines the quality of the final user answer.

### 6.2 `reAct=true`

After the executor returns, the runtime does not immediately treat the result as the final answer.

It writes:

- `CODE_RESULT: ...`
  or
- `CODE_ERROR: ...`

back to memory as a system message, then lets the model continue for another turn to organize the result into:

```json
{"type":"final","output":"..."}
```

So the benefits of `reAct=true` are:

- a more natural answer
- the model can reinterpret the execution result

The cost is:

- one more turn of tokens
- one more turn of latency

## 7. When you should customize `CodeExecutor`

You should usually replace the default executor in these situations:

### 7.1 You need a real isolation boundary

For example:

- multi-tenant execution
- untrusted code
- production compliance requirements

### 7.2 You need a process / container / K8s / remote execution environment

The default in-host interpreter is no longer enough.

### 7.3 You need stricter resource governance

For example:

- CPU
- memory
- filesystem
- network
- number of subprocesses

### 7.4 You need a more controllable audit chain

For example:

- pre-execution static checks
- post-execution audit records
- code snippet archiving

## 8. Two more reliable implementation patterns

### 8.1 Pattern A: Local process sandbox

Fits:

- single-machine deployment
- initial validation
- stronger isolation than an in-host interpreter

Core idea:

1. Validate `language`
2. Generate a temporary working directory
3. Write the code to disk
4. Execute with a restricted interpreter process
5. Kill the process on timeout
6. Collect stdout/stderr
7. Construct a standard `CodeExecutionResult`

The advantages of this route are:

- simple
- controllable
- easier to manage timeout and cleanup than in-host execution

The disadvantage is:

- still not container-level isolation

### 8.2 Pattern B: Remote sandbox service

Fits:

- platform-style execution
- multi-tenant environments
- needs unified audit and resource pooling

Core idea:

- `CodeExecutor` only acts as an RPC client locally
- the remote service handles the actual execution
- returns a unified `CodeExecutionResult`

The biggest benefit of this route is:

- the SDK does not directly execute untrusted code
- isolation and audit can be governed centrally

## 9. How tool bridging should work in a custom sandbox

There are two routes here; you must choose clearly yourself.

### 9.1 Pure compute mode

No tool calls exposed.

Here you can:

- ignore `toolExecutor`
- allow only pure code computation

The advantage is the simplest security model.

### 9.2 Host bridging mode

Expose `toolExecutor` to the bridging function in the code environment.

Here you must explicitly handle:

- tool allowlist
- parameter serialization
- user context
- timeout and concurrency
- unauthorized calls

A very practical piece of advice:

- open-source default policy should lean conservative
- don't enable high-risk tools by default
- parameter validation can't rely solely on the model's self-discipline

## 10. What a more robust implementation skeleton should consider

Whether local or remote, at least bake these points into the implementation:

### 10.1 Language validation

Don't trust that the model will always follow your desired language.

### 10.2 Timeout

`request.getTimeoutMs()` should not be ignored.

### 10.3 Encoding

The encoding of stdout / stderr / result must be stable, otherwise non-ASCII output (e.g. Chinese) is very likely to garble.

### 10.4 Result normalization

Be explicit about:

- which values go into `result`
- which values go into `stdout`
- when to fill in `error`

### 10.5 Tool call governance

If you open tool bridging, the executor itself is part of the security boundary.

## 11. The minimum security constraints production must add

At minimum, be explicit about:

1. Time limits
2. CPU / memory caps
3. Filesystem scope
4. Network access policy
5. Tool allowlist
6. Parameter validation
7. Audit logs
8. Cleanup policy

The default executors cover only a very small part of this.

## 12. Which segments to look at when observing

If tracing is enabled, CodeAct is best viewed in three segments:

1. The first `MODEL` turn
   See how long the model took to generate code.
2. `TOOL(type=code)`
   See how long the sandbox execution itself took.
3. The next `MODEL` turn
   Exists only when `reAct=true`; see how the model organizes the execution result.

If you look at these three segments mixed together, it's hard to tell whether the slowdown is in the model or the executor.

## 13. The most common pitfalls

### 13.1 Returned an error but didn't fill in `error`

The runtime will misjudge it as success.

### 13.2 Ignoring `timeoutMs`

When execution hangs, the agent experience is very poor.

### 13.3 Treating `stdout` as the final result

Under `reAct=false`, this makes finalization uncontrollable.

### 13.4 Enabled tool bridging but did no allowlist and parameter governance

:::danger
This is equivalent to exposing host capabilities directly to model-generated code.
:::

### 13.5 Assuming the default executor is already a production-security sandbox

:::danger
It is not.
:::

## 14. Recommended source-code reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutionRequest.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutionResult.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/GraalVmCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`

## 15. Further reading

1. [CodeAct Runtime](/docs/agent/codeact-runtime)
2. [Runtime Implementations](/docs/agent/runtime-implementations)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Trace and observability](/docs/agent/trace-observability)
