---
sidebar_position: 13
---

# Chat / 多模态（Vision）

ai4j 在 Chat 链路中支持文本 + 图片输入，核心对象是 `Content`。

## 1. 快速示例

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser(
                "请描述图片中的场景并识别主要对象",
                "https://example.com/demo.jpg"
        ))
        .build();

ChatCompletionResponse response = chatService.chatCompletion(request);
System.out.println(response.getChoices().get(0).getMessage().getContent().getText());
```

## 2. 底层结构

`ChatMessage.content` 使用 `Content` 封装：

- 纯文本：`Content.ofText("...")`
- 多模态：`Content.ofMultiModals(List<MultiModal>)`

`MultiModal` 支持：

- `type=text` + `text`
- `type=image_url` + `imageUrl.url`

## 3. 手工构造多模态片段

```java
List<Content.MultiModal> parts = new ArrayList<>();
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

ChatMessage user = ChatMessage.builder()
        .role("user")
        .content(Content.ofMultiModals(parts))
        .build();
```

## 4. URL 与 Base64

`image_url` 的 `url` 字段既可放网络地址，也可放 base64 data URL。

工程建议：

- 开发调试先用 URL
- 生产场景可按安全策略改成对象存储签名 URL
- 大图建议先压缩，减少请求体体积

## 5. 多模态 + 工具调用

多模态请求同样可带工具：

```java
ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("请识别图片是否下雨并查询当地天气", imageUrl))
        .functions("queryWeather")
        .build();
```

建议在 system/instructions 里明确：

- 先做图像判断
- 再决定是否调用工具

## 6. 常见问题

### 6.1 模型无视觉输出

- 模型本身是否支持视觉
- 图片链接是否可公网访问
- 图片格式/大小是否超限

### 6.2 回答偏离图像内容

- 提示词加约束：仅基于图像可见信息回答
- 减少无关历史消息
- 必要时强制输出结构化字段（对象、动作、场景）

## 7. 最佳实践

- 视觉任务优先短 prompt，避免过度引导。
- 关键业务场景做“模型回归样本库”。
- 图像识别结果要做二次校验（特别是高风险场景）。

如果你要做“图片生成”而不是“图片理解”，请看 `Image 服务` 页面。
