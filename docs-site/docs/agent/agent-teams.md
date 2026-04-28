---
sidebar_position: 11
---

# Agent Teams

`AgentTeam` 不是“把多个 Agent 放进一个列表里依次调用”，而是一个带控制面的团队运行时。

它把多成员协作拆成 5 个显式部件：

- planner：把 objective 变成任务集合
- task board：维护依赖、状态、认领和回收
- member dispatch：把 ready task 派发给成员执行
- message bus：保存协作消息，而不是把协作关系藏在 prompt 里
- synthesizer：把成员产出汇总成最终回答

如果只看表面 API，`AgentTeam.run("...")` 很像“高级版多 Agent demo”；但从源码看，它更像一个轻量 orchestration runtime。

## 1. 先抓住 3 个关键设计决策

理解 Agent Teams，最重要的不是先背类名，而是先抓住 3 个设计决策。

### 1.1 `AgentTeam` 自己就是控制面

`AgentTeam` 本身实现了 `AgentTeamControl`。这不是小细节，而是整个团队协作模型的中心。

这意味着：

- 团队工具不是发到外部控制器
- 成员在执行时调用 `team_*` 工具，最终会直接回到当前 `AgentTeam` 实例
- 任务认领、转派、消息发布、心跳更新，都是在当前运行中的 Team 对象上完成

所以 Team 不是“成员之间自由协作”，而是“成员通过统一控制面协作”。

### 1.2 成员默认是无状态任务执行器

`runMemberTask(...)` 每次都会调用：

```java
AgentSession session = dispatch.member.agent.newSession();
```

也就是说，即使同一个成员在多个 round 中反复被分配任务，默认也不会复用上一次任务的 session memory。

这带来一个非常关键的后果：

- 团队的连续性不在成员本地 memory 中
- 连续性被外移到了 `task context + message bus + task board state`

这是一种非常明确的架构取舍：成员是可重复调用的执行单元，团队状态由外部 runtime 保存。

### 1.3 协作是“结构化状态 + 工具”，不是“多写一点提示词”

很多多 Agent 方案只是把“你们互相配合”写进 prompt。AI4J Agent Teams 不是这样。

它把协作能力显式实现成：

- `AgentTeamTaskBoard`
- `AgentTeamMessageBus`
- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

这意味着成员协作不再只是自然语言约定，而是真正可拦截、可观测、可恢复的运行时行为。

## 2. `run()` 的真实生命周期

看 `AgentTeam` 最有价值的方式，是沿着 `run(...)` 的执行链读。

`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`

`run(AgentRequest)` 大致分成 6 个阶段。

### 2.1 Phase 1: 运行前初始化

运行开始时，`AgentTeam` 会做几件基础工作：

- 记录 `lastRunStartedAt`
- 提取 objective
- 若启用了消息总线，先 `messageBus.clear()`
- 快照当前成员列表
- 触发 `beforePlan(...)` hook

这里最值得注意的是 `messageBus.clear()`。

这说明每一次新的 `run()` 都被视为新的团队协作轮次，而不是天然延续上一次协作历史。因此：

- `loadPersistedState()` 恢复出来的消息更适合做观察、审计、UI hydration
- 不是为了“恢复后继续原 run 无缝接着跑”

这是当前实现的一个重要边界。

### 2.2 Phase 2: planner 生成计划

接下来执行：

```java
AgentTeamPlan plan = planner.plan(objective, members, options);
```

默认 planner 是 `LlmAgentTeamPlanner`。它的行为比表面上更具体：

1. 用一个固定 prompt 要求模型输出 JSON
2. 列出可用成员、成员 id、name、description
3. 要求输出任务数组，每个任务包含 `id/memberId/task/context/dependsOn`
4. 调用 `plannerAgent.newSession().run(...)`
5. 用 `AgentTeamPlanParser.parseTasks(...)` 容忍式解析结果

planner prompt 的核心不是“让模型自己发挥”，而是把 planner 限定为一个 JSON task planner。

### 2.3 Phase 3: 计划规范化和审批

planner 给出的任务不会直接进入派发，而是先进入：

```java
AgentTeamTaskBoard board = new AgentTeamTaskBoard(plan.getTasks());
plan = plan.toBuilder().tasks(board.normalizedTasks()).build();
```

这一步非常关键，因为 task board 会先做规范化：

