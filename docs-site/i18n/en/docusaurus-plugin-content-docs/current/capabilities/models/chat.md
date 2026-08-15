---
title: "Chat"
description: "Breaks down the runtime behavior of the AI4J Chat main line: the request object, the automatic tool loop, passThroughToolCalls, SseListener streaming aggregation, and multimodal wiring."
tags: [concept]
---

# Chat

`Chat` is currently AI4J's most mature model-access main line, with the broadest provider coverage and the easiest path to a first working call.

It is not, however, a "legacy compatibility layer". As implemented, `Chat` already carries:

- Local function tools
- MCP tools
- An automatic tool loop
- Streaming tool call aggregation
- Reasoning-fragment aggregation
- Pass-through runtime relay

:::tip All code on this page runs
Every Java snippet below comes from the executable test
[`ChatDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ChatDocExamplesLiveTest.java),
verified against a real OpenAI-compatible gateway. To reproduce locally:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_API_HOST=https://your-gateway/   # optional
export OPENAI_CHAT_MODEL=gpt-4o-mini           # optional

mvn -pl ai4j test -Plive-provider-tests -Dtest=ChatDocExamplesLiveTest
```

Without `OPENAI_API_KEY` these tests are skipped automatically and will not fail the build.
:::

## 0. Get it running first

The minimal callable, in three steps: build a service, build a request, read the answer.

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));
openAiConfig.setApiHost("https://api.openai.com/");   // replace with your gateway address

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

IChatService chatService = new AiService(configuration).getChatService(PlatformType.OPENAI);

ChatCompletion chatCompletion = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Explain what a vector database is in one sentence."))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

String answer = response.getChoices().get(0).getMessage().getContent().getText();
System.out.println(answer);
```

Read token usage (normalized to the OpenAI standard structure, consistent across providers):

```java
System.out.println("prompt=" + response.getUsage().getPromptTokens()
        + " completion=" + response.getUsage().getCompletionTokens()
        + " total=" + response.getUsage().getTotalTokens());
```

## 1. Key source entry points

The most worthwhile objects to read first when understanding `Chat` are:

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/chat/entity/ChatMessage.java`
- `platform/openai/chat/entity/Content.java`
- `platform/openai/chat/OpenAiChatService.java`
- `listener/SseListener.java`
- `service/factory/AiService.java`

What really defines "what Chat is in AI4J" is not only the request object but also the automatic tool-call loop inside `OpenAiChatService`.

## 2. What `ChatCompletion` actually carries

The primary mental model of `ChatCompletion` is of course:

- `model`
- `messages`

But the current implementation also retains many runtime-level fields:

- `stream`
- `streamOptions`
- `functions`
- `mcpServices`
- `tools`
- `toolChoice`
- `parallelToolCalls`
- `passThroughToolCalls`
- `responseFormat`
- `extraBody`
- `streamExecution`
- `builtInToolContext`

One particularly important boundary here:

- `functions` / `mcpServices` are local registration helper fields
- `tools` is the actual tool payload finally sent to the provider

In other words, AI4J separates "local capability registration" from "provider payload assembly" at the `Chat` layer.

## 3. Why `Chat` is easy to get running first

The basic input semantics of `Chat` are very stable:

- The central object is `messages`
- The return center is usually `choice.message`
- To continue the conversation, just append the new message back into the session

This is nearly isomorphic to the chat-completions mental model many teams already hold, so the migration cost is low.

Multi-turn conversation is just appending the previous assistant message back into `messages`; there is no extra state to maintain:

```java
List<ChatMessage> messages = new ArrayList<>();
messages.add(ChatMessage.withUser("Remember this number: 42. Reply only with OK."));

ChatCompletionResponse first = chatService.chatCompletion(ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(messages)
        .build());

// Append the assistant reply back into the session, then ask the next turn
messages.add(first.getChoices().get(0).getMessage());
messages.add(ChatMessage.withUser("What number did I just tell you to remember? Reply with the number only."));

ChatCompletionResponse second = chatService.chatCompletion(ChatCompletion.builder()
        .model("gpt-4o-mini")
        .messages(messages)
        .build());

// Output: 42
System.out.println(second.getChoices().get(0).getMessage().getContent().getText());
```

