---
sidebar_position: 2
title: "Platform and Service Matrix"
description: "Lists each provider's support matrix for Chat, Responses, Messages, Embedding, Rerank, Audio, Realtime, Image, Video, and Music, based on the AiService implementation."
tags: [reference]
---

# Platform and Service Matrix

This page answers only two questions:

1. Which platforms the current code actually supports
2. Which service category each platform supports

> Note: this page is governed by the current `AiService` implementation, not estimated from the README, examples, or future plans.

## 1. Platform enum

The current platform enum is defined in `PlatformType` (14 in total):

- `OPENAI`
- `ANTHROPIC`
- `ZHIPU`
- `DEEPSEEK`
- `MOONSHOT`
- `HUNYUAN`
- `LINGYI`
- `OLLAMA`
- `MINIMAX`
- `BAICHUAN`
- `DASHSCOPE`
- `DOUBAO`
- `JINA`
- `SUNO`

One caveat: the existence of a platform enum does not mean it automatically supports every service surface.
The actual support relationship still depends on whether `AiService.create*Service(...)` has a matching branch.

## 2. Current service support matrix

| Platform | Chat | Responses | Messages | Embedding | Rerank | Audio | Realtime | Image | Video | Music |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OPENAI | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| ANTHROPIC | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DOUBAO | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ |
| DASHSCOPE | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| OLLAMA | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| JINA | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| SUNO | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| ZHIPU | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| DEEPSEEK | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MOONSHOT | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| HUNYUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| LINGYI | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| MINIMAX | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| BAICHUAN | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

## 3. What this matrix really tells you

### `Chat` is the broadest main line

Almost every platform capability lands on the `Chat` entry point first, which makes it the most general-purpose model access main line today.

### `Responses` is a clearly present but narrower second main line

Currently supported only on:

- OpenAI
- Doubao
- DashScope

This means `Responses` in AI4J is not an "every platform should automatically have it" alternative interface, but rather a more focused structured-access main line.

### `Messages` is the third main line, on the Anthropic protocol

Currently only `ANTHROPIC` is supported (native `IMessagesService`). It lets systems that already speak the Anthropic dialect integrate **native-in / native-out** (zero OpenAI conversion), and reuses the same `IMessagesService` to reach Claude and partner vendors' Anthropic-compatible endpoints (Zhipu / MiniMax coding-plan). The `ANTHROPIC` platform also keeps a `Chat` entry point (a unified `IChatService` adapter that translates OpenAI format). See [Messages (Anthropic native)](/docs/core-sdk/model-access/messages).

### `Embedding` / `Audio` / `Realtime` are even narrower

- `Embedding`: OpenAI / Ollama only
- `Audio`: OpenAI only
- `Realtime`: OpenAI only

This shows that these service surfaces, although officially part of the SDK, are not disguised as fully symmetric cross-platform capabilities.

### `Video` / `Music` are independent service surfaces with async submit → poll

- `Video`: OpenAI only (`IVideoService`: `create` / `retrieve` / `content` / `remix`)
- `Music`: Suno only (`IMusicService`: `submitMusic` / `submitLyrics` / `fetch`)

Unlike the synchronous / streaming semantics of Chat and Image, both follow an async lifecycle of **submit task → poll status → pull result**, and the SDK does not build in auto-polling. The `SUNO` platform currently exists only for the Music surface; every other service is unsupported. See [Video interface](/docs/core-sdk/video-generation) and [Music interface](/docs/core-sdk/music-generation).

### `Rerank` is an independent matrix

Currently supported only on:

- Jina
- Ollama
- Doubao

This shows that retrieval-related capabilities do not always come bundled with the `Chat` provider; in real engineering you frequently need to separate the "chat provider" from the "rerank provider".

## 4. The unified entry point stays consistent

Although the matrix is asymmetric, the call entry point stays unified:

```java
AiService aiService = new AiService(configuration);

IChatService chat = aiService.getChatService(PlatformType.OPENAI);
IResponsesService responses = aiService.getResponsesService(PlatformType.DOUBAO);
IMessagesService messages = aiService.getMessagesService(PlatformType.ANTHROPIC);
IEmbeddingService embedding = aiService.getEmbeddingService(PlatformType.OLLAMA);
IRerankService rerank = aiService.getRerankService(PlatformType.JINA);
IImageService image = aiService.getImageService(PlatformType.DOUBAO);
IVideoService video = aiService.getVideoService(PlatformType.OPENAI);
IMusicService music = aiService.getMusicService(PlatformType.SUNO);
```

:::note
If a platform does not support a service, the current implementation throws directly:

- `IllegalArgumentException("Unknown platform: ...")`

In other words, unsupported does not mean "silent degradation", but explicit failure.
:::

## 5. How it combines with the multi-instance registry

If you need to manage multiple provider instances within one application, you typically combine it with:

- `AiServiceRegistry`

e.g.:

```java
IChatService tenantA = aiServiceRegistry.getChatService("tenant-a-openai");
IChatService tenantB = aiServiceRegistry.getChatService("tenant-b-doubao");
IRerankService rerank = aiServiceRegistry.getRerankService("tenant-rerank");
```

The point here is not "fetch an object by id", but rather that you can explicitly organize the asymmetric provider capability matrix into a multi-instance routing graph, instead of scattering platform selection across your business code.

## 6. Mistakes to avoid when reading this matrix

### Don't treat `PlatformType` as a capability guarantee

The platform enum is only a candidate set; what is actually supported must be checked against the `AiService` dispatch implementation.

### Don't assume `Responses` is a full-coverage replacement for `Chat`

The current matrix already shows it is not.

### Don't tie the retrieval-chain provider to the chat provider

The `Rerank` support matrix is independent on its own.

## 7. Conclusion of this page

> AI4J's current unification is not about full symmetry of platform capabilities, but rather that it explicitly maintains an asymmetric yet clear service matrix through a unified entry point. Understanding this matrix matters more than memorizing any single provider's example, because it directly determines whether you can correctly design the engineering boundaries for a multi-platform, multi-service system.
