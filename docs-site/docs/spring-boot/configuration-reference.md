# Spring Boot Configuration Reference

这一页只讲配置入口，不讲业务调用。

## 1. 配置分层

AI4J 的 Spring Boot 配置不是一坨平铺字段，而是按能力面分层组织的。

常见前缀包括：

- `ai.openai.*`
- `ai.doubao.*`
- `ai.dashscope.*`
- `ai.ollama.*`
- `ai.jina.*`
- `ai.okhttp.*`
- `ai.platforms[]`
- `ai.vector.*`
- `ai.agentflow.*`

## 2. 这些配置最终流向哪里

可以先把主线记成：

```text
application.yml
  -> *ConfigProperties
  -> AiConfigAutoConfiguration
  -> Configuration / Bean graph
```

所以这页的重点不是字段列表本身，而是：

- 这组字段属于哪个能力面
- 它会进入单实例主线，还是多实例注册表主线

## 3. 单实例和多实例

### 单实例

像 `ai.openai.*` 这种配置，适合最直接的 provider 接入。

OpenAI-compatible 中转平台也属于这一类。比如 TroveBox：

```yaml
ai:
  openai:
    api-key: ${TROVEBOX_API_KEY}
    api-host: https://codex.trovebox.online/
```

此时业务代码仍然从 `AiService` 获取 `PlatformType.OPENAI` 的服务。

### 多实例

像 `ai.platforms[]` 这种配置，适合构建 `AiServiceRegistry`，用于多账号、多租户或多平台路由。

两条线不是互斥，而是粒度不同。

示例：

```yaml
ai:
  platforms:
    - id: openai-main
      platform: openai
      api-key: ${OPENAI_API_KEY}
      api-host: https://api.openai.com/
    - id: trovebox-low-cost
      platform: openai
      api-key: ${TROVEBOX_API_KEY}
      api-host: https://codex.trovebox.online/
```

```java
IChatService chatService = aiServiceRegistry.getChatService("trovebox-low-cost");
```

`id` 是业务路由名；`platform` 决定底层 provider 适配。多个 OpenAI-compatible endpoint 可以共享 `platform: openai`，只通过不同 `id` 和 `api-host` 区分。

## 4. `ai.okhttp.*` 的位置

`ai.okhttp.*` 不是 provider 配置，而是底层网络栈配置。

它影响的是：

- 日志级别
- 超时时间
- 代理
- SSL 策略

这类配置会通过 `AiConfigAutoConfiguration.initOkHttp()` 进入统一 `OkHttpClient`。

## 5. 这页应该怎么用

当你要加一个新环境配置时，先问自己三个问题：

1. 这是 provider 级参数，还是 HTTP 栈参数
2. 这是单实例配置，还是多实例注册表配置
3. 这项配置是否会影响 RAG、Tool 或多实例路由链

如果这三个问题没想清楚，字段加对了也容易放错层。

## 6. 关键对象

继续对照源码时，优先看：

- `AiConfigProperties`
- 各类 `*ConfigProperties`
- `AiConfigAutoConfiguration`
- `Configuration`

它们共同构成了从 YAML 到运行时对象图的路径。

## 7. 继续阅读

- 首次接入：看 [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)
- 中转平台：看 [OpenAI-compatible 与 TroveBox](/docs/start-here/openai-compatible-and-trovebox)
- 多实例入口：看 [Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
