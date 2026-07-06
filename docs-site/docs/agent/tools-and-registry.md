# Tools and Registry

在 `ai4j-agent` 里，工具体系真正解决的不是“函数怎么暴露给模型”，而是四个边界如何拆开：

- 模型能看见什么工具
- 宿主允许执行什么工具
- 哪一层负责结构校验，哪一层负责审批和拦截
- 工具结果如何重新进入 Agent loop

如果这四件事没有拆开，最终结果通常是：

- schema 暴露和权限审批混在一起
- 不同 runtime 各自实现一套工具治理
- 工具失败后如何继续推理没有统一语义

`ai4j-agent` 的设计选择非常明确：

- `AgentToolRegistry` 负责暴露面
- `ToolExecutor` 负责执行面
- `BaseAgentRuntime` 负责把工具调用纳入主循环

## 1. 最短对象图：先把角色看清

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`

最小对象关系可以压缩成一条链：

```text
AgentBuilder
  -> AgentToolRegistry
  -> ToolExecutor
  -> AgentContext
  -> BaseAgentRuntime.runInternal()
  -> AgentToolCallSanitizer
  -> ToolExecutor.execute(call)
  -> AgentMemory.addToolOutput(callId, output)
```

这个拆分非常重要，因为它让“可见工具面”和“实际执行权”不再是同一个对象的隐式副作用。

## 2. `AgentToolRegistry` 只负责一件事：给模型看 schema

`AgentToolRegistry` 接口很窄：

```java
public interface AgentToolRegistry {
    List<Object> getTools();
}
```

它不执行工具，也不判断权限。它只回答：

- 当前 Agent 打算把哪些工具 schema 传给模型

常见实现包括：

- `StaticToolRegistry`
- `CompositeToolRegistry`
- `ToolUtilRegistry`
- `AgentTeamToolRegistry`

这里的核心设计原则是：模型看到什么，不等于系统一定允许执行什么。

## 3. `ToolExecutor` 才是执行边界，也是权限边界

`ToolExecutor` 同样很窄：

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

它解决的是另一个问题：

- 当模型真的发起工具调用时，系统怎么执行

这也是做以下治理的最佳位置：

- 权限审批
- allow-list / deny-list
- 参数重写
- 审计日志
- 远程代理
- 沙箱执行
- 重试和超时控制

如果你要解释“ai4j 的工具权限审批是怎么拦截的”，最准确的答案是：

- 普通 Agent 工具链的稳定拦截点在 `ToolExecutor.execute(...)`
- 不是 `AgentToolCallSanitizer`
- 也不是某个通用 hook

## 4. 默认 Builder 装配路径比看起来更具体

`AgentBuilder.build()` 的默认逻辑不是“自动帮你把工具接好”，而是一条明确的决策链。

### 4.1 你没有传 `toolRegistry(...)`

默认走：

```java
StaticToolRegistry.empty()
```

这意味着模型看不到任何工具。

### 4.2 你用了便捷方法 `toolRegistry(List<String> functions, List<String> mcpServices)`

Builder 会创建：

- `ToolUtilRegistry`

而 `ToolUtilRegistry` 背后再通过 `ToolUtil.getAllTools(functionList, mcpServerIds)` 合并：

- 本地函数工具
- MCP 服务工具

也就是说，MCP 在这里不是“tools 的一个子类页面问题”，而是统一工具暴露面的一种来源。

### 4.3 你没有显式传 `toolExecutor(...)`

Builder 会先从“基础 registry”里提取工具名，再尝试创建 `ToolUtilExecutor`。

这一步有两个重要后果：

- 默认执行器只允许执行 registry 已经暴露的工具名
- 如果基础 registry 里解析不出工具名，默认执行器可能是 `null`

第二点非常容易踩坑。因为 `resolveToolNames(...)` 只会从 `Tool` 类型对象里抽名字；如果你提供的是自定义 schema 对象，又没有自己传 `ToolExecutor`，Builder 并不会神奇地知道怎么执行。

### 4.4 subagent 是怎么接进来的

当配置了 subagent registry 后，Builder 会：

- 用 `CompositeToolRegistry` 合并原始工具面和 subagent tool surface
- 用 `SubAgentToolExecutor` 包装原执行器

这说明 subagent 治理不是注册表层面的例外，而是执行器侧的专门包装器。

## 5. runtime 如何消费工具调用

主链在 `BaseAgentRuntime.runInternal()` 里。

### 5.1 模型先返回 `toolCalls`

模型客户端把响应折叠成 `AgentModelResult`，其中可包含：

- `outputText`
- `memoryItems`
- `toolCalls`

### 5.2 runtime 先做归一化

`normalizeToolCalls(...)` 会补齐缺失的 `callId`，格式默认是：

- `tool_step_<step>_<index>`

这一步的意义不是好看，而是保证工具结果回写 memory 时有稳定引用。

### 5.3 再做结构校验

`AgentToolCallSanitizer.validationError(...)` 只做结构合法性验证，例如：

- 工具名不能为空
- `arguments` 必须是 JSON object
- `bash` 不同 action 的必要字段是否存在
- `read_file.path` 是否为空
- `apply_patch.patch` 是否为空

它解决的是“这个调用像不像一个可执行调用”，不是“你允不允许它执行”。

### 5.4 然后进入执行器

通过校验的调用最终统一进入：

```java
toolExecutor.execute(call)
```

工具异常不会默认把整个 Agent 打崩。`BaseAgentRuntime.executeTool(...)` 会把异常包成：

- `TOOL_ERROR: {"errorType":"...","error":"...","tool":"...","callId":"..."}`

默认不带 stack trace。然后继续把这个错误结果回灌给 memory 和后续轮次。这是典型的
“模型可恢复失败语义”。

### 5.5 最后重新进入 memory

不管是正常结果还是包装后的错误结果，都会调用：

```java
memory.addToolOutput(callId, output)
```

工具执行因此不是 loop 外围动作，而是 loop 自身的一部分。

## 6. 审批、拦截、Hook 到底该放哪层

这是最容易被写模糊的地方。

### 6.1 普通 Agent 的正确拦截点

如果你要做审批或访问控制，优先包在执行器外层：

```java
ToolExecutor guarded = call -> {
    approvalService.check(call.getName(), call.getArguments());
    auditService.record(call);
    return delegate.execute(call);
};
```

这层的优势非常直接：

- 已经拿到规范化后的工具名
- 已经拿到结构合法的参数 JSON
- 仍然处在统一 Agent loop 中，错误、审计、trace、memory 回写都不会失真

### 6.2 为什么不放在 `AgentToolCallSanitizer`

因为 sanitizer 的职责太窄。把业务授权逻辑塞进去会造成：

- 结构错误和权限拒绝混成一类错误
- 不同执行器无法共享同一套授权逻辑
- 代码层次错位，后续更难扩展

### 6.3 为什么不是靠一个通用 hook

当前 `ai4j-agent` 并没有为普通 Agent 提供统一的“工具审批 hook”抽象。

有审批/策略概念的是：

- Team 层的 `planApproval` 和 `hooks`
- SubAgent 层的 `HandoffPolicy`

而普通 Agent 的工具治理，本质上就是执行器包装问题。

## 7. `SubAgentToolExecutor` 和 `AgentTeamToolExecutor` 说明了什么

这两个类非常能代表 ai4j 的工具设计哲学。

### 7.1 `SubAgentToolExecutor`

它不是把 handoff 逻辑塞进 registry，而是在执行期做更强治理，例如：

- `allowedTools`
- `deniedTools`
- `maxDepth`
- `timeoutMillis`
- `inputFilter`
- `onDenied`
- `onError`

也就是说，subagent 并没有推翻“registry 负责暴露、executor 负责治理”的边界。

### 7.2 `AgentTeamToolExecutor`

它只拦截 `team_*` 工具：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

其他工具会直接委托给原始执行器。如果没有 delegate，而成员又调用了非 team 工具，它会直接抛错。

这再次证明：

- Team 工具不是独立 runtime 魔法
- 而是统一工具执行链上的一层包装

## 8. `parallelToolCalls` 的真实含义

`BaseAgentRuntime.runInternal()` 会在满足两个条件时并行执行工具：

- `context.getParallelToolCalls() == true`
- 当前轮合法 tool call 数量大于 1

这会对执行器提出一个硬要求：

- 你的 `ToolExecutor` 必须线程安全

如果执行器内部复用可变状态、共享临时文件或依赖单线程顺序，那么一开并行就会出问题。这个问题通常不是模型层报错，而是工具层 race condition。

## 9. 典型接入方式

### 9.1 用 `ToolUtil` + MCP 快速拼出统一工具面

```java
Agent agent = Agents.react()
        .modelClient(modelClient)
        .model("gpt-4.1")
        .toolRegistry(
                java.util.Arrays.asList("queryWeather", "read_file"),
                java.util.Arrays.asList("github", "filesystem")
        )
        .build();
