# Extension 总览

`Extension` 这一章讲的是：当默认能力不够时，你应该沿哪条线扩展，而不是绕开基座自己另搭一套。

## 1. 先确定它在 Core SDK 里的位置

扩展点不是附属补丁，而是基座设计的一部分。

如果 `Core SDK` 不提供清楚的扩展面，那么后面的：

- `Spring Boot`
- `Agent`
- `Coding Agent`
- `Flowgram`

都会被迫各自绕开基座写平台特化代码。

## 2. 当前有哪些扩展面

- provider 扩展
- model 扩展
- service 扩展
- HTTP / connection SPI 扩展

这些扩展面共同回答的是：你如何在不破坏 AI4J 分层的前提下，把新平台、新模型或新底层网络能力接进来。

## 3. 为什么它属于基座

因为扩展点不是上层 runtime 的补丁，而是基础工程模型的一部分。

如果这层没有设计好，后面的 `Spring Boot`、`Agent`、`Coding Agent`、`Flowgram` 就都只能各自绕路。

## 4. 一条原则

尽量沿 AI4J 已经定义好的抽象扩展，而不是直接绕开基座写平台私货。

更直接地说：

- 想接新 provider，先看 provider/model/service 扩展
- 想改底层 HTTP 行为，再看 SPI / HTTP stack

## 5. 什么时候应该先看这一章

更适合直接进入 `extension` 的情况包括：

- 你要接一个新的模型平台
- 你要补一个当前没有的能力接口实现
- 你要调整底层 HTTP / 连接层策略
- 你要做企业内平台接入，但不想破坏 AI4J 主分层

如果你只是普通使用现有平台，这一章可以后看，不必一开始就进入。

## 6. 推荐阅读顺序

1. [Provider Extension](/docs/core-sdk/extension/provider-extension)
2. [Model Extension](/docs/core-sdk/extension/model-extension)
3. [Service Extension](/docs/core-sdk/extension/service-extension)
4. [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)

## 7. 扩展决策顺序

真正动手扩展前，建议先按下面顺序判断：

1. 这是新 provider，还是现有 provider 下的新模型
2. 现有 service 契约还能不能承载这项能力
3. 问题是在能力抽象层，还是只在底层网络与连接层
4. 这次改动要不要同步进入 starter 的配置和装配体系

这个顺序能显著减少“明明只该补 model，却误改 provider 总线”这类结构性错误。

## 8. 关键对象

如果要继续从文档进入源码，优先看下面这些入口：

- `service/PlatformType.java`：provider 维度枚举
- `service/factory/AiService.java`：能力工厂与 provider 分发中心
- `service/factory/AiServiceRegistry.java`：注册与装配扩展点
- starter 侧自动装配：负责把基座扩展接进 Spring 容器

这组对象决定了扩展点为什么能成立，也决定了“新增能力应该改哪一层”这个问题最终该如何落地。

## 9. 什么时候不要先动这一章

如果你只是：

- 使用现成 provider 发请求
- 调整 prompt、tool 或 memory 组合
- 排查某个具体 provider 的字段映射问题

那通常应先回到对应能力页，而不是一上来就进入 extension。扩展页更适合处理“基础抽象本身不够用”的情况。
