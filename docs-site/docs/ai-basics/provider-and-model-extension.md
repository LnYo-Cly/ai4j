---
sidebar_position: 5
---

# 新增 Provider 与模型适配

本页面向两类读者：

- 想在现有 provider 下切换新模型的人
- 想给 AI4J 新增一个模型平台接入的人

这两件事的复杂度完全不同，必须分开理解。

---

## 1. 先区分“换模型”和“加 provider”

### 1.1 仅切换模型

如果目标平台已经接入 AI4J，而你只是想换一个新的模型名，通常只需要改请求里的 `model` 字段。

例如：

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Hello"))
        .build();
```

这种场景下通常不需要改 SDK 源码，只需要确认：

- 上游平台确实支持该模型
- 当前服务接口与该模型能力匹配
- 请求字段没有使用该模型不支持的特性

### 1.2 新增 provider

如果是新增一个全新的平台，例如未来新增某个兼容 OpenAI 但有自己鉴权和 URL 规则的平台，那就属于 SDK 扩展，需要改源码。

---

## 2. 当前 Provider 扩展的真实边界

截至当前实现，新增 provider 不是通过运行时插件自动装配完成的，而是走源码扩展路线。

也就是说，至少需要补齐这些链路：

1. `PlatformType`
2. `Configuration` 中的平台配置对象
3. 对应平台服务实现类
4. `AiService` 工厂分发
5. Spring Boot 配置绑定
6. 多实例注册表映射

如果这几层没有一起补齐，新增 provider 只会停留在“配了个 apiHost”，但业务无法真正取到服务。

---

## 3. 新增一个 Provider 的最小工作链路

可以按下面顺序理解。

### 3.1 增加平台标识

首先要让系统认识这个平台。

典型入口是：

- `PlatformType`

它承担两件事：

- 作为代码里的统一选择入口
- 作为配置和注册表里的平台标识

### 3.2 增加平台配置对象

然后要在 `Configuration` 中为这个平台保留配置槽位，并提供对应配置类，例如：

- `apiHost`
- `apiKey`
- 各服务 endpoint

已有 provider 都是按这个模式组织的，例如：

- `OpenAiConfig`
- `DoubaoConfig`
- `DashScopeConfig`

### 3.3 实现具体服务类

接下来要按能力实现服务类。

例如：

- Chat：`XxxChatService`
- Responses：`XxxResponsesService`
- Embedding：`XxxEmbeddingService`
- Audio：`XxxAudioService`
- Image：`XxxImageService`

不要求一个 provider 一次性支持所有服务，但必须明确“当前支持哪几类，不支持哪几类”。

### 3.4 在 `AiService` 中注册分发

`AiService` 是统一工厂入口。

如果新增 provider 后没有在这里补 switch 分发，业务层就无法通过：

```java
aiService.getChatService(PlatformType.Xxx)
```

拿到真正实现。

当前 `AiService` 的行为很明确：

- 支持就返回具体服务实现
- 不支持就抛 `IllegalArgumentException`

这条边界不应该被模糊化。

### 3.5 补 Spring Boot 自动配置

如果项目需要继续支持 starter 方式接入，还必须同步补：

- `XxxConfigProperties`
- `AiConfigAutoConfiguration` 中的初始化逻辑
- `@EnableConfigurationProperties(...)` 列表

否则 SDK 核心层虽然能用，Spring Boot 用户却配不起来。

### 3.6 补多实例注册表映射

如果项目使用 `AiServiceRegistry` / `FreeAiService` 做多实例路由，还需要在：

- `DefaultAiServiceRegistry`

中补充：

- 平台字符串到 `PlatformType` 的映射
- `AiPlatform` 到具体配置类的拷贝逻辑

---

## 4. 推荐的新增顺序

新增 provider 时，建议按服务粒度逐步落地，而不是一次性把所有能力都补完。

推荐顺序：

1. 先补 Chat
2. 再补 Responses 或 Embedding
3. 最后再补 Audio / Image / Realtime

原因是：

- Chat 是默认首调能力
- 先把主路径打通，最容易验证网络、鉴权、JSON 映射是否正确
- Audio / Realtime 往往更依赖平台特定协议和二进制处理

---

## 5. 何时不需要新增 Provider

很多时候其实不需要真的新增 provider。

如果某个平台：

- 请求/返回和 OpenAI 高度兼容
- 只需要改 base URL 和 api key
- 主要走 Chat 或 Responses

那么先评估能否直接复用现有 provider 路径，例如：

- 通过 `OPENAI` + 自定义 `baseUrl`
- 或现有兼容平台分支

这样做的好处是：

- 维护成本低
- 不需要新增一整套配置对象和工厂逻辑
- 用户也更容易理解接入路径

只有当下面情况明显成立时，再考虑新建 provider：

- 鉴权方式不同
- endpoint 结构差异大
- 响应字段无法稳定复用现有实现
- 有独立服务能力矩阵需要长期维护

---

## 6. 模型切换的推荐做法

对于已经接入的 provider，模型切换应尽量保持在“配置层或请求层”，而不是写死在业务代码中。

推荐方式：

- 普通 SDK：在请求对象里显式传 `model`
- Spring Boot：通过配置管理默认模型
- Coding Agent：通过 profile / workspace override / CLI 参数切换

不推荐：

- 在业务代码里到处散落硬编码模型名
- 把 provider 和 model 强绑定成多个重复 service bean

---

## 7. 新增 Provider 时的验收清单

至少应验证下面几项：

1. `AiService` 能正确返回对应服务实现
2. 不支持的服务会明确报错
3. 同步调用成功
4. 流式调用成功
5. 错误响应能被统一错误链处理
6. Spring Boot 配置能生效
7. `AiServiceRegistry` 多实例路由能正确工作
8. 文档能力矩阵已更新

---

## 8. 与 Coding Agent 的关系

如果只是让 `Coding Agent` 支持一个新模型，通常优先判断两件事：

1. 这个模型是否可以复用现有 provider 协议
2. 当前 CLI 是否已经允许该 provider / protocol 组合

也就是说，`Coding Agent` 的模型支持，首先受底层 SDK provider 能力约束；然后才是 CLI/Profile/Protocol 选择问题。

---

## 9. 推荐阅读

1. [平台适配与统一接口](/docs/ai-basics/platform-adaptation)
2. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
3. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
