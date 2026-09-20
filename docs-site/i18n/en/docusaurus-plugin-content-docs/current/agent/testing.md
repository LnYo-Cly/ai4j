---
title: Agent Test Fixtures
description: The ai4j-testing module ships reusable unit-test fixtures for agents — ScriptedModelClient replay, DeterministicToolExecutor routing, and Recording* listeners — so ReAct/CodeAct loops run deterministically in CI with zero LLM dependency.
tags: [how-to]
---

# Agent Test Fixtures (ai4j-testing)

The biggest obstacle to testing an Agent is the model: a real `AgentModelClient` calls a remote LLM, which is slow and non-reproducible in unit tests. `ai4j-testing` promotes the fake implementations long used inside this repository into public fixtures, so a single dependency lets you script the full "model emits tool_call → tool executes → model answers" round trip.

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-testing</artifactId>
    <version>${ai4j.version}</version>
    <scope>test</scope>
</dependency>
```

## ScriptedModelClient: scripted model replay

Replays a queue of `AgentModelResult`s while recording every `AgentPrompt` the runtime actually sent:

```java
ScriptedModelClient model = new ScriptedModelClient()
        .enqueueToolCall("get_weather", "{\"city\":\"sh\"}")
        .enqueueText("Sunny, 26°C in Shanghai");

Agent agent = Agents.react()
        .modelClient(model)
        .model("test-model")
        .toolRegistry(registry)
        .toolExecutor(executor)
        .build();

agent.newSession().run("Weather in Shanghai?");

assertEquals(2, model.getInvocationCount());   // one tool_call turn + one final answer
```

Common assertion surface:

| Method | Purpose |
|--------|---------|
| `getPrompts()` | Every prompt sent to the model, in order (verify system/context assembly) |
| `getLastPrompt()` | The most recent prompt |
| `getInvocationCount()` | How many model invocations happened (verify where the loop stopped) |
| `remaining()` | Unplayed script entries |
| `failWhenExhausted(true)` | Throw instead of returning an empty result past the script end — proves the loop stops where it should |

`createStream` replays the same script as events: reasoning delta, text delta, tool-call events, then `onComplete` — streaming-path tests share one script with the non-streaming path.

## DeterministicToolExecutor: deterministic tool routing

Routes by tool name to a fixed output or a function, recording every call that reaches the executor:

```java
DeterministicToolExecutor tools = new DeterministicToolExecutor()
        .on("get_weather", "{\"temp\":26}")
        .on("echo", call -> call.getArguments())           // dynamic from arguments
        .otherwise(call -> "fallback:" + call.getName());  // optional fallback

tools.getCallsFor("get_weather");   // assert call count and arguments
```

Unregistered tools throw `IllegalArgumentException` by default — a drifted tool name fails loudly instead of silently returning empty output.

## RecordingToolExecutor / RecordingStreamListener

- `RecordingToolExecutor.returning("fixed output")`: record + fixed reply; `RecordingToolExecutor.wrapping(realExecutor)`: spy on a real executor.
- `RecordingStreamListener`: implements `AgentModelStreamListener`, recording all deltas/toolCalls/events/complete/errors for streaming assertions.

## ModelResults / ToolCalls factories

Frequent `AgentModelResult`/`AgentToolCall` shapes in tests:

```java
ModelResults.text("answer");
ModelResults.reasoned("reasoning", "answer");
ModelResults.toolCall("get_weather", "{}");          // single tool_call
ModelResults.toolCalls(call1, call2);                 // multiple tool_calls in one step
ModelResults.empty();

ToolCalls.function("get_weather", "{}");              // auto callId
ToolCalls.function("call_42", "get_weather", "{}");   // explicit callId
```

## A complete end-to-end example

```java
@Test
public void reactLoop() throws Exception {
    ScriptedModelClient model = new ScriptedModelClient()
            .enqueueToolCall("get_weather", "{\"city\":\"shanghai\"}")
            .enqueueText("Sunny in Shanghai");
    DeterministicToolExecutor tools = new DeterministicToolExecutor()
            .on("get_weather", "{\"temp\":26,\"sky\":\"sunny\"}");

    Agent agent = Agents.react()
            .modelClient(model)
            .model("test-model")
            .toolRegistry(weatherRegistry)
            .toolExecutor(tools)
            .build();

    AgentResult result = agent.newSession().run("Weather in Shanghai?");
    assertEquals("Sunny in Shanghai", result.getOutputText());
    assertEquals(1, tools.countFor("get_weather"));
}
```

> Sources: `ai4j-testing/src/main/java/io/github/lnyocly/ai4j/testing/`; the end-to-end example lives in `AgentScriptedRunTest`.
