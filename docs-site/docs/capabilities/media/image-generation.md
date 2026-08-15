---
sidebar_position: 32
title: Image 接口（生成与流式）
description: 讲解 IImageService 图像生成与流式监听用法，覆盖 OpenAI 与豆包适配、请求字段、事件模型和常见接入问题。
tags: [how-to]
---

# Image 接口（生成与流式）

图像能力统一在 `IImageService`，当前支持：

- `OPENAI`
- `DOUBAO`

:::tip 本页代码可跑通
非流式生成示例来自
[`ImageGenerationLiveTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ImageGenerationLiveTest.java)，
已针对真实 OpenAI 兼容网关（grok-imagine-image）跑通。

本地复跑：`OPENAI_API_KEY=... OPENAI_API_HOST=... OPENAI_IMAGE_MODEL=... mvn -pl ai4j test -Plive-provider-tests -Dtest=ImageGenerationLiveTest`，无 key 自动跳过。

## 1. 非流式生成

```java
IImageService imageService = aiService.getImageService(PlatformType.OPENAI);

ImageGeneration request = ImageGeneration.builder()
        .model("gpt-image-1")
        .prompt("A clean isometric illustration of a Java microservice")
        .size("1024x1024")
        .responseFormat("b64_json")   // 内联 base64，避免依赖网关图片 URL 的可访问性
        .build();

ImageGenerationResponse response = imageService.generate(request);
// 图片在 response.getData().get(0).getB64Json()（或 getUrl()，取决于 responseFormat）
```

## 2. 流式生成

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

## 3. 请求参数（`ImageGeneration`）

常用字段：

- `model`
- `prompt`
- `n`
- `size`
- `quality`
- `responseFormat`（`url` / `b64_json`）
- `outputFormat`（`png` / `jpeg` / `webp`）
- `outputCompression`
- `background`
- `partialImages`
- `stream`
- `user`
- `extraBody`

## 4. 监听器字段（`ImageSseListener`）

- `getCurrEvent()`：当前图片事件
- `getEvents()`：全量事件
- `getResponse()`：聚合后的图片响应

## 5. 事件模型说明

流式中可能出现：

- partial image 事件
- completed 事件
- error 事件

监听器默认会把“最终图像事件”聚合进 `ImageGenerationResponse`。

## 6. OpenAI 与豆包差异处理

SDK 已做协议适配：

- 请求体字段转换（豆包使用 `DoubaoImageGenerationRequest`）
- 事件字段兼容（`created` / `created_at`）

业务层可以用同一套 `ImageGeneration`/`ImageSseListener`。

## 7. 常见问题

### 7.1 只收到 partial 没有 final

- 检查是否接收到 `image_generation.completed`
- 检查网络中断与超时

### 7.2 URL 可访问性问题

:::warning
- 部分网关返回的图片 URL 是**内网地址**（如 `127.0.0.1` 或私有 IP），外部无法访问——这是网关侧问题，不是 SDK 缺陷
- 部分平台返回临时 URL，需尽快下载/转存
- 生产建议落盘到对象存储

**跨网关最稳的做法**：用 `responseFormat("b64_json")` 让图片内联返回，不依赖网关的图片托管 URL。代价是响应体较大。
:::

### 7.3 base64 太大

- 建议改用 `url` 模式
- 或降低分辨率和质量