- 缺失 `id` 时自动生成 `task_1`、`task_2` ...
- 重复 id 自动去重并改名
- dependency id 统一做 normalize
- 初始状态统一进入 `PENDING`

之后才会：

- `fireAfterPlan(...)`
- `ensurePlanApproved(...)`

也就是说，`planApproval` 看到的是“已经过标准化”的 plan，而不是原始模型输出。

这对治理很重要，因为审批逻辑可以基于稳定 id 和依赖结构判断，而不是对着不稳定原文做解析。

### 2.4 Phase 4: dispatch loop

真正的团队调度发生在 `dispatchTasks(...)`。

这一层的逻辑不是“遍历成员列表”，而是一个 round-based loop：

1. 若启用了 `taskClaimTimeoutMillis`，先做超时回收
2. 若 `rounds >= maxRounds`，把剩余任务标记为 blocked
3. 根据 `parallelDispatch` 和 `maxConcurrency` 计算 batch size
4. 从 task board 取一批 `READY` 任务
5. 为每个 task 解析目标 member
6. `claimTask(...)`
7. 批量执行这一轮任务
8. 收集结果，决定是否继续下一轮

这说明 Team 的基本调度单位不是“成员”，而是“ready task batch”。

### 2.5 Phase 5: 成员执行与协作工具注入

每个 ready task 被真正执行前，会进入 `executePreparedTask(...)` 和 `runMemberTask(...)`。

这一层做了两件重要的事：

1. 构造成员执行输入
2. 注入 `team_*` 协作工具

构造输入时，`buildDispatchInput(...)` 会按配置拼接：

- member role / expertise
- task id
- dependsOn
- objective
- assigned task
- optional task context
- recent team messages

然后在 session context 上做工具层改写：

```java
mergedRegistry = new CompositeToolRegistry(originalRegistry, teamToolRegistry);
sessionContext.setToolExecutor(new AgentTeamToolExecutor(this, memberId, taskId, originalExecutor));
```

这一步是整个 Team 机制的关键注入点：

- schema 暴露面被扩展了
- 执行面也被包装了
- 而且包装器拿到了 `this`、`memberId`、`taskId`

所以成员不仅“看得到”团队工具，还能在调用时自动带上当前 task 上下文。

### 2.6 Phase 6: synthesis 与收口

所有 round 结束后，Team 会调用 synthesizer：

```java
AgentResult synthesis = synthesizer.synthesize(objective, plan, dispatch.results, options);
```

默认 synthesizer 是 `LlmAgentTeamSynthesizer`，它会：

- 新建一个 fresh session
- 给出 objective
- 给出 plan JSON
- 给出所有 member outputs / errors
- 要求输出直接可用的最终回答

这意味着 synthesis 不是复用 lead 的内部上下文，而是把团队结果重新组织后交给一个新的 Agent 调用。

最后 Team 会组装 `AgentTeamResult`，其中包含：

- `teamId`
- `objective`
- `plan`
- `memberResults`
- `taskStates`
- `messages`
- `rounds`
- `output`
- `synthesisResult`
- `totalDurationMillis`

## 3. Planner 子系统的真实容错方式

`LlmAgentTeamPlanner` 的容错并不是“planner 出错也没关系”，而是更具体的一种策略。

### 3.1 它容忍“输出不标准”，不容忍“调用直接抛异常”

`AgentTeamPlanParser` 会尽力从模型输出里解析任务：

- 支持直接是数组
- 支持直接是对象
- 支持从混杂文本中提取首个 JSON 片段
- 支持多种字段别名，如 `taskId/member/assignee/instruction/goal`

所以它对“格式不够干净”的 planner 输出有容错。

但要注意：

- 如果 `plannerAgent.newSession().run(...)` 本身抛异常
- `LlmAgentTeamPlanner` 并不会吞掉这个异常

因此 planner 的“fallback”只覆盖了解析为空的情况，不覆盖 planner 调用链本身失败。

### 3.2 `broadcastOnPlannerFailure` 的实际含义

这个配置名容易误解。

在当前实现中，它的真实语义更接近：

- “当 planner 输出无法解析出任何 task 时，是否为每个成员生成 fallback task”

而不是：

- “planner 一旦运行失败就自动广播并继续”

fallback task 的生成规则也很明确：

- 每个成员一个任务
- task id 形如 `fallback_1`
- task 内容会拼入成员 description 和原 objective
- context 固定为 `planner_fallback`

