---
title: Multimodal
description: 说明图文输入如何作为会话事实经 ChatMemoryItem 投影到 Chat 与 Responses 两条主线，区分模型原生输入与外部视觉工具。
tags: [concept]
---

# Multimodal

`Multimodal` 这一页讲的是：**文本之外的输入如何进入 AI4J 的统一模型请求链**。

重点不是“支持图片”这件事本身，而是：

- 图文输入在 AI4J 里被怎样表示
- 它如何分别投影到 `Chat` 和 `Responses`
- 哪些场景属于模型输入，哪些场景其实更像 Tool 或 MCP

:::tip 本页代码都是可跑通的
下面的 wire-shape 示例来自
[`MultimodalDocExamplesTest`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/MultimodalDocExamplesTest.java)，
无需密钥、在普通 CI 里跑；端到端的图片识别调用见
[`ChatDocExamplesLiveTest#multiModalUserMessageWithImage`](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j/src/test/java/io/github/lnyocly/ai4j/docs/ChatDocExamplesLiveTest.java)。
:::

## 1. 为什么它属于 Model Access，而不是 Tools

多模态在 AI4J 当前实现里首先是请求编码问题，不是外部能力问题。

它解决的是：

- 模型怎样接收图文混合输入
- 会话怎样统一持有这些事实
- 不同请求主线怎样消费同一份会话内容

它不解决：

- 图片下载
- OCR
- 外部视觉分析服务
- 文件裁剪、转码、存储

后面这些更像 Tool 或 MCP。

## 2. AI4J 当前最重要的多模态入口

统一入口在 `ChatMemory`：

- `addUser(String text, String... imageUrls)`

无论是 `InMemoryChatMemory` 还是 `JdbcChatMemory`，都会把这条输入先收敛成：

- `ChatMemoryItem.user(text, imageUrls)`

这很重要，因为它说明 AI4J 把多模态首先视为“会话事实”，而不是单次请求的特殊分支。

```java
// 读本地文件成 data URL（推荐，跨网关可移植）
byte[] bytes = Files.readAllBytes(Paths.get("photo.png"));
String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);

// 文本 + 图片作为一条会话事实存入 Memory
ChatMemoryItem item = ChatMemoryItem.user("这张图是什么颜色？", dataUrl);
```

:::warning 优先用 base64 data URL，而不是远程图片 URL
部分 OpenAI 兼容网关不会代拉远程图片，实测某网关对远程 URL 直接报 `Upstream service temporarily unavailable`，
换成内联 base64 则正常识别。这是网关能力差异，不是 SDK 缺陷。
:::

## 3. 同一份会话事实如何投影到 Chat

`ChatMemoryItem.toChatMessage()` 在 user item 且存在图片时，会构造成：

- `ChatMessage.role = user`
- `Content.ofMultiModals(...)`

而 `Content.MultiModal` 当前主要有两种 part：

- `text`
- `image_url`

序列化后，多模态 Chat content 不是普通字符串，而是：

- 一段文本 part
- 后续若干个 image_url part

这对应了 Chat 主线对图文混合输入的编码方式。

```java
ChatMessage message = item.toChatMessage();
// message.role = "user"
// message.content.multiModals = [
//   { type:"text", text:"这张图是什么颜色？" },
//   { type:"image_url", image_url:{ url:"data:image/png;base64,..." } }
// ]
```

不经 Memory 也可以直接构造：

```java
ChatMessage direct = ChatMessage.withUser("这张图是什么颜色？", dataUrl);
```

`ChatMessage.withUser(text, images...)` 接受任意多张图，编码成一段 `text` + N 个 `image_url` part。

## 4. 同一份会话事实如何投影到 Responses

`ChatMemoryItem.toResponsesInput()` 会把同一条用户会话转成：

- `type = message`
- `role = user`
- `content = [input_text, input_image, ...]`

其中：

- 文本会变成 `input_text`
- 图片会变成 `input_image`

也就是说，AI4J 没有把多模态做成两套互不相干的数据结构，而是把同一份会话事实投影到两条请求主线各自的格式上。

```java
Object responsesInput = item.toResponsesInput();
// { type:"message", role:"user", content:[
//     { type:"input_text", text:"这张图是什么颜色？" },
//     { type:"input_image", image_url:{ url:"data:image/png;base64,..." } }
// ] }
```

同一个 `ChatMemoryItem`，调 `toChatMessage()` 得到 Chat 形状（`image_url`），调 `toResponsesInput()` 得到 Responses 形状（`input_image`）。无需为两条主线各写一套多模态数据结构。

## 5. 为什么这种双投影很重要

很多 SDK 会出现这种问题：

- Chat 的图文输入一套结构
- Responses 的图文输入另一套结构
- Memory 又是第三套结构

这样多轮会话、重放和切换请求主线时会非常痛苦。

AI4J 当前的做法更稳定：

1. 先统一存储会话事实
2. 再按目标接口投影

这让以下场景都更自然：

- 同一会话先走 Chat，后切到 Responses
- 图文消息进入持久化 memory
- 上层 runtime 统一维护对话上下文

## 6. 图片相关场景里，什么算多模态，什么不算

### 属于多模态输入

- 文本配图片做理解
- 视觉问答
- 图像比较
- 把图片作为模型上下文的一部分

### 更像 Tool 或 MCP

- OCR 服务调用
- 从外部站点抓取图片
- 调用专门视觉分析 API
- 图片转换、压缩、裁剪

判断标准不是“是否和图片有关”，而是：

- 这是模型原生输入
- 还是模型通过外部系统间接获得的能力

## 7. 当前实现给出的一个实际约束

AI4J 当前多模态主要围绕：

- 图片 URL
- 文本说明

进行编码。

也就是说，这一层更偏向“把图片引用纳入上下文”，而不是在基座层统一处理所有视觉媒体文件形态。对于更复杂的媒体处理，通常还需要外部工具链配合。

### 视频：`video_url`（Kimi/Moonshot 扩展）

除了 `image_url`，`Content.MultiModal` 还支持 `video_url`，用于支持视频输入的 provider（如 Kimi/Moonshot）：

```java
Content.MultiModal video = Content.MultiModal.builder()
        .type(Content.MultiModal.Type.VIDEO_URL.getType())
        .videoUrl(new Content.MultiModal.VideoUrl("data:video/mp4;base64," + videoBase64))
        .build();
```

这是 provider 扩展，不属于 OpenAI 标准；发送给不支持的 provider 时该字段会被忽略或报错。

## 8. 使用时应该注意什么

### 不要把模型原生视觉输入和外部视觉工具混成一条链

否则你会把请求协议问题和权限/副作用问题混在一起。

### 文本说明最好和图片一起提供

从会话语义上看，图片 URL 只是输入的一部分，配套说明往往决定模型到底怎么理解这张图。

### 如果后续还要下载、切图、识别或存档，再叠加 Tool / MCP

多模态和工具不是互斥关系，但要分层。

## 9. 这一页的结论

> AI4J 的多模态属于 `Model Access`，因为它首先解决的是“图文输入如何进入统一请求链”。当前实现以 `ChatMemoryItem` 为中心，把同一份图文会话事实分别投影成 `Chat` 的 `text/image_url` content 和 `Responses` 的 `input_text/input_image` content，因此它是输入协议统一问题，而不是外部视觉能力接入问题。
