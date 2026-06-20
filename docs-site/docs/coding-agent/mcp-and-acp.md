# MCP and ACP

`MCP` 和 `ACP` 在 Coding Agent 里经常一起出现，所以很容易被误写成“同一套接入机制的两个名字”。  
从源码看，它们其实分属两侧完全不同的边界：

- `MCP`：把外部能力接进模型可调用工具面
- `ACP`：把 Coding Session 暴露给 IDE / 桌面端 / 其它宿主

最稳的记法是：

- MCP 面向“模型还能调什么”
- ACP 面向“宿主怎么看、怎么控这次会话”

## 1. 先把两条链拆开

如果用当前实现画一条最短链路，可以写成：

```text
MCP server
  -> CliMcpRuntimeManager
  -> toolRegistry / toolExecutor
  -> CodingAgentBuilder
  -> CodingSession
  -> HeadlessCodingSessionRuntime
  -> AcpJsonRpcServer
  -> ACP host
```

这里每一段都不是同一层语义：

- 前半段在扩展 agent 的工具面
- 后半段在协议化暴露 session

这也是为什么把 MCP 和 ACP 混写会让文档失真。

## 2. MCP 在 Coding Agent 里的真实位置

当前 CLI / Coding Agent 路径里，MCP 运行时的主入口是：

- `CliMcpConfigManager`
- `CliMcpRuntimeManager`
- `CliResolvedMcpConfig`
- `CliResolvedMcpServer`

最关键的类是：

- `ai4j-cli/.../mcp/CliMcpRuntimeManager.java`

它不是静态配置对象，而是一个活的连接层。启动时会：

1. 读取 resolved MCP config
2. 为每个 server 建立 `CliMcpConnectionHandle`
3. connect
4. `listTools()`
5. 校验工具名
6. 把 `McpToolDefinition` 转成 OpenAI 风格 `Tool.Function`
7. 生成 `toolRegistry` 和 `toolExecutor`

所以 MCP 在这条链里的角色不是“附加一段 prompt”，而是：

**把远端 server 的工具正式投影成当前 agent 的结构化工具面。**

## 3. MCP 配置为什么要分全局和工作区两层

当前 MCP 配置解析有明显的两层：

- 全局
- 工作区

设计动机很直接：

- 全局层解决“机器上有哪些 MCP server 定义”
- 工作区层解决“这个仓库当前启用哪些 server”

这也是为什么 Coding Agent 里的 MCP 不应该被理解成“启动参数再多几个 URL”。  
它本质上是一套：

- 服务器定义
- 工作区启用状态
- session 可见状态

联动的运行时配置。

## 4. `CliMcpRuntimeManager` 为什么要维护状态机

当前实现里，MCP server 并不是只有“能用/不能用”两态，而是显式维护：

- `connected`
- `disabled`
- `paused`
- `error`
- `missing`

这非常重要，因为在实际使用里，MCP 的问题来源可能完全不同：

- 工作区显式禁用了
- 当前 session 暂停了
- 配置存在但 server 不存在
- server 存在但连接失败

如果文档只说“MCP 不可用”，宿主和用户都很难定位到底是哪一类问题。

## 5. MCP 工具为什么要严格做名字冲突校验

`CliMcpRuntimeManager` 当前会显式保留这些内置工具名：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

如果某个 MCP server 返回了同名工具，运行时会直接视为冲突。

此外它还会检查：

- 同一 server 内部重复工具名
- 不同 server 之间重复工具名

这说明 AI4J 当前的态度很明确：

**MCP 工具不是随便“挂进来就行”，而是必须先保证当前工具空间可判定。**

否则宿主根本无法知道：

- 这个 `read_file` 到底是本地工具还是远端 MCP 工具

## 6. MCP 工具进入模型前经历了什么转换

MCP server 返回的是：

- `McpToolDefinition`

但模型侧最终看到的是：

- `Tool.Function`

`CliMcpRuntimeManager.convertTools(...)` 会把：

- 名称
- 描述
- input schema

转换成当前 agent/runtime 可消费的 tool schema。

所以 MCP 接入不是“把 JSON 原样透传给模型”，而是先做了一层 tool surface 适配。

这层适配的价值在于：

- 模型侧看到的仍是统一 tool 形态
- 本地 tools 和 MCP tools 可以共存于同一个 registry

## 7. ACP 在 Coding Agent 里的真实位置

如果说 MCP 解决的是“能力从哪来”，那 ACP 解决的就是“会话如何出得去”。

当前 ACP 的主入口是：

- `AcpJsonRpcServer`
- `AcpCodingCliAgentFactory`
- `AcpToolApprovalDecorator`
- `HeadlessCodingSessionRuntime`