这是一种非常务实的降级路径：planner 失手时，系统仍尽量让每个成员各自完成一部分工作，而不是直接中断。

## 4. Task Board 是真正的状态机

`AgentTeamTaskBoard` 是 Agent Teams 最核心的机制之一，因为真正的“协作顺序”和“调度合法性”都在这里。

### 4.1 初始化阶段做了哪些标准化

构造 task board 时，会统一处理：

- id 规范化：转小写、替换非法字符、去掉冗余 `_`
- 缺失 id 自动补全
- 重复 id 自动避让
- dependency 也统一做 normalize

然后为每个 task 生成初始 `AgentTeamTaskState`：

- `status = PENDING`
- `phase = planned`
- `percent = 0`

所以 planner 输出并不是“系统内部任务真值”，标准化后的 board 才是。

### 4.2 `refreshStatuses()` 决定了依赖推进语义

task board 不是简单存状态，它会在很多操作前调用 `refreshStatuses()`，重新计算任务是否：

- `READY`
- `PENDING`
- `BLOCKED`

它的判断逻辑是：

- 无依赖任务直接 ready
- 依赖全部 completed 才能 ready
- 缺失 dependency -> blocked
- dependency failed / blocked -> blocked
- 否则继续 pending

这意味着 Team 的依赖推进是显式的、可解释的，而不是由 planner 文本隐含表达。

### 4.3 claim / release / reassign / heartbeat 不是附属功能

这些控制动作都直接改写 `AgentTeamTaskState`：

- `claimTask(...)`：进入 `IN_PROGRESS`
- `releaseTask(...)`：回到 `PENDING`
- `reassignTask(...)`：保留 in-progress，但切换 `claimedBy`
- `heartbeatTask(...)`：更新 `lastHeartbeatTime` 和 `heartbeatCount`
- `recoverTimedOutClaims(...)`：超时后把 `IN_PROGRESS` 任务回收为 `PENDING`

这就是为什么 `team_*` 工具不是“聊天辅助功能”，而是调度控制面的一部分。

### 4.4 blocked 的来源不止一种

当前实现里，任务或整轮执行被标成 blocked 的原因至少有 4 类：

- 缺失 dependency
- dependency failed
- `maxRounds` 超限
- 当前没有 ready task，推断为 unresolved dependencies 或 cyclic plan

也就是说，`BLOCKED` 不只是“依赖未满足”，也可能是“调度策略已经判定这轮无法继续推进”。

## 5. 并发模型并不复杂，但很明确

Agent Teams 的并发不是 actor model，也不是复杂异步框架，而是比较直接的 Java 并发模型。

### 5.1 task board 自身是同步对象

`AgentTeamTaskBoard` 的公开方法几乎都用 `synchronized`。

这样做的效果是：

- board 内部状态修改非常直接
- 线程安全模型容易理解
- 代价是吞吐上限不会特别激进

这是一种偏工程稳态的实现，而不是追求极致并发。

### 5.2 Team 本体只用两把锁分开保护成员和运行态

`AgentTeam` 里有两把显式锁：

- `memberLock`
- `runtimeLock`

它们分别保护：

- 成员注册、删除、查找
- 当前 active board、objective、lastTaskStates

这种分拆避免了把所有行为都塞进一个巨大的同步块里。

### 5.3 真正的并行只发生在“同一轮 ready tasks”

如果开启 `parallelDispatch=true` 且本轮有多个 ready tasks，`executeRound(...)` 会：

- 按 `maxConcurrency` 建线程池
- 并行执行每个 `PreparedDispatch`
- 等待所有 future 返回
- 最终 `shutdownNow()`

因此 Team 的并发语义是：

- round 内并行
- round 间串行
- board 状态通过同步方法合并

这使得行为比较容易推理：同一轮可以并发做事，但全局推进仍然是离散轮次。

## 6. 为什么 `team_*` 工具是这套系统的灵魂

如果没有 `team_*` 工具，Agent Teams 仍然可以“lead 分配任务、member 回结果”；但那只是一种静态派发模型。

真正让它像团队运行时的，是成员可以主动操作任务和消息。

### 6.1 `AgentTeamToolRegistry` 暴露了哪些能力

默认工具有 7 个：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

其中前两个是消息面，后五个是任务控制面。

