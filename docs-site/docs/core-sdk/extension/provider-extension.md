# Provider Extension

当你要新增 provider，第一件事不是写 demo，而是判断：**这是不是一个新的平台边界。**

因为一旦答案是“是”，你改的就不只是一个 service 类，而是 AI4J 整个 provider 分发面。

## 1. 最关键的源码入口

- `service/PlatformType.java`
- `service/factory/AiService.java`
- `service/factory/AiServiceRegistry.java`
- `service/factory/FreeAiService.java`

只要看 `AiService#createChatService(...)`、`createResponsesService(...)`、`createEmbeddingService(...)` 这些方法，你就能明白 provider extension 真正改的是哪一层。

## 2. 新增 provider 往往要补哪些面

最常见至少要覆盖：

- provider 配置对象
- 具体 service 实现
- `AiService` 中的分发逻辑
- 必要时的 starter 配置绑定与自动装配

如果你只写了一个实现类，却没有进入工厂分发和配置体系，那通常还不能算“正式 provider 扩展完成”。

## 3. 什么时候它不应该叫 provider extension

如果你只是：

- 在现有 OpenAI / Ollama 等平台下新增模型
- 给已有 provider service 增加少量字段映射
- 调整 tool / stream / response format 细节

这更可能是 model extension，而不是 provider extension。

这个区分非常重要，因为它直接决定：

- 要不要改 `PlatformType`
- 要不要改全局工厂
- 要不要补整套自动装配

## 4. AI4J 的 provider 设计哲学

AI4J 的主线是：

- 用统一 service 接口承载能力
- 用 `PlatformType` 表示 provider 维度
- 用 `AiService` 做 provider 到实现类的最终分发

这意味着 provider extension 的目标不是把新平台“接进来就行”，而是让它**进入统一抽象且不破坏已有抽象**。

## 5. 常见问题

### 5.1 provider 特判蔓延到业务层

如果业务层开始到处写：

- `if openai ...`
- `if deepseek ...`

说明 provider extension 没有被收敛好。

### 5.2 一个 provider 被强行要求实现并不拥有的能力

这会导致：

- 接口表面统一
- 实际语义混乱

### 5.3 忽略 starter 侧配置整合

结果就是 core SDK 能手工 new 成功，但 Spring Boot 场景不完整。

## 6. 设计摘要

> AI4J 的 provider extension 不是“加一个实现类”这么简单，而是把新平台纳入 `PlatformType + AiService` 的统一分发体系。模型变化优先收在原 provider 内，只有平台边界真的新增时才升级成 provider extension。

## 7. 继续阅读

- [Extension / Model Extension](/docs/core-sdk/extension/model-extension)
- [Extension / Service Extension](/docs/core-sdk/extension/service-extension)
