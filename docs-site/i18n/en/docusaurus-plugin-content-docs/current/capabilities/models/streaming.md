---
title: "Streaming"
description: "Explains the aggregation model behind the two main streaming lines, Chat and Responses: the state held by SseListener and ResponseSseListener, tool-call aggregation, and termination-condition differences."
tags: [concept]
---

# Streaming

This page covers streaming semantics only.

In AI4J, "streaming" is not a single unified token-output concept. It is three distinct lines, each with its own consumption model:

- `Chat` streaming
- `Responses` streaming
- `Messages` streaming (Anthropic native)

All three are SSE-based, but their consumption targets and state organization differ: `Chat` and `Responses` use a listener-held-state aggregation model, while `Messages` uses a typed-callback model.

:::tip Every code block on this page is runnable
Each Java snippet below comes from the executable test
[`StreamingDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/StreamingDocExamplesLiveTest.java)
and has been verified against real gateways. To run locally:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_API_HOST=https://your-gateway/   # optional
export OPENAI_CHAT_MODEL=gpt-4o-mini           # optional
export ANTHROPIC_API_KEY=sk-ant-...            # required for the Messages example
export ANTHROPIC_BASE_URL=https://api.anthropic.com/  # optional
export ANTHROPIC_MODEL=claude-haiku-4-5-20251001      # optional

mvn -pl ai4j test -Plive-provider-tests -Dtest=StreamingDocExamplesLiveTest
```

Tests for a line whose key is absent are skipped automatically and never fail the build.
:::

## 0. Minimal runnable examples for the three lines

Get them running first, then read the semantics below.

### `Chat` streaming: `SseListener` aggregates deltas

```java
ChatCompletion chatCompletion = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Count from 1 to 5, digits only"))
        .stream(Boolean.TRUE)
        .build();

SseListener sseListener = new SseListener() {
    @Override
    protected void send() {
        // fires on each delta; getCurrStr() is the increment
        System.out.print(getCurrStr());
    }
};

chatService.chatCompletionStream(chatCompletion, sseListener);

// after the stream ends, aggregated state lives on the listener
String full = sseListener.getOutput().toString();
System.out.println("finishReason: " + sseListener.getFinishReason());
System.out.println("usage: " + sseListener.getUsage());
```

### `Responses` streaming: `ResponseSseListener` event-driven aggregation

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("Count from 1 to 5, digits only")
        .stream(Boolean.TRUE)
        .build();

ResponseSseListener listener = new ResponseSseListener() {
    @Override
    protected void onEvent() {
        // getCurrText() is the text increment carried by this event
        String delta = getCurrText();
        if (delta != null && !delta.isEmpty()) {
            System.out.print(delta);
        }
    }
};

responsesService.createStream(request, listener);

// after the stream ends, aggregated state lives on the listener
System.out.println("full text: " + listener.getOutputText());
System.out.println("event count: " + listener.getEvents().size());
```

### `Messages` streaming: `AnthropicStreamHandler` typed callbacks

```java
AnthropicChatCompletion request = new AnthropicChatCompletion();
request.setModel("claude-haiku-4-5-20251001");
request.setMaxTokens(128);
AnthropicMessage user = new AnthropicMessage();
user.setRole("user");
user.setContent("Count from 1 to 5, digits only");
request.setMessages(new ArrayList<>(Collections.singletonList(user)));

AnthropicStreamHandler handler = new AnthropicStreamHandler() {
    @Override
    public void onDeltaText(String delta) {
        // body text increment, print as it arrives
        System.out.print(delta);
    }

    @Override
    public void onStopReason(String stopReason, long inputTokens, long outputTokens) {
        System.out.println("stopReason: " + stopReason);
    }

    @Override
    public void onComplete() {
        // message_stop: stream finished
    }
};

// messagesStream blocks internally until message_stop or failure
messagesService.messagesStream(request, handler);
```

Service objects are built exactly as for synchronous calls (`new AiService(configuration).getChatService(PlatformType.OPENAI)` / `getResponsesService(...)` / `getMessagesService(PlatformType.ANTHROPIC)`); see the test class above for the fully compilable version.

## 1. What `Chat` streaming actually aggregates

The core object on the `Chat` side is `SseListener`.

It does not hold a raw piece of text; it holds a set of runtime state:

- `output`
- `currStr`
- `currData`
- `currToolName`
- `reasoningOutput`
- `usage`
- `toolCalls`
- `toolCall`
- `finishReason`

From this set of fields you can see that AI4J Chat streaming is not just "print tokens as they arrive" — it already supports consuming, simultaneously:

- Plain text deltas
- Reasoning content
- Complete or fragmented tool calls
- Usage rollups

**Diagram: SSE streaming sequence** — `chatCompletionStream` opens an EventSource with a `SseListener`; frames feed `onEvent`, deltas and tool-call fragments accumulate, `send()` yields the result, `awaitCompletion` blocks until close.

import useBaseUrl from '@docusaurus/useBaseUrl';

<iframe
  src={useBaseUrl('/archify/chat-streaming-sse.html')}
  title="chat-streaming-sse"
  style={{width: '100%', height: 940, border: '1px solid var(--ifm-color-emphasis-300)', borderRadius: 8}}
/>

<a href={useBaseUrl('/archify/chat-streaming-sse.html')} target="_blank" rel="noopener noreferrer">Open the full-screen diagram in a new tab</a>

## 2. Why `Chat` streaming tool calls are not simple

`SseListener.onEvent(...)` currently does dedicated handling for tool calls:

- It recognizes complete tool calls
- It recognizes fragmented argument deltas
- It merges multiple fragments of the same tool call
- It completes the final aggregation when `finishReason = tool_calls`

This matters because many providers do not deliver full tool-call arguments in one shot; they send them in chunks.

AI4J absorbs this at the listener layer, so the upper runtime does not have to reassemble things itself.

## 3. How `Chat` streaming relates to the automatic tool loop

In `OpenAiChatService.chatCompletionStream(...)`, once the streaming request ends, it reads:

- `eventSourceListener.getFinishReason()`
- `eventSourceListener.getToolCalls()`

If `finishReason == tool_calls` and `passThroughToolCalls` is not enabled, it will:

1. Backfill the assistant tool-call message into `messages`
2. Execute `ToolUtil.invoke(...)`
3. Append the tool output message
4. Issue the next round of streaming requests

In other words, Chat streaming is not a "single SSE output"; it can be part of an automatic tool loop.

## 4. What `Responses` streaming actually aggregates

The core object on the `Responses` side is `ResponseSseListener`.

It currently maintains:

- `events`
- `currEvent`
- `response`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `currText`
- `currFunctionArguments`

And it drives aggregation through event types:

- `response.output_text.delta`
- `response.output_text.done`
- `response.reasoning_summary_text.delta`
- `response.reasoning_summary_text.done`
- `response.function_call_arguments.delta`
- `response.function_call_arguments.done`

This behaves more like an event-driven state machine than a message-increment printer.

## 5. `Responses` termination conditions differ from `Chat`

In `OpenAiResponsesService.convertEventSource(...)`, `Responses` streaming completes when one of these terminal events appears:

- `response.completed`
- `response.failed`
- `response.incomplete`

That is, `Responses` decides "is the stream finished?" with a mental model oriented around the response lifecycle, not around `finish_reason`.

By contrast, the `Chat` side emphasizes:

- `stop`
- `tool_calls`
- `[DONE]`

This is the fundamental difference between the two lines in streaming-termination semantics.

## 6. `Messages` streaming: callbacks, not aggregation

The `Messages` side (Anthropic native) takes a third path: **typed callbacks**, rather than a listener holding state.

The entry point is `IMessagesService.messagesStream(...)`, where the consumer passes in an `AnthropicStreamHandler`:

```java
messages.messagesStream(request, new AnthropicStreamHandler() {
    @Override public void onStart(String messageId, String model) { }
    @Override public void onDeltaText(String text) { }
    @Override public void onThinkingDelta(String thinking) { }
    @Override public void onToolUseComplete(int index, String id, String name, String inputJson) { }
    @Override public void onStopReason(String stopReason, long in, long out) { }
    @Override public void onComplete() { }
    @Override public void onError(Throwable t) { }
});
```

Each callback on `AnthropicStreamHandler` corresponds to one kind of native SSE event. `AnthropicMessagesService.toEventListener(...)` already parses and absorbs the Anthropic events, so the caller does not need to parse SSE itself:

| Native event | Callback |
| --- | --- |
| `message_start` | `onStart(messageId, model)` / `onUsage(usage)` |
| `content_block_delta` (text_delta) | `onDeltaText(text)` |
| `content_block_delta` (thinking_delta) | `onThinkingDelta(thinking)` |
| `content_block_start` (tool_use) | `onToolUseStart(index, toolUseId, name)` |
| `content_block_delta` (input_json_delta) | `onToolUseDelta(index, partialJson)` |
| `content_block_stop` (tool_use) | `onToolUseComplete(index, id, name, inputJson)` |
| `message_delta` | `onStopReason(...)` / `onUsage(usage)` |
| `message_stop` | `onComplete()` |

All methods have default empty implementations; override them as needed.

### Key differences from `Chat` / `Responses` streaming

- **Who holds the state**: In `Chat` / `Responses`, the listener (`SseListener` / `ResponseSseListener`) holds and aggregates the runtime state; `Messages` keeps no aggregated state — it throws native semantics directly at the caller via callbacks, and the caller decides how to accumulate state.
- **Synchronization semantics**: `messagesStream(...)` uses a `CountDownLatch` internally and blocks until `message_stop` or failure, with the timeout governed by `AnthropicConfig.streamTimeoutMillis`; the `Chat` / `Responses` listeners are typically non-blocking event sources.
- **Tool-call aggregation**: `Chat` merges fragmented arguments into a complete tool call inside the listener; `Messages` likewise accumulates `input_json_delta` within each content block and, at `content_block_stop`, hands the full input JSON out via `onToolUseComplete(...)`.
- **Reuse scope**: The same event-parsing logic serves both native `IMessagesService.messagesStream(...)` and the unified adapter `AnthropicChatService` (which bridges the callbacks back to OpenAI chunks and feeds them into `SseListener`). The event parsing is never implemented twice.

:::note
For the exact callback semantics and field mapping, see [Messages (Anthropic native)](/docs/capabilities/models/messages#streaming-native-event-callbacks).
:::

## 7. Why upper-layer runtimes prefer different lines

### Scenarios that prefer `Chat` streaming

Better suited for:

- Displaying conversation output directly
- Building incremental UIs along the message mental model
- Treating a tool call as an insertion step within the conversation

### Scenarios that prefer `Responses` streaming

Better suited for:

- Event-driven state machines
- Observing reasoning in isolation
- Observing the formation of function arguments in isolation
- Keeping the full event sequence for trace or replay

## 8. Streaming is not just "turn on `stream=true`"

In AI4J, streaming also involves two kinds of local runtime control:

- `streamOptions`
- `streamExecution`

Here `streamExecution` is handed to `StreamExecutionSupport.execute(...)` to control how the actual EventSource is executed.

This shows the SDK does more than flip provider SSE on — it gives the host an extra execution-layer hook.

## 9. Where to look first when debugging streaming issues

### Chat streaming

Look first at:

- `SseListener.currData`
- `finishReason`
- `toolCalls`
- `reasoningOutput`

### Responses streaming

Look first at:

- `currEvent`
- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`

If you stare only at the final string from the start, it is easy to miss that the real problem is tool arguments that never closed, reasoning events that never arrived, or a response that already entered `incomplete`.

## 10. Takeaway for this page

> AI4J streaming is not a single "token stream" abstraction. `Chat` streaming is organized around message increments, finish reason, and tool-call aggregation; `Responses` streaming is organized around event type, the response lifecycle, and state closure; `Messages` streaming exposes Anthropic native events as typed callbacks and leaves state accumulation to the caller. All three run over SSE, but they fit completely different upper-layer consumption styles.
