# Multimodal

`Multimodal` 讲的是文本之外的输入如何进入统一模型请求。它属于 `Model Access`，不是工具能力，也不是 MCP 能力。

如果这页只写“支持图文”，其实没有把边界讲出来。真正关键的是：**多模态是模型输入协议问题，不是外部能力接入问题。**

## 1. 为什么它放在 Model Access

多模态解决的是：

- 请求体如何描述图文混合输入
- `Chat` / `Responses` 如何继续消费这份输入
- provider 差异如何被统一

也就是说，它回答的是“模型怎么收输入”，而不是“模型通过什么工具去看图片”。

## 2. AI4J 当前的基座入口

在基础会话链路里，`ChatMemory` 已经给出了一个非常重要的入口：

- `addUser(String text, String... imageUrls)`

随后你可以把这份上下文：

- 投影成 `ChatCompletion` 的 `messages`
- 或投影成 `ResponseRequest` 的 `input`

这说明多模态在 AI4J 里不是一条完全平行的新体系，而是被纳入了统一会话抽象。

## 3. 为什么这很重要

很多 SDK 的多模态设计会把：

- 文本
- 图片
- 工具输入

做成几套彼此割裂的链路。

AI4J 这里更值得强调的是：

- 会话事实可以统一维护
- 输入只是在不同接口下有不同投影

这比“单独做一个视觉请求对象”更利于多轮和上下文治理。

## 4. 多模态和工具最容易混在哪里

这是文档里必须讲透的一点。

### 属于多模态输入的场景

- 文本 + 图片理解
- 视觉问答
- 图像比较

### 更像工具 / MCP 的场景

- OCR 服务
- 外部视觉解析 API
- 图片下载、裁剪、存储、转码

判断标准不是“都和图片有关”，而是：

- 这是模型原生输入
- 还是外部系统能力

## 5. 使用时该注意什么

- 先确认目标 provider 是否支持你要的多模态形态
- 图片 URL 和文本说明最好成对出现
- 如果后续还有外部处理，再把 tool/MCP 链路叠上去

## 6. 设计摘要

AI4J 的多模态属于 `Model Access`，不是 Tools 或 MCP。它通过 `ChatMemory` 统一管理图文输入，再分别投影到 `Chat` 和 `Responses`，因此解决的是模型输入协议统一问题，而不是外部视觉能力接入问题。

## 7. 继续阅读

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
