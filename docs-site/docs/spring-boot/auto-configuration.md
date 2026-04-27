# Spring Boot Auto Configuration

这一页专门解释 starter 到底自动装配了什么，以及为什么你能只配 `application.yml` 就直接拿到可用 Bean。

## 1. 它解决什么问题

`Spring Boot starter` 的价值不在于“少写几行代码”，而在于把基座能力收敛成稳定的容器入口。

这一层主要负责：

- 绑定配置属性
- 组装统一 `Configuration`
- 暴露 `AiService`、`AiServiceRegistry` 等基础 Bean
- 挂接向量库、RAG、HTTP 栈等默认能力

## 2. 对应的真实入口类是什么

当前最核心的入口类是：

- `AiConfigAutoConfiguration`

从代码可以直接看到它会做这些事情：

- `@EnableConfigurationProperties(...)` 接入一组 `ai.*` 配置类
- 提供 `AiService`
- 提供 `AiServiceFactory`
- 提供 `AiServiceRegistry`
- 提供兼容壳 `FreeAiService`
- 按条件装配 `VectorStore`、`RagContextAssembler`、`Reranker` 等组件

也就是说，这一页不只是概念说明，它对应的是真实 starter 装配入口。

## 3. 你最该关注哪些自动装配结果

最值得先记住的 Bean 一般有两类。

第一类是统一入口 Bean：

- `AiService`
- `AiServiceRegistry`
- `FreeAiService`

第二类是知识增强和底层支撑 Bean：

- `PineconeVectorStore`
- `QdrantVectorStore`
- `MilvusVectorStore`
- `PgVectorStore`
- `RagContextAssembler`
- `Reranker`

如果你已经能发请求，但还不明白“为什么这个 Bean 会出现”，这页就是你该回看的位置。

## 4. 单实例和多实例在这一层怎么落地

如果你只配一套 provider 信息，通常主线就是：

- `AiService`

如果你开始使用：

- `ai.platforms[]`

这类多实例配置，那么自动装配会更偏向：

- `AiServiceRegistry`

所以这页最关键的边界不是“有没有 Bean”，而是：

- 你当前是单实例容器主线
- 还是多实例注册表主线

## 5. 和相邻页面怎么分工

- `quickstart` 负责最快跑通
- `auto-configuration` 负责解释 starter 到底做了什么
- `configuration-reference` 负责解释配置入口
- `bean-extension` 负责解释如何覆盖默认装配

## 6. 推荐下一步

1. [Configuration Reference](/docs/spring-boot/configuration-reference)
2. [Bean Extension](/docs/spring-boot/bean-extension)
3. [Common Patterns](/docs/spring-boot/common-patterns)

如果你要对照底层入口再回看一次主线，建议连读：

1. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
2. [Core SDK / Extension](/docs/core-sdk/extension/overview)