Meanwhile, `AiService.createChatService(...)` also shows it is the main line with the broadest coverage, supporting:

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

## 4. What AI4J does to the request before the provider sends it

Taking `OpenAiChatService.chatCompletion(...)` as an example, the following is done explicitly before sending:

1. `ToolUtil.pushBuiltInToolContext(...)`
2. In forced synchronous mode, `stream=false`
3. If `functions` or `mcpServices` are configured, call `ToolUtil.getAllTools(...)`
4. Stuff the resolved tools into `chatCompletion.tools`
5. If there are ultimately no tools, null out `parallelToolCalls`

This shows that `Chat` does not "serialize the request object and send it as-is"; it first runs a local capability expansion in the runtime.

## 5. A key property of `Chat`: the automatic tool loop

This is the key to understanding the difference between AI4J `Chat` and an ordinary provider SDK.

In synchronous mode, `OpenAiChatService.chatCompletion(...)` loops the request internally until `finishReason` is no longer:

- `first`
- `tool_calls`

When `tool_calls` is received, if `passThroughToolCalls` is not enabled, it will:

1. Extract the `toolCalls` from the assistant message
2. Backfill that assistant message into `messages`
3. Execute `ToolUtil.invoke(functionName, arguments)` for each tool call
4. Wrap the tool output as `ChatMessage.withTool(...)`
5. Append those tool output messages to `messages`
6. Continue with the next round of the request

This means `Chat` in AI4J is not a single RPC, but a conversation loop that can close the loop on tool execution locally.

A complete runnable example. First define the tool: mark the class with `@FunctionCall` and implement `Function<Request, String>`:

```java
@FunctionCall(name = "getOrderStatus", description = "Query order status by order ID")
public static class GetOrderStatus implements Function<GetOrderStatus.Request, String> {

    @Data
    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "Order ID")
        private String orderId;
    }

    @Override
    public String apply(Request request) {
        // In a real project this would query a database; the example returns a structured result directly
        return "{\"orderId\":\"" + request.getOrderId() + "\",\"status\":\"shipped\",\"eta\":\"2026-08-12\"}";
    }
}
```

Here the three annotations `@FunctionCall` / `@FunctionRequest` / `@FunctionParameter` plus reflection automatically generate the provider's JSON Schema from Java types (`String`→`string`, `enum`→`string+enum`, `Integer`→`integer`...); you do not need to write the schema by hand. For `required`, complex-type boundaries, and `strict` mode, see [Annotation-based Tools](/docs/capabilities/tools/annotation-based-tools).

When calling, register by name with `functions(...)` and let the SDK handle the rest:

```java
ChatCompletion chatCompletion = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("What is the current status of order A1001?"))
        .functions("getOrderStatus")
        .build();

// On receiving tool_calls the SDK automatically executes getOrderStatus and backfills the result,
// so what you get here is already the final answer after tool execution
ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

// The output will contain the "shipped" value returned by the tool
System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
```

Note that **only one call is made here**; tool execution, result backfill, and the second request all happen inside `chatCompletion(...)`.

## 6. Why `passThroughToolCalls` is critical

`passThroughToolCalls` decides:

- Whether tool calls are executed automatically by the SDK directly
- Or whether control is handed back to the upper-layer runtime

In the synchronous scenario, if `passThroughToolCalls=true`, receiving `tool_calls` returns the current response immediately instead of continuing local automatic execution.

In the streaming scenario, if `passThroughToolCalls=true`, `chatCompletionStream(...)` will `return` directly after obtaining the streaming-aggregated tool calls, without continuing to append tool messages and recurse into the next round.

This matters for Agents / Coding Agents, because the upper layer often still needs to do:

- Approval
- Tracing
- Sandbox execution
- Result trimming

With the same tool, switching to pass-through yields the **unexecuted** tool call:

```java
ChatCompletion chatCompletion = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("What is the current status of order A1001?"))
        .functions("getOrderStatus")
        .passThroughToolCalls(Boolean.TRUE)     // key
        .build();

ChatCompletionResponse response = chatService.chatCompletion(chatCompletion);

// The SDK no longer auto-executes; it hands the tool_calls back as-is for upper-layer approval / sandbox execution / tracing
List<ToolCall> toolCalls = response.getChoices().get(0).getMessage().getToolCalls();

System.out.println("Pending tool: " + toolCalls.get(0).getFunction().getName());
System.out.println("Arguments: " + toolCalls.get(0).getFunction().getArguments());
// Pending tool: getOrderStatus
// Arguments: {"orderId":"A1001"}
```

