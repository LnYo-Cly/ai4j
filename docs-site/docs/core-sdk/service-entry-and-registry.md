# Service Entry and Registry

这一页回答 `Core SDK` 最核心的工程问题之一：**当你真正开始接 provider、切模型、加能力时，代码应该从哪里进入。**

## 1. 先记住两条主线

### 单实例主线

```text
Configuration
  -> AiService
    -> IChatService / IResponsesService / IEmbeddingService / ...
```

这是最常见的第一条接入链。

### 多实例主线

```text
Configuration + AiConfig.platforms
  -> DefaultAiServiceRegistry.from(...)
  -> AiServiceRegistry
  -> get(id)
  -> registration.platformType + registration.aiService
```

这条链适合多账号、多租户、多 provider 配置共存。

## 2. `AiService` 真正负责什么

`AiService` 不是只给 `Chat` 用的入口，而是当前 Core SDK 的统一能力工厂。

从实现上看，它当前负责：

- `getChatService(...)`
- `getResponsesService(...)`
- `getEmbeddingService(...)`
- `getAudioService(...)`
- `getRealtimeService(...)`
- `getImageService(...)`
- `getRerankService(...)`
- `getRagService(...)`
- `getIngestionPipeline(...)`
- `getModelReranker(...)`
- `getAgentFlow(...)`
- `webSearchEnhance(...)`

这意味着它不是“provider chooser”这么简单，而是把模型访问、检索增强和部分组合能力统一收束在一个入口对象下。

## 3. `AiService` 当前是显式工厂，不是动态发现

这是必须先讲清楚的事实。

`AiService` 当前内部通过一组 `switch(platform)` 来决定创建哪个实现类。  
所以今天的 provider/service 能力矩阵不是自动发现的，而是显式维护的。

这带来两个后果：

- 支持矩阵是清晰的
- 新增 provider 或新增 service 面时必须改工厂主链

也正因为如此，`AiService` 本身就是理解支持矩阵和扩展成本的第一入口。

## 4. `AiServiceRegistry` 真正增加了什么

`AiServiceRegistry` 并不是简单的 `Map<String, AiService>`。

它正式增加的是：

- 按 `id` 管理多套注册项
- 每项都绑定 `PlatformType`
- 对外直接暴露按 `id` 取 `Chat / Responses / Embedding / RAG / Ingestion / Reranker` 的便利方法

也就是说，它不是只帮你保存对象，而是把“多实例能力入口”本身也做成了正式抽象。

## 5. `DefaultAiServiceRegistry` 的真实行为

这一实现最值得注意的不是“它能注册”，而是它怎么注册。

它会：

1. 读取 `AiConfig.platforms`
2. 校验每个 `AiPlatform.id`
3. 解析 `platform` -> `PlatformType`
4. 复制一份 base `Configuration`
5. 只覆盖当前实例所属 provider 的配置字段
6. 用 `AiServiceFactory.create(...)` 构造 scoped `AiService`
7. 生成 `AiServiceRegistration`

这意味着多实例不是完全独立容器，而更像：

- 共享底层基础配置
- 每个 id 拥有自己的 provider scoped 配置与 `AiService`

## 6. `FreeAiService` 的定位

`FreeAiService` 当前是兼容层，不是新的主线入口。

它保留了：

- 静态 `getChatService(id)`
- 静态 `getEmbeddingService(id)`
- 静态 `getResponsesService(id)` 等旧入口

但文档上更稳的理解应该是：

- 主线入口：`AiService`
- 正式多实例抽象：`AiServiceRegistry`
- 历史兼容壳：`FreeAiService`

## 7. 这页和相邻页面怎么分工

- `service-entry-and-registry` 讲“从哪里进入能力”
- `platform-service-matrix` 讲“每个平台支持哪些 service”
- `model-access` 讲“进入服务后请求语义怎么建模”
- `extension` 讲“默认入口不够时该沿哪条线扩展”

## 8. 最容易忽略的几个事实

### service 对象默认不是缓存的

`AiService` 代码里还能看到曾考虑缓存 `chatService` / `embeddingService`，但当前实现没有启用。  
这意味着 `get*Service()` 默认按次创建具体 service 实例。

### `AiServiceRegistry.get(id)` 对未知 id 会直接抛错

这让 registry 更适合作为正式多实例入口，而不是“可能有、可能没有”的松散查找工具。

### `PlatformType.getPlatform(...)` 容错偏宽

这个方法对未知值会回退到 `OPENAI`，而 `DefaultAiServiceRegistry.resolvePlatformType(...)` 对未知平台会显式抛错。  
正式多实例配置更应该依赖后者的严格行为。

## 9. 这一页的结论

> AI4J 当前的服务入口体系是显式且分层的：`AiService` 负责单实例统一能力工厂，`AiServiceRegistry` 负责正式多实例注册与路由，`FreeAiService` 只承担兼容壳角色。理解这条入口链，比记住单个 provider 的 API 更重要，因为后面的支持矩阵、扩展成本和上层装配都建立在这条链上。
