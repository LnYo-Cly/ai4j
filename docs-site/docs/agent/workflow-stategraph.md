---
sidebar_position: 6
---

# Workflow 与 StateGraph

这一层解决的不是“再造一个 runtime”，而是把多个 Agent 节点串成一个显式流程。

如果 `ReActRuntime` 负责单个 Agent run 的循环，那么 `workflow` 包负责的是：

- 节点之间怎么接力
- 分支怎么选
- 什么时候回环
- 哪些共享状态不适合塞进单个 Agent memory

它和 LangGraph 在概念上相似，但当前实现明显更轻，边界也更窄。

## 1. 先抓住 5 个关键设计决策

### 1.1 Workflow 是 Agent 之上的编排层，不是 runtime 变体

`AgentRuntime` 仍然只负责单个 Agent 的一次执行。

`SequentialWorkflow` 和 `StateGraphWorkflow` 没有接入 `BaseAgentRuntime` 的主循环；它们只是围绕：

- `AgentNode`
- `AgentRequest`
- `AgentResult`
- `WorkflowContext`

做了一层外部编排。

所以 workflow 解决的是“多个节点怎么组织”，不是“单个节点内部怎么推理”。

### 1.2 节点之间默认只传 `outputText`

无论是 `SequentialWorkflow` 还是 `StateGraphWorkflow`，节点执行完之后，框架默认只会把：

- `lastResult.getOutputText()`

重新包成下一跳的 `AgentRequest.input`。

这意味着默认链路不会自动传递：

- `rawResponse`
- `toolCalls`
- `memoryItems`
- 业务结构化字段

如果你需要更丰富的跨节点状态，必须显式写到 `WorkflowContext.state`。

### 1.3 `WorkflowContext` 是 side channel，不是节点输入主通道

节点主输入仍然是 `AgentRequest`。

`WorkflowContext` 只是补充一块共享状态区，用来存：

- 路由结果
- 计数器
- 上一个节点 ID
- 临时业务字段

它不是 LangGraph 那种强类型全局 state object；当前就是一个 `Map<String, Object>`。

### 1.4 StateGraph 的边解析有固定优先级

`StateGraphWorkflow.resolveNext(...)` 的顺序不是随机的，而是：

1. 先遍历当前节点上的 `conditionalEdges`
2. 第一个返回非空 route 的 router 胜出
3. 如果配了 `routeMap`，再把 route key 映射成真正节点 ID
4. 如果 router 都没命中，再按注册顺序遍历 `transitions`
5. 第一条 condition 为空或 `matches(...) == true` 的 transition 胜出
6. 都没命中就返回 `null`，图直接结束

也就是说：

- `conditionalEdges` 优先级高于普通 `transition`
- 所有边都命中不了时，不会抛错，而是“正常结束”

### 1.5 `maxSteps` 只是死循环保险丝，不是成功条件

`StateGraphWorkflow` 默认 `maxSteps = 32`。

while 条件是：

```java
while (currentNodeId != null && steps < maxSteps)
```

一旦达到上限，执行会直接停止并返回当前 `lastResult`，不会自动抛出“超步数”异常。

所以它的语义是：

- 防止无限循环
- 但不会替你判断“业务流程是否真正完成”

## 2. 对象关系先看清

当前 `io.github.lnyocly.ai4j.agent.workflow` 里的核心对象很少：

```text
AgentWorkflow
  -> SequentialWorkflow
  -> StateGraphWorkflow

WorkflowAgent
  -> 持有 AgentWorkflow + AgentSession

AgentNode
  -> RuntimeAgentNode
  -> 你自己的自定义节点

WorkflowContext
  -> session
  -> state(Map<String, Object>)
```

最关键的职责分工是：

| 对象 | 真正职责 |
| --- | --- |
| `AgentWorkflow` | 定义 workflow 的统一运行接口 |
| `SequentialWorkflow` | 串行节点接力 |
| `StateGraphWorkflow` | 分支、条件、循环 |
| `WorkflowAgent` | 把 workflow 包成更像 Agent 的入口 |
| `AgentNode` | 单个节点的执行抽象 |
| `RuntimeAgentNode` | 把 `AgentSession` 直接包装成节点 |
| `WorkflowContext` | 节点之间共享状态 |

## 3. `WorkflowAgent` 和 `AgentSession` 的真实边界

很多人第一次看这个包，会误以为：

- `WorkflowAgent` 持有的 `session`
- 就是所有节点都会自动用的 session

源码不是这样。

`WorkflowAgent` 很薄：

```java
public AgentResult run(AgentRequest request) throws Exception {
    return workflow.run(session, request);
}
```

它只是把一个根 `AgentSession` 传给 workflow。

但节点到底用不用这份 session，要看节点实现。

例如内置 `RuntimeAgentNode`：

```java
public class RuntimeAgentNode implements AgentNode, WorkflowResultAware {
    private final AgentSession session;
}
```

它用的是自己构造时传入的 session，而不是 `WorkflowContext.session`。

这意味着当前 workflow 层存在两种 session 来源：

1. workflow 根 session
2. 每个 node 自己持有的 session

