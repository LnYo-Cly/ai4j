# Tool Execution Model

这页讲的不是“工具怎么声明”，而是**模型看到工具以后，这条链到底怎么执行**。如果你只看工具定义，不看执行模型，就会误以为 Core SDK 已经等于完整 Agent runtime。

## 1. 先把执行链拆开

在 AI4J 里，一次工具相关请求至少包含四段：

1. 声明阶段：工具长什么样
2. 暴露阶段：本次请求开放哪些工具
3. 返回阶段：模型如何表达 tool call
4. 执行阶段：谁真正执行、谁接收结果、谁决定下一步

Core SDK 主要负责前 3 段的一致性；第 4 段开始才和具体 runtime 强绑定。

## 2. 代码锚点

这条执行链最关键的类和字段有：

- 工具集合解析：`tool/ToolUtil.java#getAllTools(...)`
- `Chat` 请求对象：`platform/openai/chat/entity/ChatCompletion.java`
- `Responses` 请求对象：`platform/openai/response/entity/ResponseRequest.java`
- `Chat` 流式监听：`listener/SseListener.java`
- `Responses` 流式监听：`listener/ResponseSseListener.java`

另外两个特别关键的控制位是：

- `parallelToolCalls`
- `passThroughToolCalls`

它们决定的不是“工具存不存在”，而是**工具调用如何被当前 runtime 处理**。

## 3. `Chat` 链里的执行模型

`ChatCompletion` 里已经能看到完整控制面：

- `functions`
- `mcpServices`
- `toolChoice`
- `parallelToolCalls`
- `passThroughToolCalls`

发送前，各 provider chat service 会做同一件事：

```java
ToolUtil.getAllTools(chatCompletion.getFunctions(), chatCompletion.getMcpServices())
```

这一步完成后，provider 请求里才真正出现 `tools`。

模型返回阶段，`SseListener` 会帮你累计：

- 文本增量
- reasoning 片段
- tool call 片段
- `finishReason`

也就是说，`Chat` 的工具执行模型并不是“只看最后一句字符串”，而是已经有了 tool call 事件消费能力。

## 4. `Responses` 链里的执行模型

`Responses` 的执行模型更偏结构化事件：

- 发送前：`ResponseRequestToolResolver.resolve(request)`
- 返回后：`ResponseSseListener` 消费 `response.output_text.*`、`response.function_call_arguments.*` 等事件

它的重点不是“自动执行工具”，而是把工具调用过程拆成了更可消费的事件流。

这对于做 runtime 很重要，因为你往往不是只想知道“模型最后说了什么”，而是想知道：

- 它准备调用什么
- 参数是怎么增量生成出来的
- reasoning 和最终输出之间怎么衔接

## 5. `parallelToolCalls` 到底表示什么

这个字段的语义是：

- provider 是否允许模型在一个请求里并发提出多个工具调用

它解决的是**provider 级工具调用形态**，不是业务级副作用治理。

所以就算 provider 支持并行 tool calls，也不代表：

- 你的写操作工具应该并行执行
- 你的审批逻辑可以跳过

对于有副作用的工具，上层 runtime 仍然应该保守。

## 6. `passThroughToolCalls` 为什么重要

这是 `Chat` 链里一个很容易被忽视但很关键的字段。

它的作用是：

- 让 provider 返回的 tool call 不被立即“消化”
- 而是保留给上层 runtime 自己执行

这对 Agent / Coding Agent 很关键，因为上层往往需要：

- 审批
- 记录 trace
- 插入中间状态
- 决定工具结果如何回填

所以 `passThroughToolCalls` 本质上是 **把工具执行权交还给 runtime**。

## 7. Core SDK 到底负责到哪里

Core SDK 负责的是：

- 工具 schema 生成
- provider 请求挂载
- tool call / args 读取
- 流式事件聚合

它不负责：

- 工具审批
- 工具失败后的策略路由
- 多轮任务推进
- checkpoint / compact / resume

这条边界要讲得非常清楚。否则你会把“支持 tool call”误听成“已经有完整的 Agent 系统”。

## 8. 本地 Tool、MCP Tool、上层 Tool Runtime 的关系

从执行模型看，有三层：

- 本地 Tool：通过 `@FunctionCall` 和 `ToolUtil` 暴露
- MCP Tool：通过 `McpGateway` 提供，再被转成 `Tool.Function`
- 上层 Tool Runtime：决定工具是否真的执行、如何记录、如何回填

也就是说，Core SDK 只负责把“可调用能力”统一成模型能看懂的形式；真正的执行治理并不在这里闭环。

## 9. 最容易误解的三件事

### 9.1 “能发 tools” 不等于 “工具已经自动执行完”

很多时候 provider 只是返回了 tool call，执行仍然属于 runtime。

### 9.2 `parallelToolCalls` 不等于业务层允许并发副作用

provider 能并发返回，不代表写文件、发请求、改资源就该并发执行。

### 9.3 `Chat` 和 `Responses` 都支持工具，不代表两者执行心智相同

`Chat` 更偏消息式 tool loop；`Responses` 更偏事件式 runtime。

## 10. 设计摘要

AI4J 的工具执行模型分成“声明、暴露、返回、执行”四段。Core SDK 统一了前 3 段，让本地 Tool 和 MCP Tool 都能稳定挂到 `Chat` / `Responses` 请求里；真正执行、审批和状态推进则交给更上层 runtime。

## 11. 继续阅读

- [Model Access / Chat](/docs/core-sdk/model-access/chat)
- [Model Access / Responses](/docs/core-sdk/model-access/responses)
- [Tools / Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
