---
sidebar_position: 32
title: "Image Interface (Generation and Streaming)"
description: "Explains IImageService image generation and streaming listener usage, covering OpenAI and Doubao adapters, request fields, the event model, and common integration issues."
tags: [how-to]
---

# Image Interface (Generation and Streaming)

Image capabilities are unified under `IImageService`, which currently supports:

- `OPENAI`
- `DOUBAO`

:::tip The code on this page is runnable
The non-streaming generation example comes from
[`ImageGenerationLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ImageGenerationLiveTest.java),
verified against a real OpenAI-compatible gateway (grok-imagine-image).

To reproduce locally: `OPENAI_API_KEY=... OPENAI_API_HOST=... OPENAI_IMAGE_MODEL=... mvn -pl ai4j test -Plive-provider-tests -Dtest=ImageGenerationLiveTest` — skipped automatically when no key is present.
:::

## 1. Non-streaming Generation

```java
IImageService imageService = aiService.getImageService(PlatformType.OPENAI);

ImageGeneration request = ImageGeneration.builder()
        .model("gpt-image-1")
        .prompt("A clean isometric illustration of a Java microservice")
        .size("1024x1024")
        .responseFormat("b64_json")   // inline base64, avoids depending on gateway image URL accessibility
        .build();

ImageGenerationResponse response = imageService.generate(request);
// The image is in response.getData().get(0).getB64Json() (or getUrl(), depending on responseFormat)
```

## 2. Streaming Generation

```java
ImageGeneration request = ImageGeneration.builder()
        .model("gpt-image-1")
        .prompt("A futuristic city at sunrise")
        .stream(true)
        .partialImages(1)
        .responseFormat("b64_json")
        .build();

ImageSseListener listener = new ImageSseListener() {
    @Override
    protected void onEvent() {
        ImageStreamEvent e = getCurrEvent();
        if (e != null) {
            System.out.println("event=" + e.getType() + ", idx=" + e.getImageIndex());
        }
    }
};

imageService.generateStream(request, listener);
```

## 3. Request Parameters (`ImageGeneration`)

Common fields:

- `model`
- `prompt`
- `n`
- `size`
- `quality`
- `responseFormat` (`url` / `b64_json`)
- `outputFormat` (`png` / `jpeg` / `webp`)
- `outputCompression`
- `background`
- `partialImages`
- `stream`
- `user`
- `extraBody`

## 4. Listener Fields (`ImageSseListener`)

- `getCurrEvent()`: the current image event
- `getEvents()`: all events
- `getResponse()`: the aggregated image response

## 5. Event Model

During a stream you may see:

- partial image events
- completed events
- error events

By default the listener aggregates the "final image event" into an `ImageGenerationResponse`.

## 6. Handling Differences Between OpenAI and Doubao

The SDK has already handled the protocol adaptation:

- Request body field conversion (Doubao uses `DoubaoImageGenerationRequest`)
- Event field compatibility (`created` / `created_at`)

The business layer can use the same `ImageGeneration` / `ImageSseListener` for both.

## 7. Common Issues

### 7.1 Only receiving partials, no final event

- Check whether `image_generation.completed` was received
- Check for network interruptions and timeouts

### 7.2 Image URL Accessibility

:::warning
- Some gateways return image URLs that are **internal addresses** (e.g. `127.0.0.1` or private IPs) that external clients cannot reach — this is a gateway-side issue, not an SDK defect
- Some platforms return temporary URLs that should be downloaded / persisted as soon as possible
- For production, persist images to object storage

**The most robust approach across gateways**: use `responseFormat("b64_json")` so the image is returned inline, without depending on the gateway's image-hosting URL. The trade-off is a larger response body.
:::

### 7.3 base64 Payload Too Large

- Switch to `url` mode
- Or reduce the resolution and quality
