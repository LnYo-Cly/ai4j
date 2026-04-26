# Runtime Implementations

这一页负责回答一个核心问题：同样是 `Agent`，为什么会有不同 runtime。

## 1. 三类常见 runtime

- `ReAct`：最通用，适合大多数文本任务与工具调用
- `CodeAct`：适合代码驱动、多工具批处理、复杂结构化任务
- `DeepResearch`：适合先规划、再取证、再汇总的研究型任务

## 2. 怎么选

可以用一个最简单的判断标准：

- 先从 `ReAct` 起步
- 需要代码执行或更强结构化处理时转 `CodeAct`
- 需要显式研究流程和证据收敛时再看研究型 runtime

## 3. 推荐连读

1. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
2. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
3. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
4. [StateGraph](/docs/agent/orchestration/stategraph)
