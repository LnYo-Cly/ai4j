---
sidebar_position: 33
---

# Realtime 接口（WebSocket）

Realtime 在 AI4J 当前是一条 **很薄但正式存在的长连接能力面**。  
它的重点不是事件协议建模得多完整，而是 SDK 已经给出了统一入口、默认鉴权头和 WebSocket 建连主线。

## 1. 当前支持矩阵

从 `AiService.createRealtimeService(...)` 的分发看，当前 realtime 只支持：

- `OPENAI`

这意味着它目前不是一个多 provider 已充分验证的抽象面，而是先把 OpenAI realtime 路径正式收进了 Core SDK。

## 2. 统一契约长什么样

统一入口是：

- `IRealtimeService`

它提供两种建连方式：

- `createRealtimeClient(String baseUrl, String apiKey, String model, RealtimeListener listener)`
- `createRealtimeClient(String model, RealtimeListener listener)`

这说明 realtime 层的抽象非常明确：

- 它只负责建连
- 不负责替你定义事件语义
- 也不负责状态机推进

## 3. `OpenAiRealtimeService` 的真实行为

这个实现很薄，但几个默认行为必须写清楚。

### 配置回退

如果调用时 `baseUrl` 或 `apiKey` 为空，会回退到：

- `OpenAiConfig.apiHost`
- `OpenAiConfig.apiKey`

### URL 拼接

它会使用：

- `openAiConfig.getRealtimeUrl()`
- `?model=<model>`

拼出最终 WebSocket URL。

### 默认请求头

当前会自动带上：

- `Authorization: Bearer <apiKey>`
- `OpenAI-Beta: realtime=v1`

这意味着 SDK 已经把 OpenAI realtime 所需的最基本协议头收进实现里，调用方不需要每次手工补。

## 4. `RealtimeListener` 真正封装了什么

`RealtimeListener` 位于：

- `io.github.lnyocly.ai4j.listener.RealtimeListener`

它继承自 `WebSocketListener`，并抽象出四个你必须关心的回调：

- `onOpen(WebSocket)`
- `onMessage(ByteString)`
- `onMessage(String)`
- `onFailure()`

这里要特别注意一个实现细节：

- `onFailure(WebSocket, Throwable, Response)` 当前只记录日志，并没有调用抽象方法 `onFailure()`

也就是说，接口表面看起来有一个统一失败回调，但当前实现实际上没有把底层 OkHttp failure 事件转发给你的抽象 `onFailure()`。

这点非常值得文档明确写出来，否则调用方会误以为覆写 `onFailure()` 一定能收到断连失败通知。

## 5. 这一层当前没有替你做什么

Realtime service 当前只帮你做了“正确建连”，没有替你做：

- 事件对象建模
- 消息类型分发
- 自动重连
- 心跳治理
- backpressure
- 会话状态恢复

所以这条能力面目前更像“正式建连入口”，而不是完整 realtime runtime。

## 6. 为什么这页不能只写建连示例

如果文档只写一段 `createRealtimeClient(...)` 示例，会漏掉三个关键事实：

### 它是最薄的一层抽象

当前 realtime 层几乎不做事件语义封装，业务方要自己消费文本或二进制消息。

### 它依赖统一 `OkHttpClient`

Realtime 和其他 HTTP 能力一样，共享 `Configuration.okHttpClient`。  
这意味着代理、超时、连接池和 dispatcher 策略也会影响这条长连接路径。

### 它当前只收口了 OpenAI 建连约定

统一接口已经存在，但 provider 覆盖还很窄，说明这一层目前更像“先建立正式能力面”，而不是已经抽象到高度稳定的跨平台协议层。

## 7. 业务接入时最该注意什么

### 不要在回调里做重 CPU 工作

`RealtimeListener` 回调直接挂在 OkHttp WebSocket listener 上。  
如果你在这里做重处理，很容易把消息消费和连接处理耦死。

### 自己定义事件路由层

当前 SDK 没有替你把 string message 进一步拆成事件对象，所以更稳的做法是在业务层自己加一层事件分发。

### 连接治理责任仍在应用层

自动重连、指数退避、会话标识、监控埋点、断连补偿，这些都还不是 SDK 当前 realtime 层的职责。

## 8. 这一页的结论

> AI4J 当前的 Realtime 能力是一条很薄的正式建连抽象：它统一了 OpenAI realtime 的 URL、鉴权头和 WebSocket 入口，但还没有把事件协议、失败转发、重连治理和会话恢复做成完整 runtime。用它时，应该把它理解成“长连接入口层”，而不是“完整 realtime 会话框架”。
