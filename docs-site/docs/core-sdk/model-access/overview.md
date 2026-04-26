# Model Access 总览

`Model Access` 这一章负责把“模型请求如何进入 AI4J”讲成一条稳定主线。

## 1. 这章到底讲什么

它不讲业务 runtime，也不讲工具编排。

它只回答这些基础问题：

- 请求走 `Chat` 还是 `Responses`
- 流式怎么处理
- 多模态输入输出怎么理解
- 请求和返回的读取约定怎么统一

## 2. 最该先分清的边界

第一次进入这一章，最重要的不是先看哪家 provider，而是先分清：

- `Chat`：更接近传统多轮对话与消息数组心智
- `Responses`：更接近 item/event 结构和更丰富的输出语义

很多后续差异，本质上都是从这里分叉出来的。

## 3. 和 legacy `chat/`、`responses/` 页的关系

仓库里仍保留一些旧的 `core-sdk/chat/**`、`core-sdk/responses/**` 深页。

它们现在更适合当细节补充材料，而当前 canonical 主线应该以 `model-access/*` 为准，因为这里只有新的能力树能把：

- `Chat`
- `Responses`
- `Streaming`
- `Multimodal`
- 请求/返回约定

放在同一阅读框架里。

## 4. 推荐阅读顺序

1. [Chat](/docs/core-sdk/model-access/chat)
2. [Responses](/docs/core-sdk/model-access/responses)
3. [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
4. [Streaming](/docs/core-sdk/model-access/streaming)
5. [Multimodal](/docs/core-sdk/model-access/multimodal)
6. [Request and Response Conventions](/docs/core-sdk/model-access/request-and-response-conventions)