Whether to execute it, how to execute it, and how to backfill the result after execution are all decided by the upper layer.

## 7. The real responsibility of `SseListener`

`SseListener` is not a console-printing callback; it is the Chat streaming aggregator.

It maintains:

- `output`
- `currStr`
- `currData`
- `currToolName`
- `reasoningOutput`
- `usage`
- `toolCalls`
- `toolCall`
- `finishReason`

And it can simultaneously handle:

- Plain text deltas
- Reasoning fragments
- Complete or fragmented tool call arguments
- `stop` / `tool_calls` / `[DONE]`

This shows that Chat streaming in AI4J is already "aggregate state consumable by the runtime", not raw token output.

Actual usage: subclass `SseListener`, implement `send()`, and take the aggregated result off the listener after the stream ends.

```java
ChatCompletion chatCompletion = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Count from 1 to 5, output numbers only."))
        .stream(Boolean.TRUE)
        .build();

SseListener sseListener = new SseListener() {
    @Override
    protected void send() {
        // Triggered as each delta arrives; getCurrStr() is this increment
        System.out.print(getCurrStr());
    }
};

chatService.chatCompletionStream(chatCompletion, sseListener);

// After the stream ends, the aggregated result is all on the listener
System.out.println("\nFull output: " + sseListener.getOutput());
System.out.println("finishReason: " + sseListener.getFinishReason());
```

:::caution Don't get the method name wrong
The streaming entry point is `chatCompletionStream(...)`, **not** an overload of `chatCompletion(...)`.
:::

## 8. How multimodal enters Chat

`ChatMessage.withUser(String content, String... images)` ultimately constructs:

- A `text` segment
- Multiple `image_url` entries

Under the hood this is organized by `Content.ofMultiModals(...)` and `Content.MultiModal.withMultiModal(...)`.

One layer up, `ChatMemoryItem.toChatMessage()` automatically projects a user item carrying images into a multimodal `ChatMessage`.

This means that in AI4J, multimodal is not a separate special path outside Chat, but an extension of how message content is encoded.

```java
// In a real project this usually reads a local file
byte[] bytes = Files.readAllBytes(Paths.get("photo.png"));
String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

// withUser(text, images...) encodes into 1 text segment + N image_url entries
ChatMessage message = ChatMessage.withUser("What color is this image? Reply with the color name only.", dataUrl);

ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(message)
        .build());

System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
```

:::warning Prefer a base64 data URL over a remote image URL
The image argument of `withUser(text, images...)` can be either a remote URL or a base64 data URL.
**Some OpenAI-compatible gateways will not fetch remote images** — in practice one gateway returned
`AiServerErrorException: Upstream service temporarily unavailable` for a remote URL, while switching
to inline base64 worked correctly.

This is a gateway capability difference, not an SDK defect. When deploying across gateways, a data URL
is the more portable form.
:::

## 9. Where the boundary of `Chat` lies

Although `Chat` is powerful, its core mental model is still:

- A list of messages
- Appending context turn by turn
- Interleaving tool calls when necessary

If your requirements have started to emphasize:

- Event-granular state consumption
- Response item structure
- Independent observation of function-arguments deltas
- Response-graph semantics like `previous_response_id`

Then you should seriously evaluate `Responses`.

## 10. When to prefer `Chat`

In the following situations, choosing `Chat` first is usually more stable:

- First time integrating AI4J
- Existing code already uses the chat-completions mental model
- You need the broadest provider coverage
- You want to get the text + tool-call main line working first
- The upper layer does not yet need event-based consumption

## 11. Conclusion for this page

> AI4J's `Chat` is not a thin request wrapper, but a mature message-style runtime chain: before the request it resolves tool registrations, on receiving `tool_calls` it can close the loop automatically, and during streaming `SseListener` aggregates text, reasoning, and tool arguments. It is therefore suitable both for quick integration and for supporting a moderately complex local tool runtime.
