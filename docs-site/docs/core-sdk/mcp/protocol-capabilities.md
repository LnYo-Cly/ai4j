# Protocol Capabilities

如果把 MCP 只理解成“远程工具调用”，那你只看到了最薄的一层。

在 AI4J 当前实现里，MCP 至少同时包含：

- 初始化握手
- Tool capability
- Resource capability
- Prompt capability
- 变更通知与缓存失效
- 会话与 transport 协议差异

这页的目标就是把这些能力面拆开，而不是继续用一句“支持 MCP”带过。

## 1. 从协议引擎反推 capability 结构

服务端统一入口是 `McpServerEngine.processMessage(...)`。

它当前直接处理：

- `initialize`
- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`
- `ping`

这已经说明一件事：

- AI4J 里的 MCP 不是“只有 Tool”
- Tool 只是 capability 之一

## 2. 初始化能力不是可有可无

`McpClient.connect()` 并不是 transport 连通就结束，而是会继续发送：

1. `initialize`
2. `notifications/initialized`

客户端声明的能力包括：

- `sampling`
- `roots`
- `tools`
- `resources`
- `prompts`

服务端则通过 `McpServerEngine.buildCapabilities()` 返回：

- `tools.listChanged`
- `resources.subscribe`
- `resources.listChanged`
- `prompts.listChanged`

所以初始化不是“打个招呼”，而是双方能力面的协商入口。

## 3. Tool capability 是动作面

Tool 对应的是动作型能力。

服务端侧：

- `tools/list` 返回工具定义
- `tools/call` 执行工具

AI4J 当前实现中，Tool 的服务端投影来自：

- `ToolUtil.getLocalMcpTools()`

这条链会把本地 `@McpService + @McpTool` 能力转换成 MCP `inputSchema`。

所以从协议角度看，Tool 解决的是：

- 模型可调用什么动作
- 这个动作需要什么参数

## 4. Resource capability 是内容面

Resource 不应该被写成“只读工具”。

在 AI4J 里，它有自己独立的协议路径：

- `resources/list`
- `resources/read`

服务端实现通过：

- `McpResourceAdapter.getAllMcpResources()`
- `McpResourceAdapter.readMcpResource(uri)`

完成 URI 模板匹配和参数提取。

这类能力更适合表达：

- 文档
- 配置
- 结构化只读数据

而不是动作型操作。

## 5. Prompt capability 是模板面

Prompt 也不是普通字符串常量。

AI4J 当前的协议入口是：

- `prompts/list`
- `prompts/get`

对应实现：

- `McpPromptAdapter.getAllMcpPrompts()`
- `McpPromptAdapter.getMcpPrompt(name, arguments)`

Prompt 更适合表达：

- 需要参数渲染的模板
- 复用的交互片段
- 服务端提供的标准调用提示

把它和 Tool 分开，最大的好处是不会把“模板获取”误写成“执行动作”。

## 6. capability 和 tool projection 不是一回事

这一点最容易讲错。

对于模型侧消费而言，最终很多能力都会被投影成：

- 一组可见工具 schema

但对协议层来说，Tool / Resource / Prompt 仍然是三类不同 capability。

更准确的理解是：

- Tool 是动作 capability
- Resource 是内容 capability
- Prompt 是模板 capability
- “tool 暴露给模型”只是运行时投影，不是 capability 本体

## 7. listChanged 通知为什么重要

`McpClient` 会缓存：

- `availableTools`
- `availableResources`
- `availablePrompts`

它还会处理：

- `notifications/tools/list_changed`
- `notifications/resources/list_changed`
- `notifications/prompts/list_changed`

收到通知后会清空对应缓存。

这意味着 AI4J 不是把 MCP 当成完全静态目录，而是已经考虑了能力面变更后的缓存失效。

## 8. 不同 server transport 的 capability 边界

### `StdioMcpServer`

- 协议版本固定 `2024-11-05`
- `initializationRequired = false`
- `pingEnabled = false`

### `SseMcpServer`

- 协议版本固定 `2024-11-05`
- `initializationRequired = true`
- `pingEnabled = true`

### `StreamableHttpMcpServer`

- 支持 `2025-03-26` 与 `2024-11-05`
- `initializationRequired = true`
- `toolsListChanged = true`

所以 capability 不只是业务能力，还和 server transport 的会话模型、协议版本直接相关。

## 9. 当前实现里最成熟的是哪一块

从代码完整度看：

- Tool capability 最成熟
- Resource / Prompt capability 结构完整，但启动前注册流程需要你自己管理

这也是为什么很多生产接入应该先从 Tool 跑通，再补其它 capability。

## 10. 什么时候该用哪种 capability

| 需求 | 更适合什么 |
| --- | --- |
| 触发一个动作、查询、调用外部系统 | Tool |
| 暴露稳定只读内容 | Resource |
| 暴露模板化提示或交互片段 | Prompt |

不要把所有东西都写成 Tool。那样短期省事，长期会让协议语义和运行治理都变差。

## 11. 这页最该记住的结论

AI4J 里的 MCP 能力面，不是“一个远程工具列表”，而是：

- 初始化协商
- 三类 capability
- 通知驱动的缓存失效
- 与 transport 绑定的会话语义

只有把 MCP 看成这一整套能力面，后面的 client、gateway、server 和 Agent 集成才不会讲乱。
