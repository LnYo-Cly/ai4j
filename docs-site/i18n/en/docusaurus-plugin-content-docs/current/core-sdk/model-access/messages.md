---
title: "Messages (Anthropic native)"
description: "Explains the IMessagesService native Anthropic protocol line: native in/out with zero conversion, coding-plan integration, thinking mapping, authentication, and exception handling."
tags: [concept]
---

# Messages (Anthropic native)

`Messages` is the third model-access line in AI4J alongside `Chat` (OpenAI Chat Completions) and `Responses` (OpenAI Responses). It speaks the **Anthropic Messages protocol** (`POST /v1/messages`).

The core reason it exists is not "one more provider", but rather:

> To let systems that already speak the Anthropic dialect integrate **native in / native out**, with zero OpenAI format conversion and zero field loss.

:::tip All code on this page is runnable
The examples below come from
[`MessagesDocExamplesLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/MessagesDocExamplesLiveTest.java),
and have been verified against a real Anthropic-compatible gateway (MiniMax-M3). To reproduce locally:

```bash
export MINIMAX_API_KEY=sk-...
export MINIMAX_MODEL=MiniMax-M3   # optional

mvn -pl ai4j test -Plive-provider-tests -Dtest=MessagesDocExamplesLiveTest
```

Automatically skipped when `MINIMAX_API_KEY` is not set.
:::

## 1. Three lines, not a new-vs-old relationship

| Line | Protocol | Interface | Fits |
| --- | --- | --- | --- |
| Chat | OpenAI Chat Completions | `IChatService` | OpenAI-compatible chains; the vast majority of domestic models |
| Responses | OpenAI Responses | `IResponsesService` | Structured event / item streams; full pushdown of reasoning/store |
| **Messages** | **Anthropic Messages** | **`IMessagesService`** | **Claude; the Anthropic-compatible entry of each vendor's coding-plan** |

These are three different protocol lines, not a hierarchy. Which one you pick depends on "which dialect your system/model speaks", not "which is more advanced".

## 2. Key source entry points

- `service/IMessagesService.java` — protocol-family interface (alongside `IChatService` / `IResponsesService`)
- `platform/anthropic/chat/AnthropicMessagesService.java` — native implementation (transport + SSE parsing)
- `platform/anthropic/chat/entity/AnthropicChatCompletion.java` — native request
- `platform/anthropic/chat/entity/AnthropicChatCompletionResponse.java` — native response
- `platform/anthropic/stream/AnthropicStreamHandler.java` — native streaming event callbacks
- `platform/anthropic/errors/AnthropicApiException.java` — typed exception
- `platform/anthropic/chat/AnthropicChatService.java` — unified `IChatService` adapter (delegates to native + OpenAI translation)
- `service/factory/AiService.java` — `getMessagesService(PlatformType.ANTHROPIC)`

## 3. Two-layer design: native below, unified above

`Messages` exposes two entry points at once; pick by your needs:

```text
Unified layer  IChatService (OpenAI format)         ← want cross-provider unified calls / already have an OpenAI chain
            │ translation (OpenAI ⇄ Anthropic), lossy at the lingua-franca boundary
Native layer ★ IMessagesService (Anthropic native)   ← system already speaks Anthropic / want zero conversion
            │
         Transport   x-api-key + anthropic-version + SSE
```

- **Native `IMessagesService`**: `AnthropicChatCompletion` in, `AnthropicChatCompletionResponse` out, zero OpenAI conversion. Streaming uses `AnthropicStreamHandler` to call back native events (`text_delta` / `thinking_delta` / `tool_use` / `stop_reason`).
- **Unified `AnthropicChatService` (`IChatService`)**: translates the unified `ChatCompletion` into Anthropic Messages, and translates the response / streaming events back into OpenAI format. It **delegates** transport to the native service and only does translation itself.

> Picking the unified entry point = you deliberately accept the loss at the OpenAI lingua-franca boundary (e.g. content blocks get flattened); picking the native entry point = zero loss. `AnthropicContentBlock` preserves the `thinking` field, so the native path never drops thinking content.

## 4. Use the native `IMessagesService` directly

```java
AnthropicConfig config = new AnthropicConfig();
config.setApiKey(apiKey);
// Defaults to https://api.anthropic.com/; to target a partner vendor's Anthropic-compatible entry, just override apiHost
config.setApiHost("https://open.bigmodel.cn/api/anthropic/");

Configuration configuration = new Configuration();
configuration.setAnthropicConfig(config);
IMessagesService messages = new AiService(configuration).getMessagesService(PlatformType.ANTHROPIC);

AnthropicChatCompletion request = new AnthropicChatCompletion();
request.setModel("glm-5.1");
request.setSystem("Reply concisely.");
AnthropicMessage user = new AnthropicMessage();
user.setRole("user");
user.setContent("Introduce yourself.");
request.setMessages(Collections.singletonList(user));
request.setMaxTokens(128);

AnthropicChatCompletionResponse response = messages.messages(request);

// Native content blocks (text / thinking / tool_use) are obtained as-is
// Iterate content to read assistant text (the text blocks other than thinking / tool_use)
StringBuilder answer = new StringBuilder();
for (AnthropicContentBlock block : response.getContent()) {
    if ("text".equals(block.getType())) {
        answer.append(block.getText());
    }
}
System.out.println(answer);

System.out.println("input=" + response.getUsage().getInputTokens()
        + " output=" + response.getUsage().getOutputTokens());
```

### tool_use (native Messages tool calls)

```java
Map<String, Object> inputSchema = new LinkedHashMap<>();
inputSchema.put("type", "object");
inputSchema.put("properties", Map.of("city",
        Map.of("type", "string", "description", "City name")));
inputSchema.put("required", List.of("city"));

AnthropicTool tool = new AnthropicTool();
tool.setName("get_weather");
tool.setDescription("Get the current weather for a city.");
tool.setInputSchema(inputSchema);

AnthropicChatCompletion toolReq = request("What is the weather in Beijing? Use the get_weather tool.");
toolReq.setTools(List.of(tool));

AnthropicChatCompletionResponse resp = messages.messages(toolReq);

// The model emits a content block with type="tool_use", carrying name / id / input (an already-parsed object)
for (AnthropicContentBlock block : resp.getContent()) {
    if ("tool_use".equals(block.getType())) {
        System.out.println("To execute: " + block.getName() + " input=" + block.getInput());
        System.out.println("call_id: " + block.getId());   // must be used when filling back tool_result
    }
}
```

:::note tool_use differs from the Chat tool format
Anthropic's tool declaration uses `input_schema` (not OpenAI's `parameters`), and the returned `input` is an **already-parsed object** (not a JSON string);
to feed back the result, use a `tool_result` block inside a `role:"user"` message plus `tool_use_id` (not OpenAI's `role:"tool"` + `tool_call_id`).
The native path speaks exactly this set, with zero conversion.
:::

### Streaming (native event callbacks)

```java
messages.messagesStream(request, new AnthropicStreamHandler() {
    @Override public void onStart(String messageId, String model) {}
    @Override public void onDeltaText(String text) { System.out.print(text); }
    @Override public void onThinkingDelta(String thinking) { /* thinking delta */ }
    @Override public void onToolUseComplete(int idx, String id, String name, String inputJson) {}
    @Override public void onStopReason(String stopReason, long in, long out) {}
    @Override public void onComplete() {}
});
```

Each callback of `AnthropicStreamHandler` corresponds to one kind of native SSE event; you do not need to parse SSE yourself.

## 5. One-line agent wiring (`.anthropicMessages`)

The agent canonical model (`AgentPrompt` / `AgentModelResult`) is protocol-neutral by design. `AgentBuilder.anthropicMessages(...)` wires the agent directly to the native Anthropic wire protocol:

```java
Agent agent = Agents.react()
        .anthropicMessages(apiKey, "https://open.bigmodel.cn/api/anthropic/")
        .model("glm-5.1")
        .build();

AgentResult result = agent.newSession().run("Introduce yourself in one sentence.");
// result.getOutputText()  — main text
// result.getReasoningText() — thinking (when thinking is on)
// result.getRawResponse() — native AnthropicChatCompletionResponse (not an OpenAI type)
```

Under the hood this is `MessagesModelClient` (the third `AgentModelClient` alongside `ChatModelClient` / `ResponsesModelClient`). See [Model Client Selection](/docs/agent/model-client-selection).

## 6. coding-plan: the Anthropic entry for Zhipu / MiniMax

The coding-plan keys of Zhipu (GLM) and MiniMax **speak the Anthropic format**, not their own OpenAI-compatible endpoints. Just set `apiHost` to reuse the same `IMessagesService`:

| Vendor | Anthropic entry | Model |
| --- | --- | --- |
| Anthropic official | `https://api.anthropic.com/` | claude-* |
| Zhipu Coding Plan | `https://open.bigmodel.cn/api/anthropic/` | glm-5.1 / glm-5.2 |
| MiniMax Coding Plan | `https://api.minimaxi.com/anthropic/` | MiniMax-M3 (international `api.minimax.io/anthropic`) |

:::warning
Note: hitting the vendor's **OpenAI-compatible endpoint** (e.g. Zhipu's `api/paas/v4`) with the same coding-plan key reports "insufficient balance" — the key is not broken, the coding-plan quota is only attached to the Anthropic entry. You must go through `api/anthropic`.
:::