### 6.2 `AgentTeamToolExecutor` 的拦截语义

这个执行器只处理 `team_*`：

- 命中 `team_*` -> 走 Team 控制逻辑
- 其他工具 -> 直接委托给原始 `delegate`

因此它不是替换原成员工具链，而是在原有工具链前面增加了一层团队控制拦截器。

### 6.3 `defaultTaskId` 让模型少传一半样板参数

构造 `AgentTeamToolExecutor` 时，会传入当前 `taskId`。

之后执行器在解析参数时，如果工具调用里没显式给 `taskId`，就自动回退到当前任务。

这个设计很重要，因为它降低了模型使用团队工具的门槛：

- 模型不需要每次都自己回填当前 task id
- 但执行结果仍然能稳定绑定到正确任务

### 6.4 工具合并没有去重

`CompositeToolRegistry` 的行为只是简单拼接多个 registry 的 `getTools()` 结果，并不做去重。

这意味着：

- 如果成员原本就有和 `team_*` 同名的工具
- 或多个 registry 暴露同名 schema

模型可见工具面就可能出现冲突或重复声明。

当前 Team 设计默认假设：

- 团队工具名是保留前缀
- 使用方不要在成员原工具集中重复定义 `team_*`

这是一条应该显式遵守的约束。

## 7. 消息总线不是附属日志，而是协作状态的一部分

`AgentTeamMessageBus` 的作用，不是单纯“记录谁说了什么”，而是承担三件事：

- 给成员协作提供显式通信面
- 为后续任务注入历史消息
- 为持久化和 UI 提供团队轨迹

### 7.1 Team 自己也会发系统消息

例如在任务分配和完成时，Team 会主动发送：

- `task.assigned`
- `task.result`
- `task.error`
- `run.complete`

因此 message bus 里不只有成员互相聊天，也包含系统视角的协作事件。

### 7.2 历史消息会进入成员 prompt

当以下两个开关同时成立时：

- `enableMessageBus = true`
- `includeMessageHistoryInDispatch = true`

成员执行输入里会拼入近期消息历史。

这进一步证明了一个关键点：

- Team 的连续性主要来自外部化的消息与任务状态
- 不是来自成员 session 的长期复用

## 8. 持久化与恢复的边界，要讲清楚

Agent Teams 的持久化能力很有用，但很容易被误解成“可暂停后继续执行同一个团队进程”。

当前实现并不是这个语义。

### 8.1 `snapshotState()` 保存的是运行快照

快照包含：

- team 元数据
- objective
- member snapshots
- task states
- messages
- 上次输出、轮次、开始结束时间

它保存的是“这个团队当时长什么样”，不是“这个团队的 Java 执行现场”。

### 8.2 `loadPersistedState()` 更像 UI hydration / inspection

调用后会：

- 校验 `teamId`
- 恢复 message bus
- 恢复 `lastTaskStates`
- 恢复 `lastOutput` / `lastRounds` / 时间戳

但不会恢复：

- 当前正在运行的 thread pool
- 成员内部 session memory
- planner / synthesizer / member agent 的真实实例状态

### 8.3 `storageDirectory(...)` 只是约定式文件存储入口

如果只设置 `storageDirectory(...)`，默认派生出：

- `mailbox/<teamId>.jsonl`
- `state/<teamId>.json`

这让 Team 很容易本地落盘，但仍然属于“快照恢复”，不是“进程级 continuation”。

### 8.4 新一轮 `run()` 会清空消息总线

这是最容易忽略的边界。

即使你刚刚 `loadPersistedState()`，一旦调用新的 `run()`，当前实现仍会先 `messageBus.clear()`。

所以目前的恢复更适合：

- 查看上次团队状态
- 让 UI / CLI 重新展示历史
- 作为外部治理或审计的数据源

不适合直接理解成“恢复后自然接着上一轮继续协作”。

## 9. 配置项里真正高杠杆的部分

不是所有 `AgentTeamOptions` 都同等重要。真正会改变系统行为形态的主要有下面几组。

### 9.1 调度形态

- `parallelDispatch = true`
- `maxConcurrency = 4`
- `maxRounds = 64`

这三项决定 Team 是：

- 偏并发批处理
- 还是偏串行保守执行

### 9.2 失败语义

- `continueOnMemberError = true`
- `failOnUnknownMember = false`
- `broadcastOnPlannerFailure = true`

