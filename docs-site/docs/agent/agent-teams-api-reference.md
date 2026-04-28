---
sidebar_position: 15
---

# Agent Teams API Reference

这页不是脚本导出的类表，而是 `ai4j-agent` Team runtime 的源码导航。

如果你要做的事情是：

- 判断 `AgentTeamBuilder` 每个入口实际会影响哪段运行链
- 理解 Team runtime 的默认行为和失败语义
- 给团队协作能力做扩展、持久化、审批或调试

那么比“类/字段/方法全量枚举”更有价值的，是先把 Team runtime 的真实控制面看清。

## 1. 先看入口：你到底在构建什么

核心源码：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamAgentRuntime.java`

`AgentTeamBuilder` 有两个终点：

### 1.1 `build()`

返回 `AgentTeam`。

适合：

- 你明确要使用 team-specific API
- 你要读取任务板、消息总线、持久化状态
- 你要接 `planApproval`、`hooks`、`stateStore`

### 1.2 `buildAgent()`

返回一个普通 `Agent`，但 runtime 被替换成 `AgentTeamAgentRuntime`。

这个包装做了两件事：

- 用模板 `AgentTeamBuilder` 每次复制并构建新的 `AgentTeam`
- 给包装后的 `Agent` 配一份新的 `InMemoryAgentMemory`

因此 `buildAgent()` 的意义不是“把 Team 变成另一个对象模型”，而是：

- 让 Team orchestration 可以挂进统一 `Agent` 接口
- 让 Team 能和普通 Agent 一样接入 session、listener、通用调用面

## 2. Builder 字段如何映射到运行链

`AgentTeamBuilder` 暴露的配置项可以按运行角色分成五组。

### 2.1 规划与汇总角色

- `leadAgent(...)`
- `plannerAgent(...)`
- `synthesizerAgent(...)`
- `planner(...)`
- `synthesizer(...)`

默认回退链不是对称的。

规划器的回退顺序是：

1. 显式 `planner(...)`
2. `plannerAgent(...)`
3. `leadAgent(...)`

汇总器的回退顺序是：

1. 显式 `synthesizer(...)`
2. `synthesizerAgent(...)`
3. `leadAgent(...)`
4. `plannerAgent(...)`

如果最终仍然拿不到对应角色，会直接抛 `IllegalStateException`。这意味着 Team runtime 不允许“没有人负责规划”或“没有人负责收口”。

### 2.2 成员注册

- `member(...)`
- `members(...)`

构建阶段要求至少一个成员，否则 `AgentTeam` 构造函数直接失败。

成员会被转成内部 `RuntimeMember`，并按 `id` 建索引。重复 `id` 也会在构造阶段直接报错，而不是运行到一半才冲突。

### 2.3 协作与治理

- `options(...)`
- `messageBus(...)`
- `planApproval(...)`
- `hook(...)`
- `hooks(...)`

这部分控制的是：

- 并发派发策略
- 消息历史是否进入成员上下文
- 计划是否需要人工或宿主审批
- 任务前后能否挂自定义事件逻辑

### 2.4 状态持久化

- `stateStore(...)`
- `teamId(...)`
- `storageDirectory(...)`

如果显式提供 `stateStore`，Team 直接使用它。

如果没有提供 `stateStore`，但提供了 `storageDirectory`，`AgentTeam` 会自动派生：

- `FileAgentTeamStateStore(storageDirectory.resolve("state"))`
- `FileAgentTeamMessageBus(storageDirectory.resolve("mailbox").resolve(teamId + ".jsonl"))`

也就是说，`storageDirectory` 不是一个摆设字段，它会自动把 state 和 mailbox 一起落到文件系统。

### 2.5 团队包装成 Agent

`buildAgent()` 使用的是：

- `new AgentTeamAgentRuntime(this)`
- `new InMemoryAgentMemory()`

这里没有把 `stateStore` 映射进 `AgentMemory`。Team 自己的长期状态仍然由 `AgentTeamStateStore` 负责，包装出来的 `AgentMemory` 只是通用 Agent 接口需要的一层会话容器。

## 3. Team runtime 的真实生命周期

只看类名很难看出运行顺序，真正的控制链在 `AgentTeam.run(...)`。

### 3.1 规划阶段

runtime 会先调用 planner 生成 `AgentTeamPlan`，再把 plan 交给 `AgentTeamTaskBoard` 归一化。

如果提供了 `planApproval`，会先执行审批回调。

如果没有提供回调，但 `options.requirePlanApproval = true`，runtime 会直接失败；它不会默默放行。

### 3.2 派发阶段

`dispatchTasks(...)` 控制主循环。它每一轮会做：

1. 如有超时设置，先尝试 `recoverTimedOutClaims(...)`
2. 检查是否超过 `maxRounds`
3. 按 `parallelDispatch` 和 `maxConcurrency` 取一批 `READY` 任务
4. 解析任务目标成员并执行认领
5. 批量或串行执行该轮任务

两个容易忽略的边界在这里：

- 如果 `readyTasks` 为空，但 board 仍有未完成工作，runtime 会把剩余任务标成 `BLOCKED`，理由是依赖无法解开或存在循环
- 如果轮数超过上限，runtime 也会把停滞任务标成 `BLOCKED`

它不会无限自旋等待。

### 3.3 成员执行阶段

每个任务最终会走到 `runMemberTask(...)`。

这里有三个关键实现点：

- Team 不直接复用成员 Agent 的当前上下文，而是为成员创建 `newSession()`
- 如果 `enableMemberTeamTools = true`，会把 `AgentTeamToolRegistry` 合并进成员原始 registry
- 同时会用 `AgentTeamToolExecutor` 包装成员原执行器

这意味着 Team 协作工具不是“只有 Team runtime 自己能看见的特殊能力”，而是通过普通工具链注入给成员 session 的。

### 3.4 汇总阶段

成员结果收集完成后，runtime 再调用 synthesizer 汇总 `AgentTeamMemberResult` 列表，形成最终输出。

所以 Team runtime 的结构不是“多 agent 并发之后自然拼接”，而是：

- planner 产计划
- member 按任务执行
- synthesizer 负责最终收口

## 4. `AgentTeamTaskBoard` 才是任务状态机的核心

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java`

