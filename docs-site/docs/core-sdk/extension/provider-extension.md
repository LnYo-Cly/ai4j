# Provider Extension

`provider extension` 解决的是：**把一个新的模型平台正式纳入 AI4J 的平台分发体系**。  
它不是“再写一个 service 类”这么简单，因为 AI4J 当前对 provider 的建模是显式的、枚举驱动的。

## 1. 先看真实入口

这一类改动至少会经过下面几组源码：

- `service/PlatformType.java`
- `service/Configuration.java`
- `service/factory/AiService.java`
- `service/factory/DefaultAiServiceRegistry.java`
- `ai4j-spring-boot-starter/.../AiConfigAutoConfiguration.java`

其中最关键的是 `AiService`。当前 provider 能不能被 SDK 正式识别，不取决于“classpath 上有没有某个实现类”，而取决于它是否进入了这些 `switch` 分发：

- `createChatService(...)`
- `createResponsesService(...)`
- `createEmbeddingService(...)`
- `createImageService(...)`
- `createAudioService(...)`
- `createRealtimeService(...)`
- `createRerankService(...)`

这意味着 provider 支持矩阵是显式维护的，而不是自动发现的。

## 2. 当前 provider 扩展的真实形态

AI4J 目前并没有“注册一个 provider 插件即可接入”的通用 provider SPI。

真实流程是：

1. 给平台命名：增加 `PlatformType`
2. 给平台配配置：增加对应 `*Config`
3. 给统一配置对象开槽：在 `Configuration` 中增加字段
4. 给能力面落实现：增加 chat / responses / embedding 等具体 service
5. 给工厂补分支：在 `AiService` 中把该 provider 接进支持矩阵
6. 给多实例补配置复制逻辑：在 `DefaultAiServiceRegistry.applyPlatformConfig(...)` 增加分支
7. 给 Spring Boot starter 补属性绑定和初始化逻辑

只做其中一两步通常都不够。比如你只写了 `FooChatService`，但没进入 `AiService.createChatService(...)`，外部用户仍然拿不到它。

## 3. 什么时候它不该叫 provider extension

下面这些情况，通常不该升级为 provider extension：

- 同一个 provider 下增加一个新模型名
- 现有请求对象多一个字段映射
- 某个 provider 的 `Chat` 已有能力里增加一个可选选项
- 同一协议族下只是切换 `apiHost`

这些更像 [Model Extension](/docs/core-sdk/extension/model-extension)。

只有当你需要：

- 新的 `PlatformType`
- 新的 provider 配置类型
- 新的工厂分发分支
- 新的 starter 配置装配

它才算真正的 provider extension。

## 4. 现有代码链会要求你改什么

### 4.1 `PlatformType`

这是平台名的正式枚举入口。新增 provider 必须进入这里，否则工厂分发没有合法枚举值可用。

要注意一个细节：

- `DefaultAiServiceRegistry.resolvePlatformType(...)` 对未知平台会直接抛错
- `PlatformType.getPlatform(...)` 对未知值会回退到 `OPENAI`

前者适合正式配置校验，后者则可能掩盖拼写错误。扩展代码里不要把 `getPlatform(...)` 当成严格校验入口。

### 4.2 `Configuration`

所有 provider 配置最终都挂在统一的 `Configuration` 上。新增 provider 时，不仅要有自己的配置类，还要让 `Configuration` 能承载它。

否则即使你写出了 service，也没有统一配置对象可供下游实现读取。

### 4.3 `AiService`

这是最重要的一层。`AiService` 目前是“平台到能力实现”的最终路由器。

例如当前 `Chat` 的 provider 覆盖远多于 `Responses`。这不是文档策略，而是 `AiService` 里的实际分支矩阵。

所以新增 provider 时，你必须明确回答：

- 它支持哪些顶层 service
- 不支持哪些顶层 service
- 不支持时是否保留显式 `IllegalArgumentException`

这里最好显式保留“不支持”的事实，而不是做含糊兜底。

### 4.4 `DefaultAiServiceRegistry`

多实例模式不是简单复用同一份 `Configuration`，而是先复制 base `Configuration`，再把当前 `AiPlatform` 的字段拷贝到对应 provider config 中。

如果你忘了给 `applyPlatformConfig(...)` 增加新 provider 分支，那么：

- 单实例手工 new `AiService` 也许还能跑
- 多实例注册表场景会直接失效

这类问题很常见，因为扩展作者往往先在最短路径 demo 上成功，再漏掉 registry 路径。

### 4.5 Spring Boot starter

starter 侧还要补至少两类东西：

- `*ConfigProperties`
- `AiConfigAutoConfiguration` 中的初始化逻辑

否则仓库会出现一种非常典型的不一致：

- core SDK 手工组装可用
- Spring Boot 场景无法通过配置完整落地

## 5. 现有实现的边界和代价

### 没有 provider 自动发现

当前 provider 扩展是显式代码接线，不是自动注册式扩展。这么做的好处是能力矩阵清晰，坏处是每次新增 provider 都要修改多个中心点。

### service 对象默认不是缓存的

`AiService.getChatService(...)` 当前直接调用 `createChatService(...)`，注释里还能看到曾经考虑过缓存，但现在没有启用。

这意味着：

- 具体 provider service 要尽量无状态
- 重资源应复用 `Configuration` 中的共享 `OkHttpClient`
- 不要把一次性初始化代价埋在每次 `get*Service()` 调用里

### 多实例只是 provider 配置分片，不是完全独立容器

`DefaultAiServiceRegistry` 复制 `Configuration` 时，会继承 base `OkHttpClient` 等共享对象，再覆盖当前 provider 的 scoped 配置。

所以多实例更像“多套平台配置 + 共享底层客户端”，而不是每个 id 都从零创建整套网络栈。

## 6. 调试时先看哪条链

### 能手工 new，不能在配置里声明

先看：

- `DefaultAiServiceRegistry.applyPlatformConfig(...)`
- `AiConfigAutoConfiguration`

这是最常见的“demo 通，starter 不通”故障。

### `getChatService(...)` 能用，`getResponsesService(...)` 报错

先看 `AiService` 里对应 `create*Service(...)` 是否真的给该 provider 加了分支。  
AI4J 当前本来就允许不同 service 面支持矩阵不对称，所以这类报错往往不是运行时偶发，而是工厂矩阵没有定义。

### provider 名配置了却走错平台

检查是不是错误使用了 `PlatformType.getPlatform(...)`。它对未知值回退 `OPENAI`，会让拼写错误变成“看起来还能跑，但平台不对”。

## 7. 一条实用判断线

如果这次改动必须同时触碰：

- `PlatformType`
- `Configuration`
- `AiService`
- `DefaultAiServiceRegistry`

那你做的基本就是正式 provider extension，而不是局部修补。

## 8. 这一页的结论

> AI4J 当前的 provider extension 是显式工厂分发扩展，不是插件式自动接入。新增 provider 的本质是把一个新平台接进 `PlatformType + Configuration + AiService + Registry + Starter` 这条主链；只写实现类而不补这条链，扩展就还没有真正完成。
