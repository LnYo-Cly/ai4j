---
sidebar_position: 11
---

# Agent Teams 详解（队员管理 + 任务管理 + 信息管理）

本页是 AI4J Agent Teams 的完整使用与设计说明，重点覆盖：

- 如何只配置 `leadAgent` 就跑起来；
- 如何管理队员、任务、消息；
- 如何让队员通过 `team_*` 工具主动协作；
- 如何在工程上做回退、治理与排障。

---

## 1. Agent Teams 解决什么问题

当一个目标需要多角色协作时（例如：采集 -> 分析 -> 格式化 -> 风险校验），单 Agent 往往会出现：

- 上下文混杂，角色边界不清；
- 任务依赖不透明，失败重试困难；
- 结果可以生成，但过程不可审计。

Agent Teams 的设计目标是把“多角色协作过程”结构化：

- Lead 负责规划、调度、汇总；
- Members 负责执行任务；
- TaskBoard 负责任务状态与依赖；
- MessageBus 负责成员间信息流转。

---

## 2. 与 SubAgent 的边界

- **SubAgent**：主从 handoff 模式，偏“委派工具调用”。
- **Agent Teams**：共享任务板模式，偏“团队协同执行”。

简单记忆：

- SubAgent 更像“主 Agent 调子代理工具”；
- Teams 更像“项目组协作”。

---

## 3. 执行模型（当前实现）

执行链路：

```text
Objective
  -> Planner 产出任务 JSON
  -> TaskBoard 规范化 id / dependsOn
  -> 按轮次挑选 READY 任务（可并发）
  -> 成员执行并写回 task state + message
  -> Synthesizer 汇总最终输出
```

关键特性：

- 依赖驱动状态流转；
- 支持并发派发；
- 支持失败继续（可配置）；
- 支持消息历史注入到成员 prompt；
- 支持任务认领超时回收（可配置）。

---

## 4. 快速开始：只配置一个 `leadAgent`

当前推荐默认写法是“单 Lead 模式”。

```java
Agent lead = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个团队负责人，先规划再汇总")
        .build();

Agent researcher = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

Agent formatter = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

AgentTeam team = Agents.team()
        .leadAgent(lead)
        .member(AgentTeamMember.builder()
                .id("researcher")
                .name("资料员")
                .description("负责事实收集")
                .agent(researcher)
                .build())
        .member(AgentTeamMember.builder()
                .id("formatter")
                .name("整理员")
                .description("负责输出格式化")
                .agent(formatter)
                .build())
        .build();

AgentTeamResult result = team.run("给出北京天气简报并格式化输出");
System.out.println(result.getOutput());
```

说明：

- 未显式配置 `plannerAgent/synthesizerAgent` 时，会回退到 `leadAgent`；
- 你也可以分别覆盖 planner 与 synthesizer。

---

## 5. 高级模式：覆盖 Planner/Synthesizer

适合做模型分工优化：

- Planner 用推理更强模型；
- Synthesizer 用成本更低模型。

```java
AgentTeam team = Agents.team()
        .leadAgent(lead)
        .plannerAgent(plannerAgent)          // 可选覆盖
        .synthesizerAgent(synthAgent)        // 可选覆盖
        .member(...)
        .build();
```

优先级：

1. 自定义 `planner/synthesizer`（接口实现）
2. `plannerAgent/synthesizerAgent`
3. `leadAgent`

---

## 6. 队员管理（Member Management）

控制接口：`AgentTeamControl`

- `registerMember(...)`
- `unregisterMember(...)`
- `listMembers()`

可通过 `AgentTeamOptions.allowDynamicMemberRegistration` 控制是否允许运行期增删成员。

实践建议：

- 生产环境把成员 ID 作为稳定主键（不要动态变更）；
- 成员 description 要写“能力边界”，避免规划器误分配；
- 禁止“同能力重复成员”时可在构建阶段做校验。

---

## 7. 任务管理（Task Management）

### 7.1 任务模型

`AgentTeamTask` 字段：

- `id`
- `memberId`
- `task`
- `context`
- `dependsOn`

推荐规划输出（JSON）：

```json
{
  "tasks": [
    {"id": "collect", "memberId": "researcher", "task": "收集天气事实"},
    {"id": "format", "memberId": "formatter", "task": "格式化输出", "dependsOn": ["collect"]}
  ]
}
```

### 7.2 状态机

`AgentTeamTaskStatus`：

