# Memory and Tool Boundaries

这一页的重点不是 API，而是边界。把 memory、tool、MCP 三者分清，才能知道基座层到底负责到哪里。

## 1. `ChatMemory` 的边界

`ChatMemory` 负责的是会话上下文内容：

- system / user / assistant 消息
- tool calls 的对话记录
- tool output 的结果回填
- `Chat` / `Responses` 可复用的上下文投影

它是“会话事实容器”，不是“工具治理中心”。

## 2. 为什么 `addToolOutput(...)` 不代表它成了工具系统

`ChatMemory` 里存在 `addToolOutput(toolCallId, output)`，只是说明它能把工具结果写回对话历史，让下一轮模型继续看到上下文。

这不意味着 `ChatMemory` 负责：

- 是否允许调用工具
- 工具是否需要审批
- 工具副作用是否可执行

它记录的是结果，不是授权。

## 3. Tool 边界在哪

Core SDK 的本地工具边界主要在：

- `ToolUtil`
- `@FunctionCall` / `@FunctionRequest` / `@FunctionParameter`
- 请求侧的 `functions` / `mcpServices`

这部分负责的是：

- 暴露哪些能力
- 参数 schema 长什么样
- provider 请求怎么挂工具

它和 memory 是邻接关系，不是包含关系。

## 4. MCP 在这里扮演什么角色

MCP 也是能力来源之一，但它的主职责是：

- 连接外部服务
- 管理 transport
- 聚合可见能力

当 MCP 能力最终暴露给模型时，会表现得像 tool；但概念上它仍然属于协议/集成层，而不是 memory 层。

## 5. 一个对架构很有用的分层法

- `ChatMemory`：保存对话事实
- `Tool`：提供本地执行能力
- `MCP`：引入外部服务能力

如果你发现某层开始同时承担“记忆、授权、执行、审批”，通常就是边界已经糊掉了。

## 6. 什么时候该升级到上层 runtime

如果你需要：

- 工具审批
- 多步状态推进
- checkpoint / resume
- 工作区级副作用治理

那就不该继续把责任压在 `ChatMemory` 上，而应该进入 `ai4j-agent` 或 `ai4j-coding`。

## 7. 继续阅读

- [Tools / Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
- [Skills / Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)
