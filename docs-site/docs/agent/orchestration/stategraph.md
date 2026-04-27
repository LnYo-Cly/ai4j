# StateGraph

`StateGraph` 是 `Agent` 章节里最适合解释“显式编排”的入口。

## 1. 什么时候该看它

- 任务天然是节点流
- 有明确状态、分支、条件路由
- 你希望流程结构可以被调试和解释

## 2. 它和普通 Agent loop 的区别

- 普通 runtime 更偏模型按需决定下一步
- `StateGraph` 更偏你先把阶段和转移关系定义出来

如果你的任务已经像流程图，就不要用一个大 prompt 去硬模拟流程图。

## 3. 推荐下一步

1. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
2. [Agent Teams](/docs/agent/orchestration/teams)
3. [Trace](/docs/agent/observability/trace)
