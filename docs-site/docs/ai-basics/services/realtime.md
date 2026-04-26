---
sidebar_position: 33
---

# Realtime 接口（WebSocket）

Realtime 能力统一在 `IRealtimeService`，当前主线是 OpenAI Realtime 路径。

它和普通 HTTP 请求最大的区别是：

- 不是“一次请求，一次响应”
- 而是“建立连接，持续交换事件”

---

## 1. 适合什么场景

Realtime 更适合：

- 实时语音交互
- 低延迟多轮对话
- 长连接会话场景
- 需要边收边处理的事件流

如果你的需求只是普通文本问答，不要优先上 Realtime，先用 Chat 或 Responses 更稳。

---

## 2. 获取服务入口

```java
IRealtimeService realtimeService = aiService.getRealtimeService(PlatformType.OPENAI);
```

---

## 3. 建立连接

### 3.1 最小示例

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

### 3.2 可自定义的连接参数

`createRealtimeClient(...)` 支持自定义：

- `baseUrl`
- `apiKey`
- `model`
- `listener`

默认会带上必要请求头，例如：

- `Authorization: Bearer ...`
- `OpenAI-Beta: realtime=v1`

---

## 4. 监听器如何设计

`RealtimeListener` 是基于 `WebSocketListener` 的封装。

通常你会关心这几类回调：

- `onOpen`
- `onMessage(String)`
- `onMessage(ByteString)`
- `onFailure`

推荐做法是：

- `onMessage(String)` 里只做事件解析与投递
- 真正业务逻辑放到独立线程或队列处理

不要把重 CPU、重 IO 的逻辑直接压在回调线程里。

---

## 5. 文本事件与二进制事件怎么分工

Realtime 场景里，文本和二进制往往承担不同职责。

### 5.1 文本消息

通常用于：

- JSON 事件
- 状态更新
- 指令回执
- 会话控制消息

### 5.2 二进制消息

通常用于：

- 音频片段
- 压缩帧
- 其他二进制载荷

因此业务层建议先拆分处理通道，而不是把所有消息都混成一个字符串处理器。

---

## 6. 生产环境的连接管理建议

### 6.1 生命周期管理

建议为每条连接维护：

- `sessionId`
- 建连时间
- 最后活跃时间
- 当前状态
- 重连次数

### 6.2 重连策略

Realtime 长连接不可避免会遇到断连。

建议使用：

- 指数退避
- 最大重试次数
- 熔断窗口

不要在 `onFailure` 里立即无上限死循环重连。

### 6.3 backpressure

如果消息生产速度快于消费速度，必须有缓冲与限流策略。

常见做法：

- 有界队列
- 丢弃低优先级事件
- 按 session 做并发隔离

---

## 7. 与普通 HTTP 服务的分层建议

Realtime 接口建议单独做一层网关或连接管理器，不要和普通 Chat Controller 混成一个 Service。

推荐分层：

1. Connection Manager
2. Event Decoder
3. Business Dispatcher
4. Session State Store

这样做的好处是：

- 连接异常更容易排查
- 文本/音频事件可独立扩展
- 重连和状态恢复不会污染业务代码

---

## 8. 常见问题

### 8.1 建连失败

优先检查：

- 模型名是否可用
- 网络或代理是否允许 WebSocket
- API key、host、beta header 是否正确

### 8.2 消息处理阻塞

典型原因是把重逻辑写进了回调。

建议：

- 回调只做解码
- 真正业务逻辑投递到异步线程池

### 8.3 连接经常被关闭

常见原因：

- 服务端空闲回收
- 中间网关超时
- 心跳缺失
- 客户端消费过慢

### 8.4 二进制包过大

建议：

- 明确单帧大小上限
- 做审计与限流
- 必要时按片段传输

---

## 9. 推荐阅读

1. [统一服务入口与调用方式](/docs/ai-basics/unified-service-entry)
2. [统一请求与返回读取约定](/docs/ai-basics/request-and-response-conventions)
3. [Audio 接口](/docs/ai-basics/services/audio)
