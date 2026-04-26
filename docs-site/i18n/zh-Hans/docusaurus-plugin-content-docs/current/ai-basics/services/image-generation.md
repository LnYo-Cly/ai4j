---
sidebar_position: 32
---

# Image 接口（生成与流式）

图像能力统一在 `IImageService`，当前支持：

- `OPENAI`
- `DOUBAO`

## 1. 非流式生成

```java
IImageService imageService = aiService.getImageService(PlatformType.OPENAI);

ImageGeneration request = ImageGeneration.builder()
        .model("gpt-image-1")
        .prompt("A clean isometric illustration of a Java microservice")
        .size("1024x1024")
        .responseFormat("url")
        .build();

ImageGenerationResponse response = imageService.generate(request);
System.out.println(response);
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

- 部分平台返回临时 URL，需尽快下载/转存
- 生产建议落盘到对象存储

### 7.3 base64 太大

- 建议改用 `url` 模式
- 或降低分辨率和质量
