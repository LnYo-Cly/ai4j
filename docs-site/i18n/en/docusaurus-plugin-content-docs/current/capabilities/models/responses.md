---
title: "Responses"
description: "Breaks down the AI4J Responses line: ResponseRequest semantics, the shared tool-resolution foundation, payload construction, streaming event aggregation, and the runtime-friendly execution model."
tags: [concept]
---

# Responses

`Responses` is the more modern, more structured model-access line in AI4J today.

Its biggest difference from `Chat` is not that the field name changes from `messages` to `input`, but rather that **it treats model output first and foremost as a stream of events and items, not as a single assistant message**.

:::tip All code on this page runs
Every Java snippet below comes from the executable test
[`ResponsesDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ResponsesDocExamplesLiveTest.java),
verified against a real OpenAI-compatible gateway. To reproduce locally:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_API_HOST=https://your-gateway/   # optional
export OPENAI_CHAT_MODEL=gpt-4o-mini           # optional

mvn -pl ai4j test -Plive-provider-tests -Dtest=ResponsesDocExamplesLiveTest
```

Without `OPENAI_API_KEY` these tests are skipped automatically and will not fail the build.
:::

## 0. Run it first

The minimal viable call. Note that Responses output is an **item list** — every item carries content parts. Reading assistant text goes through this structure, unlike Chat where you read `choice.message.content` directly:

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
openAiConfig.setApiHost("https://api.openai.com/");   // replace with your gateway address

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

IResponsesService responsesService = new AiService(configuration)
        .getResponsesService(PlatformType.OPENAI);

ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("Explain reactive programming in one sentence")
        .build();

Response response = responsesService.create(request);

// Responses output is an item list: iterate output, each item carries content parts
String answer = "";
for (ResponseItem item : response.getOutput()) {
    if (item.getContent() == null) continue;
    for (ResponseContentPart part : item.getContent()) {
        if (part.getText() != null) answer += part.getText();
    }
}
System.out.println(answer);

System.out.println("input=" + response.getUsage().getInputTokens()
        + " output=" + response.getUsage().getOutputTokens()
        + " total=" + response.getUsage().getTotalTokens());
```

`response.getStatus()` is typically `"completed"`, and `getObject()` is `"response"`.

## 1. Key source entry points

The most important objects for understanding `Responses` are:

- `platform/openai/response/entity/ResponseRequest.java`
- `platform/openai/response/entity/Response.java`
- `platform/openai/response/OpenAiResponsesService.java`
- `tool/ResponseRequestToolResolver.java`
- `listener/ResponseSseListener.java`
- `service/factory/AiService.java`

`OpenAiResponsesService` is particularly important because it spells out the mapping between "request object fields" and "the final provider payload" very clearly.

## 2. What is the central semantic of `ResponseRequest`

The main fields of `ResponseRequest` today include:

- `model`
- `input`
- `instructions`
- `previousResponseId`
- `maxOutputTokens`
- `parallelToolCalls`
- `reasoning`
- `store`
- `stream`
- `streamOptions`
- `text`
- `toolChoice`
- `tools`
- `truncation`
- `user`
- `extraBody`

It also retains two local-only registration helper fields:

- `functions`
- `mcpServices`

Just like in `Chat`, these two fields are never sent to the provider directly; they are only inputs used during local tool resolution.

`instructions` acts as a system-level instruction, and `maxOutputTokens` caps output length:

```java
Response response = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .instructions("You may only answer in Chinese, in no more than 20 characters.")
        .input("What is a vector database?")
        .maxOutputTokens(200)
        .build());
```

## 3. What happens before the provider is called

The first critical thing both `OpenAiResponsesService.create(...)` and `createStream(...)` do is:

`request = ResponseRequestToolResolver.resolve(request);`

`ResponseRequestToolResolver` will:

1. Check whether `functions` or `mcpServices` exist on the request
2. If so, call `ToolUtil.getAllTools(...)`
3. **Project** the resolved local function tools and MCP tools into `request.tools`
4. Return the new request

So `Responses` and `Chat` are not two disjoint tool systems; they share the same tool-resolution foundation, just with different entry points:

- `Chat` resolves tools directly inside the chat service
- `Responses` goes through `ResponseRequestToolResolver` first

:::warning Responses function-tool shape differs from Chat
Chat Completions nests function declarations **under** the `function` key, whereas the Responses API requires them **flat** (`type` / `name` / `description` / `parameters` at the top level). `ResponseRequestToolResolver` performs this projection automatically — so registering with the same `@FunctionCall` works correctly on both lines.

```java
@FunctionCall(name = "getStockPrice", description = "Query the current price of a stock")
public static class GetStockPrice implements Function<GetStockPrice.Request, String> {
    @Data @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Stock ticker, e.g. AAPL")
        private String symbol;
    }

    @Override
    public String apply(Request request) {
        return "{\"symbol\":\"" + request.getSymbol() + "\",\"price\":195.42}";
    }
}

// Responses line: register via functions(...), the SDK auto-projects to the flat shape
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("How much is AAPL right now?")
        .functions("getStockPrice")
        .build();

