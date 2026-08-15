---
sidebar_position: 3
title: "Model Client Selection"
description: "Compares ChatModelClient vs ResponsesModelClient: protocol mapping of systemPrompt/instructions, top-level field pass-through differences, streaming signals, and memoryItems shapes — to help you pick the right model protocol path for your Agent."
tags: [concept]
---

# Model Client Selection

`AgentModelClient` does not decide "which provider you use", but rather:

> In what protocol shape the `AgentPrompt` assembled by the Agent runtime is ultimately sent to the model.

The two core implementations today are:

- `ChatModelClient`
- `ResponsesModelClient`

They are not simply an "old protocol" and a "new protocol", nor two losslessly-equivalent shells. The real differences land on:

- how prompt fields map
- how tools are passed through
- which intermediate signals the stream can emit
- what the final `memoryItems` look like
- which advanced fields are in fact never passed through on a given path

## 1. Start with 6 key design decisions

### 1.1 This is a protocol adaptation choice, not a runtime choice

`BaseAgentRuntime` depends only on:

- `AgentModelClient`

It does not directly depend on:

- `IChatService`
- `IResponsesService`

Therefore:

- ReAct / CodeAct / DeepResearch are runtime semantic choices
- Chat / Responses are model protocol choices

Keep these two layers distinct.

### 1.2 `systemPrompt` and `instructions` are not mapped equivalently on the two paths

This is arguably the single most important fact on this page.

#### Chat path

- `systemPrompt` -> one system message
- `instructions` -> an additional system message appended

#### Responses path

- `systemPrompt` -> `ResponseRequest.instructions`
- `instructions` -> wrapped as `systemMessage(...)` and inserted at the very front of the input items

In other words:

- On the Chat path both land in the message sequence
- On the Responses path both land in two different protocol positions

### 1.3 The Responses path passes through more top-level fields

`ResponsesModelClient.toResponseRequest(...)` pushes these fields straight down:

- `tools`
- `toolChoice`
- `parallelToolCalls`
- `temperature`
- `topP`
- `maxOutputTokens`
- `reasoning`
- `store`
- `user`
- `extraBody`

`ChatModelClient.toChatCompletion(...)`, by contrast, only passes through a subset.

This means certain Agent-level configurations, on the Chat path, are effectively "written into the prompt, but never become protocol-level top-level parameters".

### 1.4 The Chat path has a narrower `toolChoice` constraint

`ChatModelClient` only sets `builder.toolChoice(...)` when:

```java
prompt.getToolChoice() instanceof String
```

`ResponsesModelClient`, instead, passes `toolChoice` through verbatim.

If you rely on more complex tool-choice structures, the Responses path is the more expressive one.

### 1.5 The `memoryItems` shape differs between the two paths

`ChatModelClient.toModelResult(...)` constructs the memory items itself:

- plain text answer -> `assistant` message
- with tool calls -> `assistantToolCallsMessage(...)`

`ResponsesModelClient.toModelResult(...)` takes:

- `response.getOutput()`

and stuffs it into `memoryItems` as-is.

So for the same call:

- the Chat path yields a converted, unified message shape
- the Responses path preserves an item list much closer to the raw response output

### 1.6 What stream sees depends not on how strong the underlying protocol is, but on what the current adapter actually emits

Many people assume upfront that:

- the Responses protocol is more structured
- therefore the Agent-level stream must be richer

The current implementation is not like that.

You need to look at what the current client's `createStream(...)` actually calls back.

## 2. What `ChatModelClient` actually does

### 2.1 How a prompt is translated into a chat completion

The core mappings of `toChatCompletion(...)` are:

1. `systemPrompt` becomes the first system message
2. `instructions` becomes the second system message
3. `items` are converted one by one into `ChatMessage`
4. `tools` is converted into `List<Tool>`
5. when tools are non-empty, set `passThroughToolCalls(true)`

The top-level fields it actually pushes down include:

- `model`
- `messages`
- `stream`
- `streamExecution`
- `temperature`
- `topP`
- `maxCompletionTokens`
- `user`
- `parallelToolCalls`
- `toolChoice` (string only)
- `tools`
- `extraBody`

### 2.2 Which Agent fields are not pushed down at the protocol level on the Chat path

The current `ChatModelClient` does not explicitly push down:

- `reasoning`
- `store`

So if you treat these two as strong protocol parameters, the Responses path matches your intuition better.

### 2.3 `items` is not simple string concatenation

`ChatModelClient.convertToMessage(...)` accepts richer input shapes than just "user text".

It handles:

- a plain `ChatMessage`
- a map with `type=message`
- a map with `type=function_call_output`
- multimodal `input_text`
- multimodal `input_image`

This shows that the Chat path does not only consume plain text messages; internally it is already doing a layer of item -> chat message protocol normalization.

### 2.4 The Chat streaming path preserves reasoning delta

`StreamingSseListener.send()` distinguishes:

- `isReasoning() == true` -> `onReasoningDelta(delta)`
- otherwise -> `onDeltaText(delta)`

So within the current Agent adaptation layer, the Chat path can surface reasoning token-level increments earlier.

This is valuable for real-time debug panels and trace timelines.

## 3. What `ResponsesModelClient` actually does

### 3.1 How a prompt is translated into a response request

The mapping order in `toResponseRequest(...)` is:

1. `model`
2. `input(buildItems(prompt))`
3. `tools`
4. `toolChoice`
5. `parallelToolCalls`
6. `temperature`
7. `topP`
8. `maxOutputTokens`
9. `reasoning`
10. `store`
11. `user`
12. `stream`
13. `streamExecution`
14. `systemPrompt -> instructions`
15. `extraBody`

This path preserves the `AgentPrompt` top-level fields noticeably more completely.

