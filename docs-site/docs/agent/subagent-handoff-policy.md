---
sidebar_position: 8
---

# SubAgent 与 Handoff Policy

SubAgent 的核心，不是“主 Agent 调另一个 Agent”，而是把另一个 `Agent` 包装成一个受治理的工具面。

从源码上看，这条链路分成两层：

- `StaticSubAgentRegistry` 负责把 subagent 变成 function tool
- `SubAgentToolExecutor` 负责拦截、治理、执行和 fallback

因此 SubAgent 的本质不是“多 Agent 聊天”，而是“带 handoff policy 的工具委派”。

## 1. 先抓住 3 个关键设计决策

### 1.1 SubAgent 先是 tool surface，后才是协作能力

`AgentBuilder.build()` 在装配阶段会做两步非常关键的事情：

1. 把 subagent tools 合并进最终 `toolRegistry`
2. 用 `SubAgentToolExecutor` 包装原始 `ToolExecutor`

也就是说，SubAgent 不是挂在 runtime 旁边的侧边逻辑，而是进入了标准的“模型可见工具面 + 工具执行面”链路。

### 1.2 治理点放在执行器，而不是注册器

`StaticSubAgentRegistry` 只负责：

- 定义工具 schema
- 调用真正的 subagent

真正的策略控制，如：

- 是否允许 handoff
- 最大深度
- timeout
- retry
- deny / fallback

都在 `SubAgentToolExecutor`。

这说明 AI4J 对 SubAgent 的理解很明确：

- registry 负责暴露能力
- executor 负责治理能力

### 1.3 session 语义是 subagent 设计的一部分

`SubAgentSessionMode` 不只是一个可选小开关，而是直接决定子代理是：

- 每次 fresh session 执行
- 还是把多次 handoff 压到同一个 session 里累积 memory

因此 SubAgent 不是“另一个 Agent 实例”这么简单，而是“带明确 session policy 的 delegated runtime”。

## 2. 它和普通工具、Agent Teams 的边界

| 能力 | 核心抽象 | 适合什么问题 | 不适合什么问题 |
| --- | --- | --- | --- |
| 普通工具 | `ToolExecutor` | 一次同步函数就能解决的问题 | 需要独立 prompt、独立 memory 的子任务 |
| SubAgent | `SubAgentDefinition` + `HandoffPolicy` | 把复杂专项能力封装成可委派工具 | 多成员共享任务板、消息总线、主动协作 |
| Agent Teams | `AgentTeam` | 显式团队协作与任务调度 | 单次主从委派 |

SubAgent 最适合的场景是：

- 你仍然想保留“主 Agent 决策”
- 但某个能力已经复杂到不适合再写成普通工具函数

## 3. `AgentBuilder` 是怎么把 SubAgent 装进去的

SubAgent 真正进入系统，是在 `AgentBuilder.build()` 里。

核心装配顺序是：

1. 先拿到 `baseToolRegistry`
2. `resolveSubAgentRegistry()`
   - 显式传了 `subAgentRegistry` 就用它
   - 否则若有 `subAgentDefinitions`，就创建 `StaticSubAgentRegistry`
3. `resolveToolRegistry(...)`
   - 用 `CompositeToolRegistry(baseToolRegistry, new StaticToolRegistry(subRegistry.getTools()))`
   - 把 subagent tools 合并进模型可见工具面
4. 解析原始 `ToolExecutor`
5. 如果有 subagent registry，再用 `new SubAgentToolExecutor(...)` 包一层

这条链有一个很重要的副作用：

- 原始 `ToolExecutor` 不需要认识 subagent tools
- 因为 subagent tool 名会先被包装器拦截
- 非 subagent tool 才继续委托给原执行器

所以 SubAgent 的集成点非常干净：它不会污染普通工具执行器的实现。

## 4. `SubAgentDefinition` 的语义比字段表更重要

字段只有 5 个：

- `name`
- `description`
- `toolName`
- `agent`
- `sessionMode`

但真正重要的是默认行为。

### 4.1 `sessionMode` 默认是 `NEW_SESSION`

这意味着如果你什么都不配，SubAgent 的默认语义是：

- 每次 handoff 独立新建一个 `AgentSession`
- 子代理不会继承上一次 handoff 的 memory

它偏向隔离和可预测，而不是长期上下文连续性。

### 4.2 `name` 不是纯展示字段

如果没有显式给 `toolName`，`StaticSubAgentRegistry` 会按 `name` 生成默认工具名。

所以 `name` 不只是文档标签，还影响最终暴露给模型的工具名字。

## 5. `StaticSubAgentRegistry` 到底做了什么

这层的职责可以概括成三句话：

- 把定义变成工具
- 把工具调用变成 subagent 输入
- 把 subagent 输出压平成工具结果字符串

### 5.1 工具名生成规则