如果你不刻意统一，很容易出现：

- workflow 顶层传了一份 session
- 每个节点其实又各跑各的 session

这不是 bug，但必须心里有数。

## 4. `SequentialWorkflow` 的真实执行链

`SequentialWorkflow` 的实现非常直接，但恰恰因为太直接，边界也很明显。

### 4.1 它做了什么

执行链基本就是：

1. 新建 `WorkflowContext`
2. `current = request`
3. 依次执行每个 node
4. 如果 `lastResult.outputText != null`
5. 把它重新包成新的 `AgentRequest.input`
6. 交给下一个 node

也就是：

```text
nodeA.outputText -> nodeB.input
nodeB.outputText -> nodeC.input
```

### 4.2 它没做什么

它不会自动：

- 合并多个节点输出
- 传递结构化对象
- 做条件分支
- 检查节点是否重复执行
- 保存完整链路历史

如果你要保留中间结构，做法不是期待框架替你传，而是自己写入 `WorkflowContext`。

### 4.3 最容易忽略的地方

只要上一个节点返回了 `outputText`，原始 request 就会被覆盖。

所以如果下一节点既需要：

- 原始用户输入
- 上一节点输出

那你不能只靠默认接力，必须自己把原始输入写进 `WorkflowContext.state`。

## 5. `StateGraphWorkflow` 到底怎么推进

`StateGraphWorkflow` 的主循环也很短，但逻辑比 `SequentialWorkflow` 多一层“下一跳解析”。

### 5.1 主循环骨架

核心流程如下：

1. 校验 `startNodeId`
2. `currentNodeId = startNodeId`
3. `currentRequest = request`
4. while `currentNodeId != null && steps < maxSteps`
5. 找到当前 node
6. 把 `currentNodeId`、`currentRequest` 写进 `WorkflowContext`
7. 执行 node
8. 把 `lastResult`、`lastNodeId` 写进 `WorkflowContext`
9. 如果 `lastResult.outputText != null`，重建下一跳 request
10. `resolveNext(...)` 计算下一节点
11. `steps += 1`

真正的图式语义，其实都在第 10 步。

### 5.2 `addEdge` 和 `addTransition` 的关系

这两个 API 没有两套底层实现。

`addEdge(from, to)` 只是：

```java
return addTransition(from, to, null);
```

也就是说：

- `addEdge` 代表无条件边
- `addTransition` 代表可带条件边

底层都进同一份 `transitions` 列表，按注册顺序匹配。

### 5.3 `addConditionalEdges` 不是“多条条件边语法糖”

`addConditionalEdges` 走的是另一条机制。

它不是把 route 拆成多条 `StateTransition`，而是保存一条：

- `from`
- `StateRouter`
- `routeMap`

运行时先调用 `router.route(context, request, result)`。

然后：

- 如果没配 `routeMap`，就把 router 返回值直接当节点 ID
- 如果配了 `routeMap`，就先查映射，再拿到真正节点 ID

所以 `routeMap` 的作用不是“描述图”，而是“把业务路由键翻译成节点 ID”。

## 6. 边解析顺序决定了很多行为

这是最值得直接读源码的地方。

### 6.1 Conditional edges 先匹配

只要当前 node 配了 `conditionalEdges`，`resolveNext(...)` 会先遍历它们。

一旦某个 router 返回非空 route，且 routeMap 也能解析到节点，就立即返回。

后面的 transition 根本不会看。

### 6.2 普通 transition 后匹配

只有当 conditional route 都没命中时，才轮到 `transitions`。

而 `transitions` 本身也是按添加顺序匹配第一条命中项。

所以当你写：

```java
.addTransition("route", "incident", incidentCondition)
.addTransition("route", "general", null)
```

这里第二条 `null` 条件本质上就是 fallback。

### 6.3 没有下一跳时是静默结束

如果：

- 没有 conditional route
- 也没有任何 transition 命中

`resolveNext(...)` 最终返回 `null`，然后 while 循环结束。

这意味着图结束的两种方式是：

1. 你显式路由到 `null`
2. 你根本没配出下一跳

框架不会替你区分这两者。

## 7. WorkflowContext 真正适合放什么

`WorkflowContext` 当前只有三块东西：

- `session`
- `state`
- `eventPublisher`

但要注意：

- `eventPublisher` 目前在 workflow 主链路里基本没有被用起来
- 真正常用的是 `state`

更准确地说，`WorkflowContext.state` 适合承载的是：

- 路由决定
- 计数器
- 节点间共享的业务元数据
- 需要跨节点保留但不想塞进 prompt 的对象

例如：

- `route`
- `count`
- `riskLevel`
- `city`
- `draftJson`

它不适合变成一个“什么都往里塞的大包”，否则最后会很难判断状态来自哪一层。

## 8. 两个最常见模式

### 8.1 顺序加工链

适合：

- 草稿 -> 审校 -> 格式化
- 抽取 -> 分类 -> 输出

示例：

```java
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new RuntimeAgentNode(draftAgent.newSession()))
        .addNode(new RuntimeAgentNode(formatAgent.newSession()));
```

