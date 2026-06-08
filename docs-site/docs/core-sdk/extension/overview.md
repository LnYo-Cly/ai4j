# Extension 总览

`extension` 这一章讲的不是“哪里能塞自定义代码”，而是 **AI4J 当前把哪些变化看成基座内正式扩展，哪些变化仍然要求你进入工厂和配置主链改代码**。

这点必须先说清，因为 AI4J 目前并不是所有扩展面都采用同一种机制：

| 扩展面 | 当前实现形态 | 真实入口 |
| --- | --- | --- |
| Provider extension | 代码内分发扩展，不是通用 SPI | `service/PlatformType.java`、`service/factory/AiService.java` |
| Model extension | 现有 provider 内部能力扩展 | 请求对象 + provider service 实现 |
| Service extension | 代码内能力面扩展，不是通用 SPI | `service/*.java`、`AiService`、`AiServiceRegistry`、`FreeAiService` |
| HTTP stack extension | 真正的 SPI 扩展 | `network/*Provider.java`、`META-INF/services/*`、`AiConfigAutoConfiguration.initOkHttp()` |
| Plugin package | 第三方 jar 形式的运行时资源扩展 | `ai4j-extension-api`、`ServiceLoader`、`ExtensionRegistry`、Agent / Coding Agent `.extensions(...)`、Spring Boot `ai.extensions.*` |

## 1. 这一章在 Core SDK 里的位置

如果 `service-entry-and-registry` 讲的是“从哪里拿 service”，那 `extension` 讲的就是：

- 当现有平台不够时，该改哪一层
- 哪些改动只影响单个 provider
- 哪些改动会膨胀成全 SDK 的抽象变更
- 哪些底层行为已经有正式 SPI，可以不碰 provider 主链

这一章仍然属于 `ai4j/` 基座本身。它不是 Spring Boot、Agent 或 Coding Agent 的补丁说明页。

但从插件包开始，扩展面会进入 `ai4j-extension-api`、`ai4j-agent` 和 `ai4j-coding` 的交界处。插件包不是新增 provider 的方式，而是把工具、命令、Skill、Prompt、Guardrail 等运行时资源交给 Agent / Coding Agent 使用。

## 2. 先看真实执行链

AI4J 当前的扩展链路，大体上是下面这条：

1. 构造 `Configuration`
2. 把 provider 配置放进 `Configuration` 对应字段
3. 通过 `AiService` 或 `AiServiceRegistry` 选择平台
4. 由 `AiService` 内部各个 `create*Service(...)` 方法按 `PlatformType` 分发到具体实现
5. 具体 provider service 再把统一请求对象投影到外部平台协议

多实例场景则多了一层：

1. `DefaultAiServiceRegistry.from(...)` 读取 `AiConfig.platforms`
2. 为每个 `AiPlatform` 复制一份 scoped `Configuration`
3. `applyPlatformConfig(...)` 把该实例的 provider 配置写回 scoped `Configuration`
4. 生成 `AiServiceRegistration(id, platformType, aiService)`

这里有两个非常实际的后果：

- provider 维度的扩展，必须进入 `PlatformType`、`AiService` 和 `DefaultAiServiceRegistry`
- service 实例默认不是缓存单例，`AiService.getChatService(...)` 这类入口每次都会新建具体 service

第二点意味着，如果你的扩展实现依赖昂贵初始化，应该把共享资源收敛到 `Configuration` 或 `OkHttpClient`，而不是假设 `AiService` 会帮你复用具体 service 对象。

## 3. 四条扩展线分别解决什么问题

### Provider extension

你在增加一个新的平台边界，例如新的 `PlatformType`、新的 provider 配置对象、以及它支持的各类 service 实现。

这类改动一定会进入工厂分发主链。

### Model extension

你仍然停留在同一个 provider 下，只是在补模型名、请求字段、返回字段或同一能力面的变体。

这类改动通常不该触碰 `PlatformType`。

### Service extension

你在新增一条新的顶层能力面，而不是给现有能力补字段。

这类改动会扩大 SDK 的公共抽象面，代价通常高于 model extension 和 provider extension。

### HTTP stack extension

你不想改模型协议本身，只想控制底层 `OkHttp` 的并发调度和连接池策略。

这类改动已经被做成正式 SPI，是当前这一章里最接近“插件化”的扩展面。

### Plugin package

你不想改核心 SDK 主链，只想把一组可复用资源提供给宿主应用，例如：

- agent 可调用工具
- coding agent 辅助工具
- CLI 可检查的 extension manifest
- prompt、skill 或 guardrail 资源

