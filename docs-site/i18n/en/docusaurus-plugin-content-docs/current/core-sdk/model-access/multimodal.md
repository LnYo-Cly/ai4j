---
title: "Multimodal"
description: "Explains how image-text input, carried as a session fact via ChatMemoryItem, projects onto both the Chat and Responses pipelines, and distinguishes native model input from external visual tools."
tags: [concept]
---

# Multimodal

This `Multimodal` page is about: **how inputs beyond text enter AI4J's unified model request chain**.

The focus is not on "supporting images" itself, but rather:

- How image-text input is represented inside AI4J
- How it projects onto `Chat` and `Responses` respectively
- Which scenarios count as model input, and which actually look more like a Tool or MCP

:::tip All code on this page runs
The wire-shape examples below come from
[`MultimodalDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/MultimodalDocExamplesTest.java),
which needs no keys and runs in an ordinary CI; for an end-to-end image-recognition call see
[`ChatDocExamplesLiveTest#multiModalUserMessageWithImage`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ChatDocExamplesLiveTest.java).
:::

## 1. Why it belongs to Model Access, not Tools

In AI4J's current implementation, multimodal is first and foremost a request-encoding problem, not an external-capability problem.

It solves:

- How a model receives mixed image-text input
- How a session uniformly holds these facts
- How different request pipelines consume the same session content

It does not solve:

- Image download
- OCR
- External visual analysis services
- File cropping, transcoding, storage

These latter items look more like a Tool or MCP.

## 2. The most important multimodal entry point in AI4J today

The unified entry point is on `ChatMemory`:

- `addUser(String text, String... imageUrls)`

Whether `InMemoryChatMemory` or `JdbcChatMemory`, this input is first converged into:

- `ChatMemoryItem.user(text, imageUrls)`

This matters, because it shows AI4J treats multimodal first as a "session fact", not as a special branch of a single request.

```java
// Read a local file into a data URL (recommended, portable across gateways)
byte[] bytes = Files.readAllBytes(Paths.get("photo.png"));
String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

// Store the text + image as a single session fact in Memory
ChatMemoryItem item = ChatMemoryItem.user("What color is this image?", dataUrl);
```

:::warning Prefer a base64 data URL over a remote image URL
Some OpenAI-compatible gateways will not fetch remote images on your behalf; in testing, one gateway returned `Upstream service temporarily unavailable` for a remote URL, while inline base64 was recognized normally. This is a gateway-capability difference, not an SDK defect.
:::

## 3. How the same session fact projects onto Chat

When the item is a user item and has images, `ChatMemoryItem.toChatMessage()` builds:

- `ChatMessage.role = user`
- `Content.ofMultiModals(...)`

And `Content.MultiModal` currently has two main part types:

- `text`
- `image_url`

After serialization, multimodal Chat content is not a plain string, but rather:

- One text part
- Followed by several image_url parts

This is how the Chat pipeline encodes mixed image-text input.

```java
ChatMessage message = item.toChatMessage();
// message.role = "user"
// message.content.multiModals = [
//   { type:"text", text:"What color is this image?" },
//   { type:"image_url", image_url:{ url:"data:image/png;base64,..." } }
// ]
```

You can also construct it directly, bypassing Memory:

```java
ChatMessage direct = ChatMessage.withUser("What color is this image?", dataUrl);
```

`ChatMessage.withUser(text, images...)` accepts any number of images, encoding them as one `text` + N `image_url` parts.

## 4. How the same session fact projects onto Responses

`ChatMemoryItem.toResponsesInput()` turns the same user session into:

- `type = message`
- `role = user`
- `content = [input_text, input_image, ...]`

Where:

- Text becomes `input_text`
- Images become `input_image`

In other words, AI4J does not model multimodal as two unrelated data structures; it projects the same session fact onto the format each request pipeline expects.

```java
Object responsesInput = item.toResponsesInput();
// { type:"message", role:"user", content:[
//     { type:"input_text", text:"What color is this image?" },
//     { type:"input_image", image_url:{ url:"data:image/png;base64,..." } }
// ] }
```

The same `ChatMemoryItem` yields the Chat shape (`image_url`) when you call `toChatMessage()`, and the Responses shape (`input_image`) when you call `toResponsesInput()`. No need to write a separate multimodal data structure for each pipeline.

## 5. Why this dual projection matters

Many SDKs run into this problem:

- One structure for Chat image-text input
- Another structure for Responses image-text input
- A third structure for Memory

This makes multi-turn sessions, replay, and switching request pipelines painful.

AI4J's current approach is more stable:

1. First store the session fact uniformly
2. Then project onto the target interface

This makes the following scenarios more natural:

- The same session runs on Chat first, then switches to Responses
- Image-text messages enter persistent memory
- The upper-layer runtime maintains the conversation context uniformly

## 6. In image-related scenarios, what counts as multimodal and what does not

### Counts as multimodal input

- Pairing text with an image for understanding
- Visual question answering
- Image comparison
- Including an image as part of the model context

### Looks more like a Tool or MCP

- Calling an OCR service
- Fetching images from an external site
- Calling a dedicated visual-analysis API
- Image conversion, compression, cropping

The criterion is not "whether it involves images", but rather:

- Whether this is native model input
- Or a capability the model obtains indirectly through an external system

## 7. A practical constraint of the current implementation

AI4J's current multimodal support mainly encodes around:

- Image URLs
- Text descriptions

In other words, this layer leans toward "bringing image references into the context", rather than handling every form of visual media file uniformly at the foundation layer. More complex media processing typically still needs an external toolchain.

### Video: `video_url` (Kimi/Moonshot extension)

Besides `image_url`, `Content.MultiModal` also supports `video_url`, for providers that accept video input (e.g. Kimi/Moonshot):

```java
Content.MultiModal video = Content.MultiModal.builder()
        .type(Content.MultiModal.Type.VIDEO_URL.getType())
        .videoUrl(new Content.MultiModal.VideoUrl("data:video/mp4;base64," + videoBase64))
        .build();
```

This is a provider extension, not part of the OpenAI standard; when sent to a provider that does not support it, the field is either ignored or an error is returned.

## 8. What to watch for when using it

### Do not merge native model visual input with external visual tools into one chain

Otherwise you conflate request-protocol concerns with permission/side-effect concerns.

### Provide a text description alongside the image when possible

In session semantics, an image URL is only part of the input; the accompanying description often determines how the model actually interprets the image.

### If you later need to download, split, recognize, or archive, layer on a Tool / MCP then

Multimodal and tools are not mutually exclusive, but they should be layered.

## 9. Conclusion of this page

> AI4J's multimodal support belongs under `Model Access`, because it first solves "how image-text input enters the unified request chain". The current implementation centers on `ChatMemoryItem`, projecting the same image-text session fact into `Chat`'s `text/image_url` content and `Responses`'s `input_text/input_image` content respectively — so it is an input-protocol unification problem, not an external visual-capability integration problem.
