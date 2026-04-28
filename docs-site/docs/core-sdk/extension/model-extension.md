# Model Extension

`Model Extension` 解决的是：**在现有 provider 体系不变的前提下，如何补进新的模型能力，而不打穿上层调用抽象。**

这件事表面上像“小改动”，但如果边界判断错了，最后很容易把业务代码打成一堆 provider 分支。

## 1. 先分清它和 provider extension 的区别

- `Provider Extension`：新增一个平台接入面
- `Model Extension`：仍然在现有平台下工作，只是补新的模型族、字段或能力变体

如果你还在同一个 `PlatformType` 里，只是模型名、少量字段、或者 provider 已有能力矩阵在扩展，通常都应该先按 model extension 理解。

## 2. 源码入口

这一层最该看的不是某个文档示例，而是：

- `service/PlatformType.java`
- `service/factory/AiService.java`
- 对应请求对象：`ChatCompletion`、`ResponseRequest`、`Embedding`

为什么？

因为模型扩展真正要回答的是：

- 现有服务接口够不够
- 现有请求对象够不够
- 现有 provider service 需不需要补字段映射

## 3. 三种常见的 model extension 场景

### 3.1 最轻的一类：只新增模型名

例如：

- provider 现有 chat service 已经支持
- 只是业务上换一个新模型名

这种情况下，往往不需要改基座抽象，只需要：

- 配置模型名
- 调整调用策略

### 3.2 中等复杂度：新增少量请求字段或返回差异

例如：

- 新模型支持新的 reasoning / response format 选项
- 新模型对 tool choice、输出格式、流式控制有额外要求

这时通常要改的是：

- 请求对象的可承载字段
- provider service 的序列化映射

但仍然不一定需要新增 provider。

### 3.3 已经不是 model extension，而是 service extension

如果你发现：

- 输入输出语义都变了
- 调用心智不再是 `Chat` / `Responses` 现有模式
- 事件模型发生质变

那就别再把它硬称成“加个模型”，它更可能已经是新的 service 面。

## 4. AI4J 这层最重要的设计原则

**模型变化尽量留在 provider service 内部，不要把 provider 特判传播到业务层。**

这意味着：

- 业务层优先依赖统一接口
- provider 差异收敛在 `AiService` 分发后的具体实现里
- 只有现有接口真的承载不了，才升级为 service extension

## 5. 为什么这对文档读者重要

很多人看 SDK 时容易误以为：

- “新增模型”就等于“SDK 需要大改”

实际上更准确的判断是：

- 模型变化有没有打破现有服务契约

只要服务契约没破，模型扩展通常就是可控的、局部的。

## 6. 常见坑

### 6.1 把 provider 新模型当成新 provider

这会把 `PlatformType` 和工厂分发 unnecessarily 膨胀。

### 6.2 把模型差异直接暴露到业务代码

后面一旦模型换代，业务层会充满 provider 特判。

### 6.3 明明已经是新服务语义，还假装只是“改个 model 名”

这会让抽象层长期扭曲。

## 7. 设计摘要

> 在 AI4J 里，model extension 的关键不是“能不能填一个新 model 字符串”，而是判断现有 `Chat` / `Responses` / `Embedding` 契约是否还成立。只要契约没破，变化应尽量被吸收在 provider service 内部，而不是扩散到业务层。

## 8. 继续阅读

- [Extension / Provider Extension](/docs/core-sdk/extension/provider-extension)
- [Extension / Service Extension](/docs/core-sdk/extension/service-extension)
