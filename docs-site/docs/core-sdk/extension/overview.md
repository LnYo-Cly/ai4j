---
title: Extension 总览
description: 建立 AI4J 扩展面心智：provider/service 走 PlatformType+AiService+Registry 代码主链，HTTP 并发与连接治理走底层 SPI，第三方插件包走 ai4j-extension-api+ServiceLoader+ExtensionRegistry，扩展面并不对称。
tags: [concept]
---

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
- agent 生命周期观察 hook（session/turn/model/tool/compact 事件）

这类扩展走 `ai4j-extension-api`。使用者先通过 Maven / Gradle 把插件 jar 放进 classpath，再用 `ExtensionRegistry.discover()` 发现，用 `enable(...)` 启用。工具必须再用 `exposeTool(...)` 显式暴露给模型；command、Skill、Prompt、Guardrail 可以继续使用默认兼容的整包启用语义，也可以通过 `requireExplicitResourceActivation()` 和 `allow*` API 逐项授权。Spring Boot 项目可以用 `ai.extensions.enabled`、`ai.extensions.tools.expose`、`ai.extensions.explicit-resource-activation` 和 `ai.extensions.{commands,skills,prompts,guardrails}.allow` 完成同样配置，但仍不会自动创建 Agent 或自动安装插件依赖。

插件作者和使用者可以用 `ExtensionValidator` 或 `ai4j-cli extension validate <id>|--all` 做本地校验。校验会调用插件 `apply(...)` 收集运行时贡献，只报告 manifest、runtime resource、tool schema 和 classpath 资源问题，不会暴露工具给模型，也不会执行 command。接入前还可以用 `ai4j-cli extension plan <id> --enable ... --strict` 查看本次计划启用、授权和暴露后的 activation state；recipe 固定后，用 `ai4j-cli extension check <id> --enable ... --strict` 作为 CI 或发布前门禁。`check` 会在 validation 失败或显式请求的资源没有 active 时返回非零，但不会强制启用未请求资源。

官方 `ai4j-plugin-ask-user` 是第一个随 SDK 发布的样板插件，展示如何把 Agent 需要的用户确认表达成 host-mediated JSON envelope；独立仓库 `ai4j-plugin-dynamic-workflow` 展示如何把动态工作流请求表达成同样受宿主管控的 plugin envelope。已经准备接入插件时，优先看 [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes)，它把依赖、检查、启用、授权、暴露和 Spring Boot / CLI 配置串成可复制配方。

#### 3.1 插件能力清单（六种）

`ai4j-extension-api` 把一个插件能贡献的资源归为六种 `ExtensionCapability`，插件在 manifest 里声明它要贡献哪几种，registry 才会接受对应注册：

| Capability | 注册入口（`ExtensionContext`） | 贡献什么 | 稳定性 |
| --- | --- | --- | --- |
| `TOOL` | `tools()` | 模型可调用工具（spec + executor），必须 `exposeTool(...)` 才可见 | 稳定 |
| `COMMAND` | `commands()` | 人工 / 宿主执行的命令，不自动暴露给模型 | 稳定 |
| `SKILL` | `skills()` | classpath 文本 Skill 资源 | 实验 |
| `PROMPT` | `prompts()` | classpath 文本 Prompt 资源 | 实验 |
| `GUARDRAIL` | `guardrails()` | tool execution 前置允许/拒绝判断 | 实验 |
| `LIFECYCLE` | `lifecycle()` | agent 生命周期事件 hook（session/turn/model/tool/compact） | 实验 |

前五种能力覆盖“插件贡献什么资源”。`LIFECYCLE` 是第六种，覆盖的是另一类需求：**插件想在 agent 执行的关键节点（会话开始结束、每轮前后、模型请求前后、工具调用前后、上下文压缩前后）收到通知**，而不是贡献工具或资源。它解决的是观察/遥测/审计类扩展，而不是新增能力。

声明 `LIFECYCLE` 后，插件在 `apply(...)` 里通过 `context.lifecycle().register(hook)` 注册 `AgentLifecycleHook`，agent 运行时会通过 `AgentLifecycleHookDispatcher` 把 `AgentLifecycleEvent` 分发给每个 hook。详见 [Lifecycle Extensions](/docs/core-sdk/extension/lifecycle-extensions)。

