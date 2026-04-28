---
sidebar_position: 8
---

# SubAgent 与 Handoff Policy

SubAgent 解决的是“把某一类专业任务从主 Agent 中拆出去，以工具调用的形式委派给另一个 Agent”。

它不是 Team 的轻量版，也不是普通函数工具的别名。它的本质是：把另一个 `Agent` 封装成一个可治理的 tool surface，再用 `HandoffPolicy` 控制这条委派链能否发生、能走多深、失败后如何处理。

## 1. SubAgent 解决什么问题

当一个 Agent 同时承担下面几类职责时，往往会开始失控：

- 决策与路由
- 领域检索
- 格式化输出
- 代码审查或专项分析

如果继续把所有能力都塞进一个 system prompt，常见后果是：

- 上下文越来越混杂
- 工具面不断膨胀
- 失败责任不清
- 某个专项能力无法独立演化

SubAgent 的设计目标是把“专项能力”拆成独立 Agent，但仍让主 Agent 以熟悉的工具调用方式使用它。

## 2. 它和普通工具、Teams 的边界

| 能力 | 核心抽象 | 适合什么场景 | 不适合什么场景 |
| --- | --- | --- | --- |
| 普通工具 | `ToolExecutor` | 一个同步函数调用就能完成的能力 | 需要独立提示词、独立 memory 的复杂子任务 |
| SubAgent | `SubAgentDefinition` + `HandoffPolicy` | 主 Agent 把专项任务委派给另一个 Agent | 多成员共享任务板、消息总线、主动协作 |
| Agent Teams | `AgentTeam` | 多角色长期协作、任务依赖、团队状态持久化 | 单次主从委派 |

记住一个判断标准：

- 你只是想“把一个复杂能力包装成一个工具”，用 SubAgent
- 你需要“多个成员围绕任务板协作”，用 Agent Teams

## 3. 核心对象关系

SubAgent 主线涉及下面几个对象：

| 对象 | 角色 | 关键职责 |
| --- | --- | --- |
| `SubAgentDefinition` | 子代理定义 | 描述 name、description、toolName、agent、sessionMode |
| `StaticSubAgentRegistry` | 注册器 | 把定义转换成可暴露工具，并负责真实调用 |
| `SubAgentToolExecutor` | 执行器包装层 | 拦截子代理工具调用，应用 handoff policy |
| `HandoffPolicy` | 治理策略 | 控制是否允许、深度、重试、超时、失败动作 |
| `HandoffContext` | 深度上下文 | 通过 `ThreadLocal` 记录嵌套委派深度 |
| `SubAgentSessionMode` | 会话模式 | 决定每次新建 session 还是复用已有 session |

装配关系如下：

```text
AgentBuilder
  -> resolveSubAgentRegistry()
  -> StaticSubAgentRegistry
  -> CompositeToolRegistry(base tools + subagent tools)
  -> SubAgentToolExecutor(delegate executor + handoff policy)
```

## 4. `SubAgentDefinition` 的真实语义

`SubAgentDefinition` 的字段并不多，但每个字段都对应实际运行语义：

- `name`
- `description`
- `toolName`
- `agent`
- `sessionMode`

其中默认值需要特别注意：

- `sessionMode` 默认是 `SubAgentSessionMode.NEW_SESSION`

这意味着，如果你什么都不配，SubAgent 的默认行为是：

- 每次 handoff 都新建一个 `AgentSession`
- 不复用子代理 memory
- 不共享上一次委派的对话历史

这对隔离性更安全，但不适合需要长期记忆的子代理。

## 5. 子代理如何被“变成工具”

`StaticSubAgentRegistry` 会把每个 `SubAgentDefinition` 变成一个 function tool。

### 5.1 工具名生成规则

如果你没有显式提供 `toolName`，注册器会自动生成：

```text
subagent_<normalized name>
```

标准化规则包括：

- 转小写
- 非 `[a-z0-9_]` 字符替换成 `_`
- 合并连续 `_`
- 去掉首尾 `_`
- 若首字符是数字，自动补 `agent_`

