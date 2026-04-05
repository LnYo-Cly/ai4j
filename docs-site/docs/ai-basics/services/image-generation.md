---
sidebar_position: 32
---

# Image 接口（生成与流式）

图像能力统一在 `IImageService`，当前支持：

- `OPENAI`
- `DOUBAO`

本页重点讲两件事：

1. 图片生成请求怎么构造
2. 非流式与流式两条链路分别怎么读结果

---

## 1. 获取服务入口

```java
IImageService imageService = aiService.getImageService(PlatformType.OPENAI);
```

如果平台当前不支持图片服务，`AiService` 会直接抛异常。

---

## 2. 非流式生成

### 2.1 最小示例

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

### 2.2 关键请求字段

最常用字段包括：

- `model`
- `prompt`
- `n`
- `size`
- `quality`
- `responseFormat`
- `outputFormat`
- `outputCompression`
- `background`

其中最常见的组合是：

- `responseFormat = url`
- 或 `responseFormat = b64_json`

### 2.3 返回结果怎么理解

业务最常关心的是：

- 图片 URL
- base64 数据
- 图片数量
- 错误信息

建议在业务层做一层 DTO 映射，而不是把 `ImageGenerationResponse` 原样透出给上层。

---

## 3. 流式生成

### 3.1 最小示例

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
        ImageStreamEvent event = getCurrEvent();
        if (event != null) {
            System.out.println("event=" + event.getType() + ", idx=" + event.getImageIndex());
        }
    }
};

imageService.generateStream(request, listener);
```

### 3.2 流式监听器里关注什么

`ImageSseListener` 常用字段：

- `getCurrEvent()`：当前事件
- `getEvents()`：累计事件
- `getResponse()`：聚合后的最终响应

这意味着流式链路里你可以同时做两件事：

- 实时监听 partial 事件
- 在结束后拿完整聚合结果

### 3.3 常见事件语义

流式过程中通常会出现：

- partial image 事件
- completed 事件
- error 事件

如果你的前端需要渐进式预览，可以利用 partial 事件；如果只关心最终成图，也可以只在 completed 后读取聚合结果。

---

## 4. OpenAI 与豆包的统一方式

AI4J 对图片能力的统一不只是统一接口名，还包括请求与事件层的协议适配。

当前已经处理了这类差异：

- 请求体字段转换
- 时间字段兼容
- 流式事件聚合

因此业务层可以统一面对：

- `ImageGeneration`
- `ImageGenerationResponse`
- `ImageSseListener`

而不需要为 OpenAI 和豆包分别写两套主调用链。

---

## 5. 参数选择建议

### 5.1 `url` 还是 `b64_json`

优先用 `url`：

- 返回体更轻
- 前端展示更直接
- 适合普通业务系统

优先用 `b64_json`：

- 需要立即本地落盘
- 需要直接上传对象存储
- 不希望依赖临时 URL

### 5.2 `partialImages`

适合：

- 想做渐进式生成体验
- 需要在长图生成中给用户反馈

不适合：

- 只关心最终结果
- 前端没有事件驱动渲染能力

### 5.3 `outputFormat`

建议按场景选：

- `png`：质量优先
- `jpeg`：体积更小
- `webp`：前端分发友好

---

## 6. 生产接法建议

### 6.1 URL 模式

如果平台返回的是 URL：

- 及时下载或转存
- 不要长期依赖上游临时链接
- 统一入对象存储或 CDN

### 6.2 base64 模式

如果返回的是 `b64_json`：

- 尽快解码写文件
- 控制单次图片大小
- 避免在日志里打印完整 base64

### 6.3 流式模式

如果前端消费流式事件：

- 在服务端就统一做事件转发
- 明确 partial 与 final 的状态机
- 不要把 UI 渲染逻辑掺进 SDK 调用层

---

## 7. 常见问题

### 7.1 只收到 partial，没有 final

优先检查：

- 是否真正接收到 completed 事件
- 网络是否中断
- 读超时是否过短

### 7.2 URL 很快失效

这通常不是 SDK 问题，而是上游平台返回的是临时资源链接。

生产环境建议：

- 下载后自存储
- 或改用 `b64_json`

### 7.3 返回体过大

通常由下面原因导致：

- `b64_json`
- 高分辨率
- 高质量
- 图片数量 `n` 过大

---

## 8. 推荐阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
3. [Realtime 接口（WebSocket）](/docs/ai-basics/services/realtime)
