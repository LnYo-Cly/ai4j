---
sidebar_position: 3
---

# 服务工厂与多实例注册表

这一页专门讲清楚 AI4J 基础层里最容易混淆的三件事：

- `AiService` 到底是什么
- `AiServiceRegistry` 和 `DefaultAiServiceRegistry` 负责什么
- `FreeAiService` 为什么还在，但不再是主线入口

如果你只看“怎么发一个请求”，前面的 [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry) 已经够用。  
这一页讲的是更底层的工程骨架：单实例工厂、多实例路由、Spring Boot 自动装配后的真实 Bean 关系。

---

## 1. 先分清三层角色

### 1.1 `AiService`

`AiService` 是单个 `Configuration` 作用域内的统一服务工厂。

它解决的是：

- 给定一套配置，如何按 `PlatformType` 取 `IChatService / IResponsesService / IEmbeddingService`
- 如何从同一套配置继续拿 `VectorStore / RagService / IngestionPipeline / Reranker`
- 如何把 `web search` 这种增强能力包在 `IChatService` 外面

它不解决：

- 多租户路由
- 同一进程里按 id 管理多套平台实例
- 按业务维度做 provider 选择表

换句话说，`AiService` 不是“平台注册中心”，而是“单配置作用域内的服务工厂”。

### 1.2 `AiServiceRegistry`

`AiServiceRegistry` 是正式的多实例抽象。

它解决的是：

- 一个应用里管理多套 `AiService`
- 按 `id` 路由到不同 provider / 环境 / 租户
- 对外暴露统一的 `getChatService(id)` / `getResponsesService(id)` / `getRagService(id, vectorStore)` 形式

这层适合：

- 多租户
- 灰度模型切换
- A/B provider 比较
- Agent / Flowgram 中按 service id 解析模型入口

### 1.3 `FreeAiService`

`FreeAiService` 现在是兼容层，不是主线设计。

它保留了旧版静态入口：

```java
IChatService chat = FreeAiService.getChatService("tenant-a-openai");
```

但从源码语义看，它本质上只是把静态调用转发到当前 `AiServiceRegistry`，并不再承担新的架构职责。

---

## 2. `AiService` 这一层到底暴露什么

源码入口：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiService.java`

这一层当前提供四类能力。

### 2.1 基础模型服务

- `getChatService(PlatformType)`
- `getResponsesService(PlatformType)`
- `getEmbeddingService(PlatformType)`
- `getRerankService(PlatformType)`
- `getAudioService(PlatformType)`
- `getImageService(PlatformType)`
- `getRealtimeService(PlatformType)`

这组方法的行为很直接：

- 支持就返回具体 provider 实现
- 不支持就抛 `IllegalArgumentException`

它不会做 silent fallback，也不会偷偷切换到别的协议。

### 2.2 联网增强包装

```java
IChatService base = aiService.getChatService(PlatformType.OPENAI);
IChatService enhanced = aiService.webSearchEnhance(base);
```

这里返回的是 `ChatWithWebSearchEnhance`，仍然实现 `IChatService`，只是把 “先搜 SearXNG 再回答” 包装到了 Chat 调用链中。

### 2.3 RAG 与向量库快捷入口

`AiService` 不是只有模型调用，它还直接暴露：

- `getPineconeVectorStore()`
- `getQdrantVectorStore()`
- `getMilvusVectorStore()`
- `getPgVectorStore()`
- `getRagService(platform, vectorStore)`
- `getIngestionPipeline(platform, vectorStore)`

这说明 AI4J 的基础工厂层并不只面向 Chat，也面向检索增强链路。

### 2.4 Rerank 到 RAG 的桥接入口

```java
Reranker reranker = aiService.getModelReranker(
        PlatformType.JINA,
        "jina-reranker-v2-base-multilingual",
        5,
        "优先保留制度原文"
);
```

这里把基础服务层的 `IRerankService`，桥接成了 RAG 层可消费的 `Reranker`。

这也是为什么文档里一直强调：

- `IRerankService` 属于基础模型服务层
- `ModelReranker` 属于 RAG 编排层

---

## 3. 多实例路由是怎么做出来的

源码入口：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistry.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/DefaultAiServiceRegistry.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceRegistration.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/AiServiceFactory.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/DefaultAiServiceFactory.java`

### 3.1 注册表的最小数据结构

`AiServiceRegistration` 很简单，只保存三件事：

- `id`
- `platformType`
- `aiService`

也就是说，注册表不是直接存 `IChatService`，而是存“某个 `PlatformType` 绑定下的 `AiService`”。

这样做的好处是：

- 同一个 `id` 后续可以继续取 Chat、Responses、Embedding、RAG
- 路由层不需要为每个 service 再单独维护一张表

### 3.2 `DefaultAiServiceRegistry.from(...)` 的核心逻辑

`DefaultAiServiceRegistry` 的主入口是：

```java
AiServiceRegistry registry = DefaultAiServiceRegistry.from(
        configuration,
        aiConfig,
        new DefaultAiServiceFactory()
);
```

它会做这几步：

1. 遍历 `AiConfig.platforms`
2. 根据 `platform` 字符串解析成 `PlatformType`
3. 复制一份基础 `Configuration`
4. 把当前 `AiPlatform` 的 provider 配置覆盖到复制后的 `Configuration`
5. 用 `AiServiceFactory` 创建一个新的 `AiService`
6. 以 `id -> AiServiceRegistration` 的形式放进注册表

