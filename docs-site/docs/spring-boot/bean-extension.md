# Spring Boot Bean Extension

默认自动装配只是起点，真正的业务系统通常还需要覆盖 Bean。

## 1. 这页在 Spring Boot 主线里的位置

如果：

- `quickstart` 负责最快跑通
- `auto-configuration` 负责解释默认装配

那么这页负责解释：

- 默认 Bean 不够时，你应该沿哪条线接管
- 哪些覆盖是健康扩展，哪些是在破坏分层

## 2. 什么时候需要覆盖

常见场景：

- 自定义 `OkHttpClient`
- 指定自己的 `VectorStore`
- 自定义 `RagContextAssembler`
- 自定义 `Reranker`
- 对接企业内部的能力装配或路由策略

也就是说，当默认 starter 已经帮你把入口搭好之后，这一页负责解释你应该沿哪条线接管。

## 3. 基本原则

- 优先复用 AI4J 的统一抽象
- 在容器层替换 Bean，而不是直接改底层 provider 实现
- 显式区分默认能力与业务自定义能力

这样做的价值是：你的 Spring Boot 代码仍然留在 AI4J 的工程模型里，而不是重新绕开基座。

## 4. 最容易做错的地方

最常见的问题不是“不会写 Bean”，而是扩展位置选错：

- 本该在容器层替换，却去改底层 SDK 行为
- 本该用统一抽象，却在业务代码里写平台私货
- 本该交给注册表或服务层处理，却硬塞进 Controller

这会让项目很快失去 AI4J 原本的分层价值。

## 5. 常见坑

- 同类型 `VectorStore` 多实例时没有 `@Primary`
- 把业务路由逻辑直接塞进 Controller
- 既依赖全局 `AiService`，又在业务里硬编码平台切换

## 6. 推荐下一步

1. [Common Patterns](/docs/spring-boot/common-patterns)
2. [Core SDK / Extension](/docs/core-sdk/extension/overview)
3. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
