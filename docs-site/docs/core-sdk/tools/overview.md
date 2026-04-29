# Tools 总览

这一章讲的不是“模型可以调函数”这么一句话，而是 AI4J 如何把可执行能力组织成一个正式子系统。

从源码看，`Tools` 至少包含 4 层：

- 工具声明
- 请求级白名单暴露
- provider 侧 schema 投影
- 本地执行与结果回流

如果只讲第一层注解，就会把真正的执行链讲薄。

## 1. `Tools` 在基座里的真实定位

`Tools` 属于 `Core SDK`，不是 `Agent` 或 `Coding Agent` 才有的附属能力。

原因很直接：

- `ChatCompletion` 请求可以挂工具
- `ResponseRequest` 请求也可以挂工具
- 工具 schema 组装和本地执行桥都在 `ai4j/` 模块里

也就是说，即使你完全不用上层 runtime，只要想让模型调用 JVM 内能力，这一章就已经和你相关。

## 2. 这套子系统到底解决什么问题

它回答的是 4 个问题：

1. 某个能力如何被声明成模型可调用对象
2. 本次请求到底暴露哪些工具
3. 这些工具如何被统一转成 provider 可识别 schema
4. 模型返回 tool call 之后，调用如何落回本地执行链

所以 `Tools` 不是“注解语法”，而是从声明到执行的完整桥。

## 3. 当前 AI4J 里有哪些工具来源

从 `ToolUtil` 的实现看，最后进入模型视野的“工具面”并不只有一类。

### 3.1 内建 coding tools

由 `BuiltInTools` / `BuiltInToolExecutor` 提供：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

这些工具已经有固定 schema 和固定执行器，不经过普通业务函数反射调用。

### 3.2 注解式 Function 工具

由：

- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`

声明，再由 `ToolUtil` 扫描、缓存和调用。

### 3.3 本地 MCP 工具投影

本地 `@McpService` / `@McpTool` 也可以被 `ToolUtil.scanMcpTools()` 扫描，并转换成普通 `Tool.Function` 视图。

注意这里讲的是“最终投影成 tool schema”，不是说 MCP 概念上属于 `Tools` 子章节。MCP 的生命周期、transport、网关治理仍然属于独立协议层。

### 3.4 Gateway 管理的远程 MCP 工具

当 `McpGateway` 已初始化时，`ToolUtil` 还能把 gateway 里的远程服务工具并入当前请求的工具集。

这就是为什么模型看到的“一个工具列表”，在实现层其实可能来自多条不同的能力来源。

## 4. 一条最重要的主线

如果只记一条链，可以先记这个：

```text
tool declaration
  -> request-scoped whitelist
    -> provider tool schema
      -> model emits tool call
        -> ToolUtil routes invocation
          -> result returns as string payload
```

这条链里最重要的分界点有两个：

- provider 之前：是工具暴露问题
- provider 之后：是工具执行问题

很多文档把这两件事写成一件事，读起来就会很混。

## 5. `ToolUtil` 是整个子系统的中心

如果你只看一个文件，先看：

- `ai4j/src/main/java/io/github/lnyocly/ai4j/tool/ToolUtil.java`

它至少负责 5 件事：

1. 扫描注解工具
2. 扫描本地 MCP 工具
3. 根据 `functions` / `mcpServices` 生成本次工具白名单
4. 统一执行入口 `invoke(...)`
5. 在 built-in、Function、本地 MCP、远程 MCP 之间做调用路由

因此 `ToolUtil` 不是一个“小工具类”，而是当前整个工具子系统的调度中心。

## 6. `Tools` 和 `Function Calling` 的关系

`Function Calling` 是这章的主线，但不是全部。

更准确地说：

- `Function Calling` 讲的是模型与可执行能力之间的桥
- `Tools` 讲的是 AI4J 如何把不同来源的能力统一组织到这条桥上

所以 `Function Calling` 是这章的核心机制，`Tools` 是更大的能力面。

## 7. `Tools` 和 `Skill` 的边界

这点必须保持非常清楚。

- `Tool` 负责执行
- `Skill` 负责说明

模型读了一个 `Skill`，不代表它获得了执行权限；模型看到了一个 `Tool`，也不代表它理解了最佳工作方法。

这两者经常配合使用，但职责不同。

## 8. `Tools` 和 `MCP` 的边界

这点也不能写混。

- `Tools` 关注最终模型可调用的执行面
- `MCP` 关注外部能力如何通过协议接入宿主

到了请求发送前，MCP 工具确实会被转换成 `Tool.Function` 风格 schema；但那只是投影结果，不是归属关系。

## 9. 当前子系统的真实限制

从源码看，有几个限制应该提前说清楚。

### 9.1 反射扫描是 classpath 级的

`ToolUtil` 基于 `Reflections` 扫描 `@FunctionCall` 和 `@McpService`。这适合中小规模工具集，但不是无限扩展的注册中心。

### 9.2 本地工具返回值最终走字符串

无论是 built-in、Function 还是 gateway 调用，`ToolUtil.invoke(...)` 最终返回的都是 `String`。上层 runtime 看到的是文本化结果，而不是强类型 Java 对象。

### 9.3 执行治理不在这一层闭环

Core SDK 负责：

- 工具组织
- 工具暴露
- 工具桥接

它不直接负责：

- 人机审批
- 多步重试策略
- 长任务 checkpoint
- 宿主级权限策略

这些必须由更上层 runtime 接管。

## 10. 这章最适合哪些读者

### 只想暴露本地 JVM 函数

重点看：

- [Function Calling](/docs/core-sdk/tools/function-calling)
- [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)

### 想理解模型返回 tool call 后怎么执行

重点看：

- [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)

### 关心暴露边界和宿主安全

重点看：

- [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)

## 11. 推荐阅读顺序

1. [Function Calling](/docs/core-sdk/tools/function-calling)
2. [Annotation-based Tools](/docs/core-sdk/tools/annotation-based-tools)
3. [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
4. [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
5. [Skill vs Tool vs MCP](/docs/core-sdk/skills/skill-vs-tool-vs-mcp)

## 12. 这页最该记住的结论

AI4J 的 `Tools` 不是几个注解，而是一个把多种能力来源统一成模型可调用执行面的基座子系统。

它真正解决的是：

- 什么能力可被声明
- 本次请求暴露什么
- 调用怎么回到宿主

而不是上层 runtime 的所有治理问题。
