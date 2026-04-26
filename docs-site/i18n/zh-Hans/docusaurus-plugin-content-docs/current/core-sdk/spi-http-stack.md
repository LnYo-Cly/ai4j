---
sidebar_position: 42
---

# SPI：Dispatcher 与 ConnectionPool

ai4j 的网络层扩展点通过 SPI 提供，目的是让你按业务并发模型定制 OkHttp。

## 1. 扩展接口

- `DispatcherProvider`
- `ConnectionPoolProvider`

默认实现：

- `DefaultDispatcherProvider`
- `DefaultConnectionPoolProvider`

## 2. 为什么有必要

不同业务并发模式差异很大：

- 流式对话：长连接多，吞吐要求高
- 批处理：短请求多，峰值并发高
- 多租户：需要隔离/限速

如果统一一个默认参数，往往在生产会踩坑。

## 3. 自定义实现示例

### 3.1 Dispatcher

```java
public class CustomDispatcherProvider implements DispatcherProvider {
    @Override
    public Dispatcher getDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(64);
        return dispatcher;
    }
}
```

### 3.2 ConnectionPool

```java
public class CustomConnectionPoolProvider implements ConnectionPoolProvider {
    @Override
    public ConnectionPool getConnectionPool() {
        return new ConnectionPool(100, 5, TimeUnit.MINUTES);
    }
}
```

## 4. SPI 注册

`src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.DispatcherProvider`

```text
com.example.CustomDispatcherProvider
```

`src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.ConnectionPoolProvider`

```text
com.example.CustomConnectionPoolProvider
```

## 5. Spring Boot 自动装配路径

starter 在 `AiConfigAutoConfiguration` 里通过 `ServiceLoaderUtil.load(...)` 加载 SPI 并写入 `OkHttpClient.Builder`。

这意味着你只要把 SPI 放到 classpath，就会自动生效。

## 6. 非 Spring 用法

```java
DispatcherProvider dispatcherProvider = ServiceLoaderUtil.load(DispatcherProvider.class);
ConnectionPoolProvider poolProvider = ServiceLoaderUtil.load(ConnectionPoolProvider.class);

OkHttpClient client = new OkHttpClient.Builder()
        .dispatcher(dispatcherProvider.getDispatcher())
        .connectionPool(poolProvider.getConnectionPool())
        .build();
```

## 7. 调优建议

先压测再调参，不要拍脑袋：

- `maxRequests`
- `maxRequestsPerHost`
- 连接池大小
- keepAlive 时长

并结合上游平台的限流规则。

## 8. 常见问题

### 8.1 SPI 不生效

- `META-INF/services` 文件名写错
- 实现类全限定名写错
- 资源没有打包进 jar

### 8.2 多实现冲突

当前 `ServiceLoaderUtil` 取第一个实现，建议每个应用只保留一套 SPI 实现。

### 8.3 调大并发后错误更多

通常是上游限流触发，建议联动：

- 请求限流
- 指数退避重试
- 熔断降级
