---
sidebar_position: 5
---

# Spring Boot 自动配置与属性绑定

这页不再讲“怎么跑第一个请求”，而是讲 `ai4j-spring-boot-starter` 在 Spring Boot 里到底做了什么。

> Legacy note: 本页保留为历史源码导读。当前 Spring Boot 正式主线优先从 [Spring Boot / Overview](/docs/spring-boot/overview)、[Auto Configuration](/docs/spring-boot/auto-configuration) 和 [Configuration Reference](/docs/spring-boot/configuration-reference) 进入。

如果你已经能用 `@Autowired AiService` 发请求，但想继续搞清楚：

- 哪些 Bean 是 starter 自动创建的
- `application.yml` 是怎么映射到 `Configuration` 的
- 为什么 `AiService` 和 `AiServiceRegistry` 会同时存在
- 向量库 Bean 为什么有的默认就有，有的必须 `enabled=true`

这一页就是对应的源码导读。

---

## 1. 模块入口在哪里

Starter 模块源码位置：

- `ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j`

自动配置注册文件有两套：

- `ai4j-spring-boot-starter/src/main/resources/META-INF/spring.factories`
- `ai4j-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

这意味着当前 starter 同时兼容：

- 传统 `spring.factories` 发现方式
- 新版 `AutoConfiguration.imports` 发现方式

真正的自动配置类只有一个：

- `AiConfigAutoConfiguration`

---

## 2. 自动配置类的职责

源码入口：

- `ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigAutoConfiguration.java`

它做了三件核心事情：

1. 注册配置属性类
2. 把属性对象组装成运行时 `Configuration`
3. 基于这份 `Configuration` 暴露基础 Bean

换句话说，starter 不是直接帮你创建“某家平台 SDK”，而是先构建 AI4J 自己的统一运行时配置，再通过统一工厂把能力暴露出来。

---

## 3. Bean 图是什么样的

当前自动配置会直接提供这些主 Bean：

- `AiService`
- `AiServiceFactory`
- `AiServiceRegistry`
- `FreeAiService`
- `PineconeService`
- `PineconeVectorStore`
- 可选的 `QdrantVectorStore`
- 可选的 `MilvusVectorStore`
- 可选的 `PgVectorStore`
- 默认 `RagContextAssembler`
- 默认 `Reranker`

可以把它理解成下面这张关系图：

```text
application.yml
  -> *ConfigProperties
  -> AiConfigAutoConfiguration
  -> Configuration
  -> AiService
  -> AiServiceRegistry
  -> VectorStore / Rag defaults
```

其中：

- `AiService` 是单实例统一工厂
- `AiServiceRegistry` 是多实例路由层
- `FreeAiService` 只是兼容静态壳

---

## 4. 属性前缀是怎么分层的

### 4.1 单 provider 基础配置

这类属性直接写进统一 `Configuration`：

- `ai.openai.*`
- `ai.zhipu.*`
- `ai.deepseek.*`
- `ai.moonshot.*`
- `ai.hunyuan.*`
- `ai.lingyi.*`
- `ai.ollama.*`
- `ai.minimax.*`
- `ai.baichuan.*`
- `ai.dashscope.*`
- `ai.doubao.*`
- `ai.jina.*`

它们分别对应各自的 `*ConfigProperties`，例如：

- `OpenAiConfigProperties`
- `OllamaConfigProperties`
- `DoubaoConfigProperties`
- `JinaConfigProperties`

### 4.2 通用网络与联网增强配置

- `ai.okhttp.*`
- `ai.websearch.searxng.*`

前者控制 `OkHttpClient`，后者控制 `ChatWithWebSearchEnhance` 所用的 `SearXNGConfig`。

### 4.3 多实例注册表配置

- `ai.platforms[*].*`

这部分绑定到：

- `AiConfigProperties`
- `AiPlatformProperties`

它不是用来替代 `ai.openai.*` 这类单实例配置，而是给 `AiServiceRegistry` 构建多套 `AiService` 注册项。

### 4.4 向量库配置

- `ai.vector.qdrant.*`
- `ai.vector.milvus.*`
- `ai.vector.pgvector.*`
- `ai.pinecone.*`

这里有一个源码层细节：

- `PineconeService` 与 `PineconeVectorStore` 默认可直接创建
- `Qdrant / Milvus / PgVector` 只有在 `enabled=true` 时才自动注册对应 `VectorStore` Bean

---

## 5. `Configuration` 是怎么组出来的

`AiConfigAutoConfiguration` 内部维护了一份可复用的：

```java
private io.github.lnyocly.ai4j.service.Configuration configuration
```

在 `@PostConstruct` 的 `init()` 里，会按顺序初始化：

1. `initOkHttp()`
2. Pinecone / Qdrant / Milvus / PgVector
3. `SearXNG`
4. OpenAI / Zhipu / DeepSeek / Moonshot / Hunyuan / Lingyi / Ollama / Minimax / Baichuan / DashScope / Doubao / Jina

也就是说，starter 的思路不是“每次取 Bean 时现组配置”，而是“应用启动时先把统一 `Configuration` 组好”。

---

## 6. `initOkHttp()` 做了什么

这是 starter 最关键的一段初始化逻辑之一。

它会：

- 创建 `HttpLoggingInterceptor`
- 注入 `ErrorInterceptor`
- 注入 `ContentTypeInterceptor`
- 设置 connect / write / read timeout
- 通过 SPI 加载 `DispatcherProvider`
- 通过 SPI 加载 `ConnectionPoolProvider`
- 按配置决定是否启用代理
- 按配置决定是否忽略 SSL 校验

对应源码入口：

- `ServiceLoaderUtil`
- `DispatcherProvider`
- `ConnectionPoolProvider`

所以 `ai.okhttp.*` 并不只是“超时配置”，它实际定义了整个 SDK 的底层 HTTP 执行栈。

---

## 7. `AiServiceRegistry` 为什么会自动存在

starter 里不仅创建了 `AiService`，还会额外注册：

```java
@Bean
public AiServiceRegistry aiServiceRegistry(AiServiceFactory aiServiceFactory) {
    AiConfig aiConfig = new AiConfig();
    aiConfig.setPlatforms(BeanUtil.copyToList(aiConfigProperties.getPlatforms(), AiPlatform.class));
    return DefaultAiServiceRegistry.from(configuration, aiConfig, aiServiceFactory);
}
```

这说明：

- 多实例路由是 starter 的正式能力，不是外部自己拼出来的
- `ai.platforms[]` 只要配置了，就会在启动期被展开成多套注册项
- 如果没有配置平台列表，注册表会是一个空实现，而不是报错

所以应用里既可以这样用：

```java
IChatService chat = aiService.getChatService(PlatformType.OPENAI);
```

也可以这样用：

```java
IChatService tenantChat = aiServiceRegistry.getChatService("tenant-a-openai");
```

---

## 8. 为什么 `FreeAiService` 也会被注册

starter 还会创建：

```java
@Bean
public FreeAiService getFreeAiService(AiServiceRegistry aiServiceRegistry) {
    return new FreeAiService(aiServiceRegistry);
}
```

这不是鼓励你继续写新代码依赖 `FreeAiService`，而是为了兼容旧项目。

从源码含义上看：

- 它只是把容器里的 `AiServiceRegistry` 塞进旧版静态壳
- 新项目应直接注入 `AiService` 或 `AiServiceRegistry`

---

## 9. 向量库 Bean 的自动装配规则

### 9.1 Pinecone

默认提供：

- `PineconeService`
- `PineconeVectorStore`

并且 `PineconeVectorStore` 带 `@ConditionalOnMissingBean`，允许你自己覆盖。

### 9.2 Qdrant / Milvus / pgvector

这三类 `VectorStore` 需要显式打开：

```yaml
ai:
  vector:
    qdrant:
      enabled: true
