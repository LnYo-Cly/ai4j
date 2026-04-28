# Tools 总览

`Tools` 这一章讲的是 AI4J 基座里的本地工具能力面。

## 1. 这章在 Core SDK 里的位置

这里讲的是本地 JVM 内部的工具能力，不是上层 runtime 才有的“特殊工具”。

也就是说，就算你没有引入 `Agent` 或 `Coding Agent`，只要模型需要调用本地 Java 能力，这一章就已经和你相关。

## 2. 这章解决什么问题

它回答的是：

- Java 方法如何暴露给模型
- 工具 schema 如何组织
- 执行语义和返回结果怎么处理
- 白名单和安全边界怎么控

如果你要讲清楚 `Function Call` 在 AI4J 里的位置，这一章就是主入口。

## 3. 一条最重要的链路

可以把本地工具主线先记成这样：

```text
Java method / tool definition
    -> tool schema exposure
        -> model chooses tool
            -> tool execution
                -> result returns to model flow
```

这也是为什么 `Function Call` 不能只被理解成“请求里多写了一个函数名”，而是模型调用第一次进入“可执行能力”的阶段。

## 4. 它和 `MCP` 的边界

这里讲的是本地工具声明和执行，不是协议化外部能力接入。

所以边界应该这样记：

- `Tools`：本地函数工具与执行模型
- `MCP`：外部能力通过协议暴露给模型

两者都可能被上层 `Agent` 或 `Coding Agent` 复用，但归属不同。

## 5. 它和 `Skill` 的边界

还要再和 `Skill` 分开：

- `Tools`：让模型真的去调用某个本地能力
- `Skill`：给模型一份说明、模板或方法论资源

所以这里负责的是“执行能力”，不是“说明资产”。

## 6. 为什么它在 `Core SDK`

因为 `Function Call` 不是上层 runtime 的专属能力。

就算你没有引入 `Agent`，只在基础 `Chat` 或 `Responses` 路径里调用本地函数，它也已经是基座能力。

## 7. 推荐阅读顺序

1. [Function Calling](/docs/core-sdk/tools/function-calling)
2. [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
3. [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
4. [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)

如果你是从 [First Tool Call](/docs/start-here/first-tool-call) 进来的，这一章就是第一条本地工具主线的正式入口。

## 8. 关键对象

如果你要继续下钻源码，优先看下面几个入口：

- `annotation/FunctionCall.java`：把 Java 方法声明成可供模型调用的工具入口
- `annotation/FunctionRequest.java`：请求体 schema 的结构化定义
- `annotation/FunctionParameter.java`：参数级描述与约束入口
- `tool/ToolUtil.java`：工具元数据提取、转换与执行适配的核心工具类

这几个对象连起来，基本就构成了 AI4J 本地工具能力的“声明层 -> 暴露层 -> 执行层”主线。

## 9. 阅读这一章时要抓住什么

- 工具定义不等于 prompt 说明，它首先是一个正式的可执行能力契约
- 注解式工具是主入口，但最终重点是 schema 如何进入请求链、结果如何回流到模型链
- 白名单和安全限制不是附属选项，而是工具真正进入生产场景前必须明确的边界

抓住这三点，再去看单独页面里的代码片段和执行链，会更容易理解为什么 `Function Call` 在 AI4J 里属于基座能力。
