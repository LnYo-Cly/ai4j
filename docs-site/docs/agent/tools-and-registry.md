# Tools and Registry

在 `Agent` 层，工具问题不再只是“函数怎么声明”，而是下面四件事如何被稳定治理：

- 模型能看到哪些工具
- 哪些工具调用可以真正执行
- 工具调用在哪一层被校验、审批、拦截和审计
- 工具结果如何重新进入 Agent loop

这页解释的不是某个函数工具怎么写，而是 `ai4j-agent` 如何把工具暴露面和执行面拆开，并让 ReAct / CodeAct / SubAgent / MCP 共享一套治理边界。

## 1. 它解决什么问题

如果没有独立的工具架构，业务层通常会把三种逻辑混在一起：

- 给模型暴露 tool schema
- 执行本地函数或远程能力
- 在调用前后做权限、审计、回写和失败处理

这会导致两个直接问题：

- 模型“看见的能力”和宿主“允许执行的能力”边界不清
- 权限审批只能零散塞进业务代码，而不是挂在统一执行面

`ai4j-agent` 的做法是把这三件事拆开：

- `AgentToolRegistry` 负责声明面
- `ToolExecutor` 负责执行面
- `BaseAgentRuntime` 负责把 tool call 纳入主循环

## 2. 设计原则

### 2.1 工具可见性和工具执行必须分离

模型是否知道一个工具存在，和系统是否允许执行这个工具，不应该由同一个对象隐式决定。

因此 `AgentToolRegistry` 只返回 schema 列表，`ToolExecutor` 才真正执行 `AgentToolCall`。这样做的好处是：

- 白名单暴露更明确
- 执行期可以做额外审批
- 同一工具面可以复用不同执行策略

### 2.2 结构校验不等于权限控制

`AgentToolCallSanitizer` 只验证调用是否“像一个可执行的调用”，例如：

- 工具名是否为空
- `arguments` 是否是 JSON object
- 某些内置工具是否缺参数

它不是权限系统，也不是审批系统。真正的授权边界应放在 `ToolExecutor` 或其包装器中。

### 2.3 工具调用必须回到 Agent 主循环

工具执行不是离散动作。模型返回 tool call 后，runtime 会：

1. 归一化 tool call
2. 校验 tool call
3. 执行 tool call
4. 把结果写回 `AgentMemory`
5. 继续下一轮推理

所以工具层本质上是 Agent loop 的一部分，而不是 loop 旁边的附属能力。

### 2.4 工具面可以由多种来源合并

本地函数、MCP 工具、subagent handoff surface、team tools 最终都可以被折叠进同一个“模型可见工具面”。

这样做的好处是：

- 模型只面对统一 tool surface
- 上层协作能力可以复用同一套工具治理链路
- 文档和运行时边界都更清晰

## 3. 核心抽象

### 3.1 `AgentToolRegistry`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`

接口非常窄：

```java
public interface AgentToolRegistry {
    List<Object> getTools();
}
```

它只回答一个问题：当前 Agent 要把哪些工具 schema 暴露给模型。

典型实现：

- `StaticToolRegistry`
- `CompositeToolRegistry`
- `ToolUtilRegistry`

### 3.2 `ToolExecutor`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`

接口也很窄：

```java
public interface ToolExecutor {
    String execute(AgentToolCall call) throws Exception;
}
```

它回答的是另一个问题：当模型真的发起工具调用时，系统如何执行。

典型实现：

- `ToolUtilExecutor`
- `SubAgentToolExecutor`
- `AgentTeamToolExecutor`
- 业务自定义包装执行器

### 3.3 `AgentToolCall` 与 `AgentToolResult`

源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolCall.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolResult.java`

二者分别承载：

- 工具名
- 参数 JSON 字符串
- `callId`
- 结果输出字符串

Agent runtime 并不要求工具结果必须是特定 Java 类型；它默认把执行结果视为字符串，再回灌给 memory 和模型。

## 4. 代码路径与对象关系

工具主链可以压缩成下面这条路径：

```text
AgentBuilder
  -> AgentToolRegistry
  -> ToolExecutor
  -> AgentContext
  -> BaseAgentRuntime.runInternal()
  -> AgentToolCallSanitizer
  -> ToolExecutor.execute(call)
  -> AgentMemory.addToolOutput(...)
```

关键源码位置如下。

### 4.1 Registry 侧

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/AgentToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/StaticToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/CompositeToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolUtilRegistry.java`

### 4.2 Executor 侧

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/ToolUtilExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`

### 4.3 Runtime 消费侧

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/BaseAgentRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`