这三项合起来，会让默认 Team 更偏“尽量继续推进”，而不是“任何异常立即终止”。

尤其要注意 `failOnUnknownMember = false`：

- 当 planner 指向未知成员时，`resolveMember(...)` 会回退到第一个成员
- 这能提升容错，但也可能掩盖 planner 分配错误

### 9.3 团队连续性来源

- `enableMessageBus = true`
- `includeMessageHistoryInDispatch = true`
- `messageHistoryLimit = 20`
- `includeTaskContextInDispatch = true`

这组配置决定成员之间共享多少外部化状态。

因为成员默认每次都是 `newSession()`，所以这些外部化上下文开关的影响比单 Agent 场景更大。

### 9.4 团队治理

- `requirePlanApproval = false`
- `allowDynamicMemberRegistration = true`
- `taskClaimTimeoutMillis = 0L`
- `enableMemberTeamTools = true`

这组配置决定 Team 更像：

- 一个自由协作的默认 runtime
- 还是一个受严格控制的审批型系统

## 10. 一份更接近真实语义的接入示例

下面这个示例的重点不是“能跑”，而是体现 Team 的几个关键结构。

```java
Path storage = Paths.get(".ai4j/teams");

AgentTeam team = Agents.team()
        .teamId("release-review-team")
        .leadAgent(leadAgent)
        .member(AgentTeamMember.builder()
                .id("reader")
                .name("Reader")
                .description("Read code and extract behavior changes.")
                .agent(readerAgent)
                .build())
        .member(AgentTeamMember.builder()
                .id("reviewer")
                .name("Reviewer")
                .description("Assess risks, regressions, and missing coverage.")
                .agent(reviewerAgent)
                .build())
        .storageDirectory(storage)
        .options(AgentTeamOptions.builder()
                .parallelDispatch(true)
                .maxConcurrency(2)
                .continueOnMemberError(true)
                .enableMemberTeamTools(true)
                .taskClaimTimeoutMillis(30_000L)
                .build())
        .build();

AgentTeamResult result = team.run("Review the latest release candidate and summarize the main risks.");
```

这个配置表达的真实含义是：

- planner / synthesizer 默认可由 leadAgent 兜底
- 团队消息和状态都能落盘
- 成员可以主动调用 `team_*` 工具
- 如果某个任务被认领后长时间无心跳，task board 可以回收它

## 11. 当前实现的几个真实限制

如果要把 Team 用到生产级长期任务，这几个限制必须知道。

### 11.1 planner 和 synthesizer 仍然是 prompt-driven Agent

它们不是特殊系统组件，本质上还是普通 `Agent.newSession().run(...)` 包装出来的角色。

优点是复用简单；代价是它们的质量高度依赖：

- 模型能力
- prompt 质量
- 输出可解析性

### 11.2 成员 memory 不会天然积累

默认每个 task 都是 fresh session。

所以如果你希望：

- 同一个成员跨任务持续记忆
- 成员形成长期局部上下文

当前 Team 默认实现并不会直接给你这个能力。

### 11.3 恢复不是挂起后继续执行

恢复的是状态视图，不是运行现场。

如果你需要真正的 resumable execution，需要在更上层补充：

- 外部调度器
- 幂等任务设计
- 更强的 state machine / checkpoint 机制

### 11.4 hook 失败不会中断主流程

所有 `AgentTeamHook` 调用都吞掉异常。

这保证了观测与审计逻辑不会打断主链路，但也意味着：

- hook 自身失败默认是静默的
- 如果你依赖 hook 做关键治理，必须自己在 hook 内部记录失败

## 12. 推荐阅读顺序

如果你想按源码深入，建议顺序如下：

1. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java`
3. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamPlanner.java`
4. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamPlanParser.java`
5. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java`
6. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`
7. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamSynthesizer.java`

## 13. 推荐验证用例

建议结合这些测试一起读，它们基本构成了当前行为契约：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/FileAgentTeamStateStoreTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/DoubaoAgentTeamBestPracticeTest.java`

## 14. 继续阅读

如果读完这一页还想往下钻，建议继续：

1. [Agent Teams API Reference](/docs/agent/agent-teams-api-reference)
2. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [Workflow StateGraph](/docs/agent/workflow-stategraph)
4. [Trace 与可观测性](/docs/agent/trace-observability)