这里的真实语义不是“两个 Agent 协作”，而是：

- 第一个 Agent 的 `outputText`
- 被直接作为第二个 Agent 的 `input`

### 8.2 路由后收敛

适合：

- 先识别类型
- 再分支处理
- 最后统一格式化

示例：

```java
StateGraphWorkflow workflow = new StateGraphWorkflow()
        .addNode("route", new RoutingNode(router.newSession()))
        .addNode("weather", new RuntimeAgentNode(weather.newSession()))
        .addNode("generic", new RuntimeAgentNode(generic.newSession()))
        .addNode("final", new RuntimeAgentNode(formatter.newSession()))
        .start("route")
        .addConditionalEdges("route", (ctx, req, res) -> String.valueOf(ctx.get("route")))
        .addEdge("weather", "final")
        .addEdge("generic", "final");
```

这里的关键不是分支本身，而是：

- route 决定放在 `WorkflowContext`
- 业务输出仍通过 `outputText -> input` 这条默认接力线继续流

## 9. Stream 模式现在的真实边界

这部分如果不读源码，很容易误判。

### 9.1 `AgentNode.executeStream(...)` 的默认实现非常薄

默认实现只是：

1. 直接调用同步 `execute(...)`
2. 然后 `listener.onEvent(context.createResultEvent(result))`

也就是说，默认 stream node 并没有独立的节点级事件模型，只是把最终结果包装成一个 `FINAL_OUTPUT` 事件。

### 9.2 `RuntimeAgentNode` 的 stream 路径不会自动回填 `lastResult`

`RuntimeAgentNode.executeStream(...)` 只做：

```java
session.runStream(request, listener);
```

它没有像同步 `execute(...)` 一样给 `lastResult` 赋值。

而 `SequentialWorkflow` / `StateGraphWorkflow` 在 stream 模式下，只有 node 同时实现 `WorkflowResultAware` 并能返回最新 `lastResult` 时，才有机会继续把结果传给下一跳。

这意味着当前内置 `RuntimeAgentNode` 的 stream 语义存在一个明显限制：

- 你能拿到流式事件
- 但默认不保证节点输出还能像同步模式那样稳定回灌到 workflow 下一跳

如果你要认真做流式多节点编排，这一层还需要额外封装。

### 9.3 Workflow 本身没有节点级 trace 体系

当前 workflow 包没有像 `BaseAgentRuntime` 那样发布：

- `STEP_START`
- `MODEL_REQUEST`
- `TOOL_CALL`

这类结构化 runtime 事件。

所以 workflow 的 stream 更准确地说是：

- 把内部 node 的 listener 透传出来
- 不是自己再构建一套 workflow trace 模型

## 10. 默认值、失败路径和边界

### 10.1 没有 `start(...)` 会直接抛错

`StateGraphWorkflow` 如果没设置起点，`run(...)` 会直接抛：

```text
IllegalStateException: start node is required
```

### 10.2 节点 ID 不存在会直接抛错

运行时如果 `nodes.get(currentNodeId) == null`，也会直接抛：

```text
IllegalStateException: node not found: <id>
```

### 10.3 达到 `maxSteps` 时不会报错，只会提前结束

这是当前 StateGraph 最需要主动记住的失败语义之一。

如果你把 `maxSteps` 当成功保障，就会误判“流程完成了”，但其实只是被保险丝截断了。

### 10.4 节点输出为空时，不会自动重写下一跳 request

只有 `lastResult != null && lastResult.getOutputText() != null` 时，才会生成新的 `AgentRequest`。

否则下一节点会继续收到上一轮的 `currentRequest`。

这在“只更新状态、不更新文本”的节点里很常见，也很容易被忽略。

### 10.5 Workflow 默认不会帮你隔离节点 memory

如果多个 `RuntimeAgentNode` 复用了同一个 `AgentSession`，那它们就共享同一份 Agent memory。

如果每个节点都 `newSession()`，那它们默认就是隔离的。

当前框架不会替你做统一策略。

## 11. 什么时候该用 Workflow，什么时候不该

### 适合用 Workflow

- 节点结构需要显式表达
- 你要把“路由决定”和“执行节点”拆开
- 某些共享状态不适合进单个 Agent prompt
- 你想把多个 Agent 串成一条稳定管线

### 不适合先上 Workflow

- 只是单个 Agent 的工具循环
- 只是想要更强工具编排，这通常先看 [CodeAct Runtime](/docs/agent/codeact-runtime)
- 只是要把一个能力委派给另一个 Agent，这通常先看 [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
- 需要任务板、角色和消息总线，这通常应该看 [Agent Teams](/docs/agent/agent-teams)

## 12. 推荐阅读源码顺序

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowAgent.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/AgentNode.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/SequentialWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/StateGraphWorkflow.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow/WorkflowContext.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentWorkflowTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentWorkflowUsageTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/StateGraphWorkflowTest.java`

## 13. 继续阅读

1. [Agent Architecture](/docs/agent/architecture)
2. [Minimal ReAct Agent](/docs/agent/minimal-react-agent)
3. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
4. [Agent Teams](/docs/agent/agent-teams)
