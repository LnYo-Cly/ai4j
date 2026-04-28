# SPI HTTP Stack

AI4J 的网络层不是只能被动接受默认 `OkHttp` 设置。基座层已经把底层并发调度和连接池暴露成正式 SPI 扩展点。

如果文档只写“可以自定义 Dispatcher 和 ConnectionPool”，其实还没有讲清它为什么值得存在。

## 1. 核心源码入口

- `network/DispatcherProvider.java`
- `network/ConnectionPoolProvider.java`
- `network/impl/DefaultDispatcherProvider.java`
- `network/impl/DefaultConnectionPoolProvider.java`
- `AiConfigAutoConfiguration`

这些类说明 AI4J 对 HTTP 栈的态度不是“用户自己 new 一个 OkHttpClient 爱怎么配怎么配”，而是给出了**正式扩展位**。

## 2. 为什么这层会成为真实问题

在 AI4J 的典型场景里，网络行为差异很大：

- 流式对话：长连接多
- embedding 批处理：短请求密集
- MCP / 外部服务：连接形态复杂

如果所有场景都用一套拍脑袋默认值，到了真实环境里就很容易碰到：

- 吞吐不足
- 连接池抖动
- 上游限流放大

所以 SPI HTTP stack 不是什么“高级优化”，而是生产可治理性的入口。

## 3. 这层到底允许你控制什么

最直接的两个点是：

- `Dispatcher`
- `ConnectionPool`

也就是：

- 请求并发上限
- host 级并发
- 连接复用规模
- keep-alive 行为

这类参数对流式模型、向量请求、外部协议连接的影响都非常直接。

## 4. 为什么用 SPI 而不是业务层手改

如果每个业务应用都自己局部调整 `OkHttpClient`，会出现两个问题：

- 很难统一治理
- 很难和 starter / 配置体系对齐

AI4J 把它做成 SPI 的意义就在于：

- classpath 即可生效
- 扩展点稳定
- starter 和非-starter 场景都能对齐

这比“在一个工具类里手动改 builder”可维护得多。

## 5. 什么时候该真的来调这层

- 高并发聊天服务
- 大批量 embedding / rerank
- 长连接 / 流式输出很多
- 外部 MCP 连接比较重

如果只是本地 demo，一般没必要过早下沉到这层。

## 6. 设计摘要

> AI4J 把底层 `OkHttp` 的并发调度和连接池扩展做成了 SPI，而不是让每个应用零散地手调 HTTP client。这样流式聊天、批量 embedding、MCP 等网络行为可以进入统一治理，而不是停留在业务层局部 patch。

## 7. 继续阅读

- [Extension / Overview](/docs/core-sdk/extension/overview)
- [Model Access / Streaming](/docs/core-sdk/model-access/streaming)
