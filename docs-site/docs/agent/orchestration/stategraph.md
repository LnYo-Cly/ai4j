# StateGraph

`StateGraph` 是 `Agent` 体系里最适合解释“显式编排”的入口。

如果你已经发现任务更像流程图，而不是让一个大模型自由决定所有下一步，那么就该进入这一页。

## 1. 它解决什么问题

`StateGraph` 适合下面这些任务：

- 有明确节点和阶段
- 有条件分支
- 有循环或重试
- 你希望流程结构本身可调试、可解释、可回放

这类任务如果还强行塞进单个 prompt，通常会越来越难维护。

## 2. 真实代码路径

关键包：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/workflow`

核心类：

- `AgentWorkflow`
- `AgentNode`
- `RuntimeAgentNode`
- `SequentialWorkflow`
- `StateGraphWorkflow`
- `WorkflowAgent`
- `WorkflowContext`

## 3. 它和普通 Agent loop 的边界

- 普通 runtime 更偏“模型按需决定下一步”
- `StateGraph` 更偏“你先把阶段、边和状态转移定义出来”

这两层不是互斥关系。

最常见的组合方式是：

- 单个节点内部仍然跑一个 Agent
- 整体外面再用 `StateGraphWorkflow` 串成明确流程

## 4. 关键 API 应该怎么理解

- `addNode(nodeId, node)`：注册节点
- `start(nodeId)`：声明起点
- `addEdge(from, to)`：固定跳转
- `addConditionalEdges(from, router)`：按路由结果决定下一跳
- `maxSteps(int)`：限制图执行步数，防止死循环

简单记忆：

- 结构稳定、固定流转：用 `addEdge`
- 下一跳依赖状态判断：用 `addConditionalEdges`

## 5. 最小心智模型

```text
input
  -> routing node
  -> branch node A / branch node B
  -> format node
  -> final output
```

这就是你最常见的业务工作流形态。

## 6. 为什么它值得宣传

- 它比“巨型 prompt 模拟流程图”更稳定
- 任务结构清楚，方便给面试或架构评审解释
- 节点、边、状态可以分开定位问题
- 很适合把 `Agent`、`Tool`、`RAG` 组合成可复用流程

## 7. 什么时候不要先上它

- 任务还很小，只是单 Agent + 少量工具调用
- 你还没跑稳最小 runtime
- 流程其实并不稳定，硬画图只会增加维护成本

先用最小 ReAct 跑通，再决定是否需要显式编排，通常更稳。

## 8. 推荐下一步

1. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
2. [Teams](/docs/agent/orchestration/teams)
3. [Trace](/docs/agent/observability/trace)

如果你想进一步看“主 Agent 如何把局部任务委派出去”，下一页直接看 [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)。
