# Tool Exposure Semantics

这一页只讲一件事：**MCP 的能力在 AI4J 里究竟在什么条件下会变成模型可见的 tool。**

如果这层语义没讲清楚，MCP 很容易被误解成：

- 只要接上就自动全开
- gateway 里有什么，模型就能看见什么
- 所有 capability 最终都等价于 tool

这些理解在 AI4J 当前实现里都不准确。

## 1. 先给出总规则

AI4J 当前的 MCP tool 暴露遵循三段式：

1. 服务先被 gateway 接入
2. 请求再用 `mcpServices` 白名单选择服务
3. 选中的服务工具被投影成 provider tool schema

所以可见性不是由“服务存在”决定，而是由“本次请求显式选择了哪些服务”决定。

## 2. 进入模型前，工具是怎么被解析的

provider 发送前会调用：

`ToolUtil.getAllTools(functions, mcpServices)`

这一步会：

1. 收集本地 function tools
2. 判断 `mcpServices` 是否为空
3. 如果有用户前缀 serviceId，则走 `getUserMcpTools(...)`
4. 否则走 `getGlobalMcpTools(...)`
5. 把返回的 `Tool.Function` 再包装成 provider `Tool`

所以 MCP tool 在 provider 看来只是结果，不是原始来源。

## 3. “可见服务”与“可用服务”是两回事

`McpGateway` 里可能已经初始化了很多服务，但如果本次请求没有写：

- `chatCompletion.mcpServices("github")`
- 或 `responseRequest.mcpServices("github")`

那这些服务不会自动进入本次 provider 请求。

这意味着：

- gateway 解决“有什么”
- 请求白名单解决“这次给模型看什么”

两者职责完全不同。

## 4. 全局与用户级暴露的实际分流

`ToolUtil.getAllTools(...)` 会先检查传入的 serviceId 是否长得像：

`user_{userId}_service_{serviceId}`

如果是，就会：

1. 提取 `userId`
2. 提取真实 `serviceId`
3. 调用 `gateway.getUserAvailableTools(serviceIds, userId)`

这一步返回的是：

- 用户专属服务工具
- 符合过滤条件的全局工具

所以用户请求上下文不是简单替换，而是“用户私有能力 + 全局能力”的组合暴露。

## 5. 远端 tool 名称现在如何进入 provider

远端工具最终的名字来自：

- `McpToolDefinition.getName()`
- `McpToolConversionSupport.convertToOpenAiTool(...)`

AI4J 当前不会在 gateway 层自动给远端 tool 拼上服务名前缀。也就是说：

- provider 看到的远端 tool 名字，就是服务端上报的那个名字

这对使用者有两个直接影响：

1. prompt 和日志里看到的名字会和服务端原名一致
2. 多个服务如果暴露同名工具，映射可能发生覆盖

## 6. 当前实现中的同名工具风险

`McpGatewayToolRegistry` 对全局工具使用：

- `toolName -> clientId`

作为映射键。

这意味着：

- 同名全局工具不会共存
- 后写入映射的 client 会覆盖先前映射
- 调用 `gateway.callTool(toolName, ...)` 时只会命中一个 client

相比之下，用户级工具会使用：

- `user_{userId}_tool_{toolName}`

作为路由键，所以隔离更完整。

这也是为什么在多服务设计时，不应该假设 AI4J 已经替你做好了所有远端命名治理。

## 7. 本地 Tool 与 MCP Tool 的共同点和不同点

### 共同点

进入 provider 前，都会被表示成 `Tool` / `Tool.Function`。

### 不同点

本地 Tool：

- 来自 JVM 内部扫描
- 命名由本地类和注解决定
- 执行通常在进程内完成

MCP Tool：

- 来自远端服务的 `tools/list`
- 先经过 gateway 与请求白名单
- 执行时需要再路由回具体 client

所以二者“长得一样”只是为了兼容 provider 接口，不代表治理方式一样。

## 8. capability 与 tool exposure 不是同义词

这一点尤其容易写错。

MCP 协议层有：

- Tool capability
- Resource capability
- Prompt capability

但当前直接进入 provider tool 暴露链的主要是 Tool capability。

`resources/*` 和 `prompts/*` 仍然存在于协议层，只是没有被 AI4J 默认当作 provider 原生 tool 自动展开。

所以“模型看见的 tools” 只是 MCP 能力面的一个投影，不是 MCP 全部能力的总和。

## 9. 设计暴露面时的建议

### 只把当前任务真正需要的服务放进 `mcpServices`

不要为了省事默认把所有服务都挂进去。

### 远端服务尽量避免全局同名工具

当前实现没有自动为远端全局工具做服务前缀隔离。

### 高副作用服务和只读服务尽量拆开

这样白名单更精确，也更容易做审计和审批。

## 10. 这一页的结论

> 在 AI4J 里，MCP tool 暴露不是“已连接即已开放”，而是“gateway 已接入 + 请求显式白名单 + 运行时 tool 投影”的结果。远端全局工具当前按原始 `toolName` 暴露，没有自动服务前缀隔离；因此多服务场景下的命名治理需要使用者自己设计清楚。
