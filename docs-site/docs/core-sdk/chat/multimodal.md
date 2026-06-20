---
sidebar_position: 13
---

# Chat / 多模态（Vision）

这页讲的是：多模态输入怎样沿着 `Chat` 这条旧主线进入 AI4J。

如果你想先看统一抽象，建议先读：[Model Access / Multimodal](/docs/core-sdk/model-access/multimodal)。

## 1. 先给一句工程结论

在 AI4J 当前实现里，多模态不是 `Chat` 之外的独立运行时。

它本质上只是 `ChatMessage.content` 的一种编码方式：

- 文本 part
- 图片 part

也就是说，`Chat` 处理多模态的关键，不是额外 service，而是 `Content` 和 `ChatMemoryItem` 如何把图文事实投影进请求。

## 2. 关键源码入口

建议重点看：

- `platform/openai/chat/entity/ChatMessage.java`
- `platform/openai/chat/entity/Content.java`
- `memory/ChatMemoryItem.java`

其中最关键的一点是：

- 手工消息可以直接构造 `Content.ofMultiModals(...)`
- 走 memory 时，`ChatMemoryItem.toChatMessage()` 会自动把 user 的图文输入投影成多模态消息

## 3. 最短使用路径

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser(
                "请描述图片中的场景并识别主要对象",
                "https://example.com/demo.jpg"
        ))
        .build();
```

这条写法的价值在于：它没有把视觉输入做成旁路协议，而是仍然落在 `ChatMessage` 这个统一消息模型里。

## 4. `Content` 里实际存的是什么

`ChatMessage.content` 有两种主要形态：

- `Content.ofText("...")`
- `Content.ofMultiModals(List<Content.MultiModal>)`

其中 `MultiModal` 当前最重要的 part 类型是：

- `type=text`
- `type=image_url`

所以多模态消息最终不是一个“带图片字段的字符串”，而是一组有顺序的内容片段。

## 5. 为什么顺序很重要

模型拿到的不是“文本和图片的无序集合”，而是按顺序排列的 content parts。

这意味着下面两种写法，模型感知并不完全一样：

- 先放任务说明，再放图片
- 先放图片，再放任务说明

AI4J 当前不会替你重排这些片段；你传什么顺序，provider 就看到什么顺序。

## 6. 手工构造时，你其实在控制协议细节

```java
List<Content.MultiModal> parts = new ArrayList<Content.MultiModal>();
parts.add(Content.MultiModal.builder()
        .type("text")
        .text("比较这两张图片的差异")
        .build());
parts.add(Content.MultiModal.builder()
        .type("image_url")
        .imageUrl(new Content.MultiModal.ImageUrl("https://example.com/a.png"))
        .build());
parts.add(Content.MultiModal.builder()
        .type("image_url")
        .imageUrl(new Content.MultiModal.ImageUrl("https://example.com/b.png"))
        .build());
```

这种写法的优点是透明。

代价也很明确：

- 你要自己负责 part 顺序
- 你要自己保证图片引用格式符合目标 provider 预期
- 你要自己区分“这是一张模型原生可读图片”还是“这其实应该先经由外部工具处理”

## 7. `ChatMemory` 会把同一份图文事实投影成什么

`ChatMemoryItem.user(text, imageUrls...)` 最终会在 `toChatMessage()` 里生成：

- 一条 `role=user` 的 `ChatMessage`
- 里面的 `content` 是 `text + image_url + image_url ...`

这件事很重要，因为它说明：

- 多模态首先被当作会话事实保存
- 然后再按 `Chat` 协议格式投影出去

这也是为什么同一份 memory 后面还可以投影到 `Responses`。

## 8. `image_url` 的边界是什么

SDK 这一层做的事情其实很少：

- 把你的字符串装进 `image_url.url`

它不会在基座层替你完成：

- 文件上传
- 图片压缩
- URL 签名
- 远程图片可达性检查

所以如果你传的是公网 URL、签名 URL 或 data URL，本质上都只是 provider 将收到的输入字符串。可不可用，取决于模型和上游协议，不取决于 `Chat` 这一层自己“理解图片”。

## 9. 多模态和工具不是同一层能力

下面这些属于多模态输入：

- 看图描述
- 图像比较
- 图文问答

下面这些更像 Tool 或 MCP：

- OCR
- 下载私有图床文件
- 裁剪、压缩、格式转换
- 调外部视觉服务做结构化识别

判断标准不是“是不是和图片有关”，而是：

- 这是模型原生输入
- 还是模型借助外部系统得到的能力

## 10. 常见失败路径

### 10.1 模型没有输出任何视觉结果

先排查：

- 模型本身是否支持视觉
- 图片 URL 是否真的可访问
- 请求是否被错误地发到了不支持视觉的 provider / model

### 10.2 回答严重偏离图片内容

通常不是 `Content` 结构错了，而是：

- 文本说明太弱
- 历史消息干扰太强
- 你把视觉理解和后续工具规划混成了一段模糊指令

### 10.3 一会儿想看图，一会儿想让系统下载图

这是最常见的分层混淆。

更稳的做法是：

- 图片理解留在多模态输入层
- 文件处理、权限访问、二次分析交给 Tool / MCP

## 11. 什么时候不该继续沿用旧入口心智

如果你后面还要考虑：

- 同一份图文会话在 `Chat` 和 `Responses` 之间切换
- 图文上下文的持久化和裁剪策略

那就直接连读：

- [Model Access / Multimodal](/docs/core-sdk/model-access/multimodal)
- [Memory / Chat Memory](/docs/core-sdk/memory/chat-memory)

## 12. 这一页的结论

> 在 AI4J 里，`Chat` 多模态首先是消息内容编码问题，而不是额外运行时。图文输入会被组织成 `Content.MultiModal` 的顺序片段；如果你走 `ChatMemory`，同一份图文事实还会自动投影成 `Chat` 可消费的 `text/image_url` 结构。因此，图片理解属于模型输入协议，图片处理才属于工具体系。