这类扩展走 `ai4j-extension-api`。使用者先通过 Maven / Gradle 把插件 jar 放进 classpath，再用 `ExtensionRegistry.discover()` 发现，用 `enable(...)` 启用，最后用 `exposeTool(...)` 显式暴露给模型。Spring Boot 项目可以用 `ai.extensions.enabled` 和 `ai.extensions.tools.expose` 完成同样的启用与暴露，但仍不会自动创建 Agent 或自动安装插件依赖。

## 4. 当前实现里，哪些是“真 SPI”，哪些不是

这是这一章最容易被误读的地方。

### 不是通用 SPI 的部分

- provider 扩展
- 顶层 service 扩展

虽然仓库里有 `AiServiceFactory` 这样的抽象，但 provider 能力矩阵本身仍然写死在 `AiService.createChatService(...)`、`createResponsesService(...)`、`createImageService(...)` 等 `switch` 分发里。

也就是说，今天新增 provider 不是“注册一个实现就自动可见”，而是要进入主线工厂代码。

### 真正通过 SPI 生效的部分

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `Ai4jExtension`，用于插件包 manifest 和运行时资源发现

Spring Boot starter 在 `AiConfigAutoConfiguration.initOkHttp()` 里通过 `ServiceLoaderUtil.load(...)` 加载这两个扩展点，并把返回的 `Dispatcher` 与 `ConnectionPool` 注入统一的 `OkHttpClient.Builder`。

`Ai4jExtension` 也通过 `ServiceLoader` 发现，但它服务的是插件包资源注册，不会自动改变 provider 工厂分发。

## 5. 扩展决策顺序

遇到“现有 SDK 不够用”时，先按下面顺序判断：

1. 这是新平台，还是同平台内的新模型
2. 现有 `Chat / Responses / Embedding / Image / Audio / Realtime / Rerank` 契约还能不能承载它
3. 问题出在请求语义，还是只出在网络栈
4. 这次改动要不要覆盖多实例注册表和 Spring Boot 自动装配

按这个顺序判断，能避免两类常见结构错误：

- 明明只是模型变化，却把 `PlatformType` 和工厂层一起膨胀
- 明明只是并发和连接治理问题，却跑去新增 provider 分支

## 6. 需要特别注意的默认行为

### `PlatformType.getPlatform(...)` 的容错并不严格

这个方法在找不到匹配值时会回退到 `OPENAI`。这对快速 demo 可能方便，但对正式扩展不安全，因为拼错 provider 名字时可能不会立即暴露。

相比之下，`DefaultAiServiceRegistry.resolvePlatformType(...)` 遇到未知平台会直接抛出 `Unsupported ai platform ...`，这才是更适合正式配置的行为。

### starter 的 HTTP SPI 不是可有可无

`ServiceLoaderUtil.load(...)` 找不到实现时会直接抛 `IllegalStateException`。默认实现之所以能工作，不是因为代码里写了 `new DefaultDispatcherProvider()` 兜底，而是因为 `ai4j/src/main/resources/META-INF/services/` 已经注册了默认实现。

所以这层对打包结果敏感。缺失 `META-INF/services` 时，Spring Boot 启动就会在 `initOkHttp()` 阶段失败。

## 7. 什么时候不要进这一章

如果你只是：

- 调一个现有 provider 发起请求
- 改 prompt、tool、memory 组合
- 排查某个字段为什么没有发出去

那通常应该先回到对应能力页，而不是上来就看扩展文档。`extension` 更适合“当前抽象不够了”的场景。

## 8. 推荐阅读顺序

1. [Provider Extension](/docs/core-sdk/extension/provider-extension)
2. [Model Extension](/docs/core-sdk/extension/model-extension)
3. [Service Extension](/docs/core-sdk/extension/service-extension)
4. [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)
5. [Plugin Packages](/docs/core-sdk/extension/plugin-packages)

## 9. 这一页的结论

> AI4J 当前的扩展面并不对称。provider 和顶层 service 仍然走 `PlatformType + AiService + Registry` 这条代码主链，HTTP 并发与连接治理走底层 SPI；第三方插件包则走 `ai4j-extension-api + ServiceLoader + ExtensionRegistry`，用于给 Agent / Coding Agent 暴露可控的运行时资源，Spring Boot 只是把同一套 registry/snapshot 配置化。真正开始扩展前，先判断你碰到的是平台边界、模型变体、能力新增、网络栈治理，还是插件资源复用。
