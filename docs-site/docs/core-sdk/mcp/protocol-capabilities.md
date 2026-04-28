# Protocol Capabilities

这一页讲的是：**MCP 协议面到底能承载什么。**  
如果只把 MCP 理解成“远端工具调用”，那你看到的只是它最表层的一小块。

## 1. 三类核心 capability

在 MCP 语义里，最核心的能力通常是：

- `Tools`
- `Resources`
- `Prompts`

AI4J 在服务端侧也按这三类去建模，这不是文档概念，而是源码层真实存在的类型分工。

## 2. 源码入口

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `McpToolAdapter`
- `McpResourceAdapter`
- `McpPromptAdapter`

只看这些类就能知道，AI4J 并没有把 MCP 简化成“只支持 tool”，而是保留了更完整的协议能力面。

## 3. 为什么 `Resource` 和 `Prompt` 不能被省略

如果所有东西都强行做成 tool，会出现两个问题：

- 只读内容被伪装成动作
- 提示模板被伪装成函数调用

更合理的划分是：

- 动作型能力 -> Tool
- 稳定只读内容 -> Resource
- 可复用交互模板 -> Prompt

这会直接影响：

- 模型如何理解能力
- 服务端如何命名
- 客户端如何做治理和可观测性

## 4. 这和“工具执行”是什么关系

在运行时，MCP 的 tool 型能力最终可能会被 provider 看成某种 `tool schema`。但这不意味着 MCP 只剩工具。

更准确的理解是：

- `Tool` 是能力中的动作面
- `Resource` 是内容面
- `Prompt` 是交互模板面

AI4J 把它们分开，意味着后续做服务设计时不会把所有东西都堆成“远端函数调用”。

## 5. 为什么这对读 SDK 的人重要

如果你把 MCP 只看成 tool：

- 会低估它的协议定位
- 会误把资源暴露做成工具接口
- 会看不懂后续服务端和客户端为什么需要不同适配器

而如果你知道 MCP 至少是三类 capability 的组合，就能更清楚地读懂：

- 服务端发布
- 客户端接入
- gateway 管理

## 6. 设计摘要

> AI4J 的 MCP 不是“远端函数调用 SDK”，而是完整协议能力面。它至少明确区分了 Tool、Resource、Prompt 三类 capability，所以在设计服务时不会把所有东西都硬塞成 tool，这也是它和单纯远端 RPC 包装的根本区别。

## 7. 继续阅读

- [MCP / Publish Your MCP Server](/docs/core-sdk/mcp/publish-your-mcp-server)
- [MCP / Positioning and When to Use](/docs/core-sdk/mcp/positioning-and-when-to-use)