```

适合：

- 工具已经在 `ToolUtil` 或 MCP 服务注册好
- 只想给当前 Agent 暴露最小白名单

### 9.2 自定义执行器做审批和审计

```java
ToolExecutor guardedExecutor = call -> {
    approvalService.requireApproved(call.getName(), call.getArguments());
    auditService.record(call);
    return ToolUtil.invoke(call.getName(), call.getArguments());
};

Agent agent = Agents.builder()
        .modelClient(modelClient)
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .toolExecutor(guardedExecutor)
        .build();
```

适合：

- 权限审批不想散落在各个工具函数内部
- 想把拒绝、审计、失败信息统一纳入 loop

### 9.3 schema 和执行完全分离

```java
AgentToolRegistry registry = new StaticToolRegistry(myToolSchemas);

ToolExecutor executor = call -> gateway.execute(call);

Agent agent = Agents.builder()
        .modelClient(modelClient)
        .toolRegistry(registry)
        .toolExecutor(executor)
        .build();
```

适合：

- schema 来源不是 `ToolUtil`
- 真正执行要走远程网关、沙箱或代理进程

## 10. 失败边界和容易误判的点

### 10.1 工具结果默认是字符串语义

`ToolExecutor.execute(...)` 返回值是 `String`。复杂对象最终都要被你自己序列化成字符串，再让模型消费。

### 10.2 默认执行器只对默认工具体系友好

如果你用自定义 registry，但没有显式提供 executor，Builder 很可能帮不了你。因为默认执行器的创建逻辑假设自己能理解 registry 里的工具对象。

### 10.3 工具白名单只约束默认执行器

`ToolUtilExecutor` 会校验 `allowedToolNames`，但只对它自己生效。你换成自定义执行器后，白名单、黑名单、审批全部由你负责。

### 10.4 工具失败默认不终止 Agent

这通常是正确的，因为模型还能根据 `TOOL_ERROR` 决定重试、改参或换路线。但如果你的
业务要求“某些工具失败必须立即中止”，那就应该在执行器里显式实现，而不是假设 runtime
会替你做。默认错误 payload 只有 `errorType`、`error`、`tool`、`callId`，没有 stack trace。

## 11. 调试优先看这些入口

出现“模型能看到工具但调不起来”“审批逻辑失效”“工具返回了但下一轮没用上”时，先看：

- `AgentBuilder.build()` 最终生成的 `toolRegistry` 和 `toolExecutor` 是什么
- `AgentToolCallSanitizer.validationError(...)` 是否把调用拦成了结构错误
- `ToolExecutor.execute(...)` 是否真的执行到了目标逻辑
- `BaseAgentRuntime.executeTool(...)` 是否把异常包装成了 `TOOL_ERROR`
- `memory.addToolOutput(...)` 是否拿到了稳定的 `callId`

这几处比看最终自然语言输出更接近根因。

## 12. 继续阅读

1. [Memory and State](/docs/agent/memory-and-state)
2. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
3. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/agent-teams)
5. [Trace Observability](/docs/agent/trace-observability)
