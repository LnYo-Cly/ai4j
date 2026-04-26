# Extension 总览

`Extension` 这一章讲的是：当默认能力不够时，你应该沿哪条线扩展，而不是绕开基座自己另搭一套。

## 1. 当前有哪些扩展面

- provider 扩展
- model 扩展
- service 扩展
- HTTP / connection SPI 扩展

这些扩展面共同回答的是：你如何在不破坏 AI4J 分层的前提下，把新平台、新模型或新底层网络能力接进来。

## 2. 为什么它属于基座

因为扩展点不是上层 runtime 的补丁，而是基础工程模型的一部分。

如果这层没有设计好，后面的 `Spring Boot`、`Agent`、`Coding Agent`、`Flowgram` 就都只能各自绕路。

## 3. 一条原则

尽量沿 AI4J 已经定义好的抽象扩展，而不是直接绕开基座写平台私货。

更直接地说：

- 想接新 provider，先看 provider/model/service 扩展
- 想改底层 HTTP 行为，再看 SPI / HTTP stack

## 4. 推荐阅读顺序

1. [Provider Extension](/docs/core-sdk/extension/provider-extension)
2. [Model Extension](/docs/core-sdk/extension/model-extension)
3. [Service Extension](/docs/core-sdk/extension/service-extension)
4. [SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)
