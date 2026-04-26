# Subagent Handoff

`Subagent handoff` 关注的是主 Agent 如何把局部任务委派出去，同时保留边界和回收控制权。

## 1. 适合什么场景

- 主 Agent 要保持全局调度
- 某些子任务需要专门角色处理
- 你需要 handoff policy，而不是完全放任并发协作

## 2. 不要和 Teams 混

- `Subagent` 偏受控委派
- `Teams` 偏多角色长期协作

两者都会拆分任务，但组织方式完全不同。

## 3. 推荐下一步

1. [Agent Teams](/docs/agent/orchestration/teams)
2. [StateGraph](/docs/agent/orchestration/stategraph)
3. [Reference Core Classes](/docs/agent/reference-core-classes)
