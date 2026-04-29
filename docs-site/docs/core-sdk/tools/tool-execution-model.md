# Tool Execution Model

这一页讲的不是“工具怎么声明”，而是工具一旦进入请求链后，AI4J 实际怎样完成：

- 工具集合生成
- 调用路由
- 本地执行
- 结果回流

如果只看注解，不看执行模型，就会误以为 Core SDK 已经等于完整 Agent runtime。源码并不是这样分层的。

## 1. 先把执行链拆成 4 段

一次工具相关请求，至少会经过下面 4 段：

1. 发现与注册
2. 请求级白名单暴露
3. provider 返回 tool call
4. 本地调用路由与执行

前两段主要是 `ToolUtil` 负责，第三段进入 provider / listener，第四段才真正落回宿主执行。

## 2. `ToolUtil` 是真正的调度中心

最关键的入口仍然是：

- `tool/ToolUtil.java`

它同时承担：

- 初始化扫描
- schema 聚合
- built-in tool 拦截
- 本地 Function 调用
- 本地 MCP 调用
- `McpGateway` 远程调用

所以这不是一个“辅助类”，而是当前工具执行模型的中心路由器。

## 3. 初始化阶段到底扫描了什么

`ToolUtil.ensureInitialized()` 只会做一次初始化，内部调用：

```java
scanAndRegisterAllTools();
```

而这个方法当前只扫描两类：

1. `scanFunctionTools()`
2. `scanMcpTools()`

也就是说，初始化时会缓存：

- `@FunctionCall` 声明的本地 Function 工具
- `@McpService/@McpTool` 声明的本地 MCP 工具

内建的 `bash/read_file/write_file/apply_patch` 不需要靠扫描注册才能执行，因为它们在 `BuiltInToolExecutor` 里有固定实现。

## 4. 请求级暴露是怎么组装的

### 本地 Function 白名单

调用：

```java
ToolUtil.getAllFunctionTools(functionList)
```

会只返回你显式传入的 `functionList` 中对应工具。

### 远程 MCP 白名单

调用：

```java
ToolUtil.getGlobalMcpTools(mcpServerIds)
ToolUtil.getUserMcpTools(mcpServerIds, userId)
```

会从 `McpGateway` 中提取指定服务的工具。

### 总聚合入口

最终请求常走：

```java
ToolUtil.getAllTools(functionList, mcpServerIds)
ToolUtil.getAllTools(functionList, mcpServerIds, userId)
```

这两个入口只会合并：

- 显式传入的本地 Function 工具
- 显式传入的 MCP 服务工具

不会因为类在 classpath 上，就把所有工具自动暴露给模型。

## 5. 一次调用回到宿主时，真实优先级是什么

`ToolUtil.invoke(functionName, argument)` 的优先级，当前大致是：

1. built-in tool
2. 用户级远程 MCP 工具
3. 本地 MCP 工具
4. 注解式 Function 工具
5. 全局 `McpGateway` 远程工具

这条顺序是非常重要的，因为“同名工具”最终会按这套优先级落到不同执行器。

最容易被忽略的一点是：

- `read_file`、`bash` 这种名字即使也有 `@FunctionCall` 类
- 执行时仍然会先被 `BuiltInToolExecutor` 拦截

因此暴露层和执行层不是简单一一对应的。

## 6. 内建工具为什么是特殊路径

`ToolUtil.invoke(...)` 一开始就会先尝试：

```java
BuiltInToolExecutor.invoke(functionName, argument, builtInToolContext)
```

如果返回非空，后续本地 Function / MCP 路由就不会再走。

当前内建工具包括：

- `bash`
- `read_file`
- `write_file`
- `apply_patch`

这些工具的特点是：

- schema 由 `BuiltInTools` 直接定义
- 执行由 `BuiltInToolExecutor` 直接完成
- 安全边界受 `BuiltInToolContext` 约束

这也是为什么它们更像“宿主级基础能力”，而不是普通业务函数。

## 7. 注解式 Function 工具是怎么执行的

当 built-in 和本地 MCP 都没命中时，`ToolUtil` 才会走：