如果不理解 `AgentTeamTaskBoard`，就会把 Team 误认为“几个 Agent 顺序跑一下”。

### 4.1 初始化时就做规范化

构造 `AgentTeamTaskBoard(tasks)` 时，会先：

- 规范化 task id
- 为空的 task 自动生成 `task_1`、`task_2` 之类的 id
- 去重冲突 id
- 规范化依赖列表
- 把所有任务先建成 `PENDING`

然后调用 `refreshStatuses()` 把无依赖任务推进到 `READY`。

### 4.2 状态集合

任务状态不是自由文本，而是固定状态机：

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

这套状态机编码的是依赖求解，不只是 UI 展示。

### 4.3 认领与释放

- `claimTask(taskId, memberId)` 只允许从 `READY -> IN_PROGRESS`
- `releaseTask(taskId, memberId, reason)` 只允许当前持有者把任务放回 `PENDING`
- `reassignTask(...)` 只改持有者，不改变 `IN_PROGRESS` 状态
- `heartbeatTask(...)` 只更新心跳与元信息

如果成员 ID 不匹配，release/reassign/heartbeat 都会失败。这不是提示性校验，而是真正的所有权约束。

### 4.4 超时恢复

`recoverTimedOutClaims(timeoutMillis, reason)` 会把超时的 `IN_PROGRESS` 任务回收到 `PENDING`，然后重新 `refreshStatuses()`。

这意味着 task claim timeout 不是“给用户看个告警”，而是真会改变调度结果。

### 4.5 完成、失败、卡死

- `markCompleted(...)` 会写入输出和耗时，再触发状态刷新
- `markFailed(...)` 会写入错误和耗时，再触发状态刷新
- `markStalledAsBlocked(...)` 会把剩余 `PENDING/READY` 任务改成 `BLOCKED`

这使得最终 task board 既能表达成功路径，也能准确表达“计划本身解不开”的失败路径。

## 5. `AgentTeamOptions` 的默认值不是中性的

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamOptions.java`

默认值如下：

| 选项 | 默认值 | 后果 |
| --- | --- | --- |
| `parallelDispatch` | `true` | 默认按批次并发派发，不是串行执行 |
| `maxConcurrency` | `4` | 并发开度默认上限为 4 |
| `continueOnMemberError` | `true` | 单个成员失败后，Team 默认继续推进和汇总 |
| `broadcastOnPlannerFailure` | `true` | planner 失败时允许退化广播分发 |
| `failOnUnknownMember` | `false` | 计划里引用未知成员时，默认不在第一时间整体失败 |
| `includeOriginalObjectiveInDispatch` | `true` | 每个成员默认能看到完整原始目标 |
| `includeTaskContextInDispatch` | `true` | task context 默认会进入成员 prompt |
| `includeMessageHistoryInDispatch` | `true` | 最近消息历史默认会进入成员 prompt |
| `messageHistoryLimit` | `20` | 默认最多注入 20 条团队消息 |
| `enableMessageBus` | `true` | 团队通信默认开启 |
| `allowDynamicMemberRegistration` | `true` | 运行时默认允许动态增删成员 |
| `requirePlanApproval` | `false` | 默认不强制审批计划 |
| `maxRounds` | `64` | 调度主循环默认最多 64 轮 |
| `taskClaimTimeoutMillis` | `0L` | 默认不启用 claim 超时回收 |
| `enableMemberTeamTools` | `true` | 成员默认可调用 `team_*` 工具 |

这些默认值组合起来，说明 Team runtime 的默认倾向是：

- 能并发就并发
- 单点失败尽量别拖死全局
- 成员默认拿到较完整的协作上下文
- 团队通信和 team tools 默认打开

如果你的业务更偏强约束而不是高吞吐，通常至少要重新评估：

- `continueOnMemberError`
- `enableMemberTeamTools`
- `includeMessageHistoryInDispatch`
- `taskClaimTimeoutMillis`

## 6. Team 工具面：成员如何主动协作

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`

