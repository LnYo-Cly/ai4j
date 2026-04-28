# Service Extension

当你不是要加 provider，而是要加新的服务类型时，看这一页。它解决的是：**AI4J 基座里是否需要新增一条新的统一能力面。**

这通常比 model extension 和 provider extension 成本都更高，因为你改的是平台内的“能力版图”。

## 1. 先看现有能力版图

当前 Core SDK 已经有几类稳定服务接口：

- `IChatService`
- `IResponsesService`
- `IEmbeddingService`
- `IImageService`
- `IAudioService`
- `IRealtimeService`
- `IRerankService`

这些接口不是随便列出来的，而是 AI4J 对“模型能力类型”的正式切分方式。

## 2. 源码入口

你要理解 service extension，必须看：

- `service/*.java`
- `service/factory/AiService.java`
- `service/factory/AiServiceRegistry.java`
- `service/factory/FreeAiService.java`

因为新增 service 不只是“写一个接口”，而是要让：

- 工厂知道它
- 注册表知道它
- 兼容壳知道它
- provider 实现能落下来

## 3. 什么情况才值得新增 service

只有在下面这些信号同时成立时，才应该认真考虑：

- 现有接口无法自然承载
- 输入输出语义发生明显变化
- 上层调用心智不再是 `Chat` / `Responses` / `Embedding` 的一种变体

如果只是 provider 字段多一点、模型事件多一点，通常还不该直接新增 service 面。

## 4. 新增一条 service 面通常意味着什么

至少要补这些东西：

- 新的统一接口
- 对应请求 / 返回对象
- `AiService` 的创建和分发逻辑
- `AiServiceRegistry` 和 `FreeAiService` 的访问入口
- 至少一类 provider 实现

这说明 service extension 是一件“架构级”动作，而不是“局部补丁”。

## 5. 为什么这层最容易被误做

因为团队常会遇到一种诱惑：

- 新需求有点不同
- 现有接口看着不够优雅
- 于是想直接加一个新 service

但如果太早做这件事，就会让 SDK 的能力面碎片化。

AI4J 在这层最重要的原则其实是：

**只有语义真的不同，才新增服务接口。**

## 6. 设计摘要

> AI4J 的 service extension 改的是平台的能力切分，不是 provider 的局部实现。只有当现有 `Chat / Responses / Embedding ...` 契约已经承载不了新能力时，才值得新增一条 service 面；否则优先应该在现有抽象里吸收变化。

## 7. 继续阅读

- [Extension / Model Extension](/docs/core-sdk/extension/model-extension)
- [Model Access / Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)
