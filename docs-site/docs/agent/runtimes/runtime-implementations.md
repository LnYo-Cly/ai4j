# Runtime Implementations

这一页回答一个核心问题：同样是 `Agent`，为什么要分成不同 runtime。

因为不同任务的“稳定中间表示”不一样。

- 有些任务更适合文本推理循环
- 有些任务更适合“先产代码再执行”
- 有些任务更适合“先规划、再取证、再汇总”

## 1. 当前主 runtime 地图

代码路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime`

主要实现：

- `ReActRuntime`
- `CodeActRuntime`
- `DeepResearchRuntime`
- `BaseAgentRuntime`

## 2. 三种主 runtime 怎么选

### `ReActRuntime`

适合：

- 通用问答
- 工具调用数量有限
- 任务主要靠模型逐步决定下一步

优点是简单、默认、接入成本最低。

### `CodeActRuntime`

适合：

- 工具链复杂
- 需要批量处理、结构化转换
- “写一段临时代码再运行”比反复文本思考更稳定

优点是把一部分复杂度转移给可执行代码。

### `DeepResearchRuntime`

适合：

- 研究型任务
- 先规划、再收集证据、再总结
- 你希望 runtime 明确体现“研究流程”

它更像“过程组织”而不是单步工具循环。

## 3. 选择时最实用的判断标准

可以先用这套简化判断：

- 先从 `ReAct` 起步
- 当任务复杂到文本 loop 不稳定时，再转 `CodeAct`
- 当任务的核心不是工具执行而是研究流程组织时，再看 `DeepResearch`

这比一上来就选“最强 runtime”更稳。

## 4. BaseAgentRuntime 在体系里的位置

`BaseAgentRuntime` 是默认语义骨架。

它负责：

- step loop
- maxSteps 限制
- stream/non-stream 模式
- tool call 归一化与执行
- parallel tool calls
- event 发布

`ReActRuntime` 基本沿用它。

这也是为什么理解 `BaseAgentRuntime`，就能更快理解整个 agent 执行主线。

## 5. runtime 选择不是架构终局

要注意，runtime 和 orchestration 是两层问题：

- runtime 决定“单个 Agent 怎么跑”
- orchestration 决定“多个阶段或多个成员怎么组织”

所以你完全可能：

- 单节点里用 `CodeAct`
- 整体流程外面再用 `StateGraph`
- 某一步 handoff 给 subagent

## 6. 推荐阅读顺序

1. [Minimal ReAct Agent](/docs/agent/runtimes/minimal-react-agent)
2. [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)
3. [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)
4. [StateGraph](/docs/agent/orchestration/stategraph)

如果你已经确定需要代码执行，下一页直接看 [CodeAct Runtime](/docs/agent/runtimes/codeact-runtime)。
