# Teams

`Agent Teams` 解决的是多角色、多成员、长期协作的组织层问题。

如果 `SubAgent` 还是“主 Agent 调一个受控工具”，那么 `Teams` 已经进入“任务板 + 消息 + 状态流转 + 汇总”的团队协作模型。

## 1. 什么时候该升级到 Teams

以下情况通常说明单 Agent 或一次 handoff 已经不够：

- 任务本身需要多个角色长期分工
- 你需要显式任务板和依赖关系
- 你需要成员间消息流转
- 你需要把协作过程持久化、恢复、回放

一句话判断：

> 当问题变成“多个成员怎么协同交付”时，就该进入 `Teams`。

## 2. 真实代码路径

关键包：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team`

核心类：

- `AgentTeam`
- `AgentTeamBuilder`
- `AgentTeamMember`
- `AgentTeamTaskBoard`
- `AgentTeamMessageBus`
- `AgentTeamStateStore`
- `LlmAgentTeamPlanner`
- `LlmAgentTeamSynthesizer`
- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

## 3. 当前执行模型怎么理解

可以先把执行链压成下面这条：

```text
objective
  -> planner
  -> task board
  -> members execute tasks
  -> message bus / state store
  -> synthesizer
  -> final output
```

它和普通 Agent 最大的区别在于：

- 已经不只是 step loop
- 而是显式的协作过程管理

## 4. Builder 的关键心智

`AgentTeamBuilder` 至少要组织三类东西：

- 成员
- 规划器
- 汇总器

如果你没有手动指定 `planner` / `synthesizer`，当前实现会回退到：

- `plannerAgent`
- `synthesizerAgent`
- 再不行就回退到 `leadAgent`

这使得 team 既能快速起步，也能后续做模型分工优化。

## 5. 它的优势是什么

- 比“多个 prompt 手工拼接”更可解释
- 有任务状态和依赖，适合调试与恢复
- 有消息总线和状态存储，适合做平台级协作
- 能把 Lead、Planner、Member、Synthesizer 的职责拆清楚

这也是为什么 `Teams` 非常适合“要拿去讲架构”的场景。

## 6. 和 SubAgent 的边界再强调一次

- `SubAgent`：受控委派，主链仍是一条 Agent loop
- `Teams`：显式协作，存在任务板、成员状态、消息、汇总阶段

不要一开始就上 team。

通常顺序应该是：

1. 单 Agent
2. handoff
3. team

## 7. 什么时候值得引入持久化

如果你希望 team 不是一次性玩具，而是可恢复系统，通常就要考虑：

- `AgentTeamStateStore`
- `AgentTeamMessageBus`
- 快照恢复
- 任务板持久化

这时 team 已经不只是模型编排，而是平台能力。

## 8. 推荐下一步

1. [Teams API Reference](/docs/agent/orchestration/teams-api-reference)
2. [Trace](/docs/agent/observability/trace)
3. [Reference Core Classes](/docs/agent/reference-core-classes)
