# Memory and Tool Boundaries

这一页只讲边界。把 memory、tool 和 MCP 分开，才能知道 Core SDK 到底负责到哪里。

## 1. `ChatMemory` 负责什么

`ChatMemory` 负责的是会话事实内容：

- system / user / assistant 消息
- 多模态用户输入
- assistant 发起过的 tool calls
- tool output 回写结果
- `Chat` / `Responses` 可复用的上下文投影
- 上下文裁剪、摘要、快照和恢复

它的职责本质上是：**保存“模型下一轮需要再次看到的事实”**。

## 2. 为什么 `addToolOutput(...)` 不代表它成了工具系统

`ChatMemory` 里有：

- `addAssistantToolCalls(...)`
- `addToolOutput(toolCallId, output)`

这只说明 memory 能把工具交互事实写回会话历史，让后续模型继续看到这段上下文。

它不意味着 `ChatMemory` 负责：

- 允许不允许调用工具
- 工具是否需要审批
- 工具副作用是否可执行
- 工具失败后怎么补偿
- 多步工具链怎么推进

它记录“发生过什么”，不决定“应该让什么发生”。

## 3. Tool 层真正负责什么

Core SDK 里，本地工具相关的核心边界在：

- `ToolUtil`
- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`
- 请求侧的 `functions`
- 请求侧的 `mcpServices`

这部分负责的是：

- 暴露哪些工具能力
- 参数 schema 长什么样
- 调用方把哪些工具挂给模型
- 本地工具和远端 MCP 工具如何被汇总

这和 memory 是邻接关系，不是包含关系。

## 4. MCP 在这里的角色

MCP 也是能力来源之一，但它的主职责是：

- 建立外部服务连接
- 管理 transport 和 capability
- 暴露可用 tools / resources / prompts

当 MCP 最终把能力暴露给模型时，它会表现得像工具来源；但概念上它仍然属于协议与集成层，而不是 memory 层。

## 5. 一个对架构很有用的分层法

- `ChatMemory`：保存对话事实
- `Tool`：提供本地执行能力
- `MCP`：引入外部服务能力

如果某一层开始同时承担“记忆、授权、执行、审批、恢复”，通常说明边界已经糊掉了。

## 6. 为什么这条边界重要

如果把工具治理责任硬塞进 memory，会马上出现两个问题：

- 会话存储和执行控制耦合，导致 memory 变成隐式运行时
- 业务代码开始依赖 memory 来表达权限、审批或副作用状态，导致抽象扭曲

AI4J 当前把这几层拆开，目的是让：

- 会话历史保持稳定
- 工具选择保持显式
- 外部能力接入保持可替换

## 7. 什么时候应该升级到上层 runtime

如果你真正关心的是：

- 工具审批
- 多步状态推进
- checkpoint / resume
- 工作区级副作用治理
- 多 agent handoff

那就不该继续把责任压在 `ChatMemory` 上，而应该进入：

- `ai4j-agent`
- `ai4j-coding`

## 8. 这一页的结论

> 在 AI4J 里，memory 负责保存上下文事实，tool 负责暴露和执行能力，MCP 负责引入外部服务能力。`ChatMemory` 可以记录工具交互，但它不是工具治理中心；一旦问题开始涉及审批、执行控制或多步运行时语义，就应该上升到更高层 runtime。
