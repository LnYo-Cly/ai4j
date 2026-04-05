---
sidebar_position: 4
---

# 统一请求与返回读取约定

AI4J 的核心价值，不只是“把多个平台接进来”，还包括把不同平台的调用方式收敛成一套更稳定的 Java 使用习惯。

本页专门回答两个问题：

1. 各类服务的请求对象通常怎么构造
2. 返回值应该优先从哪里读取，而不是在对象树里盲找

---

## 1. 统一使用习惯

AI4J 各类服务虽然底层协议不同，但调用习惯尽量保持一致：

1. 通过 `AiService` 拿到具体服务接口
2. 用 builder 构造请求对象
3. 执行同步或流式调用
4. 从“主结果字段”读取最终数据

可以把它理解成下面这套通用模板：

```java
Service service = aiService.getXxxService(PlatformType.OPENAI);

Request request = Request.builder()
        .model("...")
        .xxx(...)
        .build();

Response response = service.call(request);
```

不同服务的主要区别，在于：

- 请求对象里哪些字段最关键
- 返回对象的主结果落在哪个字段
- 流式场景下监听器暴露的增量字段是什么

---

## 2. 各服务的“主请求字段”

### 2.1 Chat

最关键字段通常是：

- `model`
- `message(...)` 或 `messages(...)`
- 可选：`functions`、`temperature`、`stream`

主请求对象：

- `ChatCompletion`

### 2.2 Responses

最关键字段通常是：

- `model`
- `input`
- 可选：`instructions`、`tools`、`stream`

主请求对象：

- `ResponseRequest`

### 2.3 Embedding

最关键字段通常是：

- `model`
- `input`

主请求对象：

- `Embedding`

### 2.4 Audio

Audio 需要先分三类：

- TTS：`TextToSpeech`
- STT：`Transcription`
- Translation：`Translation`

### 2.5 Image

最关键字段通常是：

- `model`
- `prompt`
- 可选：尺寸、数量、格式

主请求对象：

- `ImageGeneration`

### 2.6 Realtime

Realtime 不是标准“单请求单响应”对象模型，更像：

- 模型名
- 监听器
- WebSocket 会话

---

## 3. 各服务的“主返回字段”

### 3.1 Chat

主返回对象：

- `ChatCompletionResponse`

推荐读取路径：

```java
response.getChoices().get(0).getMessage().getContent().getText()
```

排查顺序建议：

1. `choices` 是否为空
2. `message` 是否存在
3. `content` 是否为文本或多模态结构

### 3.2 Responses

主返回对象：

- `Response`

推荐理解方式：

- `Responses` 不是单一字符串字段
- 最终文本通常来自 `output` 中的 message/output_text 项

因此在业务里更推荐：

- 做一层你自己的结果提取封装
- 不要把 `Response` 原始结构直接散落在 Controller 中

### 3.3 Embedding

主返回对象：

- `EmbeddingResponse`

最常见读取路径：

```java
response.getData().get(0).getEmbedding()
```

如果是批量输入，就遍历：

- `response.getData()`

### 3.4 Audio

三类返回各不相同：

- `textToSpeech(...)` -> `InputStream`
- `transcription(...)` -> `TranscriptionResponse`
- `translation(...)` -> `TranslationResponse`

主结果字段通常是：

- STT：`getText()`
- Translation：`getText()`

### 3.5 Image

主返回对象：

- `ImageGenerationResponse`

业务通常关注：

- 图片 URL
- base64 数据
- 失败信息

### 3.6 Realtime

Realtime 更关注事件监听，而不是一次性返回对象。

业务主线通常是：

- 监听增量事件
- 维护本地会话状态
- 处理连接关闭与重连

---

## 4. 流式调用的统一理解

流式场景下，AI4J 统一做的是“把上游流事件包装成 Java 监听器回调”，而不是强行把所有平台都压成完全相同的事件模型。

因此要记住：

- Chat 流式：主看 `SseListener`
- Responses 流式：主看 `ResponseSseListener`
- Image 流式：主看 `ImageSseListener`

统一点在于“你可以用监听器拿增量”；差异点在于“每种服务的增量字段并不一样”。

---

## 5. 业务层推荐封装方式

为了让业务代码稳定，建议按服务类型各写一层轻薄封装。

例如：

- `ChatGateway`：只返回最终文本和 usage
- `EmbeddingGateway`：只返回 `List<List<Float>>`
- `AudioGateway`：统一落盘、存储或生成 URL
- `ResponsesGateway`：统一把复杂 `Response` 解析成业务 DTO

不要把下面这些原始对象到处透传：

- `ChatCompletionResponse`
- `Response`
- `EmbeddingResponse`
- `ImageGenerationResponse`

原因很简单：

- 平台差异仍然存在
- 原始对象结构通常偏深
- 将来替换平台或升级协议时，维护成本会迅速升高

---

## 6. 常见误区

### 6.1 误把所有服务都当成“返回一个字符串”

只有最简单的聊天文本看起来像这样。

实际上：

- Embedding 返回向量
- Audio TTS 返回流
- Responses 返回事件化结果结构
- Realtime 返回连接与持续事件

### 6.2 误把流式当成“一个事件等于一个 token”

这不成立。

上游可能一次发出：

- 一个字
- 一个词
- 一整段文本
- 一个结构化事件对象

### 6.3 误把统一接口理解为“所有平台能力完全一样”

AI4J 统一的是调用方式和主要对象模型，不是抹平所有平台能力边界。

如果平台不支持某服务，`AiService` 会直接报错，而不是偷偷降级。

---

## 7. 推荐阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
3. [新增 Provider 与模型适配](/docs/ai-basics/provider-and-model-extension)