其中最核心的是：

- `ai4j-cli/.../acp/AcpJsonRpcServer.java`

它不是工具运行时，而是 session 协议网关。

## 8. ACP 当前实际暴露了哪些方法

从 `AcpJsonRpcServer` 里的常量可以直接看到，当前 ACP 至少显式处理：

- `initialize`
- `session/new`
- `session/load`
- `session/list`
- `session/prompt`
- `session/set_mode`
- `session/set_config_option`
- `session/cancel`
- `session/request_permission`

这组方法本身已经说明 ACP 的定位：

- 它不是单次“发 prompt 拿 answer”的薄 RPC
- 它是在协议层暴露一个可持续的 coding session

## 9. `session/prompt` 为什么要走 `HeadlessCodingSessionRuntime`

ACP 下，一次 prompt 并不是直接塞给 `CodingSession.run()` 就返回字符串。  
`AcpJsonRpcServer` 实际会把它交给：

- `HeadlessCodingSessionRuntime`

后者会把一次 prompt 运行拆成结构化事件：

- `USER_MESSAGE`
- `ASSISTANT_MESSAGE`
- `TOOL_CALL`
- `TOOL_RESULT`
- `AUTO_CONTINUE`
- `AUTO_STOP`
- `BLOCKED`
- `COMPACT`
- `ERROR`

这说明 ACP 的重点不是“把终端内容远程打印出来”，而是把 session 内部过程变成宿主能理解的事件流。

## 10. ACP 审批为什么是协议往返，不是本地终端交互

CLI/TUI 下，审批由：

- `CliToolApprovalDecorator`

通过终端完成。

ACP 下则不同。  
`AcpToolApprovalDecorator` 会通过：

- `PermissionGateway`

把审批请求交回 `AcpJsonRpcServer`，再由它发出：

- `session/request_permission`

因此 ACP 的审批本质上是：

- runtime 发权限请求
- 宿主返回 allow / reject 决策
- 再决定工具是否继续执行

这说明 ACP 不是“远程 CLI 镜像”，而是带权限往返的宿主协议。

## 11. ACP 和 MCP 为什么会在同一会话里同时生效

这是 Coding Agent 非常容易讲错的地方。

在同一个 session 里，可能同时发生两件事：

- MCP runtime 往 agent 注入远端工具
- ACP runtime 把 session 状态暴露给宿主

所以一个 ACP 宿主看到的工具调用，完全可能是：

- 本地 `bash`
- 本地 `apply_patch`
- 远端 MCP tool

而宿主不需要关心工具来源差异的底层实现，只需要消费统一事件流即可。

这也是分层的价值：

- MCP 管能力接入
- ACP 管会话协议

## 12. 当前实现最真实的边界

### MCP 边界

MCP 在当前 CLI/Coding Agent 里解决的是：

- server connect
- tools discover
- conflict validation
- toolRegistry/toolExecutor 注入

它不直接解决：

- 宿主 UI 如何渲染
- session 怎样恢复
- 权限弹窗怎样展示

### ACP 边界

ACP 在当前实现里解决的是：

- session 生命周期协议
- prompt 执行与取消
- permission request
- structured event delivery

它不直接解决：

- 外部工具从哪来
- 工具 schema 怎样定义
- MCP server 怎样连

## 13. 最容易踩坑的 5 个点

### 13.1 把 MCP 写成 Coding Agent 的一个“本地工具”

它不是单个工具，而是外部工具运行面的接入层。

### 13.2 把 ACP 写成“另一套 CLI”

它是会话协议层，不是终端壳层。

### 13.3 忽略 MCP 工具名冲突

当前运行时会严格拦 built-in 和跨 server 冲突，这不是小细节。

### 13.4 把 `session/request_permission` 理解成 UI 提示文本

它本质上是权限协议往返的一部分。

### 13.5 以为 ACP 宿主看到的只有文本输出

ACP 当前看到的是结构化 session 事件，而不只是最终 assistant 文本。

## 14. 这页最该记住的结论

AI4J 当前的 Coding Agent 里：

- MCP 负责把外部 server 工具接成当前 agent 可调用的 tool surface
- ACP 负责把当前 coding session 协议化暴露给宿主

两者会在一条运行链上同时出现，但处理的是完全不同的边界。  
把这两层分清，才能真正理解“工具从哪里来”和“会话怎样出去”分别落在哪一层。

## 15. 推荐阅读

1. [Runtime 架构](/docs/coding-agent/runtime-architecture)
2. [会话、流式与进程](/docs/coding-agent/session-runtime)
3. [Tools 与审批机制](/docs/coding-agent/tools-and-approvals)
4. [Core SDK / MCP](/docs/core-sdk/mcp/overview)
