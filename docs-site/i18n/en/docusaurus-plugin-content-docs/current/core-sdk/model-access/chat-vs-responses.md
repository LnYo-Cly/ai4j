---
title: "Chat vs Responses"
description: "Compare the Chat and Responses main lines across input mental model, provider coverage, tool integration, streaming semantics, and multimodal projection to help you choose."
tags: [concept]
---

# Chat vs Responses

This is one of the most important selection questions in the AI4J foundation.

The real difference is not "which one is newer", but rather **what kind of model output semantics you actually want to consume, and whether you care more about provider coverage or event structure**.

:::tip All code on this page is runnable
The comparison examples below come from
[`ChatDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ChatDocExamplesLiveTest.java)
and
[`ResponsesDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ResponsesDocExamplesLiveTest.java),
and have been verified against real gateways.
:::

## 1. Start with a one-line conclusion

- `Chat`: a message-oriented main line, with broad provider coverage; suitable both for getting things working quickly and for automatic tool loops.
- `Responses`: an event-oriented main line, more strongly structured; suitable for runtime, tracing, and complex interactions.

If you have no stronger constraint, starting with `Chat` is usually safer; if you are already consuming runtime-level state, take a serious look at `Responses`.

## 2. From an input-centric view, the two have different mental models

### `Chat`

The central object is:

- `messages`

A single request is more like:

- Initiating a full conversation carrying the current conversation context.

### `Responses`

The central objects are:

- `input`
- `instructions`
- `previousResponseId`

A single request is more like:

- Advancing state within a more structured response protocol.

So the difference between the two is not a tweak of parameter names, but a different interface philosophy.

The same "one-sentence Q&A" compares in code form across the two lines:

```java
// Chat: a messages list
ChatCompletion chatReq = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Explain a vector database in one sentence"))
        .build();
ChatCompletionResponse chatResp = chatService.chatCompletion(chatReq);
String chatAnswer = chatResp.getChoices().get(0).getMessage().getContent().getText();

// Responses: an input + item list
ResponseRequest resReq = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("Explain a vector database in one sentence")
        .build();
Response resResp = responsesService.create(resReq);
String resAnswer = "";   // iterate over the content parts of output items
for (ResponseItem item : resResp.getOutput()) {
    for (ResponseContentPart part : item.getContent()) {
        if (part.getText() != null) resAnswer += part.getText();
    }
}
```

## 3. In terms of provider coverage, `Chat` is closer to the default main line

Currently `AiService.createChatService(...)` supports:

- OpenAI
- Zhipu
- DeepSeek
- Moonshot
- Hunyuan
- Lingyi
- Ollama
- Minimax
- Baichuan
- DashScope
- Doubao

While `AiService.createResponsesService(...)` currently supports only:

- OpenAI
- Doubao
- DashScope

This means if your top priorities are:

- Broader provider coverage
- Lower migration cost

Then `Chat` is closer to the default main line.

## 4. In terms of tool integration, the two share a foundation but have different wiring points

Both can attach:

- `functions`
- `mcpServices`
- `toolChoice`
- `parallelToolCalls`

But the wiring paths differ:

### Chat

Called directly inside `OpenAiChatService`:

`ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())`

### Responses

First goes through:

`ResponseRequestToolResolver.resolve(request)`

Then writes the result into `request.tools`

That is to say:

- The tool resolution foundation is shared.
- The wiring point and the subsequent runtime semantics are not the same.

## 5. In terms of the auto-execution mental model, `Chat` is more proactive

`Chat` currently supports an automatic tool loop inside the service layer:

1. Receive `tool_calls`
2. Execute the tool via `ToolUtil.invoke(...)`
3. Append the tool output message
4. Continue to the next round of the request

If `passThroughToolCalls` is not enabled, this chain keeps going in both sync and streaming mode.

While `Responses` currently leans toward:

- Resolving tools well
- Aggregating events and the response out
- Leaving subsequent orchestration to the upper-layer runtime

So if you want "the SDK to run a round of tools for me first", `Chat` is smoother:

```java
// Chat: register with functions(...); when the SDK receives tool_calls it executes
// and back-fills automatically. What you get here is already the final answer
// after tool execution.
ChatCompletionResponse resp = chatService.chatCompletion(ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("What is the status of order A1001?"))
        .functions("getOrderStatus")
        .build());
```

While `Responses` hands the `function_call` item back as-is; whether to execute it is up to you:

```java
// Responses: tools are resolved into the payload, but the function_call item is
// handed back to the upper layer
Response response = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("How much is AAPL right now?")
        .functions("getStockPrice")
        .build());
// response.getOutput() may contain an item with type="function_call";
// you read its name/arguments, execute it, and back-fill with function_call_output
```

## 6. In terms of streaming semantics, the two consume completely different objects

### Chat streaming

The core aggregator is `SseListener`, focused around:

- `output`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

It is more like "message stream + tool call aggregation".

### Responses streaming

The core aggregator is `ResponseSseListener`, focused around:

- `events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `response`

It is more like "event state machine + final response aggregation".

If your UI or runtime needs to distinguish "text delta" from "function argument delta", `Responses` is more natural.

## 7. In terms of multimodal projection, the two share the same conversation foundation

On this point the two are actually consistent.

The same `ChatMemoryItem`:

- In `Chat` becomes `Content.MultiModal`
- In `Responses` becomes `input_text / input_image`

So multimodal is not the fundamental divide between the two; the real divide remains whether the mental model is message-oriented or event-oriented.

## 8. When to prefer `Chat`

The following situations usually lead you to choose `Chat` first:

- First time integrating AI4J
- Code migrated from chat-completions
- Provider coverage matters more than event structure
- You want the SDK to auto-close the loop on tool calls
- The upper layer does not yet need a complex state machine

## 9. When to prefer `Responses`

The following situations fit `Responses` better:

- You need event-level consumption
- You need to observe reasoning separately
- You need function argument deltas
- You need the full response lifecycle state
- You are building an agent / coding / trace / structured UI runtime

## 10. A simple way to decide

If you care more about:

- "What did the model finally say"

Start with `Chat`.

If you care more about:

- "What happened during the process"

Look at `Responses`.

## 11. Conclusion of this page

> `Chat` and `Responses` in AI4J are not a new-vs-old relationship, but rather two different main lines for model access. `Chat` is centered on messages and an automatic tool loop, better suited for broad coverage and quick integration; `Responses` is centered on events and a structured response, better suited for runtime, tracing, and complex interactions. The real selection criterion is not "which is more advanced", but whether what you want to consume is the message result or the process state.