### 6.1 暴露给成员的内置工具

`AgentTeamToolRegistry` 固定暴露 7 个工具：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

这说明 Team 里的“协作”并不是 runtime 自己偷偷转发，而是成员 Agent 可以显式调用的工具面。

### 6.2 执行器的拦截策略

`AgentTeamToolExecutor.execute(...)` 只对这 7 个 `team_*` 工具做内建处理。

如果调用的是其他工具：

- 有 delegate，就透传给原始执行器
- 没有 delegate，就直接抛错

这保证了 Team 工具注入不会吞掉成员原本已有的工具能力。

### 6.3 返回值语义

Team 工具返回的是 JSON 字符串，里面通常至少包含：

- `action`
- `ok`
- `memberId`

具体动作还会附带：

- `taskId`
- `taskState`
- `toMemberId`
- `tasks`

因此它既适合给模型读，也适合宿主拿来做调试和审计。

## 7. 状态持久化和恢复

`AgentTeam` 自己维护一套比 `AgentMemory` 更宽的团队状态。

关键 API 包括：

- `snapshotState()`
- `loadPersistedState()`
- `restoreState(...)`
- `clearPersistedState()`

持久化内容不只是最后答案，还包括：

- `teamId`
- 当前 objective
- 成员快照
- task states
- message history
- `lastOutput`
- `lastRounds`
- 运行起止时间

这说明 Team 恢复语义面向的是“协作过程重建”，而不是单纯保留最后一段文本输出。

## 8. 运行时可变字段说明了什么

`AgentTeam` 内部有一组 runtime mutable fields：

- `activeBoard`
- `lastTaskStates`
- `activeObjective`
- `lastOutput`
- `lastRounds`
- `lastRunStartedAt`
- `lastRunCompletedAt`

这组字段说明 Team 不是纯函数式对象。它会在运行中维护当前执行态，也会保留最近一次运行快照。

因此，一个 `AgentTeam` 实例更像“有状态协作运行器”，而不是“只读配置对象”。

## 9. 扩展点应该接在哪

如果你要扩展 Agent Teams，优先看下面几类接口，而不是直接改主循环。

### 9.1 改规划策略

实现或替换：

- `AgentTeamPlanner`
- `planner(...)`

适合自定义任务拆解、成员匹配或计划格式。

### 9.2 改最终收口策略

实现或替换：

- `AgentTeamSynthesizer`
- `synthesizer(...)`

适合自定义最终汇总、排序、冲突消解或结构化输出。

### 9.3 改状态或消息存储

实现或替换：

- `AgentTeamStateStore`
- `AgentTeamMessageBus`

适合接数据库、对象存储、事件流或外部审计系统。

### 9.4 改审批与生命周期回调

实现或注入：

- `AgentTeamPlanApproval`
- `AgentTeamHook`

适合做计划审批、任务前后审计、指标采集、告警或人工干预。

### 9.5 改成员协作面

如果只想改成员可见的 team tools，优先看：

- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

不要直接在 planner prompt 里“口头要求成员协作”，那只是提示，不是能力面。

## 10. 调试 Team runtime 时，先查这些源码点

出现“计划产出了但任务不跑”“成员串任务”“协作消息没生效”“恢复后状态不对”时，优先查：

- `AgentTeam.dispatchTasks(...)` 是否因为 `maxRounds` 或依赖问题把任务标成了 `BLOCKED`
- `AgentTeamTaskBoard.refreshStatuses()` 是否正确推进了依赖状态
- `runMemberTask(...)` 是否真的为成员 `newSession()`
- `enableMemberTeamTools` 是否导致 team tools 被正确合并进成员 registry
- `resolveMessageBus(...)` / `resolveStateStore(...)` 是否按预期落到了文件或自定义后端

单看最终输出文本，通常看不到这些控制面问题。

## 11. 测试入口

下面几组测试最适合作为行为地图：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`

它们分别覆盖：

- 团队主链执行
- 任务板状态机
- 持久化与恢复

## 12. 继续阅读

1. [Agent Teams](/docs/agent/agent-teams)
2. [Subagent Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Tools and Registry](/docs/agent/tools-and-registry)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Trace Observability](/docs/agent/trace-observability)
