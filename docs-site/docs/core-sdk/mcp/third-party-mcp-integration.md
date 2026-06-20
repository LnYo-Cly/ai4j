# Third-party MCP Integration

接第三方 MCP 服务时，最容易犯的错误是只盯着“能不能连上”，却忽略了真正更贵的问题：

- 这个服务背后到底握着什么权限
- 它的工具目录是不是稳定
- 当前请求到底该开放多少能力
- 宿主出了问题时该从哪一层排查

AI4J 当前第三方 MCP 接入的主要控制点是：

- `McpTypeSupport`
- `McpClient`
- `McpGateway`
- `ToolUtil`
- 请求对象里的 `mcpServices`

## 1. 第三方接入的第一步不是写 prompt，而是确认 transport

先确认对方到底提供的是哪类服务：

- 本地子进程式 `stdio`
- 双端点事件流 `sse`
- 单端点 `streamable_http`

这一步非常重要，因为 transport 会决定：

- 连接如何建立
- 会不会有 session
- 是否要处理 endpoint 事件
- 错误长什么样

如果 transport 都没搞清楚，后面所有 provider 层排查都容易跑偏。

## 2. AI4J 当前的接入姿势是什么

第三方服务接入后，典型链路是：

1. 把服务信息写入 `mcp-servers-config.json` 或 `McpConfigSource`
2. `McpGateway.initialize(...)` 创建对应 `McpClient`
3. `McpClient.connect()` 完成 transport 启动与握手
4. 请求用 `mcpServices("serviceId")` 显式选择本次开放它
5. provider 发送前用 `ToolUtil.getAllTools(...)` 把它投影成 tools

这说明 AI4J 接第三方 MCP 的心智不是“直接把 URL 透传给模型”，而是：

- 先纳入宿主运行时
- 再做请求级暴露

## 3. 第三方服务最危险的不是协议，而是权限投射

很多第三方 MCP 后面连着的是高权限真实系统，例如：

- GitHub 仓库写权限
- 浏览器登录态
- 数据库访问权限
- 内部业务系统操作能力

模型看到的是 tool name，但宿主真正承担的是系统权限后果。

所以第三方接入时必须把下面两层拆开：

- 服务本身能做什么
- 模型本次被允许看到什么

AI4J 目前已经提供了第二层的第一道边界：`mcpServices` 白名单。

## 4. 当前实现里什么是“做对了”的

AI4J 现在最重要的正确默认值是：

- gateway 接入不等于请求开放

哪怕第三方服务已经成功连上，只有当请求显式传入相应 `serviceId` 时，它的 tool 才会进入 provider 请求。

这避免了最糟糕的默认行为：

- 只要宿主连了服务，模型就全部自动可见

## 5. 但你不能高估当前实现的隔离程度

虽然请求级白名单已经有了，但仍然要知道几个现实边界：

### 边界一：远端全局同名工具会冲突

`McpGatewayToolRegistry` 对全局第三方工具只按 `toolName -> clientId` 做映射。

如果两个第三方服务都暴露 `search`，会产生覆盖风险。

### 边界二：宿主权限控制仍然在模型外

AI4J 只是在运行时层做了服务白名单和路由控制，不能替代：

- 账号权限系统
- 网络访问控制
- 审批系统
- 审计系统

### 边界三：能力目录缓存不是实时强一致

`McpClient` 会缓存工具、资源、提示词目录，并依赖 `list_changed` 通知失效。

如果第三方服务目录异常变化，你可能会看到短暂的不一致。

## 6. 第三方服务故障时，排查顺序应该怎样走

建议按下面顺序查：

1. transport 是否真的建立
2. `initialize` 是否成功
3. `McpGateway` 中 client 是否连通
4. `mcpServices` 是否真的包含目标 `serviceId`
5. `toolRegistry` 映射是否被同名工具覆盖
6. 最后再看 provider 层的 tool call

不要一上来就把所有问题都归因到模型选择错误。

## 7. 第三方服务接入的配置治理建议

### 优先用显式 `serviceId`

请求白名单依赖服务 ID；服务 ID 一开始就要稳定。

### 为高风险服务做最小暴露

不要因为“以后可能会用”就默认把一整组高权限服务挂进所有请求。

### 把只读和高副作用能力拆开

如果一个第三方服务同时暴露读写能力，最好在上游服务设计上就拆成不同服务或至少不同工具集合。

## 8. 什么时候该考虑用户级服务而不是全局服务

如果第三方 MCP 服务与用户身份强绑定，例如：

- 每个用户自己的 GitHub token
- 每个用户自己的工作区资源
- 每个用户自己的浏览器会话

那更适合接成用户级服务。

AI4J 当前已经支持：

- `user_{userId}_service_{serviceId}`

形式的 user-scoped client key，以及对应的 user tool key。

这比把所有用户都绑到一个全局第三方服务上，更符合权限边界。

## 9. 这一页的结论

> AI4J 接第三方 MCP 的关键不是“能不能连”，而是“能否被宿主纳入正式治理”。当前正确的接入心智是：先按 transport 把服务接入 gateway，再通过 `mcpServices` 做请求级最小暴露，并明确知道远端全局工具仍有同名冲突和模型外权限控制这两类边界。
