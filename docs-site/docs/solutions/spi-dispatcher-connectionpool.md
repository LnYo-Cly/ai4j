# SPI Dispatcher ConnectionPool

这个方案讲的不是模型能力，而是当你已经进入生产并发和网络治理阶段时，AI4J 的 HTTP 栈该怎么扩展。

## 1. 适合什么场景

- 高并发调用模型
- 多 provider 共用一套网络池
- 想按业务流量模型定制 OkHttp

它的重点是网络层扩展，而不是 AI 能力本身。

## 2. 核心模块组合

这条方案的主链是：

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `ServiceLoader`
- `OkHttpClient.Builder`

这说明 AI4J 并没有把网络层写死，而是显式留出了 SPI 扩展点。

## 3. 为什么这条线重要

当你开始进入生产并发治理，真正要关心的往往是：

- provider 并发上限
- 每主机连接复用
- 网络稳定性与隔离
- 不同业务流量模型之间的影响

这时 “AI SDK 能不能调起来” 已经不是核心问题，HTTP 栈治理才是。

## 4. 什么时候不用先看它

如果你还在验证功能，或者仍然处于单机低并发阶段，不必先花精力看这一页。

先把功能面跑通，再处理网络池和并发治理，成本更低。

## 5. 先补哪些主线页

1. [Core SDK / Service Entry and Registry](/docs/core-sdk/service-entry-and-registry)
2. [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)
3. [Core SDK / SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)

## 6. 继续看实现细节

如果你要看：

- 自定义 `DispatcherProvider`
- 自定义 `ConnectionPoolProvider`
- SPI 注册方式

继续看深页：

- [旧路径案例页](/docs/guides/spi-dispatcher-connectionpool)

## 7. 关键对象

这条方案对应的关键对象很集中：

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `ServiceLoader`
- `OkHttpClient.Builder`

这组对象已经足够说明 AI4J 的 HTTP 栈为什么能进入正式扩展，而不是只能手工 patch 默认客户端。

## 8. 什么时候值得进入这一层

通常当你开始遇到下面的问题时，这条方案才会变成主线：

- 多 provider 并发互相影响
- 默认连接池不再匹配业务流量模型
- 需要对不同业务流量做网络隔离和治理

在此之前，优先把上层能力链跑通通常更合适。

## 9. 实施时应先约束什么

- 并发调度是否按 provider 或业务域隔离
- 连接池参数是否与实际部署拓扑匹配
- 扩展后是否仍能保持统一配置和可观测性
