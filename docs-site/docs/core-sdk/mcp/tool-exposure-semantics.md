# Tool Exposure Semantics

这一页只讲一件事：**MCP 的能力在什么条件下会变成模型可见的 tool。**

这件事如果没讲透，MCP 就会看起来像“连上就全开”的黑箱系统，但 AI4J 现在的设计并不是这样。

## 1. 先给一句总规则

AI4J 当前的 MCP 暴露遵循两条硬边界：

1. 传什么服务，暴露什么服务
2. 不显式传入的能力，不应该自动可见

也就是说：

- `McpGateway` 里连着什么 ≠ 模型都能看见什么
- 某个第三方服务存在 ≠ 当前请求就默认开放它

## 2. 运行时到底发生了什么

当请求写了 `mcpServices(...)` 之后，执行链大致是：

1. 当前请求先确定服务白名单
2. `ToolUtil.getAllTools(functions, mcpServices)` 读取这些服务的可见能力
3. `McpGateway` 返回工具定义
4. 工具定义被转换成 provider 能理解的 `Tool.Function` schema
5. provider 才真正看到这些 tool

所以“暴露为 tool”是**运行时适配结果**，不是 MCP 概念本身。

## 3. 为什么这层语义特别关键

因为它直接决定：

- 模型本次能看到哪些外部能力
- 第三方系统是否被过度暴露
- 多租户、多用户情况下是否还能保持边界稳定

换句话说，**这层就是 MCP 的最小安全边界之一**。

## 4. 源码里最关键的几个点

- `ToolUtil#getAllTools(...)`
- `ToolUtil#getGlobalMcpTools(...)`
- `ToolUtil#getUserMcpTools(...)`
- `McpGateway#getAvailableTools(...)`
- `ToolUtil.generateApiFunctionName(...)`

尤其是最后一个很重要，因为 MCP 工具最终会以 provider 允许的函数名规则暴露出去，所以 AI4J 会把 `serviceName + toolName` 做规范化处理。

这不是小细节，而是让多服务场景下 tool 命名稳定、可追踪的前提。

## 5. 全局工具和用户级工具为什么要分开

从 `ToolUtil` 和 gateway 的 key 规则可以看出，AI4J 已经显式区分：

- 全局 MCP 工具
- 用户级 MCP 工具

这说明“是否暴露”不仅是一个服务列表问题，还涉及：

- 暴露给谁
- 同名工具在谁的上下文里生效

如果你不区分这层语义，后面权限隔离会非常难补。

## 6. 本地 Tool 和 MCP Tool 最后的共同点与不同点

### 共同点

最终都会被 provider 看成某种 `tool schema`。

### 不同点

- 本地 Tool 来自 JVM 内部声明
- MCP Tool 来自外部服务能力目录

所以它们**最终表现相似**，但**来源和治理方式完全不同**。

这也是为什么文档里必须把 `Tool` 和 `MCP` 分开讲。

## 7. 工程上最容易做错的地方

### 7.1 以为 gateway 里有的全部都该暴露

这直接违反当前白名单语义。

### 7.2 把副作用强的 MCP 服务和只读服务混在一个 serviceId 下

这会让暴露边界很难控制。

### 7.3 工具命名不稳定

模型 prompt、日志、回归基线都会漂移。

## 8. 设计摘要

> AI4J 的 MCP 暴露不是“已连接即已开放”，而是“请求白名单 -> gateway 能力目录 -> provider tool schema”三段式。MCP 工具最终会长得像 tool，但这个 tool 只是协议接入能力的运行时投影，不是 MCP 本体。

## 9. 继续阅读

- [Tools / Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
- [MCP / Gateway and Multi-service](/docs/core-sdk/mcp/gateway-and-multi-service)
