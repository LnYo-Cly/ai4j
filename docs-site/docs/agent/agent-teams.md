---
sidebar_position: 11
---

# Agent Teams

`AgentTeam` 是 AI4J 在单 Agent 之上的团队编排层。

它解决的不是“再多开几个 Agent 实例”，而是把多角色协作过程显式建模为：

- 规划器
- 任务板
- 成员执行
- 消息总线
- 团队工具
- 状态快照与恢复

如果 SubAgent 代表“主 Agent 把一个专项任务委派出去”，那么 Agent Teams 代表“多个成员围绕同一个目标持续协作”。

## 1. Agent Teams 解决什么问题

单 Agent 在下面这些场景里通常不够用：

- 一个目标需要分多个角色完成
- 任务之间存在依赖关系
- 需要显式记录谁在做什么
- 需要成员间主动发消息、转派任务、保活任务
- 需要保留团队运行快照，用于恢复或审计

把这些能力直接塞进一个大 prompt，会很快变成不可维护的黑盒。

Agent Teams 的设计目标，是把“协作过程”从提示词技巧提升为结构化运行时。

## 2. 它和 SubAgent / Workflow 的边界

| 能力 | 核心模式 | 适合什么场景 | 不负责什么 |
| --- | --- | --- | --- |
| `SubAgent` | 主从 handoff | 主 Agent 把专项能力封装成工具委派出去 | 团队状态、任务板、消息总线 |
| `AgentTeam` | 团队协作 | 多成员围绕同一 objective 协作 | 显式状态图级别的节点 DSL |
| `Workflow / StateGraph` | 图式编排 | 需要节点、边、条件路由、状态推进 | 成员级消息协作与团队治理 |

这三个能力不是互斥关系，但抽象层级不同：

- SubAgent 偏委派
- Team 偏协作
- Workflow 偏图式编排

## 3. 核心对象关系

Agent Teams 的核心对象如下：

| 对象 | 角色 | 关键职责 |
| --- | --- | --- |
| `AgentTeamBuilder` | 装配入口 | 组装 lead/planner/synthesizer、members、options、storage |
| `AgentTeam` | 团队运行时 | 执行规划、派发、汇总、状态恢复、团队工具注入 |
| `AgentTeamPlanner` | 规划器抽象 | 把 objective 变成结构化任务集合 |
| `AgentTeamSynthesizer` | 汇总器抽象 | 把成员结果合成为最终输出 |
| `AgentTeamTaskBoard` | 任务板 | 维护任务状态、依赖、认领与回收 |
| `AgentTeamMessageBus` | 消息总线 | 保存成员消息、提供快照与恢复 |
| `AgentTeamToolRegistry` | 团队工具声明面 | 暴露 `team_*` 协作工具 |
| `AgentTeamToolExecutor` | 团队工具执行面 | 拦截并执行 `team_*` 协作动作 |
| `AgentTeamStateStore` | 状态存储 | 保存和恢复团队运行快照 |

运行时的核心链路可以简化为：

```text
Objective
  -> Planner
  -> AgentTeamTaskBoard
  -> dispatch ready tasks to members
  -> member session + team_* tools
  -> MessageBus / TaskBoard updates
  -> Synthesizer
  -> AgentTeamResult
```

## 4. Builder 的默认与回退规则

`AgentTeamBuilder` 不只是收集参数，它还定义了 Team 的默认装配语义。

### 4.1 成员是必填项

`AgentTeam` 构造时会检查 `members`，至少需要一个成员，否则直接抛异常。

### 4.2 planner 的回退规则

在 `AgentTeam` 构造函数中：

1. 如果显式设置了 `planner(...)`，优先使用
2. 否则取 `plannerAgent(...)`
3. 若仍为空，则回退到 `leadAgent(...)`
4. 还为空则抛出异常

### 4.3 synthesizer 的回退规则

汇总器回退顺序稍复杂：

1. 如果显式设置了 `synthesizer(...)`，优先使用
2. 否则取 `synthesizerAgent(...)`
3. 若为空，则回退到 `leadAgent(...)`
4. 若 `leadAgent` 也为空但 `plannerAgent` 不为空，则回退到 `plannerAgent`
5. 仍为空则抛出异常

这意味着最常见的接法是：只给一个 `leadAgent`，然后把 planner / synthesizer 都复用它。

### 4.4 `buildAgent()` 的含义

`AgentTeamBuilder.buildAgent()` 不返回 `AgentTeam`，而是返回一个普通 `Agent` 外壳：

- runtime 是 `AgentTeamAgentRuntime`
- memory 是新的 `InMemoryAgentMemory`

它的用途是把 Team 以 Agent 形态嵌入其它编排层，而不是替代 `build()`。

## 5. 一条完整的执行链

Agent Teams 一次 `run(objective)` 的主流程如下：

1. 由 planner 生成任务计划
2. `AgentTeamTaskBoard` 标准化任务状态与依赖关系
3. 找出当前 `READY` 任务
4. 根据 `parallelDispatch` 与 `maxConcurrency` 派发给成员
5. 每个成员新建 session 执行任务输入
6. 若启用了团队工具，把 `team_*` 工具注入成员上下文
7. 成员输出回写到 task board，并通过 message bus 发送结果消息
8. 当任务结束或达到轮次上限后，由 synthesizer 汇总最终输出

