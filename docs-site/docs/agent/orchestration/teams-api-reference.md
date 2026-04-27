# Teams API Reference

这页不是再讲“为什么需要 team”，而是帮你在真正落代码时，快速建立 `Agent Teams` 的对象地图。

## 1. Builder 与运行入口

- `AgentTeam`
- `AgentTeamBuilder`
- `AgentTeamControl`
- `AgentTeamResult`

这组类回答的是：

- team 怎么被构建
- team 怎么运行
- team 最终交付什么结果

## 2. 成员与角色

- `AgentTeamMember`
- `AgentTeamMemberSnapshot`
- `AgentTeamMemberResult`
- `AgentTeamOptions`

这组类回答的是：

- 成员是谁
- 成员能力边界怎么描述
- 成员执行结果如何表达
- 运行期允许哪些 team 行为

## 3. 任务板与状态

- `AgentTeamTask`
- `AgentTeamTaskBoard`
- `AgentTeamTaskState`
- `AgentTeamTaskStatus`
- `AgentTeamPlan`
- `AgentTeamPlanParser`

这组类回答的是：

- planner 产出的任务怎么被规范化
- 任务依赖怎么表示
- 当前状态如何流转
- READY / IN_PROGRESS / COMPLETED / FAILED 如何被记录

## 4. 协作消息与持久化

- `AgentTeamMessage`
- `AgentTeamMessageBus`
- `InMemoryAgentTeamMessageBus`
- `FileAgentTeamMessageBus`
- `AgentTeamState`
- `AgentTeamStateStore`
- `InMemoryAgentTeamStateStore`
- `FileAgentTeamStateStore`

这组类回答的是：

- 成员之间如何传递消息
- team 状态如何快照和恢复
- 内存模式和文件模式的持久化边界在哪里

## 5. 规划与汇总

- `AgentTeamPlanner`
- `LlmAgentTeamPlanner`
- `AgentTeamSynthesizer`
- `LlmAgentTeamSynthesizer`
- `AgentTeamPlanApproval`

这组类回答的是：

- objective 怎么被拆成任务
- 最终结果怎么综合
- 规划前是否需要显式 approval

## 6. Hooks 与工具面

- `AgentTeamHook`
- `AgentTeamEventHook`
- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`
- `AgentTeamAgentRuntime`

这组类回答的是：

- team 事件如何插入自定义逻辑
- team 协作如何暴露成工具面
- team 如何和更大的 Agent runtime 体系相接

## 7. 推荐阅读方式

如果你是第一次真正落 team 代码，建议按下面顺序读：

1. `AgentTeamBuilder`
2. `AgentTeam`
3. `AgentTeamTaskBoard`
4. `AgentTeamMessageBus`
5. `AgentTeamStateStore`
6. `LlmAgentTeamPlanner`
7. `LlmAgentTeamSynthesizer`

这样会先建立主执行链，再进入细节对象。

## 8. 这页之后看什么

- 想回到能力定位：看 [Teams](/docs/agent/orchestration/teams)
- 想看过程观测：看 [Trace](/docs/agent/observability/trace)
- 想看更完整类索引：看 [Reference Core Classes](/docs/agent/reference-core-classes)
