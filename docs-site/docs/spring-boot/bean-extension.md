# Spring Boot Bean Extension

默认自动装配只是起点，真正的业务系统通常还需要覆盖 Bean。

## 1. 什么时候需要覆盖

常见场景：

- 自定义 `OkHttpClient`
- 指定自己的 `VectorStore`
- 自定义 `RagContextAssembler`
- 自定义 `Reranker`

也就是说，当默认 starter 已经帮你把入口搭好之后，这一页负责解释你应该沿哪条线接管。

## 2. 基本原则

- 优先复用 AI4J 的统一抽象
- 在容器层替换 Bean，而不是直接改底层 provider 实现
- 显式区分默认能力与业务自定义能力

这样做的价值是：你的 Spring Boot 代码仍然留在 AI4J 的工程模型里，而不是重新绕开基座。

## 3. 常见坑

- 同类型 `VectorStore` 多实例时没有 `@Primary`
- 把业务路由逻辑直接塞进 Controller
- 既依赖全局 `AiService`，又在业务里硬编码平台切换

## 4. 推荐下一步

1. [Common Patterns](/docs/spring-boot/common-patterns)
2. [Core SDK / Extension](/docs/core-sdk/extension/overview)
3. [Core SDK / Search & RAG](/docs/core-sdk/search-and-rag/overview)