#### 3.2 插件 SPI 的稳定性矩阵

`ai4j-extension-api` 内部的公共元素并不都处于同一稳定性级别。AI4J 用三个标记区分：

| 标记 | 含义 | 向后兼容承诺 |
| --- | --- | --- |
| 无注解 | 稳定 | 当前大版本内向后兼容 |
| `@Experimental` | 实验：已发布但设计仍在收敛 | **不承诺**向后兼容，签名/行为/存在性都可能在小版本或 patch 版本里变，可能被移除或晋升为稳定 |
| `@Internal` | 内部实现 | 不供消费者使用，无任何承诺 |

:::warning
依赖 `@Experimental` 标记的 API 时，应 pin 死确切版本，并在升级小版本时主动回归。这些 API 不会给弃用宽限期。
:::

截至 `2.4.3`，extension SPI 的实际分布：

| 元素 | 类型 | 稳定性 |
| --- | --- | --- |
| `Ai4jExtension`、`ExtensionManifest`、`ExtensionRegistry`、`ExtensionContext` | 核心 SPI 入口 | 稳定 |
| `ToolRegistry`、`CommandRegistry` | tool/command 注册 | 稳定 |
| `SkillRegistry`、`PromptRegistry`、`GuardrailRegistry` | skill/prompt/guardrail 注册 | `@Experimental(since = "2.4.3")` |
| `ExtensionGuardrail` | guardrail 接口 | `@Experimental(since = "2.4.3")` |
| `LifecycleHookRegistry`、`AgentLifecycleHook` | lifecycle hook 注册与接口 | `@Experimental(since = "2.4.3")` |
| `ServiceLoaderExtensionLoader` | 默认 ServiceLoader 加载器 | `@Internal`（依赖 `ExtensionLoader`，不要直接依赖实现类） |

换句话说：tool / command 主链路是稳定的；skill、prompt、guardrail、lifecycle 这四类资源注册接口仍是实验阶段，设计可能在后续版本调整。`@Experimental` 和 `@Internal` 都通过反射保留到运行时，插件作者可以直接在 IDE 里看到注解，不必记这张表。

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

插件发现本身也有一层小 SPI：`ExtensionLoader` 接口（默认实现是 `@Internal` 的 `ServiceLoaderExtensionLoader`）。`ExtensionRegistry.discover()` 默认走 ServiceLoader，但你可以传一个自定义 `ExtensionLoader` 实现非 ServiceLoader 发现（例如固定列表、运行时扫描）。读取插件 jar 内的 Skill / Prompt 文本资源时，公共助手 `ExtensionResourceResolver` 会按“插件 classloader → TCCL → resolver classloader”的顺序解析，并把解析约束在插件自己的 classloader 上，避免同名资源被别的 jar 串读。这两者属于运行时接线细节，详见 [Extension SPI Internals](/docs/core-sdk/extension/extension-spi)。

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

:::warning
这个方法在找不到匹配值时会回退到 `OPENAI`。这对快速 demo 可能方便，但对正式扩展不安全，因为拼错 provider 名字时可能不会立即暴露。
:::

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
6. [Plugin Recipes](/docs/core-sdk/extension/plugin-recipes)
7. [Plugin Author Cookbook](/docs/core-sdk/extension/plugin-author-cookbook)
8. [Ask User Plugin](/docs/core-sdk/extension/ask-user-plugin)
9. [Dynamic Workflow Plugin](/docs/core-sdk/extension/dynamic-workflow-plugin)
10. [Lifecycle Extensions](/docs/core-sdk/extension/lifecycle-extensions)
11. [Extension SPI Internals](/docs/core-sdk/extension/extension-spi)

## 9. 这一页的结论

> AI4J 当前的扩展面并不对称。provider 和顶层 service 仍然走 `PlatformType + AiService + Registry` 这条代码主链，HTTP 并发与连接治理走底层 SPI；第三方插件包则走 `ai4j-extension-api + ServiceLoader + ExtensionRegistry`，用于给 Agent / Coding Agent 暴露可控的运行时资源，Spring Boot 只是把同一套 registry/snapshot 配置化。真正开始扩展前，先判断你碰到的是平台边界、模型变体、能力新增、网络栈治理，还是插件资源复用。
