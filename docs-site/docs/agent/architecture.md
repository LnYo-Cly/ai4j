# Agent Architecture

`Agent` 这一层真正解决的不是“怎么调用模型”，而是“怎么把模型、工具、记忆和编排组织成一个能持续运行的 runtime”。

## 1. 先记住四层结构

可以先把 `Agent` 看成四层：

- model client：模型请求从哪里发出，用 `chat` 还是 `responses`
- runtime：`ReAct`、`CodeAct`、`DeepResearch` 这些执行策略怎么跑
- tool surface：工具如何注册、暴露、执行、审计
- state layer：memory、workflow、subagent、team、trace 怎么承接长期任务

这四层叠起来，才是 AI4J 的 `Agent runtime`，而不是单个 builder。

## 2. 模块路径怎么读

源码主路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

通常可以按下面顺序读源码：

- `model`：先看模型接入边界
- `runtime`：再看运行时策略
- `tool`：理解工具注册与执行
- `memory`：看状态与压缩
- `workflow` / `subagent` / `team`：看编排能力
- `trace`：最后看观测和调试

## 3. 这页和相邻页面怎么分工

- `overview` 负责回答“这一章讲什么、边界在哪里”
- `architecture` 负责回答“这套 runtime 是按什么层次组织的”
- `quickstart` 负责最短可运行路径
- `tools-and-registry` 负责工具面
- `runtimes/*` 负责不同 runtime 策略
- `orchestration/*` 负责显式编排
- `observability/trace` 负责观测

## 4. 阅读顺序建议

如果你是第一次读 `Agent`，建议直接按当前 canonical 主线走：

1. [Agent 总览](/docs/agent/overview)
2. [Agent Quickstart](/docs/agent/quickstart)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
5. [StateGraph](/docs/agent/orchestration/stategraph)
6. [Trace](/docs/agent/observability/trace)
7. [Reference Core Classes](/docs/agent/reference-core-classes)
