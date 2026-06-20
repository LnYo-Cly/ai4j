# SPI HTTP Stack

这一页讲的是 AI4J 当前少数真正已经 SPI 化的扩展面之一：**底层 `OkHttp` 并发调度与连接池策略**。

它和 provider extension 最大的区别是：这里不是靠 `AiService` 里的 `switch` 接线，而是真的走了 Java `ServiceLoader`。

## 1. 真实入口在哪

核心入口非常集中：

- `network/DispatcherProvider.java`
- `network/ConnectionPoolProvider.java`
- `network/impl/DefaultDispatcherProvider.java`
- `network/impl/DefaultConnectionPoolProvider.java`
- `service/spi/ServiceLoaderUtil.java`
- `ai4j-spring-boot-starter/.../AiConfigAutoConfiguration.initOkHttp()`

默认实现之所以能生效，不是因为代码里写死了 fallback，而是因为：

- `ai4j/src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.DispatcherProvider`
- `ai4j/src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.ConnectionPoolProvider`

已经把默认实现注册到了 classpath。

## 2. Spring Boot starter 里的真实装配顺序

`AiConfigAutoConfiguration.initOkHttp()` 当前大致按下面顺序组装统一 `OkHttpClient`：

1. 创建 `HttpLoggingInterceptor`
2. 从 `okHttpConfigProperties` 读取日志级别
3. 用 `ServiceLoaderUtil.load(...)` 加载 `DispatcherProvider`
4. 用 `ServiceLoaderUtil.load(...)` 加载 `ConnectionPoolProvider`
5. 构造 `OkHttpClient.Builder`
6. 挂上 `ErrorInterceptor` 和 `ContentTypeInterceptor`
7. 应用 connect/write/read timeout
8. 注入 SPI 提供的 `Dispatcher` 与 `ConnectionPool`
9. 按配置决定是否启用代理
10. 按配置决定是否忽略 SSL 校验
11. `build()` 后写回统一 `Configuration`

这说明 HTTP stack 扩展在 starter 场景里非常靠前。  
一旦这里出问题，后面的 OpenAI、DashScope、Doubao、Qdrant 之类所有基于同一 `Configuration.okHttpClient` 的能力都会一起受影响。

## 3. 这层到底控制什么

当前正式暴露出来的点只有两个：

- `Dispatcher`
- `ConnectionPool`

看起来不多，但它们已经足以决定很多关键运行时特性：

- 请求并发调度方式
- 同类请求之间的排队和竞争关系
- 长连接与短请求之间的资源复用表现
- 统一网络栈在聊天、向量、外部协议请求间的共享行为

AI4J 把这两个点抽出来，目的不是给 demo 增加炫技选项，而是让底层网络治理有一个正式入口。

## 4. 为什么它值得单独作为扩展面

在这个仓库的典型场景里，网络行为并不单一：

- `Chat` 和 `Responses` 会有同步与流式两种模式
- `Embedding`、`Rerank` 往往是批量请求
- 向量存储和外部服务接入也会复用统一客户端

如果所有场景都只能依赖一套隐式默认网络参数，问题通常不会在 demo 阶段暴露，而会在真实环境里变成：

- 并发治理困难
- 长连接与短请求互相干扰
- 某个模块的网络调优意外影响其他能力面

HTTP SPI 的意义就在于，它把这种底层策略差异从“业务代码里随手 new 一个 client”提升成了可复用、可替换、可打包的正式扩展点。

## 5. 这层和手工注入 `OkHttpClient` 的边界

要特别区分两种使用方式。

### Spring Boot starter 路径

如果你走 starter，`initOkHttp()` 会主动使用 `ServiceLoaderUtil` 去找 SPI 实现，然后生成统一 `OkHttpClient`。

这时：

- SPI 是正式生效路径
- 默认实现来自 `META-INF/services`
- 自定义实现会影响整个 starter 装配出来的统一客户端

### 手工组装 `Configuration` 路径

如果你不走 starter，而是手工 new `Configuration` 并 `setOkHttpClient(...)`，那 SPI 本身不会自动介入。  
这时真正的扩展点是你手里的 `OkHttpClient` 组装代码，不是 `ServiceLoader`。

这个边界很重要，否则很容易误以为“我实现了 `DispatcherProvider`，所有场景都会自动生效”。实际上不是，只有走对应装配链时才会触发。

## 6. 当前默认实现的真实含义

`DefaultDispatcherProvider` 和 `DefaultConnectionPoolProvider` 当前都只是直接返回新的 OkHttp 默认对象：

- `new Dispatcher()`
- `new ConnectionPool()`

所以默认行为的本质是“沿用 OkHttp 自身默认策略”，而不是 AI4J 对并发和连接做了特别积极的调优。

这既有好处，也有代价：

- 好处：默认路径足够简单，行为接近原生 OkHttp
- 代价：一旦进入高并发、混合长短请求、或者严格资源治理场景，你往往需要自己给出更明确的策略

## 7. 这层最关键的失败路径

### `META-INF/services` 丢了

`ServiceLoaderUtil.load(...)` 找不到实现时会直接抛：

- `IllegalStateException("No implementation found for ...")`

所以如果你做了 fat jar、shade、重打包，或者错误排除了资源文件，starter 可能会在初始化 `OkHttpClient` 前就启动失败。

### 自定义 SPI 实现过度全局化

同一个 `Configuration.okHttpClient` 会被多个能力面共享。  
如果你把某个只适合单一路径的 dispatcher/connection pool 策略做成全局实现，就可能把问题从一个 provider 扩散到整个 SDK。

### 以为改了 SPI 就等于改了所有运行方式

如果某段代码根本没有走 starter，而是自己塞入了 `OkHttpClient`，那 SPI 改动对它不会自动生效。

## 8. 调试建议

出现网络层异常时，先按下面顺序排：

1. 看启动日志里 `ServiceLoaderUtil` 是否打印了已加载的实现类
2. 检查最终产物里是否还保留 `META-INF/services/*`
3. 确认当前路径到底是 starter 自动装配，还是手工 `Configuration.setOkHttpClient(...)`
4. 确认你的自定义 dispatcher / connection pool 是不是无意中影响了所有 provider

如果问题只出现在某一个 provider 的字段映射或返回解析上，那通常不该回到这一层，而应该去看对应 provider service。

## 9. 什么时候应该用这一层

适合下沉到 HTTP SPI 的场景包括：

- 想统一治理所有 AI 请求的底层并发行为
- 想把连接池策略做成 classpath 级替换
- 使用 starter，希望网络策略跟随自动装配统一落地

不适合过早下沉的场景包括：

- 只是某个 provider 的业务语义变化
- 只是新增模型字段
- 只是单个 demo 想临时改一个请求

## 10. 这一页的结论

> AI4J 的 HTTP stack 扩展是当前少数真正走 SPI 的部分。它通过 `ServiceLoader` 把 `Dispatcher` 与 `ConnectionPool` 注入 starter 构造出的统一 `OkHttpClient`，从而影响整个 SDK 的底层网络行为。这里的关键不是“能不能自定义”，而是要清楚它是全局策略入口、依赖 `META-INF/services` 打包完整性，而且只会在对应装配链上自动生效。
