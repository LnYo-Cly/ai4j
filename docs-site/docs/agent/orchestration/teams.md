# Agent Teams

`Agent Teams` 解决的是多角色、多成员、长期协作的组织层问题。

## 1. 什么时候该升级到 Teams

- 已经不是单 Agent 或一次 handoff 能解决
- 需要成员角色分工
- 需要任务板、状态流转、成员消息

## 2. 它和前面几层的关系

- `ReAct` / `CodeAct` 解决单个 runtime 怎么跑
- `StateGraph` 解决显式流程怎么编排
- `Teams` 解决多个成员怎么协同交付

它是更高一级的组织抽象，不建议一上来就用。

## 3. 推荐下一步

1. [Agent Teams API Reference](/docs/agent/orchestration/teams-api-reference)
2. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
3. [Trace](/docs/agent/observability/trace)