未显式指定 `toolName` 时，默认名是：

```text
subagent_<normalized name>
```

normalize 规则包括：

- 小写化
- 非 `[a-z0-9_]` 字符改成 `_`
- 合并连续 `_`
- 去掉首尾 `_`
- 如果首字符是数字，自动补 `agent_`

此外，构造 registry 时如果工具名重复，会直接抛：

```text
duplicate subagent tool name
```

所以 tool name collision 在初始化阶段就会被拦住。

### 5.2 暴露给模型的 schema 其实非常窄

自动生成的 function tool 只暴露两个字段：

- `task`，必填
- `context`，可选

这说明 SubAgent 的设计不是“把完整 Agent API 暴露给模型”，而是刻意把 handoff 输入面收窄成一个最小委派协议。

### 5.3 返回值不是 `AgentResult`

`execute(...)` 最终返回的是 JSON 字符串，包含：

- `subagent`
- `toolName`
- `output`
- `steps`

这非常关键，因为主 Agent 最终看到的仍然是一个普通工具结果，而不是嵌套的 Agent 对象图。

SubAgent 在系统里的定位，从头到尾都被压在“工具结果通道”里。

## 6. 输入到底是怎么传给 subagent 的

`StaticSubAgentRegistry.resolveInput(...)` 的逻辑很具体：

1. 如果 arguments 为空，返回空字符串
2. 尝试把 arguments 解析成 JSON
3. 优先取 `task`
4. 若无 `task`，再尝试 `input`
5. 若同时存在 `task` 和 `context`，拼成：

```text
<task>

Context:
<context>
```

6. 若不是合法 JSON，直接把原始 arguments 当输入

这说明 SubAgent 的输入协议有两个特点：

- 面向委派任务，而不是面向结构化复杂参数
- 对参数格式有容错，但最终都会折叠成一个字符串输入

## 7. `SubAgentToolExecutor` 才是 handoff 的控制中枢

所有真正重要的 handoff 语义，都在这里。

### 7.1 普通工具不会被污染

入口逻辑很简单：

- 命中 subagent tool -> `executeSubAgent(...)`
- 否则 -> `delegate.execute(call)`

因此这不是全局工具代理器，而是“只代理 subagent tools 的选择性拦截器”。

### 7.2 handoff 从一开始就在发事件

无论走不走策略，都会先发：

- `HANDOFF_START`

结束时会发：

- `HANDOFF_END`

而且 payload 会带：

- `handoffId`
- `callId`
- `tool`
- `subagent`
- `status`
- `depth`
- `sessionMode`
- `attempts`
- `durationMillis`
- `output`
- `error`

所以 handoff 在可观测层是一等事件，不是普通工具调用的附带日志。

## 8. handoff lifecycle 的真实执行链

`executeSubAgent(...)` 可以拆成 7 个步骤。

### 8.1 先判断 `enabled`

如果 `policy.isEnabled() == false`，会直接走 `executeWithoutPolicy(...)`。

但这里要注意一个容易忽略的细节：

- `enabled=false` 不是完全绕过 wrapper
- 仍然会发 handoff events
- 仍然会调用 `executeOnce(...)`

而 `executeOnce(...)` 依然读取 `policy.getTimeoutMillis()`

所以“禁用 policy”更准确的语义是：

- 关闭 deny / filter / retry / onDenied / onError 这类治理分支
- 不是完全移除整个 handoff wrapper

### 8.2 计算下一层深度

深度是通过：

- `HandoffContext.currentDepth() + 1`

计算的。

真正执行时会用：

- `HandoffContext.runWithDepth(depth, ...)`

把深度写进一个 `ThreadLocal`。

所以 handoff depth 不是显式参数层层传，而是运行时上下文。

### 8.3 先做 admission control

`denyReason(...)` 会按顺序检查：

- `allowedTools`
- `deniedTools`
- `maxDepth`

注意这里检查的是：

- `toolName`

不是 subagent `name`。

因此 allow/deny 列表的配置对象，是暴露给模型的工具名，不是内部角色名。

### 8.4 再做输入过滤

如果配置了 `inputFilter`，会对 `AgentToolCall` 做一次改写。

这层适合做：

- 脱敏
- 长上下文裁剪
- 注入策略字段

而且如果 filter 返回 `null`，执行器会回退使用原始 call，不会把 handoff 直接吞掉。

### 8.5 执行尝试次数是 `maxRetries + 1`

真正的 attempts 计算是：

```java
int attempts = Math.max(1, policy.getMaxRetries() + 1);
```

所以：

- `maxRetries = 0` -> 实际尝试 1 次
- `maxRetries = 2` -> 实际尝试 3 次

这是典型的“retry count 不包含首次尝试”的语义。

### 8.6 timeout 是每次 attempt 的 timeout

