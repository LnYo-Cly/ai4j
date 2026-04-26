---
sidebar_position: 6
---

# Workflow 与 StateGraph（顺序 / 分支 / 循环）

你之前问到：

- `addTransition` 和 `addEdge` 是不是一回事？
- `addConditionalEdges` 怎么和普通边一起用？
- `Map.of("weather", "weather", "generic", "generic")` 到底是什么？

这页按源码结构讲清楚。

## 1. 先看类关系（最重要）

核心类在 `io.github.lnyocly.ai4j.agent.workflow`：

- `AgentWorkflow`：工作流接口
- `WorkflowAgent`：工作流执行入口
- `WorkflowContext`：工作流状态容器
- `AgentNode`：节点接口
- `RuntimeAgentNode`：把 `AgentSession` 包成节点
- `SequentialWorkflow`：线性流程
- `StateGraphWorkflow`：图式流程（支持分支、循环）
- `StateRouter/StateCondition/StateTransition`：路由与边规则

## 2. SequentialWorkflow（线性场景）

适合 A -> B -> C 的固定流程。

关键行为：

- 每个节点执行后，若 `result.outputText != null`，会作为下一节点的输入。
- 流式模式下，如果节点实现 `WorkflowResultAware`，可回读 `lastResult`。

示例：

```java
SequentialWorkflow workflow = new SequentialWorkflow()
        .addNode(new RuntimeAgentNode(agentA.newSession()))
        .addNode(new RuntimeAgentNode(agentB.newSession()));

WorkflowAgent runner = new WorkflowAgent(workflow, agentA.newSession());
AgentResult result = runner.run(AgentRequest.builder().input("task").build());
```

## 3. StateGraphWorkflow（图式场景）

当你需要“条件路由、分支、循环”时，使用它。

## 3.1 关键 API

- `addNode(nodeId, node)`：注册节点
- `start(nodeId)`：设置起点
- `maxSteps(int)`：防止死循环（默认 32）
- `addEdge(from, to)`：固定边
- `addTransition(from, to, condition)`：带条件的普通边
- `addConditionalEdges(from, router)`：由路由器直接决定下一节点 ID
- `addConditionalEdges(from, router, routeMap)`：由路由键映射到节点 ID

## 3.2 `addEdge` 和 `addTransition` 的关系

是的，`addEdge(from, to)` 本质就是 `addTransition(from, to, null)` 的语法糖。

所以你说“只保留 addEdge 能不能理解更好”是成立的：

- 没有条件 -> `addEdge`
- 有条件 -> `addTransition(..., condition)`

## 3.3 `addConditionalEdges` 什么时候用

当“下一跳由当前状态动态决定”时用它。

典型路由：

- 输入是天气问题 -> `weather`
- 输入是通用问题 -> `generic`

## 3.4 `routeMap` 是什么

`routeMap` 是“路由键 -> 实际节点 ID”映射。

```java
.addConditionalEdges(
    "decide",
    (ctx, req, res) -> String.valueOf(ctx.get("routeKey")),
    Map.of(
        "ROUTE_WEATHER", "weather",
        "ROUTE_GENERIC", "generic"
    )
)
```

如果 router 已返回真实节点 ID（如 `weather/generic`），可不传 `routeMap`。

## 4. 一套完整分支编排示例

下面就是你项目里 `StateGraphWorkflowTest#test_state_graph_with_agents` 的同类结构：

```java
StateGraphWorkflow workflow = new StateGraphWorkflow()
        .addNode("decide", new RoutingAgentNode(router.newSession()))
        .addNode("weather", new RuntimeAgentNode(weather.newSession()))
        .addNode("generic", new RuntimeAgentNode(generic.newSession()))
        .addNode("format", new FormatNode(format.newSession()))
        .start("decide")
        .addConditionalEdges("decide", (ctx, req, res) -> String.valueOf(ctx.get("route")))
        .addEdge("weather", "format")
        .addEdge("generic", "format");
```

执行语义：

1. `decide` 节点把 `ctx.route` 写成 `weather/generic`
2. 条件边根据 `route` 决定分支
3. 分支节点执行后统一流向 `format`

## 5. 循环写法（LangGraph 风格）

你可以路由回自己：

```java
.addNode("loop", loopNode)
.addNode("done", doneNode)
.start("loop")
.maxSteps(10)
.addConditionalEdges("loop", (ctx, req, res) -> {
    Integer count = (Integer) ctx.get("count");
    return count != null && count < 3 ? "loop" : "done";
});
```

这就是典型状态图模式：同一节点可重复执行，直到状态满足退出条件。

## 6. `WorkflowContext` 里都放什么

建议约定这些 key：

- `route`：路由结果
- `lastNodeId`：上一个节点
- `lastResult`：上一步输出
- `currentNodeId/currentRequest`：当前执行态

你也可以放业务字段：

- `retryCount`
- `city`
- `riskLevel`

## 7. 如何观测每个节点状态

最简单做法：包装一个 `NamedNode`，在 `execute()` 里打印：

- `NODE START: <name>`
- `NODE END: <name> | status=OK/ERROR`

你的天气 workflow 测试已经这么做了，排障体验会明显更好。

## 8. 常见设计误区

1. 只用条件边，不设置 `maxSteps`，容易循环失控。
2. router 返回业务键，但忘了 routeMap 映射，导致找不到节点。
3. 路由节点和执行节点耦合太深，后续难扩展。
4. 节点直接共享可变对象，导致并发场景状态污染。

## 9. 与 LangGraph 思想的对应

AI4J 当前 StateGraph 是轻量 Java 实现，核心思想对齐：

- 节点（Node）
- 边（Edge）
- 路由（Router）
- 状态（State）

同样支持：

- 顺序
- 分支
- 循环

## 10. 对应测试

- `StateGraphWorkflowTest#test_branching_route`
- `StateGraphWorkflowTest#test_loop_route`
- `StateGraphWorkflowTest#test_state_graph_with_agents`
- `WeatherAgentWorkflowTest`

建议你直接从这些测试复制骨架改业务字段，最快落地。