```java
invokeFunctionTool(functionName, argument)
```

执行链是：

1. 找到缓存里的 `functionClass`
2. 找到对应 `requestClass`
3. `JSON.parseObject(argument, requestClass)`
4. 反射调用 `apply(requestObject)`
5. 结果再 `JSON.toJSONString(result)`

这里要注意两个实现细节：

- 输入参数是按 request class 反序列化的
- 输出最终统一被包装成字符串 JSON

所以上层 runtime 看到的仍然是文本化结果，而不是强类型返回值。

## 8. 本地 MCP 工具执行链和 Function 工具有什么不同

本地 MCP 工具会走：

```java
invokeMcpTool(functionName, argument)
```

与 Function 工具不同的地方在于：

- 参数不是先映射到一个 request class
- 而是先 parse 成 `Map<String, Object>`
- 再按方法参数逐个做类型转换

另外，本地 MCP 工具名不是原始方法名，而是由：

```java
generateApiFunctionName(serviceName, toolName)
```

生成的 API 友好名字。它会：

- 只保留字母、数字、下划线、连字符
- 最长 64 字符
- 必要时加 `tool_` 前缀

这意味着本地 MCP 工具的最终暴露名和 Java 方法名不一定完全一致。

## 9. 远程 MCP 工具执行链是什么

当本地路径都没命中时，才会落到 gateway：

```java
gateway.callTool(functionName, argumentObject).join()
gateway.callUserTool(userId, toolName, argumentObject).join()
```

所以远程 MCP 工具在工具执行模型中的角色是：

- 先被 gateway 投影成 `Tool.Function`
- 再在执行期由 gateway 根据 `tool -> client` 映射路由到真正 `McpClient`

这也解释了为什么 MCP 工具“看起来像 tool”，但运行治理仍然属于协议层。

## 10. `BuiltInToolContext` 是如何进入执行链的

`ToolUtil` 内部维护了一个线程本地栈：

- `pushBuiltInToolContext(...)`
- `popBuiltInToolContext()`
- `currentBuiltInToolContext()`

这意味着 built-in 工具不是全局共享一套宿主配置，而是可以在当前执行上下文里临时注入：

- workspace root
- 允许读取的额外目录
- 读文件和命令执行限制

这和 skill 懒加载、coding-agent 宿主约束是连着设计的。

## 11. 结果回流的真实形态

从 Core SDK 这一层看，工具执行结果最终统一表现为：

- `String`

这条规则对：

- built-in tool
- Function tool
- 本地 MCP tool
- 远程 MCP tool

都成立。

这样做的好处是统一了上层 runtime 的消费接口；代价是：

- 结构化类型信息会在这层被文本化
- 上层如果需要强结构，就要自己再 parse

## 12. 当前这层没有做什么

这条边界必须说透。

Core SDK 当前没有直接负责：

- 人工审批
- 副作用工具串行化策略
- 多轮重试与错误恢复
- trace 持久化
- checkpoint / compact / resume

它负责的是把“工具可以被模型看见并落回宿主执行”这件事打通。

## 13. 最容易误解的 5 个点

### 13.1 “工具出现在请求里” 不等于 “已经执行”

暴露只是 provider 可见，执行还要等模型真的返回 tool call。

### 13.2 `ToolUtil` 不只管本地 Function

它还统一了 built-in、本地 MCP 和远程 gateway 工具。

### 13.3 built-in tool 不是普通 Function 的一个例子

它们的 schema 也许能长得像 Function tool，但执行时走的是独立拦截器。

### 13.4 远程 MCP 工具不是自动全量开放

它们仍然要先经过 `mcpServices` 白名单。

### 13.5 返回值统一是字符串

不要把这层误当成强类型业务调用框架。

## 14. 这页最该记住的结论

AI4J 当前的工具执行模型，本质上是一个“统一工具路由器”：

- 初始化时扫描本地能力
- 请求时按白名单组装工具面
- 执行时按 built-in / 本地 / 远程优先级路由
- 结果统一文本化回流给上层 runtime

理解这条链后，再去看 Agent 或 Coding Agent 的审批、trace、长任务治理，层次就不会混。