```

因为它们通常依赖你本地或自建环境，starter 不会假设你一定需要。

### 9.3 为什么没有默认 `IngestionPipeline` 或 `RagService`

这是源码层一个非常合理的设计点。

RAG 链路至少同时依赖：

- 一个 embedding provider
- 一个 `VectorStore`

而这两个在真实项目里都可能不是全局唯一。

所以 starter 没有直接创建“默认 `IngestionPipeline` Bean”，而是把：

- `AiService`
- `VectorStore`

交给你显式组装。

这能避免把“默认 provider 语义”偷偷写死进容器。

---

## 10. 默认 RAG Bean 只补到哪一层

starter 默认只补两类 RAG 基础组件：

- `RagContextAssembler`，默认 `DefaultRagContextAssembler`
- `Reranker`，默认 `NoopReranker`

也就是说：

- 上下文拼装器有默认实现
- 精排器默认是不做 rerank

这和 AI4J 的整体思路一致：

- 先给出稳定默认骨架
- 需要更强能力时再由业务层显式接 `ModelReranker`、`HybridRetriever`、自定义 `VectorStore`

---

## 11. 一个更接近源码语义的配置示例

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    api-host: https://api.openai.com/

  okhttp:
    connect-timeout: 300
    write-timeout: 300
    read-timeout: 300

  websearch:
    searxng:
      url: http://localhost:8080/search

  vector:
    qdrant:
      enabled: true
      host: http://localhost:6333

  platforms:
    - id: primary-openai
      platform: openai
      api-key: ${OPENAI_API_KEY}
      api-host: https://api.openai.com/
    - id: local-ollama
      platform: ollama
      api-host: http://localhost:11434/
      embedding-url: api/embeddings
      rerank-url: api/rerank
```

这个配置启动后，大致会得到：

- 一个全局 `AiService`
- 一个包含 `primary-openai` 和 `local-ollama` 的 `AiServiceRegistry`
- 一个可注入的 `QdrantVectorStore`
- 一个可用于 web search enhance 的 `SearXNGConfig`

---

## 12. 工程上最容易踩的几个点

### 12.1 以为 starter 会自动帮你选平台

不会。

`AiService` 仍然需要你显式传：

- `PlatformType`

或者你自己通过：

- `AiServiceRegistry` 的 `id`

做路由。

### 12.2 容器里有多个 `VectorStore`

如果你自己又注册了多个 `VectorStore` Bean，就需要明确：

- `@Primary`
- 或者显式按类型 / 名称注入

否则上层编排层会出现歧义。

### 12.3 以为 `ai.platforms[]` 会替代全部单实例配置

也不会。

单实例配置和多实例配置是两条线：

- `ai.openai.*` 这类是全局基础配置
- `ai.platforms[]` 是额外构建注册表条目

---

## 13. 推荐继续阅读

1. [Spring Boot / Quickstart](/docs/spring-boot/quickstart)
2. [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)
3. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
4. [Core SDK / Search & RAG / Vector Store and Backends](/docs/core-sdk/search-and-rag/vector-store-and-backends)
