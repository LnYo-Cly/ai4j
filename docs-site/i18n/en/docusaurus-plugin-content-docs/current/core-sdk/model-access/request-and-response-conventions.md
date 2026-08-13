---
title: "Request and Response Conventions"
description: "A unified walkthrough of request construction and response reading conventions, distinguishing locally registered fields from provider payload fields, the role of extraBody, and common integration pitfalls."
tags: [concept]
---

# Request and Response Conventions

This page explains, in one place: **in AI4J, how a request should be constructed, how a response should be read, which fields are local SDK semantics, and which fields actually end up in the provider payload.**

Many integration problems that look like provider instability are in fact just the result of not establishing a unified request/response convention.

## 1. What is AI4J's basic strategy

AI4J currently follows a very deliberate strategy at the model access layer:

1. Use a unified request object to carry the core semantics
2. Use local auxiliary fields to carry tool registration and runtime context
3. Construct the final payload explicitly at the provider service layer
4. Aggregate the returned result via a listener or response entity

This strategy is visible along two main lines:

- `ChatCompletion`
- `ResponseRequest`

## 2. Which fields are "local registration semantics", not native provider fields

Taking `ChatCompletion` as an example, the following fields are important in AI4J, but that does not mean they are sent to the provider as-is:

- `functions`
- `mcpServices`
- `builtInToolContext`
- `streamExecution`
- `passThroughToolCalls`

The same applies to `ResponseRequest`:

- `functions`
- `mcpServices`
- `streamExecution`

The role of these fields is:

- To help the SDK resolve tools and execution strategy locally first
- Rather than becoming part of the provider JSON payload directly

If you conflate this boundary, the business layer will easily assume that "because it is written into the request object, it must have already been sent to the model".

## 3. Where is the final payload determined

### Chat

Before sending, `OpenAiChatService` will:

- First use `ToolUtil.getAllTools(...)` to expand `functions` / `mcpServices`
- Then write the result into `chatCompletion.tools`
- Clear `parallelToolCalls` where appropriate
- Finally serialize the entire `ChatCompletion`

### Responses

`OpenAiResponsesService` is even more explicit:

1. First `ResponseRequestToolResolver.resolve(request)`
2. Then call `buildOpenAiPayload(request)`
3. Put only the allowed fields into the final `payload`
4. Then supplement allowed extension fields from `extraBody`

This shows that AI4J is not "the provider receives whatever shape the request object has", but rather that the service layer applies a formal projection.

## 4. What exactly is the role of `extraBody`

Both `ChatCompletion` and `ResponseRequest` expose `extraBody`.

Its purpose is not to let the business layer freely bypass the SDK, but rather:

- Without breaking the modeling of the core semantics
- To leave a formal outlet for provider-specific extension fields

In `OpenAiResponsesService` in particular, `extraBody` is filtered against an allowlist before it enters the payload.

So the correct usage is:

- Core semantics should go through the formal fields first
- Provider-specific extensions go into `extraBody`

Rather than dumping everything into `extraBody` in one pile.

```java
// Core semantics go through formal fields
ChatCompletion chat = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Hello"))
        .reasoningEffort("medium")     // Formal field, unified across providers
        .build();

// Only provider-specific extensions that the SDK does not model go through extraBody
ChatCompletion chat2 = ChatCompletion.builder()
        .model("deepseek-chat")
        .message(ChatMessage.withUser("Hello"))
        .extraBody("thinking", mapOf("type", "enabled"))   // DeepSeek-specific
        .build();
```

In `ChatCompletion`, `extraBody` is unfolded to the top level of the JSON via `@JsonAnyGetter`; on a name collision the `extraBody` value overrides the formal field, so before using it, confirm whether the SDK already has a corresponding field.

## 5. Why response reading also needs a unified convention

Without a unified convention, the most common bad outcomes are:

- Different call sites each parse the provider's raw JSON on their own
- Sync and streaming results are read interchangeably
- `Chat` and `Responses` return objects are treated as the same structure by the business layer

The recommended reading approach that AI4J currently provides is actually quite clear:

### Chat non-streaming

Prefer to build around:

- `ChatCompletionResponse`
- `Choice`
- `ChatMessage`
- `Usage`

```java
ChatCompletionResponse resp = chatService.chatCompletion(req);
String text = resp.getChoices().get(0).getMessage().getContent().getText();
long total = resp.getUsage().getTotalTokens();
```

### Chat streaming

Prefer to build around:

- `SseListener.output`
- `reasoningOutput`
- `toolCalls`
- `finishReason`

```java
SseListener listener = new SseListener() {
    @Override protected void send() { System.out.print(getCurrStr()); }
};
chatService.chatCompletionStream(req, listener);
String full = listener.getOutput().toString();        // Aggregated full text
String reasoning = listener.getReasoningOutput();     // Reasoning fragments
String stop = listener.getFinishReason();
```

### Responses non-streaming

Prefer to build around:

- `Response`

```java
Response response = responsesService.create(req);
// Iterate output items to read text (not choice.message)
StringBuilder text = new StringBuilder();
for (ResponseItem item : response.getOutput()) {
    if (item.getContent() == null) continue;
    for (ResponseContentPart part : item.getContent()) {
        if (part.getText() != null) text.append(part.getText());
    }
}
int inputTokens = response.getUsage().getInputTokens();
```

### Responses streaming

Prefer to build around:

- `ResponseSseListener.events`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `response`

```java
ResponseSseListener listener = new ResponseSseListener() {
    @Override protected void onEvent() { System.out.print(getCurrText()); }
};
responsesService.createStream(req, listener);
String full = listener.getOutputText().toString();
String reasoning = listener.getReasoningSummary().toString();
```

## 6. A practical team rule

It is recommended to standardize on this rule:

1. The business layer reads the unified entities first
2. Provider-specific details are read only in the adapter layer or a few boundary layers
3. Do not hand-craft raw JSON paths all over the business layer

This is not a matter of "more elegant style", but of ensuring that as the SDK evolves, provider differences do not propagate across the entire call chain.

## 7. Common pitfalls

### Pitfall 1: Treating `functions` as the final provider field

It is only the local tool registration input; what actually gets sent to the provider is the resolved `tools`.

### Pitfall 2: Treating the streaming listener as a print callback

They are in fact aggregators that already carry a large amount of structured state.

### Pitfall 3: The business layer depending directly on the provider's raw JSON structure

Flexible in the short term, but in the long term it leaks all adaptation cost into the business layer.

### Pitfall 4: `extraBody` becoming the main channel

Once every field goes through `extraBody`, the unified request object loses its meaning.

## 8. Conclusion of this page

> AI4J's core convention at the request/response layer is: unified entities carry the core semantics, local auxiliary fields carry registration and runtime semantics, the provider service is responsible for projecting them into the final payload, and the listener / response entity is responsible for re-aggregating the return into a stable read surface. Understanding this convention is what lets you avoid mixing provider differences with local runtime semantics.
