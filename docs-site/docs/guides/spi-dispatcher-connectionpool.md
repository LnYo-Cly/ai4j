---
sidebar_position: 4
---

# SPI：自定义 Dispatcher 与 ConnectionPool

ai4j 的网络层默认基于 OkHttp，并通过 SPI 暴露两个关键扩展点：

- `DispatcherProvider`
- `ConnectionPoolProvider`

这使你可以按自身并发策略替换默认实现，而不改 SDK 源码。

## 1. 为什么要做这个扩展

真实业务里，不同场景对网络层诉求不同：

- 高并发问答：要限制全局并发，防止把上游打爆；
- 多租户：要隔离连接池、控制队头阻塞；
- 网关环境：要统一超时、并发和连接生命周期。

把并发策略抽到 SPI，是更稳定的工程做法。

## 2. ai4j 的 SPI 接口

```java
public interface DispatcherProvider {
    Dispatcher getDispatcher();
}

public interface ConnectionPoolProvider {
    ConnectionPool getConnectionPool();
}
```

默认实现位于：

- `io.github.lnyocly.ai4j.network.impl.DefaultDispatcherProvider`
- `io.github.lnyocly.ai4j.network.impl.DefaultConnectionPoolProvider`

并在 `META-INF/services` 中注册。

## 3. Spring Boot 中是如何加载的

`ai4j-spring-boot-starter` 在 `AiConfigAutoConfiguration#initOkHttp()` 里会调用：

- `ServiceLoaderUtil.load(DispatcherProvider.class)`
- `ServiceLoaderUtil.load(ConnectionPoolProvider.class)`

拿到实例后写入 `OkHttpClient.Builder`：

- `.dispatcher(...)`
- `.connectionPool(...)`

## 4. 自定义实现示例

### 4.1 自定义 DispatcherProvider

```java
package com.example.ai4j.spi;

import io.github.lnyocly.ai4j.network.DispatcherProvider;
import okhttp3.Dispatcher;

public class HighThroughputDispatcherProvider implements DispatcherProvider {
    @Override
    public Dispatcher getDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(256);
        dispatcher.setMaxRequestsPerHost(64);
        return dispatcher;
    }
}
```

### 4.2 自定义 ConnectionPoolProvider

```java
package com.example.ai4j.spi;

import io.github.lnyocly.ai4j.network.ConnectionPoolProvider;
import okhttp3.ConnectionPool;

import java.util.concurrent.TimeUnit;

public class TunedConnectionPoolProvider implements ConnectionPoolProvider {
    @Override
    public ConnectionPool getConnectionPool() {
        return new ConnectionPool(80, 5, TimeUnit.MINUTES);
    }
}
```

### 4.3 注册 SPI 文件

`src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.DispatcherProvider`

```text
com.example.ai4j.spi.HighThroughputDispatcherProvider
```

`src/main/resources/META-INF/services/io.github.lnyocly.ai4j.network.ConnectionPoolProvider`

```text
com.example.ai4j.spi.TunedConnectionPoolProvider
```

## 5. 非 Spring 项目如何使用

非 Spring 项目同样可以显式加载：

```java
DispatcherProvider dispatcherProvider = ServiceLoaderUtil.load(DispatcherProvider.class);
ConnectionPoolProvider poolProvider = ServiceLoaderUtil.load(ConnectionPoolProvider.class);

OkHttpClient client = new OkHttpClient.Builder()
        .dispatcher(dispatcherProvider.getDispatcher())
        .connectionPool(poolProvider.getConnectionPool())
        .build();
```

## 6. 常见问题

### 6.1 没有找到 SPI 实现

异常通常是：`No implementation found for ...`。

排查顺序：

1. `META-INF/services` 文件名是否与接口全限定名完全一致；
2. 文件内容是否是实现类全限定名；
3. 资源文件是否被打进最终 jar。

### 6.2 多个实现冲突

`ServiceLoaderUtil.load(...)` 当前取“第一个可用实现”。

建议：

- 每个应用只保留一套 SPI 实现；
- 或保证 classpath 顺序稳定。

### 6.3 参数应该怎么调

建议先从保守值开始压测，再逐步放开：

- `maxRequests`
- `maxRequestsPerHost`
- 连接池大小与保活时间

并结合上游平台 QPS 限制做限流。

## 7. 生产建议

- 将 SPI 实现和业务代码放在同一仓库，方便版本协同。
- 配套暴露并发指标（队列长度、拒绝数、超时率）。
- 重大变更（连接池参数）走灰度发布。

SPI 这层做好后，模型切换和流量增长会稳定很多。
