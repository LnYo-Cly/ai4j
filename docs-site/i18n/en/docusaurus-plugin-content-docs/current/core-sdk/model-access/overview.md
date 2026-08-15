---
title: "Model Access Overview"
description: "Overview of how model requests are modeled, projected, sent, streamed, and replayed within the AI4J foundation, clarifying the boundaries between the Chat, Responses, and Messages main lines."
tags: [concept]
---

# Model Access Overview

The `Model Access` chapter is not about "which model names AI4J supports", but rather about **how model requests are modeled, projected, sent, streamed, and replayed within the AI4J foundation**.

The key source anchors for this layer are primarily:

- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/response/entity/ResponseRequest.java`
- `platform/anthropic/chat/entity/AnthropicChatCompletion.java`
- `platform/openai/chat/OpenAiChatService.java`
- `platform/openai/response/OpenAiResponsesService.java`
- `platform/anthropic/chat/AnthropicMessagesService.java`
- `platform/anthropic/chat/AnthropicChatService.java`
- `listener/SseListener.java`
- `listener/ResponseSseListener.java`
- `service/IMessagesService.java`
- `service/factory/AiService.java`

## 1. Where this chapter sits in the Core SDK

If `service-entry-and-registry` covers "which service entry point you fetch a capability from", then `model-access` covers:

- What the request object looks like
- What the provider adapter layer does before sending
- How streamed responses are aggregated
- What runtimes `Chat` and `Responses` are each suited to

These are still concerns of the `ai4j/` foundation layer, not topics exclusive to the Agent or Coding Agent upper layers.

## 2. What this chapter actually covers

This chapter mainly covers five questions:

1. Why AI4J keeps both `Chat` and `Responses` at the same time
2. Which fields in the request object carry the primary semantics, and which are only auxiliary registration info
3. How streamed results are aggregated locally by listeners
4. How multimodal input is projected onto the two main lines
5. To what extent provider differences are preserved at the foundation layer

It is not responsible for:

- Tool execution details
- MCP protocol integration details
- The agent loop
- The coding runtime

For those, switch to the `tools`, `mcp`, or upper-layer module docs.

## 3. The boundary to disentangle first

When entering this chapter for the first time, the most important thing is not to look at a specific provider first, but to disentangle the three different model access main lines inside AI4J, each corresponding to a family of native protocols:

### `Chat`

- Centered on `messages`
- Closer to the traditional chat completions mental model
- Widest provider coverage
- Built-in automatic tool loop and streaming tool call aggregation

### `Responses`

- Centered on `input` and the event sequence
- Closer to the structured response item / event mental model
- More focused provider coverage
- Better suited to runtime-side state consumption

### `Messages`

- Centered on the Anthropic Messages protocol (top-level `system`, `content blocks`, `tool_use` / `tool_result`, `thinking`)
- `IMessagesService` is a native first-class citizen: Anthropic format in, Anthropic format out, zero OpenAI conversion
- At the same time there is a unified adapter `AnthropicChatService` (implementing `IChatService`) that translates OpenAI Chat requests into Anthropic Messages, letting upper layers that only want to use `IChatService` integrate transparently
- Suited to scenarios that need native Anthropic semantics (thinking, content blocks) or that use a coding-plan key (the Anthropic endpoints of Zhipu / Minimax)

Many subsequent differences — including streaming, multimodal, and how tools are parsed — essentially fork from here.

## 4. Current provider coverage is not fully symmetric

The `Chat` providers that `AiService.createChatService(...)` can currently create include:

- OpenAI
- Anthropic (unified adapter `AnthropicChatService`, which translates OpenAI Chat requests into Anthropic Messages)
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

While `AiService.createResponsesService(...)` currently only covers:

- OpenAI
- Doubao
- DashScope

This means that in AI4J:

- `Chat` is the default main line with broader coverage
- `Responses` is the more structured main line, but with more focused provider coverage
- `Messages` (Anthropic format) natively covers the Anthropic endpoint; the **coding-plan key** of Zhipu and Minimax also goes through Anthropic-format endpoints, and is therefore integrated via `Messages` or its unified adapter `AnthropicChatService`

This is not a documentation narrative preference, but a fact given by the current factory implementation.

## 5. How AI4J handles "unified request" versus "provider differences"

AI4J's request object strategy is not simply to minimize the field count, but to pin down the primary semantics and leave the differences to the adapter layer.

For example:

- `ChatCompletion` has `model / messages / stream / tools / toolChoice / responseFormat`
- `ResponseRequest` has `model / input / stream / tools / toolChoice / reasoning / truncation`
- Both have local registration auxiliary fields such as `functions` and `mcpServices`
- Both have `extraBody` to carry additional payload

The key points are:

- `functions` and `mcpServices` are not sent to the provider as-is
- `tools` is the field that actually ends up in the provider payload

So when reading this chapter, keep "local registration fields" and "final provider fields" separate.

## 6. None of the three main lines is a plain-text interface

`Chat` does not "just return a string":

- Synchronous calls can auto-execute tool calls
- In streaming calls, `SseListener` aggregates reasoning, tool calls, usage, and finish reason

`Responses` does not "just return a pile of events" either:

- Non-streaming returns a complete `Response`
- When streaming, `ResponseSseListener` simultaneously aggregates `events`, `outputText`, `reasoningSummary`, `functionArguments`, and the final `response`

`Messages` is likewise not just text: content blocks carry `thinking` (mapped to the unified `reasoningContent` / agent `reasoningText` / streaming `onReasoningDelta`), `tool_use` / `tool_result`, and the unified adapter can also auto-run the tool loop and feed results back as `tool_result`.

This means all three main lines are already sufficient to support a medium-complexity runtime; they simply suit different consumption patterns.

## 7. Multimodal also belongs to this layer, not to Tool or MCP

AI4J brings image/text input into the unified session abstraction:

- `ChatMemory.addUser(String text, String... imageUrls)`
- `ChatMemoryItem.toChatMessage()`
- `ChatMemoryItem.toResponsesInput()`

The same session facts can be:

- Projected into `ChatMessage + Content.MultiModal`
- Or projected into `Responses`' `input_text / input_image`

This shows that multimodal in AI4J is a request protocol concern, not an external capability integration concern.

## 8. Recommended reading order

Read in the following order:

1. [Chat](/docs/core-sdk/model-access/chat)
2. [Responses](/docs/core-sdk/model-access/responses)
3. [Messages](/docs/core-sdk/model-access/messages)
4. [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
5. [Streaming](/docs/core-sdk/model-access/streaming)
6. [Multimodal](/docs/core-sdk/model-access/multimodal)
7. [Request and Response Conventions](/docs/core-sdk/model-access/request-and-response-conventions)

## 9. Takeaway for this page

> `Model Access` in AI4J is about "how a request is modeled and fed into a provider", not "what a model can do". The current foundation keeps two clear main lines: `Chat` for broader-coverage message-style access, and `Responses` for more structured event-style access; the two share a unified tool and multimodal foundation, but should not be seen as two skins of the same interface.
