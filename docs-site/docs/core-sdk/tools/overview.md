# Tools 总览

`Tools` 这一章讲的是 AI4J 基座里的本地工具能力面。

## 1. 这章解决什么问题

它回答的是：

- Java 方法如何暴露给模型
- 工具 schema 如何组织
- 执行语义和返回结果怎么处理
- 白名单和安全边界怎么控

如果你要讲清楚 `Function Call` 在 AI4J 里的位置，这一章就是主入口。

## 2. 它和 `MCP` 的边界

这里讲的是本地工具声明和执行，不是协议化外部能力接入。

所以边界应该这样记：

- `Tools`：本地函数工具与执行模型
- `MCP`：外部能力通过协议暴露给模型

两者都可能被上层 `Agent` 或 `Coding Agent` 复用，但归属不同。

## 3. 为什么它在 `Core SDK`

因为 `Function Call` 不是上层 runtime 的专属能力。

就算你没有引入 `Agent`，只在基础 `Chat` 或 `Responses` 路径里调用本地函数，它也已经是基座能力。

## 4. 推荐阅读顺序

1. [Function Calling](/docs/core-sdk/tools/function-calling)
2. [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
3. [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
4. [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
