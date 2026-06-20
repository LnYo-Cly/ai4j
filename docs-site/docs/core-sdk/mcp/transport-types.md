# Transport Types

这一页只讲 transport。它不决定业务 capability 是 Tool、Resource 还是 Prompt，但会直接决定：

- 服务怎么启动
- 会不会有独立会话
- 连接断了谁来恢复
- 错误看起来像本地进程问题还是网络问题

AI4J 当前 transport 相关实现主要看：

- `mcp/util/McpTypeSupport.java`
- `mcp/transport/StdioTransport.java`
- `mcp/transport/SseTransport.java`
- `mcp/transport/StreamableHttpTransport.java`
- `mcp/server/StdioMcpServer.java`
- `mcp/server/SseMcpServer.java`
- `mcp/server/StreamableHttpMcpServer.java`

## 1. AI4J 当前统一支持哪三类 transport

`McpTypeSupport` 会把各种别名归一化为三类：

- `stdio`
- `sse`
- `streamable_http`

兼容别名包括：

- `process` / `local` -> `stdio`
- `server-sent-events` / `event-stream` -> `sse`
- `http` / `mcp` / `streamable-http` / `http-streamable` -> `streamable_http`

所以 transport 在 AI4J 里已经是正式类型系统，不是任意字符串备注。

## 2. `stdio` 的真实含义

`StdioTransport` 代表的是：

- 宿主自己启动外部进程
- 通过标准输入输出收发 JSON-RPC 消息
- 连接状态和进程存活直接绑定

它的几个关键行为：

- `start()` 会启动子进程
- Windows 下对 `npx` 做了 `cmd /c npx ...` 包装
- `redirectErrorStream(true)`，标准错误会并入输出流
- `needsHeartbeat()` 返回 `false`
- `isConnected()` 依赖 `process.isAlive()`

对应服务端 `StdioMcpServer`：

- 只支持协议版本 `2024-11-05`
- `initializationRequired = false`
- `pingEnabled = false`
- `toolsListChanged = false`

这说明 `stdio` 更像：

- 本地同机服务
- 工具进程桥接
- 仓库内辅助服务

优点是部署直接，缺点是宿主要负责进程生命周期。

## 3. `sse` 的真实含义

`SseTransport` 不是“普通 HTTP GET 然后收流”这么简单。它遵循的是典型双端点模式：

1. 客户端先 `GET /sse`
2. 服务端发出 `endpoint` 事件，告诉客户端消息 POST 端点
3. 客户端再向 `/message` 发送 JSON-RPC 请求
4. 响应通过 SSE 事件流返回

从实现上看：

- `start()` 会先建立 SSE 长连接
- `waitForEndpointEvent()` 最多等 10 秒
- 没收到 endpoint 就视为启动失败
- `needsHeartbeat()` 返回 `false`
- 连接状态要求 reader 线程存活且 endpoint 已收到

对应服务端 `SseMcpServer`：

- 使用 `/sse` 和 `/message` 两个端点
- 协议版本固定 `2024-11-05`
- `initializationRequired = true`
- `pingEnabled = true`
- `toolsListChanged = false`
- SSE 连接会周期性发送 `event: ping`

这说明 `sse` 更适合：

- 已服务化但仍保持事件流响应的服务
- 明确区分“建立事件通道”和“发送请求消息”的部署模型

## 4. `streamable_http` 的真实含义

`StreamableHttpTransport` 是三者里最接近现代服务接口的一种。

它的几个关键行为：

- 向同一 `mcpEndpointUrl` 发送 `POST`
- `Accept` 同时声明 `application/json, text/event-stream`
- 服务端可以选择单次 JSON 响应，也可以升级成 SSE 流响应
- 支持 `mcp-session-id`
- 支持 `last-event-id`
- `needsHeartbeat()` 返回 `true`
- 支持 `DELETE` 终止会话

对应服务端 `StreamableHttpMcpServer`：

- 端点是 `/mcp`
- 支持 `GET / POST / DELETE / OPTIONS`
- 支持协议版本 `2025-03-26` 和 `2024-11-05`
- 默认协议版本 `2025-03-26`
- `initializationRequired = true`
- `pingEnabled = false`
- `toolsListChanged = true`

这意味着 `streamable_http` 更适合：

- 正式服务化部署
- 需要显式会话 ID
- 希望兼容标准 HTTP 基础设施
- 既能一次一响应，也能流式返回

## 5. transport 不是小细节，而是错误模型选择

同样的 tool call，在三种 transport 下失败形态完全不同：

### `stdio`

更像：

- 子进程启动失败
- 进程立即退出
- 标准输出协议不合法

### `sse`

更像：

- SSE 连接建立失败
- 长连接中断
- 没收到 endpoint 事件
- POST 端点或 session header 配置错误

### `streamable_http`

更像：

- 端点返回 HTTP 错误
- 响应内容类型不是 JSON/SSE
- 会话 ID 丢失
- `DELETE` 会话终止失败

所以 transport 选型实际上是在选“你愿意面对什么样的运维和调试问题”。

## 6. AI4J 当前的保活与恢复语义

不要把 transport 保活理解成统一逻辑。

当前实现里：

- `StdioTransport.needsHeartbeat()` 是 `false`
- `SseTransport.needsHeartbeat()` 是 `false`
- `StreamableHttpTransport.needsHeartbeat()` 是 `true`

`McpClient` 只会在 `needsHeartbeat()` 为 `true` 时启动 10 分钟一次的兜底健康检查，实际做法是重新触发一次 `getAvailableTools()`。

这意味着：

- `streamable_http` 在 AI4J 中最明确地被当成需要会话健康维护的 transport
- `stdio` 和 `sse` 更多依赖底层连接自身状态

## 7. 选型建议

### 选 `stdio`

适合：

- 本地工具链
- 子进程式集成
- 开发机或单机部署

不适合：

- 多租户远程服务
- 需要标准网关接入的场景

### 选 `sse`

适合：

- 已有事件流服务
- 明确的 GET/POST 双端点模式
- 想保留长连接事件语义

不适合：

- 只想走一个统一 HTTP 端点
- 对基础设施兼容要求很高的场景

### 选 `streamable_http`

适合：

- 服务端正式发布
- 需要 session 和流式响应协商
- 想贴近标准 HTTP 服务治理

不适合：

- 只在本地启动一个辅助子进程就能解决的问题

## 8. 当前实现里要特别记住的边界

### `stdio` server 端不要求初始化

`StdioMcpServer` 的 `initializationRequired=false`，但 `McpClient` 仍然会主动发 `initialize`。这意味着客户端侧心智仍保持一致，只是服务端门槛更低。

### `streamable_http` 才支持 `toolsListChanged=true`

这一点会影响能力目录动态变化时的表达能力。

### `sse` transport 本身不需要额外 heartbeat

服务端会自己发送 `ping` 事件，客户端 transport 侧也依赖底层长连接状态。

## 9. 这一页的结论

> AI4J 把 MCP transport 统一成 `stdio / sse / streamable_http` 三类，但三者并不只是“连接方式不同”。它们分别对应不同的进程模型、会话语义、恢复策略和调试路径。transport 一旦选错，后面遇到的问题往往不是 capability 问题，而是生命周期和运维问题。