这条链路的关键不是“有多个 Agent”，而是 Team 把协作状态显式化了。

## 6. 成员执行时到底发生了什么

每个任务派发给成员时，`AgentTeam` 会：

1. 为成员创建或获取 `AgentSession`
2. 基于 objective、task、context、历史消息拼装成员输入
3. 如果 `enableMemberTeamTools = true`：
   - 将原始工具注册器与 `AgentTeamToolRegistry` 合并
   - 将原始执行器包装成 `AgentTeamToolExecutor(this, memberId, taskId, originalExecutor)`
4. 运行成员 Agent

这个实现有两个重要含义：

- 团队工具不是全局宿主工具，而是按成员任务上下文注入
- `AgentTeamToolExecutor` 持有 `defaultTaskId`，成员调用团队工具时即使不显式传 `taskId`，也可以回落到当前任务

## 7. 任务板模型

Team 的任务不是松散字符串，而是结构化对象。

### 7.1 `AgentTeamTask`

核心字段包括：

- `id`
- `memberId`
- `task`
- `context`
- `dependsOn`

典型规划输出：

```json
{
  "tasks": [
    {"id": "collect", "memberId": "researcher", "task": "收集事实"},
    {"id": "format", "memberId": "formatter", "task": "整理输出", "dependsOn": ["collect"]}
  ]
}
```

### 7.2 任务状态

`AgentTeamTaskStatus` 目前包括：

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

这使得 Team 不只是“谁来做”，还显式维护“现在做到哪里”。

### 7.3 运行态快照

`AgentTeamTaskState` 不只是一个状态枚举，还包含更适合宿主 UI / CLI 展示的字段，例如：

- `phase`
- `detail`
- `percent`
- `heartbeatCount`
- `updatedAtEpochMs`
- `lastHeartbeatTime`
- `claimedBy`
- `durationMillis`

这些字段的意义不是伪造精确进度条，而是暴露统一的运行态协议。

## 8. `team_*` 工具为何存在

`AgentTeamToolRegistry` 默认暴露以下协作工具：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

这意味着成员不是只能“被动完成分配任务”，而是可以在执行过程中：

- 给其他成员发消息
- 广播信息
- 查看任务板
- 认领任务
- 释放任务
- 转派任务
- 上报心跳

团队协作从提示词层上升到了结构化工具层。

## 9. `AgentTeamToolExecutor` 如何工作

`AgentTeamToolExecutor` 只拦截 `team_*` 工具，其他调用直接委托给原始 `delegate.execute(call)`。

其行为可以概括为：

1. 解析 JSON arguments
2. 判断当前 action 对应哪个团队控制接口
3. 从参数中解析 `taskId`
4. 若没有显式 `taskId`，回退到构造时注入的 `defaultTaskId`
5. 执行控制动作
6. 返回 JSON 结果

返回 JSON 中至少包含：

- `action`
- `ok`
- `memberId`

某些动作还会包含：

- `taskId`
- `toMemberId`
- `taskState`
- `tasks`

这让 `team_*` 工具既可供模型理解，也可以被宿主直接消费。

## 10. 消息总线的设计目的

`AgentTeamMessageBus` 不是为了“让团队看起来更智能”，而是为了解决协作过程中的信息显式化。

消息模型 `AgentTeamMessage` 包含：

- `id`
- `fromMemberId`
- `toMemberId`
- `type`
- `taskId`
- `content`
- `createdAt`

这带来几个直接好处：

- 团队协作过程可审计
- 任务结果可以消息化回流给 lead
- 宿主可以展示成员之间的通信轨迹
- 历史消息可以重新注入后续任务

## 11. 持久化与恢复

Agent Teams 的一个关键优势是运行快照可恢复。

### 11.1 `snapshotState()`

返回 `AgentTeamState`，其中包含：

- `teamId`
- `objective`
- `members`
- `taskStates`
- `messages`
- `lastOutput`
- `lastRounds`
- `lastRunStartedAt`
- `lastRunCompletedAt`
- `updatedAt`
- `runActive`

### 11.2 `loadPersistedState()`

如果配置了 `stateStore`，会：

1. 从存储层按 `teamId` 读取状态
2. 调用 `restoreState(...)`
3. 恢复消息、任务状态、上次输出和轮次元数据

### 11.3 `clearPersistedState()`

会清理内存中的团队状态，并在有状态存储时删除对应持久化记录。

### 11.4 `storageDirectory(...)` 的默认文件布局

如果 builder 只配置了 `storageDirectory(...)`，`AgentTeam` 会自动使用：

- 状态文件：`<storageDirectory>/state/<teamId>.json`
- 邮箱文件：`<storageDirectory>/mailbox/<teamId>.jsonl`

也就是说，message bus 和 state store 都可以从一个目录约定自动派生。

### 11.5 恢复边界

