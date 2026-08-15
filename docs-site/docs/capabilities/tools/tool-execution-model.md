---
title: "Tool Execution Model"
description: "拆解 AI4J 工具执行模型四段链：发现注册、请求级白名单、provider 返回 tool call、本地调用路由与执行，讲清 built-in/Function/MCP 优先级与结果文本化回流。"
tags: [concept]
---

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

内建 coding tools（共 8 个：`bash`/`read_file`/`write_file`/`apply_patch`/`glob`/`grep`/`edit`/`update_agents_md`）不需要靠扫描注册才能执行，因为它们在 `BuiltInToolExecutor` 里有固定实现。

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

### 5.1 多租户路由：`user_{userId}_tool_{toolName}` 命名约定

优先级里排在 built-in 之后的“用户级远程 MCP 工具”不是靠额外参数识别的，而是靠**函数名编码**。`ToolUtil.invoke(...)` 在 built-in 未命中后，会先用 `extractUserIdFromFunctionName(functionName)` 检查名字是否匹配：

```text
user_{userId}_tool_{toolName}
```

例如 `user_123_tool_create_issue` 会被解析成 `userId=123`、`toolName=create_issue`，然后直接走：

```java
gateway.callUserTool("123", "create_issue", argumentObject).join()
```

这条路径绕过了本地 MCP / Function / 全局 gateway 的常规查找，把调用定向到该用户专属的 `McpClient`。配套地，工具面组装侧的 `ToolUtil.getUserMcpTools(mcpServerIds, userId)` / `getAllTools(functionList, mcpServerIds, userId)` 只会把该用户已注册的用户级服务工具投影进本次请求，因此模型看到的 `user_123_tool_*` 名字与它能调用的用户工具一一对应。

也可以不走名字编码，直接显式传 `userId` 调用：

```java
ToolUtil.invoke("create_issue", argument, "123");   // 等价于上面的路由
```

设计要点：

- **隔离靠 key，不靠运行时判断**：用户级 MCP 客户端按 `user_{userId}_service_{serviceId}` / `user_{userId}_tool_{toolName}` 注册进 gateway，隔离在映射层就完成。
- **用户级优先于全局**：一旦函数名命中 `user_..._tool_...`，就不会再落到全局 gateway 查找，同名工具不会串租户。
- **这是 SaaS / 多租户宿主的能力**：单租户场景用不到这套约定，模型拿到的就是普通工具名。

## 6. 内建工具为什么是特殊路径

`ToolUtil.invoke(...)` 一开始就会先尝试：

```java
BuiltInToolExecutor.invoke(functionName, argument, builtInToolContext)
```

如果返回非空，后续本地 Function / MCP 路由就不会再走。

当前内建工具共 8 个（见 `BuiltInTools.allCodingToolNames()`）：

- `bash` —— shell 命令 + 后台进程管理（`exec`/`start`/`status`/`logs`/`write`/`stop`/`list`）
- `read_file` —— 读工作区或已批准只读根的文本文件
- `write_file` —— 创建/覆盖/追加文本文件
- `apply_patch` —— 应用结构化补丁
- `glob` —— glob 模式匹配文件路径
- `grep` —— 正则搜索文件内容
- `edit` —— 精确字符串替换
- `update_agents_md` —— 读写 `AGENTS.md` 记忆文件

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

### 11.1 结构化 sub-trace：让 tool 内部步骤可见

文本化 `output` 喂给 LLM，但“工具内部到底发生了什么”（一次 RAG 检索命中了哪些 chunk、是否经过 rerank、引用了哪些来源）对可观测性同样重要。Agent runtime 层为此提供了一条与 `output` 并行的结构化通道，**不改变 Core SDK 的字符串回流契约**：

- `ai4j-agent` 的 `TraceableToolExecutor`（继承 `ToolExecutor`）多了一个 `Object lastTrace()`，返回该 executor 最近一次执行产生的 sub-trace（如 `RagResult`，含 `retrievedHits` / `rerankedHits` / `citations`）。
- runtime 在调用 `execute(...)` 之后读取 `lastTrace()`，写入 `AgentToolResult.trace`，再流入 `IoCapture`，使一个 TOOL 节点不只记录最终字符串，也记录工具的内部步骤。
- LLM 仍然只读 `AgentToolResult.output`，`trace` 仅供 trace / 重放 / 审计消费，对模型上下文零影响。

并发约束（接口文档明确要求）：runtime 可能在同一个 executor 实例上并行执行多个 tool call，因此 `lastTrace()` 必须返回**当前线程**最近一次执行产生的 trace。实现用 `ThreadLocal` 持有，例如 `ai4j-agent` 的 `RagTool.RagToolExecutor`：

```java
// io.github.lnyocly.ai4j:ai4j-agent:2.4.2
private class RagToolExecutor implements TraceableToolExecutor {
    private final ThreadLocal<RagResult> lastResult = new ThreadLocal<RagResult>();

    @Override
    public AgentToolResult execute(AgentToolCall call) {
        RagResult result = ragService.search(buildQuery(call));
        lastResult.set(result);                 // ponytail: ThreadLocal, per-call trace
        return AgentToolResult.builder()
                .name(call.getName())
                .callId(call.getCallId())
                .output(formatContext(result))  // 字符串给 LLM
                .build();
    }

    @Override
    public Object lastTrace() {
        return lastResult.get();                // 结构化 trace 给 IoCapture
    }
}
```

普通 tool（不实现 `TraceableToolExecutor`）的 `trace` 为 `null`，行为与之前完全一致；只有需要暴露内部步骤的 tool 才实现该接口。这让“结果回流”保持了字符串统一性，同时为可观测性留了一条可选的结构化侧通道。

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

:::note
它们仍然要先经过 `mcpServices` 白名单。
:::

### 13.5 返回值统一是字符串

不要把这层误当成强类型业务调用框架。

## 14. 这页最该记住的结论

AI4J 当前的工具执行模型，本质上是一个“统一工具路由器”：

- 初始化时扫描本地能力
- 请求时按白名单组装工具面
- 执行时按 built-in / 本地 / 远程优先级路由
- 结果统一文本化回流给上层 runtime

理解这条链后，再去看 Agent 或 Coding Agent 的审批、trace、长任务治理，层次就不会混。

## 继续阅读

- → [ToolUtil API Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/io/github/lnyocly/ai4j/tool/ToolUtil.html)（`getAllTools(...)` / `invoke(...)` / `getUserMcpTools(...)` 等工具调度入口）
