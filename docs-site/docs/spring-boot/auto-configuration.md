# Spring Boot Auto Configuration

这一页讲的是 starter 的真实装配链，而不是泛泛地说“它会自动配置一些 Bean”。

## 1. 真实入口类

核心入口是：

- `AiConfigAutoConfiguration`

它做的事情不是单点注入，而是一整条装配链：

- `@EnableConfigurationProperties(...)` 绑定一组 `ai.*` 属性类
- `@PostConstruct` 初始化统一 `Configuration`
- 创建 `AiService`
- 创建 `AiServiceFactory`
- 创建 `AiServiceRegistry`
- 创建 `FreeAiService`
- 条件性创建 `VectorStore`、`RagContextAssembler`、`Reranker`

## 2. 初始化顺序

`AiConfigAutoConfiguration` 里最关键的是 `init()`：

1. 初始化 `OkHttpClient`
2. 初始化各类向量数据库配置
3. 初始化 `SearXNG` 配置
4. 初始化各 provider 配置
5. 把这些对象写回统一 `Configuration`

这意味着你在 Spring Boot 下拿到的不是一堆互不相关的配置 Bean，而是已经被组织好的运行时图。

## 3. `initOkHttp()` 的意义

`initOkHttp()` 不是普通工具方法，它决定了整个 starter 的底层网络栈。

它会：

- 构造 `HttpLoggingInterceptor`
- 通过 `ServiceLoaderUtil.load(...)` 加载 `DispatcherProvider`
- 通过 `ServiceLoaderUtil.load(...)` 加载 `ConnectionPoolProvider`
- 组装统一 `OkHttpClient.Builder`
- 按配置加入代理和 SSL 策略
- 最后写回 `Configuration.okHttpClient`

这一步一旦失败，后面的 provider、vector、RAG、websearch 相关能力都会一起受影响，因为它们共享同一个底层客户端入口。

## 4. 单实例和多实例在这里怎么分流

### 单实例

如果你只配置一个 provider，主线通常是：

- `AiService`

### 多实例

如果你配置了 `ai.platforms[]`，主线通常是：

- `AiServiceRegistry`
- `AiServiceRegistration`
- `FreeAiService`

这不是同一个东西的不同名字，而是两条不同组织方式。

## 5. 条件装配的边界

starter 里并不是所有东西都无条件创建。

有些 Bean 是：

- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnBean`

这意味着默认 Bean 的存在是“可被接管”的，而不是强制覆盖业务实现。

## 6. 你应该怎么看这页

把它看成一个对象图说明页：

- 配置怎么进来
- 统一 `Configuration` 怎么组起来
- 哪些对象是基础入口
- 哪些对象是可选增强

如果你只把它理解成“自动装配示例”，就会看漏真正重要的部分：**装配顺序和失败传播路径**。