### 3.2 `instructions` is front-inserted as an input item, not as a top-level instructions

The logic in `buildItems(prompt)` is:

- first copy `prompt.getItems()`
- if there is an `instructions`
- at index `0`, insert an `AgentInputItem.systemMessage(...)`

So on the Responses path:

- `systemPrompt` is a request-level instruction
- `instructions` is a leading system message inside the input items

This is fundamentally different from the Chat path's "two system messages in sequence".

### 3.3 The Responses streaming path currently looks more like "text increments + a single unified wrap-up at the end"

`ResponsesModelClient.createStream(...)` currently calls back only:

- `onDeltaText(...)`
- `onRetry(...)`
- `onError(...)`
- `onComplete(...)`

It does not, unlike the Chat path, emit reasoning deltas separately.

More importantly, tool calls are not surfaced directly through the stream listener mid-stream; instead:

- wait for the stream to finish
- `ResponseSseListener.getResponse()`
- then uniformly run `toModelResult(response)`
- finally the runtime continues from `AgentModelResult.toolCalls`

So in the current implementation, the Responses path leans more toward:

- watch text during the stream
- watch the full structure after the stream

## 4. How the two paths ultimately converge back on unified Agent semantics

Even though the protocols differ, the runtime ultimately only understands `AgentModelResult`.

### Chat path

`toModelResult(ChatCompletionResponse)` extracts:

- `outputText`
- `reasoningText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

### Responses path

`toModelResult(Response)` extracts:

- `outputText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

The key unifying move here is:

- different provider responses
  -> uniformly projected into an `AgentModelResult`

What happens afterward in the runtime does not care whether it came from Chat or Responses.

## 5. Where stream behavior truly differs

### 5.1 Chat path

Currently surfaces earlier:

- reasoning delta
- text delta
- retry

Then finally yields a unified `AgentModelResult` in `onComplete(...)`.

### 5.2 Responses path

Currently mainly surfaces:

- text delta
- retry

Structured results, tool calls, and the final output are returned all at once after the stream ends.

### 5.3 Neither path "executes tools directly mid-stream"

Although `AgentModelStreamListener` has:

- `onToolCall(AgentToolCall call)`

neither of the two current clients actually invokes this callback during streaming to surface tools early.

Actual tool execution still depends on:

1. `createStream(...)` completing
2. generating the final `AgentModelResult`
3. `BaseAgentRuntime` taking `toolCalls` from the result
4. then entering the unified tool loop

So do not mistakenly assume "the Chat stream emits tokens on one side while the runtime is internally executing tools in parallel".

## 6. The semantics of stream cancellation and thread interruption

Both paths share a very similar design:

- maintain an `ACTIVE_STREAMS`
- keyed by the current `Thread`
- valued by the underlying SSE listener

and expose:

- `cancelActiveStream(Thread thread)`

Inside `createStream(...)`, if the thread is interrupted:

- it first cancels the stream
- then throws `InterruptedException`

This tells you the current cancellation semantics are:

- thread-level cancellation
- not a separate request-id / run-id cancellation protocol

This works well inside a CLI / session runtime, but it also means you must understand that the cancellation boundary is "the thread", not "any arbitrary logical task".

## 7. When to prefer Chat

Typical scenarios better suited to Chat:

- you already have a mature chat-completions-compatible pipeline
- you lean more on a message-sequence mental model
- you want to see reasoning delta earlier in the Agent-level streaming callbacks
- your current focus is classic conversation and tool loops, rather than the full expression of response top-level fields

## 8. When to prefer Responses

Typical scenarios better suited to Responses:

- you want fuller pass-through of `reasoning`, `store`, `toolChoice`
- you prefer to preserve the structure of response output items
- your downstream handling leans more toward structured results than a pure message stream
- you accept "during the stream mainly watch text; structured information is wrapped up at the end"

## 9. The things most often stated wrongly

### 9.1 "Responses is always more advanced than Chat"

False.

They are two different protocol tracks, not a simple junior/senior relationship.

### 9.2 "Chat and Responses are equivalent in prompt mapping"

False.

Especially for:

- `systemPrompt`
- `instructions`
- `reasoning`
- `store`
- `toolChoice`

The actual protocol positions and fidelity of these fields all differ.

### 9.3 "Responses streaming can naturally feed every structured event to the Agent UI in real time"

False.

The Agent-level stream adaptation of the current `ResponsesModelClient` is still mainly text increments.

### 9.4 "The Chat path executes tools directly within the stream"

False.

Tool execution in both current clients is still driven forward in a unified way after the runtime receives the final `AgentModelResult`.

## 10. A practical selection table

| Concern | Better fit |
| --- | --- |
| More mature chat-compatible wiring | `ChatModelClient` |
| Fuller pass-through of response top-level fields | `ResponsesModelClient` |
| Streaming reasoning visibility | `ChatModelClient` |
| Closer to response output item structure | `ResponsesModelClient` |
| Complex `toolChoice` / `reasoning` / `store` expression | `ResponsesModelClient` |

## 11. Examples

### Chat path

```java
Agent agent = Agents.react()
        .modelClient(new ChatModelClient(chatService))
        .model("your-chat-model")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();
```

### Responses path

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("gpt-4.1")
        .systemPrompt("You are a concise assistant.")
        .options(AgentOptions.builder().maxSteps(1).build())
        .build();
```

On the surface only one line differs, but the protocol semantics are not the same.

## 12. Recommended source-code reading order

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/AgentPrompt.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ChatModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/model/ResponsesModelClient.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

## 13. Further reading

1. [Quickstart](/docs/agent/quickstart)
2. [System Prompt vs Instructions](/docs/agent/system-prompt-vs-instructions)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