- `PENDING`
- `READY`
- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`
- `BLOCKED`

### 7.3 运行时任务控制 API

- `listTaskStates()`
- `claimTask(taskId, memberId)`
- `releaseTask(taskId, memberId, reason)`
- `reassignTask(taskId, fromMemberId, toMemberId)`
- `heartbeatTask(taskId, memberId)`

### 7.4 超时回收

`AgentTeamOptions.taskClaimTimeoutMillis` > 0 时，运行中会自动回收长时间无心跳的任务认领。

### 7.5 任务运行态可观测字段

`listTaskStates()` 返回的 `AgentTeamTaskState` 现在不只是一个粗粒度状态枚举，还会带上适合 UI / IDE 展示的运行态字段：

- `phase`
- `detail`
- `percent`
- `heartbeatCount`
- `updatedAtEpochMs`
- `lastHeartbeatTime`
- `claimedBy`
- `durationMillis`

典型语义如下：

- 规划后：`phase=planned`, `percent=0`
- 依赖满足：`phase=ready`, `percent=5`
- 成员执行中：`phase=running`, `percent=15`
- 成员主动保活：`phase=heartbeat`, `heartbeatCount + 1`
- 完成 / 失败 / 阻塞：`percent=100`

这几个字段的目的不是伪造“精确进度条”，而是给 CLI、TUI、ACP 宿主一个统一的结构化运行态快照。

### 7.6 Team task 控制动作也会发事件

除了规划、派发、完成这些主流程事件外，下面这些运行时控制动作现在也会统一发出 `TEAM_TASK_UPDATED`：

- `claimTask(...)`
- `releaseTask(...)`
- `reassignTask(...)`
- `heartbeatTask(...)`

这意味着：

- 终端可以看到任务被认领、转派、保活的过程；
- ACP 宿主可以继续只消费标准 `tool_call_update`，但 `rawOutput` 里会拿到更完整的任务字段；
- 你不需要自己再额外拼一层“团队任务状态同步协议”。

---

## 8. 信息管理（Message Management）

### 8.1 消息模型

`AgentTeamMessage` 字段：

- `id`
- `fromMemberId`
- `toMemberId`（`*` 表示广播）
- `type`
- `taskId`
- `content`
- `createdAt`

### 8.2 消息控制 API

- `publishMessage(...)`
- `sendMessage(from, to, type, taskId, content)`
- `broadcastMessage(from, type, taskId, content)`
- `listMessages()`
- `listMessagesFor(memberId, limit)`

### 8.3 消息历史注入

开启 `includeMessageHistoryInDispatch=true` 时，成员执行 prompt 会带上近期团队消息，有助于跨成员协同。

---

## 9. 队员主动协作：`team_*` 内置工具

当前版本支持把 Team 工具自动注入到成员运行时（默认开启）：

`AgentTeamOptions.enableMemberTeamTools = true`

可用工具：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

这意味着：成员不必被动等待 Lead 指令，模型可以在执行中主动协作。

---

## 10. AgentTeamOptions 参数建议

### 调度

- `parallelDispatch`: 默认 `true`
- `maxConcurrency`: 默认 `4`
- `maxRounds`: 默认 `64`

### 容错

- `continueOnMemberError`: 默认 `true`
- `broadcastOnPlannerFailure`: 默认 `true`
- `failOnUnknownMember`: 默认 `false`

### 任务上下文注入

- `includeOriginalObjectiveInDispatch`: 默认 `true`
- `includeTaskContextInDispatch`: 默认 `true`

### 消息相关

- `enableMessageBus`: 默认 `true`
- `includeMessageHistoryInDispatch`: 默认 `true`
- `messageHistoryLimit`: 默认 `20`

### 治理

- `requirePlanApproval`: 默认 `false`
- `allowDynamicMemberRegistration`: 默认 `true`

### Team 扩展

- `taskClaimTimeoutMillis`: 默认 `0`（关闭）
- `enableMemberTeamTools`: 默认 `true`

---

## 11. Hook 与 PlanApproval

### 11.1 PlanApproval

在派发前审批规划结果：

```java
.planApproval((objective, plan, members, options) -> {
    return plan != null && plan.getTasks() != null && !plan.getTasks().isEmpty();
})
```

### 11.2 Hook

监听团队执行关键阶段：

- `beforePlan`
- `afterPlan`
- `beforeTask`
- `afterTask`
- `afterSynthesis`
- `onMessage`

可用于：审计、埋点、告警、指标上报。

---

## 12. 常见问题

### Q1：队员只能是 ReAct Agent 吗？

不是。任何 `Agent` 都可以作为成员，包括：

- `Agents.react()`
- `Agents.codeAct()`
- 自定义 `runtime(...)` 的 Agent

前提是该 runtime 使用 `toolRegistry/toolExecutor` 机制，才能使用 `team_*` 工具。

### Q2：为什么我看到任务阻塞？

常见原因：

- 依赖任务失败或未完成；
- 任务依赖写错 id；
- `maxRounds` 太小导致提前结束。

### Q3：是否支持生产级追踪？

支持。可结合 `AgentTraceListener` + exporter，把 Team 链路接入你的观测系统。

---

## 13. 对应测试与验证命令

主要测试：

- `AgentTeamTest`
- `AgentTeamTaskBoardTest`
- `DoubaoAgentTeamBestPracticeTest`

运行示例：

```bash
mvn -pl ai4j -DskipTests=false "-Dtest=AgentTeamTest,AgentTeamTaskBoardTest" test
```
---

## 附录：完整类/函数/变量明细

- 若你需要逐类查看 Agent Teams 的全部字段与方法，请参考：
  - [Agent Teams 全量 API 参考（类/函数/变量 + Demo + 预期）](./agent-teams-api-reference)
