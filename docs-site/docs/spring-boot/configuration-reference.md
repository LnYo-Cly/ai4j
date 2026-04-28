# Spring Boot Configuration Reference

这一页只讲配置入口，不讲业务调用。

## 1. 这页在 Spring Boot 主线里的位置

如果 `auto-configuration` 讲的是“starter 最终装了什么 Bean”，那么这一页讲的是：

- 你能配什么
- 这些配置大致分成哪几类
- 单实例配置和多实例配置怎么分工

它不负责替代完整业务示例，也不负责讲清底层模型协议语义。

## 2. 常见配置前缀

当前高频配置面主要包括：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.jina.*`
- `ai.okhttp.*`
- `ai.platforms[]`
- `ai.vector.*`
- `ai.agentflow.*`

第一次接入时，不需要把所有前缀背下来，但要先知道配置不是一坨平铺字段，而是按能力面分层组织的。

## 3. 单实例与多实例

- `ai.openai.*` 这类配置适合单实例基础接入
- `ai.platforms[]` 适合构建 `AiServiceRegistry`

它们不是互斥关系，而是两层不同粒度的配置方式。

更简单地说：

- 只想先跑通一个 provider：先配单实例
- 一个应用内要管理多套 provider / 多账号 / 多租户：再进多实例注册表

## 4. 配置大致会流向哪里

从阅读心智上，可以先记成：

```text
application.yml
    -> *ConfigProperties
        -> starter auto-configuration
            -> Core SDK Configuration / Bean graph
```

所以这页真正要解决的问题不是“字段名本身”，而是：

- 这组字段属于哪个能力面
- 它最终会进入哪条 Bean 主线

## 5. 这一页和其他页面的边界

- `configuration-reference` 讲“你能配什么”
- `auto-configuration` 讲“这些配置最终变成了什么 Bean”
- `bean-extension` 讲“默认 Bean 不够时你怎么接管”

如果这三页边界混掉，Spring Boot 路径就会很难讲清楚。

## 6. 继续阅读

1. [Auto Configuration](/docs/spring-boot/auto-configuration)
2. [Bean Extension](/docs/spring-boot/bean-extension)
3. [Common Patterns](/docs/spring-boot/common-patterns)
4. [Start Here / Troubleshooting](/docs/start-here/troubleshooting)

## 7. 实际使用时先统一什么

建议团队先统一三类约定：

- 哪些 provider 走单实例配置，哪些走 `ai.platforms[]`
- 哪些网络、向量和联网增强配置允许按环境覆盖
- 哪些配置变化需要同步回归 `RAG`、`Tool` 或多实例路由链

这样这页就不只是字段索引，而是配置治理的入口。

## 8. 关键对象

如果你要继续对照源码，优先关注：

- 各类 `*ConfigProperties`
- `AiConfigAutoConfiguration`
- `Configuration`

它们共同构成了“YAML -> 属性对象 -> 运行时配置 -> Bean 图”的完整路径。
