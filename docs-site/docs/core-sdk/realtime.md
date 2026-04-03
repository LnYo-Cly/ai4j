---
sidebar_position: 33
---

# Realtime 接口（WebSocket）

Realtime 能力统一在 `IRealtimeService`，当前由 OpenAI 实现。

## 1. 核心入口

```java
IRealtimeService realtimeService = aiService.getRealtimeService(PlatformType.OPENAI);
```

建立连接：

```java
WebSocket ws = realtimeService.createRealtimeClient(
        "gpt-4o-realtime-preview",
        new RealtimeListener() {
            @Override
            protected void onOpen(WebSocket webSocket) {
                System.out.println("opened");
            }

            @Override
            protected void onMessage(ByteString bytes) {
                System.out.println("binary=" + bytes.size());
            }

            @Override
            protected void onMessage(String text) {
                System.out.println("text=" + text);
            }

            @Override
            protected void onFailure() {
                System.out.println("failed");
            }
        }
);
```

## 2. 连接参数

`createRealtimeClient(baseUrl, apiKey, model, listener)` 支持：

- 自定义 baseUrl
- 自定义 apiKey
- 模型名
- 监听器

默认请求头会带：

- `Authorization: Bearer ...`
- `OpenAI-Beta: realtime=v1`

## 3. 监听器设计

`RealtimeListener` 是 `WebSocketListener` 的抽象封装，约定了：

- `onOpen`
- `onMessage(ByteString)`
- `onMessage(String)`
- `onFailure`

你可以在 `onMessage(String)` 里做事件分发。

## 4. 使用建议

- 长连接场景建议独立线程池管理
- 连接断开要做重连策略（指数退避）
- 消息处理要做 backpressure（防止消费跟不上）

## 5. 常见问题

### 5.1 建连失败

- 检查模型名是否可用
- 检查网络/代理是否允许 WebSocket
- 检查 API key 与 host

### 5.2 消息处理阻塞

- 不要在回调里做重 CPU 工作
- 将业务逻辑投递到异步队列

### 5.3 连接无故关闭

- 服务端超时回收
- 心跳缺失
- 网关空闲连接策略

## 6. 生产化建议

- 给每个连接分配 `sessionId`
- 记录建连耗时、断连原因、重连次数
- 对音视频大包加大小限制与审计