这里有个很重要的源码语义：

- 基础 `Configuration` 里的共享对象会被复制过去，例如 `OkHttpClient`
- 当前平台的配置槽位会被当前 `AiPlatform` 覆盖
- 这不是“多个 provider 共用同一份可变配置对象”，而是“每个注册项拿一份作用域配置”

### 3.3 `AiPlatform` 是多实例配置载体

`AiConfig` 只保存：

```java
private List<AiPlatform> platforms;
```

真正的多实例条目在 `AiPlatform` 里，例如：

- `id`
- `platform`
- `apiHost`
- `apiKey`
- `chatCompletionUrl`
- `embeddingUrl`
- `rerankApiHost`
- `rerankUrl`

这条链路主要服务的是“按 id 路由”，不是为了取代单实例 `ai.openai.*` 这类基础配置。

---

## 4. `AiServiceRegistry` 的调用语义

`AiServiceRegistry` 本身只要求实现两件事：

- `find(String id)`
- `ids()`

但接口里已经提供了一整组默认方法，例如：

- `getChatService(id)`
- `getEmbeddingService(id)`
- `getResponsesService(id)`
- `getRagService(id, vectorStore)`
- `getModelReranker(id, model, topN, instruction, ...)`

这组默认方法的执行方式是：

1. 先根据 `id` 取 `AiServiceRegistration`
2. 再从注册项里拿 `platformType`
3. 最后调用 `registration.getAiService().getXxxService(platformType)`

所以注册表层没有额外维护一套 provider 能力分发表，真正的服务能力矩阵仍然由 `AiService` 决定。

这是非常关键的边界：

- `AiServiceRegistry` 负责“按 id 找哪套工厂”
- `AiService` 负责“这套工厂支持哪些服务”

---

## 5. `FreeAiService` 现在还剩什么作用

源码入口：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factory/FreeAiService.java`

当前 `FreeAiService` 已标记 `@Deprecated`，但仍保留两类能力：

- 兼容旧构造方式：`new FreeAiService(configuration, aiConfig)`
- 兼容静态获取方式：`FreeAiService.getChatService(id)`

它内部做的事其实很少：

- 初始化时把 `DefaultAiServiceRegistry.from(...)` 生成的结果塞进静态 `registry`
- 所有静态方法都只是把调用转发给这个 `registry`

所以它的定位已经很明确：

- 老项目兼容壳
- 不建议作为新项目主入口

新项目应优先：

- 直接用 `AiService`
- 需要多实例时显式用 `AiServiceRegistry`

---

## 6. 什么时候该用哪一个

### 6.1 只接一套 provider

直接用：

- `Configuration`
- `AiService`

这是最短主线。

### 6.2 一个应用里要管理多套 provider 实例

直接用：

- `AiServiceRegistry`

这适合多租户、灰度、地区隔离、多环境并存。

### 6.3 老代码已经大量写了 `FreeAiService.getChatService(...)`

可以先继续兼容，但新代码不要再围绕它扩展。

---

## 7. Spring Boot 下的真实关系

在 `ai4j-spring-boot-starter` 里，自动配置类会同时注册：

- `AiService`
- `AiServiceFactory`
- `AiServiceRegistry`
- `FreeAiService`

也就是说：

- 单实例主线始终可用
- 多实例注册表也会跟着初始化
- 旧版静态入口仍能继续工作

如果你在 Spring 容器里直接注入：

```java
@Autowired
private AiService aiService;

@Autowired
private AiServiceRegistry aiServiceRegistry;
```

这不是两套互相冲突的机制，而是同一套自动配置同时暴露了“单实例工厂”和“多实例路由层”。

更详细的自动配置过程，继续看：

- [Spring Boot 自动配置与属性绑定](/docs/getting-started/spring-boot-autoconfiguration)

---

## 8. 一个更接近源码的配置示例

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
  ollama:
    api-host: http://localhost:11434/

  platforms:
    - id: primary-openai
      platform: openai
      api-key: ${OPENAI_API_KEY}
      api-host: https://api.openai.com/
      chat-completion-url: v1/chat/completions

    - id: local-ollama
      platform: ollama
      api-host: http://localhost:11434/
      chat-completion-url: api/chat
      embedding-url: api/embeddings
      rerank-url: api/rerank
```

然后业务层可以按两种方式使用：

```java
IChatService single = aiService.getChatService(PlatformType.OPENAI);

IChatService routed = aiServiceRegistry.getChatService("primary-openai");
IRerankService localRerank = aiServiceRegistry.getRerankService("local-ollama");
```

---

## 9. 这套设计为什么重要

如果没有把这一层讲清楚，后面很多更高层的能力都会被误解：

- Agent 里的 model client 选择，最终仍依赖基础服务工厂
- Flowgram 的 `serviceId` 解析，本质上依赖 `AiServiceRegistry`
- Spring Boot 的自动装配，不是在容器里直接塞一堆具体 provider service，而是先组出 `Configuration -> AiService -> Registry`

所以这不是一页“补充 API 说明”，而是整个 SDK 的主骨架之一。

---

## 10. 推荐继续阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
3. [Spring Boot 自动配置与属性绑定](/docs/getting-started/spring-boot-autoconfiguration)
4. [模块架构与包地图](/docs/ai-basics/architecture-and-package-map)