因此 `name` 不只是展示字段，它还可能决定默认工具名。

### 5.2 自动生成的参数 schema

生成的工具 schema 固定要求：

- `task`：必填
- `context`：可选

也就是说，主模型看到的不是“直接运行另一个 Agent”，而是一个普通函数工具：

```json
{
  "task": "请完成这个专项任务",
  "context": "可选补充上下文"
}
```

### 5.3 返回值结构

`StaticSubAgentRegistry.execute(...)` 的返回值不是原始 `AgentResult`，而是一个 JSON 字符串，包含：

- `subagent`
- `toolName`
- `output`
- `steps`

这样做的意义是把子代理执行结果压平回主 Agent 的工具结果通道，而不是把整个运行时对象向上层泄漏。

## 6. 输入是怎么传给子代理的

`StaticSubAgentRegistry.resolveInput(...)` 会优先这样解释工具参数：

1. 先取 `task`
2. 若没有 `task`，再尝试 `input`
3. 若同时有 `task` 和 `context`，会拼成：

```text
<task>

Context:
<context>
```

如果参数根本不是合法 JSON，就把原始 arguments 直接当作输入字符串。

这意味着 SubAgent 的主输入协议很简单，但你仍然可以通过 `context` 注入额外背景，而不用把所有信息都挤进一行 `task`。

## 7. Session 模式如何影响行为

`SubAgentSessionMode` 只有两个值：

- `NEW_SESSION`
- `REUSE_SESSION`

### 7.1 `NEW_SESSION`

每次 handoff 都：

- `agent.newSession()`
- 执行一轮任务
- 不保留上次委派的上下文

优点：

- 隔离性好
- 行为更可预测
- 并发下没有会话共享副作用

### 7.2 `REUSE_SESSION`

`StaticSubAgentRegistry` 会按 `toolName` 缓存 `AgentSession`，并在调用时：

- `computeIfAbsent(...)` 复用 session
- 对同一个 session 进行 `synchronized` 保护

这说明 `REUSE_SESSION` 的真实语义不是“无限并发共享上下文”，而是：

- 同一子代理工具可持续积累记忆
- 但同一 session 的并发执行会被串行化

适合场景：

- 子代理需要在多次委派间保持长期上下文
- 可以接受该子代理实例的串行执行语义

## 8. `HandoffPolicy` 默认值与治理语义

`HandoffPolicy` 的默认值如下：

| 字段 | 默认值 | 语义 |
| --- | --- | --- |
| `enabled` | `true` | 开启策略治理 |
| `maxDepth` | `1` | 只允许 lead -> subagent 一层嵌套 |
| `maxRetries` | `0` | 子代理失败后不自动重试 |
| `timeoutMillis` | `0L` | 不做超时截断 |
| `allowedTools` | `null` | 不做显式 allow-list |
| `deniedTools` | `null` | 不做显式 deny-list |
| `onDenied` | `FAIL` | 被策略拒绝时默认失败 |
| `onError` | `FAIL` | 子代理异常时默认失败 |
| `inputFilter` | `null` | 默认不改写输入 |

这套默认值体现的原则很清楚：

- 默认允许使用 SubAgent
- 但默认不允许无限递归
- 默认不吞掉错误

## 9. `SubAgentToolExecutor` 到底拦截了什么

`SubAgentToolExecutor.execute(...)` 的执行分支非常明确：

1. 如果调用命中 subagent tool：
   - 进入 handoff policy 流程
2. 如果没命中：
   - 直接委托给下游 `delegate.execute(call)`

也就是说，它不是替代原始工具执行器，而是在原始执行链前面增加一层“只有命中 subagent tool 才生效”的拦截器。

这是一个很重要的边界：

- 普通工具仍由原始执行器负责
- 只有子代理工具才会走 handoff 逻辑

## 10. 策略检查与失败分支

当调用命中某个子代理工具后，`SubAgentToolExecutor` 会做以下检查：

### 10.1 深度控制

通过 `HandoffContext` 维护当前嵌套深度，若超过 `maxDepth` 则拒绝执行。

