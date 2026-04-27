# Subagent Handoff

`Subagent handoff` 解决的是：主 Agent 如何把局部任务委派出去，同时仍然保留边界、治理和回收控制权。

## 1. 什么时候该用它

下面这些场景很适合 handoff：

- 主 Agent 负责总控，但某些任务需要专门角色处理
- 你想把“检索、分析、格式化、审查”拆成受控委派
- 你需要的是主从式 delegation，而不是长期团队协作

简单理解：

- `SubAgent` 偏“受控委派”
- `Teams` 偏“多成员协同”

## 2. 真实代码路径

关键包：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent`

核心类：

- `SubAgentDefinition`
- `SubAgentRegistry`
- `StaticSubAgentRegistry`
- `SubAgentToolExecutor`
- `SubAgentSessionMode`
- `HandoffPolicy`
- `HandoffFailureAction`

## 3. 子代理在系统里是怎么出现的

在 AI4J 里，subagent 不是特殊“魔法通道”，而是被包装成 tool surface。

主链路是：

1. 定义 `SubAgentDefinition`
2. `StaticSubAgentRegistry` 把它转换成可见工具
3. `AgentBuilder` 用 `CompositeToolRegistry` 把基础工具和 subagent tools 合并
4. `SubAgentToolExecutor` 在执行侧识别“这个 tool call 实际上是 handoff”

所以 handoff 之所以自然，是因为它复用了原有工具调用语义。

## 4. `HandoffPolicy` 真正控制什么

`HandoffPolicy` 是这个能力能不能工程化的关键。

当前最重要的字段有：

- `enabled`
- `maxDepth`
- `maxRetries`
- `timeoutMillis`
- `allowedTools`
- `deniedTools`
- `onDenied`
- `onError`
- `inputFilter`

它解决的是：

- 能不能委派
- 最多委派几层
- 失败要不要重试
- 超时怎么办
- 拒绝或报错后是否回退

## 5. session mode 怎么选

### `NEW_SESSION`

适合：

- 每次 handoff 独立上下文
- 隔离优先
- 并发更安全

### `REUSE_SESSION`

适合：

- 希望某个子代理有持续记忆
- 同类任务多次委派给同一专长成员

这两个模式本质上是在“隔离性”和“连续性”之间做权衡。

## 6. 为什么这层比“多写几个工具”更强

因为普通工具只返回结果，不承担局部推理。

而 subagent 可以：

- 拥有自己的 model client
- 拥有自己的 memory
- 拥有自己的 system prompt 和 instructions
- 在局部任务内继续进行多轮推理

这就是它比“函数调用拼接”更适合复杂委派任务的原因。

## 7. 和 Teams 的边界

- `SubAgent`：主 Agent 仍然是唯一总控
- `Teams`：会出现任务板、消息总线、成员状态、协作轮次

如果你的需求只是“主 Agent 偶尔把局部任务交给专门角色”，先用 handoff。
如果你已经进入长期多角色协同，再看 team。

## 8. 推荐下一步

1. [Teams](/docs/agent/orchestration/teams)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Trace](/docs/agent/observability/trace)
