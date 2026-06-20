# Client Integration

这一页讲的不是协议概念，而是 **AI4J 怎么把一个 MCP 服务真正接进请求链**。

关键问题只有三个：

1. 服务怎么连上
2. 服务能力怎么被缓存和发现
3. 本次请求怎么显式开放它

对应源码主线是：

- `mcp/client/McpClient.java`
- `mcp/gateway/McpGateway.java`
- `platform/openai/chat/entity/ChatCompletion.java`
- `platform/openai/response/entity/ResponseRequest.java`
- `tool/ToolUtil.java`

## 1. 接入的起点不是 provider，而是 `McpClient`

AI4J 里，单个 MCP 服务的实际宿主是 `McpClient`。

`McpClient.connect()` 做的事比“建立连接”多得多：

1. `transport.start()` 启动具体 transport
2. 发送 `initialize`
3. 发送 `notifications/initialized`
4. 成功后才把 `connected` 置为 true
5. 如果 transport 需要心跳，则启动低频健康检查

这意味着在 AI4J 的语义里，“transport 已连通” 还不算真正可用；必须完成初始化握手，能力目录才可信。

## 2. 初始化时客户端会声明哪些能力

`McpClient.initialize()` 当前会显式声明这些 client capability：

- `sampling`
- `roots.listChanged`
- `tools.listChanged`
- `resources.subscribe`
- `resources.listChanged`
- `prompts.listChanged`

并且默认请求协议版本 `2025-03-26`。

这几点很重要，因为它说明 AI4J 接入第三方 MCP 不是“只支持 tools/call 的最小客户端”，而是按完整 capability 面去做握手。

## 3. 服务能力怎么被客户端缓存

`McpClient` 维护三份缓存：

- `availableTools`
- `availableResources`
- `availablePrompts`

第一次访问时分别通过：

- `tools/list`
- `resources/list`
- `prompts/list`

拉取远端目录，之后复用本地缓存。

收到这些通知时缓存会失效：

- `notifications/tools/list_changed`
- `notifications/resources/list_changed`
- `notifications/prompts/list_changed`

所以 AI4J 当前对远端能力目录的心智是：

- 默认按缓存读
- 收到通知再失效

不是每次请求都重新全量拉目录。

## 4. 请求是怎么显式绑定 MCP 服务的

真正把服务引入本次模型请求的入口，是请求对象上的：

- `ChatCompletion.mcpServices(...)`
- `ResponseRequest.mcpServices(...)`

当前实现里，这两个字段都是 `List<String>`，也就是：

- 传的是 `serviceId`
- 不是直接把完整 `McpServerReference` 对象塞进请求

这点很关键。AI4J 当前稳定支持的请求语义是：

1. 先由宿主把服务注册到 `McpGateway`
2. 再由请求用 `serviceId` 白名单挑选本次开放哪些服务

## 5. provider 发送前真正发生了什么

以 `OpenAiChatService` 为代表，provider 适配层发送前会调用：

`ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())`

这一步会把：

- 本地 function tools
- 请求指定的 MCP services

合并成一组 provider 可见 `Tool`。

如果 `mcpServices` 为空，MCP 服务不会自动进入本次请求。

这也是 AI4J 当前最重要的安全默认值之一。

## 6. `McpGateway` 在 client integration 里扮演什么角色

`McpGateway` 是多服务宿主，但即便只接一个服务，也最好把它视为正式运行时组件。

它至少承担：

- 保存 `serviceId -> McpClient`
- 初始化和关闭客户端
- 刷新可用工具目录
- 维护 `tool -> client` 映射
- 为 `ToolUtil` 提供统一查询和调用入口

所以真正的接入关系不是：

`request -> remote service`

而是：

`request -> ToolUtil -> McpGateway -> McpClient -> transport -> remote service`

## 7. 执行阶段如何回到远端服务

当模型返回 tool call 后，执行链最终会经过 `ToolUtil.invoke(...)`。

对全局工具，它的优先级是：

1. 先尝试本地 MCP tool
2. 再尝试传统本地 function tool
3. 最后尝试 `McpGateway.callTool(...)`

这说明 “MCP tool 最终长得像普通 tool” 并不意味着执行链完全相同。AI4J 仍然保留了本地能力和远端能力的优先分流。

## 8. 断连、失败和重连时会发生什么

`McpClient` 的失败路径不是静默的：

- `onDisconnected(...)` 会清空缓存
- 停止心跳
- 停止 transport
- 取消所有 pending request
- 如果 `autoReconnect=true`，默认 5 秒后安排一次重连

这意味着接入第三方服务时要明确一个事实：

- MCP 连接不是“配完即永久稳定”
- 它在运行时可能掉线、重连、重新初始化

如果你的业务需要严格的一致性或事务性，这些失败面必须在宿主层补治理。

## 9. 目前实现里几个容易误解的点

### 不是所有 transport 都需要同样的保活策略

- `StdioTransport.needsHeartbeat()` 是 `false`
- `SseTransport.needsHeartbeat()` 也是 `false`
- `StreamableHttpTransport.needsHeartbeat()` 才是 `true`

所以不要把“心跳存在”误解成所有 MCP 接入的统一行为。

### 请求里没有传 `mcpServices`，不会自动开放

哪怕 gateway 里已经初始化了很多服务，本次请求仍然可能一个 MCP tool 都看不见。

### `McpServerReference` 目前更多是服务定义对象，不是请求面主入口

当前实际请求面还是 `serviceId` 列表。文档里如果把二者混成一回事，会给使用者错误预期。

## 10. 推荐的接入顺序

对正式项目，建议按下面顺序接：

1. 先单独验证 transport 能连接
2. 确认 `initialize` 握手能完成
3. 在 gateway 中注册服务
4. 只给一个最小 `serviceId` 白名单做请求测试
5. 再补资源、提示词或多服务组合

不要一开始就：

- 接多个服务
- 默认全部开放
- 再在 provider 侧回头查问题

## 11. 这一页的结论

> AI4J 的 MCP client integration 是“先把服务接到 `McpGateway`，再由请求用 `mcpServices` 显式开放”。`McpClient` 负责真正的握手、缓存和断连处理；provider 只看到最后被投影出来的 tools，而不是直接面对远端服务。