## 5. 默认装配是怎么工作的

`AgentBuilder.build()` 对工具层的处理非常具体，不是模糊“自动装配”。

### 5.1 你没有传 `toolRegistry(...)`

默认使用：

```java
StaticToolRegistry.empty()
```

这意味着当前 Agent 对模型暴露空工具面。

### 5.2 你传的是 `toolRegistry(List<String> functions, List<String> mcpServices)`

这条便捷路径会走：

- `ToolUtilRegistry`
- `ToolUtil.getAllTools(functionList, mcpServerIds)`

也就是说，Builder 并没有自己解析工具，而是把函数工具和 MCP 服务 ID 交给 `ToolUtil` 汇总成统一 tool list。

### 5.3 你没有显式传 `toolExecutor(...)`

Builder 会：

1. 从 registry 中提取当前可见工具名
2. 用这些工具名构造 `ToolUtilExecutor`

`ToolUtilExecutor.execute(...)` 的行为是：

- 如果 `allowedToolNames` 不为空，只允许执行白名单内工具
- 真正执行时走 `ToolUtil.invoke(call.getName(), call.getArguments())`

这意味着默认行为不是“Agent 可以调用所有工具”，而是“Agent 只能调用当前 registry 已经暴露的工具”。

### 5.4 你配置了 subagent

Builder 会做两件事：

1. 用 `CompositeToolRegistry` 合并基础工具与 subagent tool surface
2. 用 `SubAgentToolExecutor` 包装原有执行器

此时工具面已经不只是本地函数或 MCP，还包含了 handoff surface。

## 6. Runtime 如何消费 tool call

`BaseAgentRuntime.runInternal()` 中，工具调用链条是明确可追踪的。

### 6.1 模型响应阶段

模型通过 `AgentModelClient` 返回 `AgentModelResult`，其中可能带有 `toolCalls`。

### 6.2 归一化阶段

runtime 会把模型返回值标准化为 `AgentToolCall`：

- 为空的 `callId` 自动补齐为 `tool_step_<step>_<index>`
- 空工具名会被清洗

### 6.3 校验阶段

`AgentToolCallSanitizer.validationError(...)` 负责基础结构校验。

需要特别强调：

- 这里不是审批
- 这里不是鉴权
- 这里不是资源配额控制

它只决定“这个调用从结构上能不能进入执行器”。

### 6.4 执行阶段

对合法调用，runtime 会进入：

```java
toolExecutor.execute(call)
```

这里才是最合理的权限拦截点。原因很直接：

- 此时已经拿到了规范化工具名
- 此时已经拿到了结构合法的参数 JSON
- 此时仍处在统一 Agent loop 中，失败语义、事件和 memory 回写都还能保持一致

### 6.5 回写阶段

执行结果会被包装成 `AgentToolResult`，然后：

- 追加到 `toolResults`
- 写入 `AgentMemory.addToolOutput(callId, output)`
- 通过 `TOOL_RESULT` 事件向外发射

之后模型再读取这些结果，继续下一轮推理。

## 7. 权限审批、拦截、Hook 应该放哪一层

这是最容易被文档写错的地方。

### 7.1 Agent 通用工具审批的真实拦截点

对普通 Agent 来说，最稳定的拦截点是：

```java
ToolExecutor.execute(AgentToolCall call)
```

如果你要做下面这些事情，都应该优先包在 `ToolExecutor` 外面：

- 权限审批
- allow-list / deny-list
- 参数重写
- 审计日志
- 失败重试
- 远程代理转发
- 沙箱执行

示例：

```java
ToolExecutor guardedExecutor = call -> {
    permissionService.check(call.getName(), call.getArguments());
    auditService.record(call);
    return delegate.execute(call);
};
```

### 7.2 为什么不是靠 `AgentToolCallSanitizer`

因为 sanitizer 的职责太窄，它只做“结构合法性”判断。把业务授权塞进去会产生三个问题：

- 校验规则和业务策略耦合
- 无法复用到不同执行器
- 错误语义会和普通参数错误混在一起

### 7.3 为什么不是靠通用 `hook`

在当前 `ai4j-agent` 架构里：

- `Agent` 通用工具链没有一个统一的“工具审批 hook”抽象
- `Team` 层有 `planApproval` 和 `hooks`
- `SubAgent` 层有 `HandoffPolicy`

因此，如果你问“普通 Agent 工具调用怎么拦截”，答案不是“靠 hook”，而是“靠 `ToolExecutor` 包装层”。