Response response = responsesService.create(request);
```

Unlike Chat's automatic tool loop, `Responses` does **not** run an in-service auto-loop: any `function_call` item that appears in `output[]` is handed back to the upper layer as-is, and your runtime decides whether to execute it and how to fill in the `function_call_output`.

When you need the model to follow a schema more strictly, enable [strict mode](/docs/capabilities/models/chat#5-chat-的一个关键特性自动-tool-loop) at registration time:

```java
@FunctionCall(name = "getStockPrice", description = "...", strict = true)
```
:::

## 4. Why `Responses` provider coverage is more focused

Looking at the current implementation of `AiService.createResponsesService(...)`, `Responses` only covers:

- OpenAI
- Doubao
- DashScope

This differs from `Chat`'s broad coverage.

It indicates that, at AI4J's current stage, `Responses` is more of a:

- structured-capability line
- runtime-friendly line
- but a line whose provider ecosystem is still converging

If your priority is maximum provider compatibility, look at `Chat` first.

## 5. How `OpenAiResponsesService` builds the final payload

`OpenAiResponsesService.buildOpenAiPayload(...)` currently assembles these fields explicitly:

- `model`
- `input`
- `include`
- `instructions`
- `max_output_tokens`
- `metadata`
- `parallel_tool_calls`
- `previous_response_id`
- `reasoning`
- `store`
- `stream`
- `stream_options`
- `temperature`
- `text`
- `tool_choice`
- `tools`
- `top_p`
- `truncation`
- `user`

It then supplements them with any extra fields from `extraBody` that the allowlist permits.

This has two important implications:

1. `ResponseRequest` is not serialized raw and fired off to the provider
2. The SDK controls which extension fields may enter the final OpenAI payload

This is more stable than throwing the request out as-is, and easier to debug.

## 6. Why `Responses` streaming is more runtime-friendly

`ResponseSseListener` maintains:

- `events`
- `currEvent`
- `response`
- `outputText`
- `reasoningSummary`
- `functionArguments`
- `currText`
- `currFunctionArguments`

and updates these aggregate states based on the event type, for example:

- `response.output_text.delta`
- `response.reasoning_summary_text.delta`
- `response.function_call_arguments.delta`
- `response.completed`
- `response.failed`
- `response.incomplete`

This means that in `Responses`, streaming consumption is no longer just about "which slice of text should be printed to the UI right now"; it is about:

- where the current response state has reached
- whether reasoning has formed
- whether function arguments are taking shape incrementally
- whether the final response structure has closed

Streaming usage — extend `ResponseSseListener`, implement `onEvent()`, and after the stream ends read the aggregate state off the listener:

```java
ResponseRequest request = ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("Count from 1 to 5, output numbers only")
        .stream(Boolean.TRUE)
        .build();

ResponseSseListener listener = new ResponseSseListener() {
    @Override
    protected void onEvent() {
        // currText is the text delta delivered by this event
        String delta = getCurrText();
        if (delta != null && !delta.isEmpty()) {
            System.out.print(delta);
        }
    }
};

responsesService.createStream(request, listener);

// After the stream ends, all aggregate state is on the listener
System.out.println("\nFull text: " + listener.getOutputText());
System.out.println("Event count: " + listener.getEvents().size());
```

Beyond `getOutputText()`, the listener also aggregates `getReasoningSummary()` (reasoning summary), `getFunctionArguments()` (function arguments), and `getResponse()` (final structure).

## 7. Why `Responses` fits a state machine better than an automatic tool loop

Unlike `Chat`, the current `OpenAiResponsesService` does not run the kind of local automatic `while finishReason == tool_calls` loop inside the service.

It leans toward:

- resolving tools properly
- sending the request out
- aggregating events and the response
- letting the upper-layer runtime decide how to orchestrate what comes next

That is why `Responses` is better suited to:

- agent runtimes
- coding runtimes
- complex interactive UIs
- systems that need fine-grained event tracing

rather than simply chasing "one call that automatically runs every tool to completion internally".

## 8. What `previousResponseId` and `store` imply

These two fields have no equivalent central place in the `Chat` line.

They show that `Responses` more naturally carries:

- chained response continuation
- provider-side persistence or tracing semantics
- subsequent operations oriented around a response graph

This is also why it is closer to "a structured interaction protocol" than "a message-style Q&A interface".

```java
// First turn: ask the provider side to retain this response
Response first = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .input("Remember the number 42. Reply only with STORED")
        .store(Boolean.TRUE)
        .build());

// Second turn: send only the id, do not resend history
Response second = responsesService.create(ResponseRequest.builder()
        .model("gpt-4o-mini")
        .previousResponseId(first.getId())
        .input("What number did I ask you to remember? Reply with the number only")
        .build());
```

A stored response can also be fetched or deleted independently:

```java
Response fetched = responsesService.retrieve(first.getId());
responsesService.delete(first.getId());
```

:::note Not every gateway supports stored state
`store` / `previous_response_id` / `retrieve` / `delete` rely on the provider side persisting responses. In live testing, one OpenAI-compatible gateway explicitly returned
`previous_response_id is only supported on Responses WebSocket v2`, and `retrieve` returned 404 — this is a gateway capability gap, not an SDK defect.
Official OpenAI supports these operations.
:::

## 9. When not to rush into `Responses`

In the following cases, starting with `Chat` is usually cheaper:

- plain text Q&A
- a basic tool-calling demo
- caring most about provider coverage rather than event semantics
- no state machine, tracing, or complex UI needs in the upper layer yet

`Responses` is high-value, but it is not the shortest path for every project.

## 10. Conclusion for this page

> AI4J's `Responses` is the structured response/event line, not a renamed version of `Chat`. It first merges local tools and MCP tools into the request via `ResponseRequestToolResolver`, then has `OpenAiResponsesService` build the provider payload, and during streaming uses `ResponseSseListener` to aggregate events, reasoning, and function arguments. It is therefore better suited to runtimes, tracing, and complex interactions — not to treating the model purely as a single text reply.