`executeOnce(...)` 里如果 `timeoutMillis > 0`，会：

- 提交到内部 cached thread pool
- `future.get(timeoutMillis, TimeUnit.MILLISECONDS)`

因此 timeout 是单次 handoff attempt 的超时，不是整个多次 retry 总耗时上限。

### 8.7 成功、失败、fallback 都会发 `HANDOFF_END`

不管最终是：

- completed
- failed
- fallback

都会明确发结束事件，并写入对应 status。

这让 handoff 在 trace 里是闭环的。

## 9. Session 模式不是性能开关，而是状态语义

### 9.1 `NEW_SESSION`

每次执行都：

- `agent.newSession()`
- `session.run(...)`

特点是：

- 强隔离
- 无历史泄漏
- 对并发更友好

### 9.2 `REUSE_SESSION`

`StaticSubAgentRegistry` 会按 `toolName` 缓存 `AgentSession`，并且：

- `computeIfAbsent(...)`
- `synchronized(session)`

这意味着两个非常具体的语义：

1. memory 会跨多次 handoff 累积
2. 同一个 subagent tool 的并发调用会串行化

所以 `REUSE_SESSION` 不是“更高吞吐”，而是“更强连续性，但牺牲并发独立性”。

## 10. `HandoffPolicy` 默认值到底意味着什么

默认值如下：

| 字段 | 默认值 | 真正含义 |
| --- | --- | --- |
| `enabled` | `true` | handoff 默认受策略层管理 |
| `maxDepth` | `1` | 默认只允许 lead -> subagent 一层 |
| `maxRetries` | `0` | 默认不重试 |
| `timeoutMillis` | `0L` | 默认不做超时截断 |
| `allowedTools` | `null` | 默认不做 allow-list |
| `deniedTools` | `null` | 默认不做 deny-list |
| `onDenied` | `FAIL` | 默认拒绝即失败 |
| `onError` | `FAIL` | 默认异常即失败 |
| `inputFilter` | `null` | 默认不改写输入 |

这组默认值背后的策略倾向很清楚：

- SubAgent 默认可用
- 但默认不开放递归 handoff
- 也默认不自动吞错或自动降级

## 11. `FALLBACK_TO_PRIMARY` 的语义必须说清

当 `onDenied` 或 `onError` 设成 `FALLBACK_TO_PRIMARY` 时，执行器会：

```java
delegate.execute(call)
```

这意味着 fallback 的真实语义是：

- 把同一个 `AgentToolCall` 交回原始工具执行链

它不是：

- 让主 Agent 自己重思考一遍
- 或让 subagent 降级成普通文本回答

这要求一个前提：

- 你的原始 `delegate` 必须真的能处理这个同名工具调用

否则 fallback 配了也没有意义。

## 12. 一个很容易忽略的设计细节

`AgentBuilder` 在创建默认 `ToolUtilExecutor` 时，传入的 allowed tool names 来自：

- `baseToolRegistry`

而不是合并后的 subagent registry。

这正说明默认工具执行器不需要认识 subagent tools，因为：

- subagent tool 会先被 `SubAgentToolExecutor` 拦截
- 只有没命中的普通工具才走 delegate

这是一个很干净的分层设计。

## 13. 适合什么场景，不适合什么场景

适合：

- 想保留一个主 Agent 做全局决策
- 某些专项能力已经复杂到值得独立成 Agent
- 希望这些专项能力还能继续拥有自己的 prompt、memory、tooling

不适合：

- 需要显式团队协作
- 需要任务认领、消息广播、状态恢复
- 需要多个成员平等协作而不是主从委派

这类场景应该进入 Agent Teams。

## 14. 当前实现的几个真实限制

### 14.1 SubAgent 仍然走字符串输入输出协议

即使参数表面上是 JSON，真正喂给子代理的仍然是一个字符串 input。

因此它更像“结构化委派入口 + 文本任务体”，而不是强类型 RPC。

### 14.2 `REUSE_SESSION` 只按 `toolName` 复用

不是按用户、不是按任务、不是按 tenant。

所以如果你的系统需要更细粒度的 session 隔离，不能直接把默认 `REUSE_SESSION` 当成完整方案。

### 14.3 policy 的 allow/deny 是按工具名，不是按角色名

这要求你在设计工具名时保持稳定且有治理意义，否则后续策略配置会很混乱。

## 15. 推荐阅读源码入口

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentDefinition.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentSessionMode.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/StaticSubAgentRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/SubAgentToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffPolicy.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffContext.java`

## 16. 推荐验证用例

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/SubAgentParallelFallbackTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/HandoffPolicyTest.java`

## 17. 继续阅读

1. [Tools and Registry](/docs/agent/tools-and-registry)
2. [Agent Teams](/docs/agent/agent-teams)
3. [Trace 与可观测性](/docs/agent/trace-observability)
