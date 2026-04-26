# SPI Dispatcher ConnectionPool

这个案例解决的是“默认 OkHttp 并发与连接池策略不够，需要按自身业务并发模型扩展网络层”。

## 1. 适合什么场景

- 高并发问答
- 多租户平台
- 网关环境
- 需要统一控制并发、连接池、keep-alive 的系统

这类问题通常不会出现在第一个 demo 里，但一旦进入真实生产流量，就会变得非常关键。

## 2. 技术链路

核心组合是：

- `DispatcherProvider`
- `ConnectionPoolProvider`
- `ServiceLoader` / SPI
- `OkHttpClient.Builder`

这说明它不是业务功能案例，而是底层网络栈治理案例。

## 3. 为什么值得单独看

很多团队会把网络层调优直接写死在应用代码里。

AI4J 这里更推荐的路线是：

- 沿现有 SPI 扩展点接入
- 保持 `Core SDK` 与 `Spring Boot` 的统一抽象
- 不为了并发调优去修改 SDK 源码

## 4. 先补哪些主线页

1. [Core SDK / Extension](/docs/core-sdk/extension/overview)
2. [Core SDK / SPI HTTP Stack](/docs/core-sdk/extension/spi-http-stack)
3. [Spring Boot / Bean Extension](/docs/spring-boot/bean-extension)

## 5. 深入实现细节

如果你要看 SPI 接口、Spring Boot 自动装配加载方式和完整示例，继续看旧实现细节页：

- [旧路径案例页](/docs/guides/spi-dispatcher-connectionpool)
