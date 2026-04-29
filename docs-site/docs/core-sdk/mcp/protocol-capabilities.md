# Protocol Capabilities

如果把 MCP 只理解成 `tools/call`，你只看到了最薄的一层。

AI4J 当前实现里的 MCP 协议面至少包括：

- 初始化协商
- Tool capability
- Resource capability
- Prompt capability
- list_changed 通知
- 与 transport 绑定的会话差异

真正的协议入口在：

- `mcp/server/McpServerEngine.java`
- `mcp/client/McpClient.java`

## 1. 服务端真正支持哪些协议方法

`McpServerEngine.processMessage(...)` 当前直接处理：

- `initialize`
- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`
- `ping`

这已经足够说明一件事：

- MCP 在 AI4J 中不是“远程 Tool RPC”
- Tool 只是其中一个 capability 面

## 2. 初始化协商不是礼貌性握手

初始化时，`McpClient.initialize()` 会发送：

- `protocolVersion = 2025-03-26`
- `clientInfo`
- 一组 client capabilities

随后还会发：

- `notifications/initialized`

服务端 `McpServerEngine.handleInitialize(...)` 会：

1. 解析客户端请求的协议版本
2. 在支持版本内做协商
3. 返回 server capabilities
4. 把 `session.setInitialized(true)`

如果 transport 对应的 server 设置了 `initializationRequired=true`，后续在调用：

- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`

前都会先过 `requireInitialization(...)`，否则返回 `-32002 Server not initialized`。

所以初始化不是点缀，而是后续 capability 的门禁。

## 3. 服务端返回的 capability 长什么样

`McpServerEngine.buildCapabilities()` 当前会构建：

- `tools`
  - 可选 `listChanged`
- `resources`
  - `subscribe = true`
  - `listChanged = true`
- `prompts`
  - `listChanged = true`

其中 `tools.listChanged` 是否为 `true`，取决于构造 `McpServerEngine` 时传入的 `toolsListChanged` 标志。

这说明 capability 不是全都固定不变，而会受具体 server transport 形态影响。

## 4. Tool capability 在 AI4J 里怎么落地

Tool capability 的服务端路径是：

- `tools/list`
- `tools/call`

`tools/list` 这条链最终会：

1. `ToolUtil.getLocalMcpTools()`
2. 把本地 `@McpService + @McpTool` 扫描结果转成 `Tool`
3. 再转成 `McpToolDefinition`

`tools/call` 则最终走：

`ToolUtil.invoke(toolName, JSON.toJSONString(arguments))`

这里有个很关键的执行事实：

- 服务端 MCP tool 调用链最终仍然复用 AI4J 的统一工具执行入口
- 所以本地 MCP tool、传统 function tool 与远端 gateway tool 共用了一部分执行分发逻辑

## 5. Resource capability 不是“只读 Tool”

Resource 的协议路径是：

- `resources/list`
- `resources/read`

对应实现依赖：

- `McpResourceAdapter.getAllMcpResources()`
- `McpResourceAdapter.readMcpResource(uri)`

它的核心特征是：

- 使用 URI 模板而不是函数名
- 会匹配 `{param}` 占位符
- 根据 URI 提取参数，再调用资源方法
- 最终返回 `McpResourceContent`

这更适合表达：

- 文档
- 配置
- 只读结构化内容

而不是动作型调用。

## 6. Prompt capability 不是普通字符串常量

Prompt 的协议路径是：

- `prompts/list`
- `prompts/get`

对应实现依赖：

- `McpPromptAdapter.getAllMcpPrompts()`
- `McpPromptAdapter.getMcpPrompt(name, arguments)`

它的核心特征是：

- Prompt 名称使用 `serviceName.promptName`
- 可以声明参数 schema
- 支持 `required`
- 支持 `defaultValue`
- 执行时会把 arguments 注入方法参数

这意味着 Prompt 更适合：

- 模板化交互片段
- 需要参数渲染的系统提示
- 服务端希望标准化复用的提示内容

## 7. list_changed 通知为什么重要

`McpClient` 当前会缓存：

- 工具目录
- 资源目录
- 提示词目录

并在收到这些通知时清缓存：

- `notifications/tools/list_changed`
- `notifications/resources/list_changed`
- `notifications/prompts/list_changed`

这说明 AI4J 并没有把远端 capability 当成完全静态目录，而是明确支持“能力面发生变化后重取”。

如果你要做长生命周期宿主，这一点非常关键。

## 8. transport 会影响 capability 边界

三种 server transport 在 `McpServerEngine` 构造参数上并不相同。

### `StdioMcpServer`

- 只支持 `2024-11-05`
- `initializationRequired = false`
- `pingEnabled = false`
- `toolsListChanged = false`

### `SseMcpServer`

- 只支持 `2024-11-05`
- `initializationRequired = true`
- `pingEnabled = true`
- `toolsListChanged = false`

### `StreamableHttpMcpServer`

- 支持 `2025-03-26` 和 `2024-11-05`
- 默认 `2025-03-26`
- `initializationRequired = true`
- `pingEnabled = false`
- `toolsListChanged = true`

这意味着 capability 不是单纯的业务声明，还和 transport 会话模型绑定。

## 9. 当前实现里哪块最成熟

从实现完整度看：

- Tool capability 最直接进入请求和执行主链
- Resource capability 结构完整，适合只读内容发布
- Prompt capability 也已经成型，尤其适合模板化提示

但如果你要先做一条最稳定的生产链路，通常还是先从 Tool capability 跑通，再扩展 Resource / Prompt。

## 10. 设计 capability 时的建议

### 动作放 Tool

例如：

- 查询
- 写入
- 外部系统操作

### 内容放 Resource

例如：

- 文档
- 配置
- 版本清单
- 固定结构数据

### 模板放 Prompt

例如：

- 参数化提示
- 标准化任务说明
- 交互起始模板

不要把所有东西都塞成 Tool，否则协议语义和后续治理都会变差。

## 11. 这一页的结论

> AI4J 里的 MCP capability 是“初始化协商 + Tool / Resource / Prompt + list_changed 通知 + transport 会话差异”的组合，不是单一的远程 tool 调用接口。Tool 只是模型执行链里最显眼的一层，不是 MCP 的全部。
