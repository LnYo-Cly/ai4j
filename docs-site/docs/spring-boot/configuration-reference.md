# Spring Boot Configuration Reference

这一页只讲配置入口，不讲业务调用。

## 1. 常见配置前缀

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.jina.*`
- `ai.okhttp.*`
- `ai.platforms[]`
- `ai.vector.*`

## 2. 单实例与多实例

- `ai.openai.*` 这类配置适合单实例基础接入
- `ai.platforms[]` 适合构建 `AiServiceRegistry`

它们不是互斥关系，而是两层不同粒度的配置方式。

## 3. 这一页和其他页面的边界

- `configuration-reference` 讲“你能配什么”
- `auto-configuration` 讲“这些配置最终变成了什么 Bean”
- `bean-extension` 讲“默认 Bean 不够时你怎么接管”

如果这三页边界混掉，Spring Boot 路径就会很难讲清楚。

## 4. 继续阅读

1. [Auto Configuration](/docs/spring-boot/auto-configuration)
2. [Bean Extension](/docs/spring-boot/bean-extension)
3. [Start Here / Troubleshooting](/docs/start-here/troubleshooting)