## 7. thinking → reasoningContent

When thinking is on (passed through via `extraBody`, or via the agent's `AgentPrompt.reasoning`):

- Non-streaming response: the `thinking` block in content → mapped to `ChatMessage.reasoningContent` on the unified path; mapped to `AgentModelResult.reasoningText` on the agent path.
- Streaming: `thinking_delta` → emits a `reasoning_content` delta on the unified path; the agent path calls back `onReasoningDelta(...)`.

## 8. Authentication and exceptions

- Authentication header: `x-api-key: <key>` + `anthropic-version: 2023-06-01` (not `Authorization: Bearer`).
- The native path throws a typed `AnthropicApiException` (subclasses `AnthropicRateLimitException` / `AnthropicOverloadedException` / `AnthropicAuthenticationException` / `AnthropicInvalidRequestException`), which can be caught precisely; the unified `IChatService` path still maps these to `CommonException`.

## 9. Spring Boot starter configuration

`ai4j-spring-boot-starter` exposes the Anthropic configuration via `@ConfigurationProperties(prefix = "ai.anthropic")`. The auto-configuration injects `ai.anthropic.*` into `AnthropicConfig`:

```yaml
ai:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    api-host: https://open.bigmodel.cn/api/anthropic/   # default api.anthropic.com; change this for coding-plan
    chat-completion-url: v1/messages                      # default
    api-version: "2023-06-01"                             # default
    stream-timeout-millis: 600000                         # streaming safety-net timeout (ms), default 10 minutes; raise for long-thinking streams
```

After configuration you can call `aiService.getMessagesService(PlatformType.ANTHROPIC)` to get the native service; on the agent side use `.anthropicMessages(apiKey, baseUrl)` (see section 5).

## 10. When to pick Messages

The `Messages` native path fits when:

- Your system already speaks the Anthropic dialect (legacy code, the Claude Code compatible ecosystem)
- You are using coding-plan (Zhipu / MiniMax) to run glm-5.x / MiniMax-M3
- You want native content block / thinking / cache fields with zero loss

The unified `IChatService` (`AnthropicChatService`) path fits when:

- You want one OpenAI-format call across providers, with Anthropic as just one of them
- You already have a mature OpenAI-compatible chain

## 11. Further reading

1. [Chat](/docs/core-sdk/model-access/chat)
2. [Responses](/docs/core-sdk/model-access/responses)
3. [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
4. [Model Client Selection](/docs/agent/model-client-selection)
5. [Platform and service matrix](/docs/core-sdk/platform-service-matrix)