默认 `maxDepth = 1` 的含义不是“只能执行一次工具”，而是：

- 允许主 Agent 委派给子代理
- 不允许子代理再继续 handoff 给下一层子代理

### 10.2 工具 allow/deny

如果配置了：

- `allowedTools`
- `deniedTools`

则当前 `toolName` 会先经过白名单 / 黑名单判断。

### 10.3 输入过滤

`inputFilter` 可以在真正 handoff 前改写参数。适合做：

- 脱敏
- 裁剪冗长上下文
- 注入额外治理字段

### 10.4 超时执行

当 `timeoutMillis > 0` 时，`SubAgentToolExecutor` 会把执行提交给内部线程池，并通过 `future.get(timeoutMillis, ...)` 控制单次 handoff 时长。

### 10.5 错误重试

`maxRetries` 控制的是首次失败后的补充重试次数，不包含第一次执行。

## 11. `FALLBACK_TO_PRIMARY` 的真实语义

`HandoffFailureAction` 允许把 `onDenied` 或 `onError` 配成 `FALLBACK_TO_PRIMARY`。

这时如果存在 `delegate`，执行器会改走：

```java
delegate.execute(call)
```

这不是“切回主 Agent 自己思考”，而是：

- 对同名工具调用，交还给原始 `ToolExecutor`

常见用途包括：

- 某个子代理只在部分环境启用
- 生产环境暂时关闭 handoff，但保留普通工具兜底
- 子代理异常时回退到传统实现

## 12. 一份正确的接入示例

```java
SubAgentDefinition weather = SubAgentDefinition.builder()
        .name("weather specialist")
        .description("Collect weather facts and return concise summaries.")
        .toolName("delegate_weather")
        .agent(weatherAgent)
        .sessionMode(SubAgentSessionMode.NEW_SESSION)
        .build();

Agent lead = Agents.react()
        .modelClient(managerClient)
        .model("manager-model")
        .subAgent(weather)
        .handoffPolicy(HandoffPolicy.builder()
                .maxDepth(1)
                .timeoutMillis(15000L)
                .onError(HandoffFailureAction.FAIL)
                .build())
        .build();
```

这个配置表达的是：

- 主 Agent 把天气专项能力封装成一个工具
- 每次委派都使用独立 session
- 不允许子代理继续无限递归 handoff
- 单次 handoff 最多运行 15 秒

## 13. 什么时候该用 SubAgent，什么时候别用

适合用 SubAgent：

- 某个能力需要独立 system prompt
- 某个能力需要自己的工具白名单
- 主 Agent 只想“委托并拿回结果”
- 你希望把专项能力模块化为可复用组件

不适合用 SubAgent：

- 需要多个成员共享任务状态
- 需要消息总线
- 需要任务认领、转派、保活
- 需要持久化团队运行快照

这些已经超出了 handoff 的边界，应进入 Agent Teams。

## 14. 失败语义与工程边界

### 14.1 SubAgent 不是权限系统本身

`HandoffPolicy` 提供的是 handoff 治理，不替代真正的工具权限控制。真正的执行权限仍然应当落在 `ToolExecutor` 一侧。

### 14.2 `REUSE_SESSION` 不等于高并发共享

它会复用上下文，但同一工具实例上的调用会同步执行。不要把它当作“高吞吐共享 Agent 池”。

### 14.3 `maxDepth=1` 是安全默认值

如果你把深度放开，就必须同时明确：

- 哪些子代理能继续 handoff
- 如何避免循环委派
- 如何做 trace 与审计

否则很容易出现链路不可控的问题。

## 15. 推荐阅读源码入口

建议按下面顺序阅读：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentDefinition.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentSessionMode.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/StaticSubAgentRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffPolicy.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`

## 16. 推荐验证用例

这几组测试基本覆盖了 SubAgent 的行为契约：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`

## 17. 下一步读什么

读完这一页后，建议继续：

1. [Tools and Registry](/docs/agent/tools-and-registry)
2. [Agent Teams](/docs/agent/agent-teams)
3. [Trace 与可观测性](/docs/agent/observability/trace)
