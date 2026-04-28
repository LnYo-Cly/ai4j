# Publish Your MCP Server

这一页讲的是：**如何把自己的 Java 能力正式发布成 MCP 服务**。重点不是“写个能跑的 demo”，而是把服务能力面、命名空间、transport 和暴露边界设计清楚。

## 1. 核心源码入口

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `mcp/util/McpToolAdapter.java`
- `mcp/util/McpResourceAdapter.java`
- `mcp/util/McpPromptAdapter.java`

这说明 AI4J 在服务端的视角里，MCP 不是“只有 tool”，而是完整协议能力面。

## 2. 为什么“发布”不等于“默认开放”

把 Java 能力发布成 MCP server，只代表：

- 这个能力可以被标准协议消费

并不代表：

- 所有请求都应该默认看到它
- 所有客户端都应该默认使用它

发布是“可接入”，暴露是“当前请求开放”。这两个动作必须分开。

## 3. 发布前必须想清楚的三件事

### 3.1 你暴露的是 Tool、Resource 还是 Prompt

这决定了能力的协议角色，不应该一股脑都写成 tool。

### 3.2 你用什么 transport

- `stdio`
- `sse`
- `streamable_http`

transport 决定的是部署与连接方式。

### 3.3 你打算给谁消费

- 只给本地宿主
- 给内部 agent 平台
- 给第三方客户端

这会直接影响你后面的命名、认证和权限设计。

## 4. AI4J 为什么适合做 MCP server 发布基座

因为它已经把：

- 注解声明
- capability 适配
- transport 抽象
- client / gateway 侧接入

全都放到了同一套基座里。你不是在“额外拼一个协议”，而是在已有架构内把服务端补齐。

## 5. 设计摘要

> 在 AI4J 里，把 Java 能力发布成 MCP server 不是简单写几个注解，而是明确 capability 类型、transport 形态和暴露边界。发布侧和消费侧是分层的：服务端负责可接入，客户端请求再决定本次是否开放。

## 6. 关键对象

如果你要继续落到代码层，优先对照下面这些入口：

- `@McpService`
- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- `mcp/util/McpToolAdapter.java`
- `mcp/util/McpResourceAdapter.java`
- `mcp/util/McpPromptAdapter.java`

这组对象已经覆盖了服务声明和 capability 适配的主线。

## 7. 发布时的命名与边界建议

一个稳定的 MCP server 发布面，通常应提前约束：

- service 名称和 capability 名称是否可长期兼容
- 哪些能力应该是 Tool，哪些更适合 Resource 或 Prompt
- transport 是否与目标部署环境匹配
- 服务端暴露面是否和客户端白名单策略相互配套

如果这些问题没有先想清楚，后面很容易出现“协议能跑，但能力面难以治理”的情况。

## 8. 继续阅读

- [MCP / Protocol Capabilities](/docs/core-sdk/mcp/protocol-capabilities)
- [MCP / Tool Exposure Semantics](/docs/core-sdk/mcp/tool-exposure-semantics)
