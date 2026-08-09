---
title: Protocol Capabilities
description: 讲清 AI4J MCP 协议面：服务端支持 tools/resources/prompts 三类 capability 与 list_changed 通知，legacy profile 需 initialize 握手而现代 Streamable HTTP 无状态，transport 会影响 capability 边界。
---

# Protocol Capabilities

如果把 MCP 只理解成 `tools/call`，你只看到了最薄的一层。

AI4J 当前实现里的 MCP 协议面至少包括：

- 现代无状态 Streamable HTTP 与 legacy 初始化协商
- Tool capability
- Resource capability
- Prompt capability
- list_changed 通知
- 与 transport/profile 绑定的会话差异

真正的协议入口在：

- `mcp/server/McpServerEngine.java`
- `mcp/client/McpClient.java`

## 1. 服务端真正支持哪些协议方法

`McpServerEngine.processMessage(...)` 当前直接处理：

- `server/discover`
- `tools/list`
- `tools/call`
- `resources/list`
- `resources/read`
- `prompts/list`
- `prompts/get`
- `ping`

对于 legacy transport，它还处理：

- `initialize`
- `notifications/initialized`

这已经足够说明一件事：

- MCP 在 AI4J 中不是“远程 Tool RPC”
- Tool 只是其中一个 capability 面

## 2. 初始化只属于 legacy profile

在 STDIO、SSE 或显式 `LEGACY_2025_03_26` Streamable HTTP 下，`McpClient.initialize()` 会发送：

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

当 Streamable HTTP 被显式设为 `MODERN_2026_07_28`，或默认 `AUTO` 的 `server/discover` 探测成功后，它不走这条链：没有 `initialize`、`notifications/initialized`、`Mcp-Session-Id` 或协议 session。每个 `POST /mcp` 都携带 `_meta`、`MCP-Protocol-Version`、`Mcp-Method`，并在需要时携带 `Mcp-Name` 和 schema 定义的 `Mcp-Param-*`。`isInitialized()` 在这个 profile 中表示 client ready，而不是已经握手。

`AUTO` 只发送现代 `server/discover` 探测；只有未识别的 HTTP `400`、`404` 或 `405` 才会选择 initialization-era Streamable HTTP 并回到本节的初始化链。认证失败、可识别的现代 JSON-RPC error、或不合格的 discovery response 不会触发降级，因此 AUTO 不是通用的 MCP server 探测器。

:::warning
不要把 legacy 的初始化状态、session affinity 或 capability declaration 迁移到现代 endpoint。详见 [Streamable HTTP](/docs/mcp/streamable-http)。
:::

## 3. 服务端返回的 capability 长什么样

`McpServerEngine.buildCapabilities()` 会构建 Tool、Resource 和 Prompt 目录相关的 capability 元数据。具体字段要按 profile 解释：

- `tools`
  - 可选 `listChanged`
- `resources`
  - `listChanged = true`
- `prompts`
  - `listChanged = true`

其中 `tools.listChanged` 是否为 `true`，取决于构造 `McpServerEngine` 时传入的 `toolsListChanged` 标志。

:::warning
旧实现的 capability metadata 中可能带有 `resources.subscribe` 标记；它不能作为现代 subscriptions 已实现的承诺。AI4J 目前不实现现代 subscription 或 MRTR 流程，现代 client 也不会宣称支持它们。不要基于该标记设计互操作流程。
:::

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

- 默认 `AUTO`
- AUTO 在同一个 `/mcp` endpoint 接受现代无状态请求和 initialization-era Streamable HTTP；modern 请求由 headers 或 `_meta` 识别
- 现代请求使用无状态 `POST /mcp`，无初始化握手或协议 session，并验证现代 metadata 与 required HTTP headers
- 现代请求支持 `server/discover` 和 response cache hints
- 不支持 MRTR 或 subscriptions
- `MODERN_2026_07_28` 可显式固定为 modern-only；具体 `LEGACY_*` profile 可固定 legacy-only
- deprecated HTTP+SSE 保持显式 `sse` transport，不是 AUTO fallback

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

> AI4J 里的 MCP capability 是“profile-aware lifecycle + Tool / Resource / Prompt + list metadata”的组合，不是单一的远程 tool 调用接口。Tool 只是模型执行链里最显眼的一层，不是 MCP 的全部。