需要明确：

- 恢复的是团队运行快照
- 不是把成员 Agent 本身从磁盘反序列化“复活”

你仍然需要重新提供：

- members
- planner / plannerAgent / leadAgent
- synthesizer / synthesizerAgent / leadAgent

## 12. `AgentTeamOptions` 默认值与调参

`AgentTeamOptions` 的默认值非常关键，因为它们决定了 Team 的默认协作性格。

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `parallelDispatch` | `true` | 默认并发派发 ready 任务 |
| `maxConcurrency` | `4` | 最大并发成员执行数 |
| `continueOnMemberError` | `true` | 某成员失败时默认继续推进其他任务 |
| `broadcastOnPlannerFailure` | `true` | 规划失败时默认广播失败信息 |
| `failOnUnknownMember` | `false` | 规划引用未知成员时默认不立即终止 |
| `includeOriginalObjectiveInDispatch` | `true` | 成员输入默认包含总目标 |
| `includeTaskContextInDispatch` | `true` | 成员输入默认包含任务 context |
| `includeMessageHistoryInDispatch` | `true` | 成员输入默认包含近期团队消息 |
| `messageHistoryLimit` | `20` | 默认注入最近 20 条消息 |
| `enableMessageBus` | `true` | 默认启用消息总线 |
| `allowDynamicMemberRegistration` | `true` | 默认允许运行期增删成员 |
| `requirePlanApproval` | `false` | 默认不强制计划审批 |
| `maxRounds` | `64` | 最多运行 64 轮 |
| `taskClaimTimeoutMillis` | `0L` | 默认关闭任务认领超时回收 |
| `enableMemberTeamTools` | `true` | 默认向成员暴露 `team_*` 工具 |

这组默认值说明 Team 默认是：

- 偏协作
- 偏容错
- 偏可观察

而不是“严格串行、一步错就全部失败”的保守模型。

## 13. 一份正确的接入示例

```java
Path storage = Paths.get(".ai4j/teams");

AgentTeam team = Agents.team()
        .teamId("weather-team")
        .leadAgent(leadAgent)
        .member(AgentTeamMember.builder()
                .id("researcher")
                .name("Researcher")
                .description("Collect facts and raw weather data.")
                .agent(researcherAgent)
                .build())
        .member(AgentTeamMember.builder()
                .id("formatter")
                .name("Formatter")
                .description("Turn task outputs into concise final text.")
                .agent(formatterAgent)
                .build())
        .storageDirectory(storage)
        .options(AgentTeamOptions.builder()
                .parallelDispatch(true)
                .maxConcurrency(2)
                .enableMemberTeamTools(true)
                .maxRounds(16)
                .build())
        .build();

AgentTeamResult result = team.run("给出北京天气简报，并输出可读结论。");
```

这个示例体现的不是“两个成员就够了”，而是：

- planner / synthesizer 可由 leadAgent 兜底
- 团队状态与邮箱自动落到约定目录
- 成员执行时可使用 `team_*` 协作工具

## 14. Plan Approval 与 Hook

Team 并不是只能靠默认策略运行。

### 14.1 `planApproval(...)`

适合在规划完成后、真正派发前做治理，例如：

- 是否允许某些成员被使用
- 计划是否包含空任务
- 任务依赖是否越权

### 14.2 `hook(...)`

适合在关键节点挂审计、埋点和告警逻辑，例如：

- `beforePlan`
- `afterPlan`
- `beforeTask`
- `afterTask`
- `afterSynthesis`
- `onMessage`

这两个扩展点让 Team 成为可治理运行时，而不只是协作 demo。

## 15. 失败语义与工程边界

### 15.1 Team 不是无限自组织系统

虽然成员能主动调用 `team_*` 工具，但整个团队仍然受：

- `maxRounds`
- `maxConcurrency`
- `taskClaimTimeoutMillis`
- `requirePlanApproval`

等配置约束。

### 15.2 任务失败并不总是中断全局

默认 `continueOnMemberError = true`，因此单个成员失败后，其他任务仍可能继续执行。

这更适合长任务容错，但如果你的业务语义要求“一个成员失败就整体失败”，应该显式收紧策略。

### 15.3 状态恢复不是热重启 Agent 进程

`loadPersistedState()` 恢复的是团队视角的数据快照，而不是把每个成员 Agent 之前的 JVM 执行栈恢复回来。

## 16. 推荐阅读源码入口

建议按下面顺序阅读：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamBuilder.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/FileAgentTeamStateStore.java`

## 17. 推荐验证用例

建议结合以下测试一起阅读：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamTaskBoardTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/FileAgentTeamStateStoreTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentTeamPersistenceTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/DoubaoAgentTeamBestPracticeTest.java`

## 18. 下一步读什么

读完这一页后，建议继续：

1. [Agent Teams API Reference](/docs/agent/agent-teams-api-reference)
2. [SubAgent 与 Handoff Policy](/docs/agent/subagent-handoff-policy)
3. [StateGraph](/docs/agent/orchestration/stategraph)
4. [Trace 与可观测性](/docs/agent/observability/trace)