### 7.4 `SubAgent` 的例外

`SubAgentToolExecutor` 在通用执行器之上又增加了一层 handoff 治理，包括：

- `allowedTools`
- `deniedTools`
- `maxDepth`
- `timeoutMillis`
- `onDenied`
- `onError`
- `inputFilter`

这说明 handoff 场景的审批治理仍然落在执行器侧，只是换成了更专门的执行器包装器。

## 8. 典型用法

### 8.1 便捷装配函数工具和 MCP 工具

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

适用场景：

- 你已经在 `ToolUtil` 和 MCP 配置里注册了能力
- 想按当前 Agent 的职责做最小白名单暴露

### 8.2 自定义执行器做审批和审计

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

适用场景：

- 你要对工具调用做运行时审批
- 你不希望审批逻辑散落在每个函数工具内部

### 8.3 自定义 registry 与 executor 完全分离

```java
AgentToolRegistry registry = new StaticToolRegistry(myToolSchemas);

ToolExecutor executor = call -> gateway.execute(call);

Agent agent = Agents.builder()
        .modelClient(modelClient)
        .toolRegistry(registry)
        .toolExecutor(executor)
        .build();
```

适用场景：

- schema 来源不是 `ToolUtil`
- 真正执行要走网关、远程服务或隔离沙箱

## 9. 扩展点

工具层最重要的扩展点有四个。

### 9.1 自定义 `AgentToolRegistry`

适合控制模型暴露面，例如：

- 多租户按租户暴露不同工具
- 按用户角色裁剪工具面
- 动态拼装本地工具和远程工具

### 9.2 自定义 `ToolExecutor`

适合控制执行语义，例如：

- 审批
- 限流
- 记录审计轨迹
- 调用外部沙箱
- 失败降级

### 9.3 组合 registry

`CompositeToolRegistry` 允许把多个工具源合并成一个统一视图。

这对本地函数 + MCP + subagent 共存的场景尤其重要。

### 9.4 执行器包装器

`ToolExecutor` 很适合做装饰器式扩展，因为接口稳定而且单一。

可以自然叠加：

- `ApprovalExecutor`
- `AuditExecutor`
- `RetryExecutor`
- `RemoteProxyExecutor`

## 10. 边界、限制与失败语义

### 10.1 默认工具结果是字符串语义

`ToolExecutor.execute(...)` 返回 `String`。因此复杂对象结果最终仍需序列化为字符串，再回灌给模型。

### 10.2 默认执行器依赖 `ToolUtil`

如果你走 `toolRegistry(List<String>, List<String>)` 这条便捷路径，最终会依赖：

- `ToolUtilRegistry`
- `ToolUtilExecutor`
- `ToolUtil.getAllTools(...)`
- `ToolUtil.invoke(...)`

这说明它适合快速接入，但不是所有工具体系的唯一抽象。

### 10.3 白名单控制只约束默认执行器

`ToolUtilExecutor` 会用 `allowedToolNames` 做校验，但如果你替换了自定义执行器，白名单控制就由你自己负责。

### 10.4 并行调用要求执行器线程安全

如果启用了 `parallelToolCalls`，多个工具调用可能并行进入同一个执行器。此时执行器内部必须自行保证线程安全和资源隔离。

### 10.5 工具失败默认写回模型，而不是中止 runtime

普通工具异常会被 runtime 包装成 `TOOL_ERROR` 风格输出，写回 memory 并交给后续轮次处理。这是“模型可恢复”的失败语义，而不是“立即崩溃”的失败语义。

## 11. 和相邻能力的区别

| 能力 | 职责 | 与当前页的关系 |
| --- | --- | --- |
| `ToolUtil` | 工具注册与调用基础设施 | 是最常见的工具来源，不是 Agent loop 本身 |
| `MCP` | 外部能力接入协议 | 可通过 `ToolUtilRegistry` 暴露到 Agent 工具面 |
| `Skill` | 更高层能力组织方式 | 最终仍可能折叠成可见工具面，但不等于 `AgentToolRegistry` |
| `SubAgent` | 委派给其他 Agent 执行 | 通过 `SubAgentToolExecutor` 进入同一工具治理链 |
| `Team` | 多成员协作 | 会引入 team-specific tool surface 和计划审批机制 |

最关键的判断是：

- 想定义“模型看到什么”，看 registry
- 想定义“系统真的怎么执行”，看 executor
- 想定义“调用失败后如何继续推理”，看 runtime

## 12. 建议继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
3. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Trace Observability](/docs/agent/trace-observability)
