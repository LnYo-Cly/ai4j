# StateGraph

`StateGraphWorkflow` 是 `ai4j-agent` 中“显式编排”最直接的入口。

单个 Agent runtime 适合模型自己决定下一步；`StateGraph` 适合你先把节点、状态转移、条件路由明确写出来，再让每个节点去执行自己的局部逻辑。

如果你的任务已经更像流程图而不是自由推理，这页比继续强化单个 prompt 更重要。

## 1. 它解决什么问题

下面这些任务通常已经不适合只靠单个 Agent loop：

- 有明确阶段划分
- 有条件分支
- 有回路或重试
- 某些节点必须固定执行，不能让模型自由跳过
- 你希望流程结构本身可审计、可回放、可解释

这类问题的核心不再是“模型下一步想做什么”，而是“系统规定了哪些节点、边和状态规则”。

## 2. 代码上的真实位置

主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow`

关键对象：

- `StateGraphWorkflow`
- `WorkflowContext`
- `AgentNode`
- `RuntimeAgentNode`
- `StateTransition`
- `StateCondition`
- `StateRouter`
- `WorkflowAgent`

## 3. `StateGraphWorkflow` 的核心模型

从源码看，`StateGraphWorkflow` 自己维护了三类结构：

- `nodes`
- `transitions`
- `conditionalEdges`

以及两项关键控制参数：

- `startNodeId`
- `maxSteps`

默认 `maxSteps` 是 `32`。这很重要，因为它是图执行防止无界循环的第一道保护，而不是可有可无的装饰参数。

## 4. 和普通 Agent loop 的边界

### 4.1 普通 Agent runtime 解决什么

- 单个 Agent 在一次 run 中如何推进
- 模型何时调用工具
- 工具结果怎样回灌

### 4.2 `StateGraph` 解决什么

- 节点之间如何跳转
- 状态如何决定下一跳
- 哪些节点必须显式存在

这两层不是互斥关系。最常见的组合方式其实是：

- 单个图节点内部仍然运行一个 Agent
- 整体外层再用 `StateGraphWorkflow` 把节点串成显式流程

## 5. 执行流程

`StateGraphWorkflow.executeGraph(...)` 的主链可以压成下面这条：

```text
startNodeId
  -> 找到当前节点
  -> node.execute(...) / node.executeStream(...)
  -> 将结果写入 WorkflowContext
  -> 用 lastResult.outputText 生成下一轮 currentRequest
  -> resolveNext(...)
  -> 进入下一节点
```

这里有两个实现细节值得特别写清楚。

### 5.1 `currentRequest` 的默认传播规则

如果 `lastResult.getOutputText()` 非空，工作流会自动把它变成下一节点的输入：

```java
currentRequest = AgentRequest.builder().input(lastResult.getOutputText()).build();
```

这意味着默认链路是“上一步输出文本，作为下一步输入文本”。

这个规则很方便，但也意味着：

- 如果你需要传更复杂的结构化状态，不应只依赖 `outputText`
- 应该把更宽的状态放进 `WorkflowContext.state`

### 5.2 路由优先级

`resolveNext(...)` 的顺序是：

1. 先检查 `conditionalEdges`
2. 再检查普通 `transitions`

也就是说：

> 条件路由优先于固定边。

如果文档不写清这一点，复杂图上的“为什么走了这条边”会很难排查。

## 6. 核心 API 的真实语义

### 6.1 `addNode(nodeId, node)`

注册节点。如果 `nodeId` 为空或 `node` 为空，当前实现会直接忽略，而不是抛异常。

### 6.2 `start(nodeId)`

声明起始节点。若未设置，运行时会抛：

- `IllegalStateException("start node is required")`

### 6.3 `addEdge(from, to)` / `addTransition(from, to, condition)`

定义固定跳转或带条件跳转。

适用场景：

- 固定流转
- 简单条件判断

### 6.4 `addConditionalEdges(from, router, routeMap)`

定义“先算路由 key，再映射到节点”的方式。

这更适合：

- 多分支节点
- 按状态路由
- 不想把所有条件都写成多条 `StateCondition`

### 6.5 `maxSteps(int)`

限制图执行步数。它是防止死循环的必要保护，尤其在条件路由和回路共存时应显式配置，而不是依赖默认值。

## 7. `WorkflowContext` 不是普通 Map

源码：

- `workflow/WorkflowContext`

它至少包含：

- `session`
- `state`
- `eventPublisher`

并且在执行过程中，`StateGraphWorkflow` 还会写入这些动态键：

- `currentNodeId`
- `currentRequest`
- `lastResult`
- `lastNodeId`

因此 `WorkflowContext` 不是单纯的参数传递袋，而是图级执行上下文。

## 8. 节点如何组织

### 8.1 `AgentNode`

节点抽象，决定单个图节点如何执行。

### 8.2 `RuntimeAgentNode`

适合把一个已经配置好的 `Agent` 嵌入到图节点中。它是“Agent runtime”和“StateGraph orchestrator”之间最典型的桥接器。

### 8.3 设计建议

当节点内部逻辑主要是：

- 模型推理
- 工具调用
- 局部任务闭环

就用 Agent 节点。

当节点内部逻辑主要是：

- 纯 Java 业务判断
- 状态整理
- 外部系统调用

可以考虑自定义更轻的节点实现，而不是每个节点都上 Agent。

## 9. 最小心智模型

一个常见的业务图大致是：

```text
input
  -> route
  -> branchA / branchB
  -> enrich
  -> format
  -> final
```

对应思路是：

- `route` 决定走哪条分支
- 分支节点内部可以各自运行一个 Agent
- `format` 负责统一收口

这类结构如果强塞进单个大 prompt，通常会让“结构控制”和“模型推理”互相污染。

## 10. 它的优势到底是什么

- 流程结构显式，不需要靠 prompt 暗示步骤
- 分支和回路的定位更直接
- 节点级别可以单独调试和复用
- 更适合把 Agent、Tool、RAG、纯业务节点混合组织

## 11. 当前实现的限制

### 11.1 默认状态传播偏文本

默认会用 `lastResult.outputText` 生成下一步 `AgentRequest`。对复杂结构化状态来说，这个默认值通常不够，需要你自己把关键状态放进 `WorkflowContext.state`。

### 11.2 没有内建复杂图分析

当前实现不会自动帮你检查：

- 不可达节点
- 环路风险
- 多出口冲突

这些仍需要设计时自己把控。

### 11.3 `maxSteps` 很重要

图中一旦存在自循环或条件回路，`maxSteps` 就是防护边界。不要把它当成可选优化项。

## 12. 什么时候不要先上它

- 任务还很小，只需要单 Agent + 少量工具
- 流程并不稳定，阶段划分还在频繁变化
- 你还没跑稳最小 runtime

这时先把单 Agent 跑稳，通常比一开始就画图更有效。

## 13. 继续阅读

1. [Runtime Implementations](/docs/agent/runtime-implementations)
2. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Agent Teams](/docs/agent/agent-teams)
4. [Trace Observability](/docs/agent/trace-observability)
