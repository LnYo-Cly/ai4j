---
sidebar_position: 15
---

# Agent Teams 全量 API 参考（类/函数/变量 + Demo + 预期）

本页覆盖 `io.github.lnyocly.ai4j.agent.team` 与 `io.github.lnyocly.ai4j.agent.team.tool` 包中所有源码类，
并按“类 -> 变量 -> 函数”展开说明，方便排查与二次开发。

> 文档由脚本从源码生成，建议在 Agent Teams 代码变更后重新执行：
> `python docs-site/scripts/generate_agent_teams_api_docs.py`

## 1. 快速 Demo

```java
Agent lead = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是团队负责人，先规划再汇总")
        .build();

Agent backend = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

Agent frontend = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .build();

AgentTeam team = Agents.team()
        .leadAgent(lead)
        .member(AgentTeamMember.builder().id("backend").name("后端").agent(backend).build())
        .member(AgentTeamMember.builder().id("frontend").name("前端").agent(frontend).build())
        .options(AgentTeamOptions.builder()
                .parallelDispatch(true)
                .continueOnMemberError(true)
                .maxRounds(64)
                .build())
        .build();

AgentTeamResult result = team.run("输出本周交付计划");
System.out.println(result.getOutput());
```

## 2. 预期行为（用于验收）

- 预期 1：Planner 先产出 `tasks`，任务进入 `PENDING/READY`。
- 预期 2：并发开启时，多成员任务会并行执行；串行模式则按批次单线程执行。
- 预期 3：成员成功后任务转为 `COMPLETED`；异常转 `FAILED`，依赖任务可能转 `BLOCKED`。
- 预期 4：`continueOnMemberError=true` 时，失败任务不会中断整个团队，最终仍会尝试汇总。
- 预期 5：启用 `enableMemberTeamTools` 后，成员可调用 `team_send_message/team_claim_task/...` 完成主动协作。

## 3. 类/变量/函数全量说明

### class `AgentTeam`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java:26`
- 职责：Agent Teams 的总调度器。负责规划、任务派发、并发执行、消息协作、最终汇总。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `SYSTEM_MEMBER` | `String` | `private` | `static final` | 成员相关字段，描述团队角色或成员映射。 |
| `LEAD_MEMBER` | `String` | `private` | `static final` | 成员相关字段，描述团队角色或成员映射。 |
| `planner` | `AgentTeamPlanner` | `private` | `final` | 规划相关字段，保存 planner 输入/输出。 |
| `synthesizer` | `AgentTeamSynthesizer` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `options` | `AgentTeamOptions` | `private` | `final` | 运行配置对象，影响行为和策略。 |
| `orderedMembers` | `List<RuntimeMember>` | `private` | `final` | 成员相关字段，描述团队角色或成员映射。 |
| `membersById` | `Map<String, RuntimeMember>` | `private` | `final` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `messageBus` | `AgentTeamMessageBus` | `private` | `final` | 消息相关字段，承载协作通信数据。 |
| `planApproval` | `AgentTeamPlanApproval` | `private` | `final` | 规划相关字段，保存 planner 输入/输出。 |
| `hooks` | `List<AgentTeamHook>` | `private` | `final` | 集合字段，用于维护批量数据或索引映射。 |
| `teamToolRegistry` | `AgentTeamToolRegistry` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `activeBoard` | `AgentTeamTaskBoard` | `private` | `volatile` | 运行期状态或配置字段，参与该类的核心行为。 |
| `activeObjective` | `String` | `private` | `volatile` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `builder()` | `AgentTeamBuilder` | `public` | `static` | 构建入口方法，用于创建并返回目标对象。 |
| `registerMember(AgentTeamMember member)` | `void` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `unregisterMember(String memberId)` | `boolean` | `public` | `-` | 布尔判定方法，返回条件是否满足。 |
| `listMembers()` | `List<AgentTeamMember>` | `public` | `-` | 查询方法，读取当前快照或历史记录。 |
| `listMessages()` | `List<AgentTeamMessage>` | `public` | `-` | 查询方法，读取当前快照或历史记录。 |
| `listMessagesFor(String memberId, int limit)` | `List<AgentTeamMessage>` | `public` | `-` | 查询方法，读取当前快照或历史记录。 |
| `publishMessage(AgentTeamMessage message)` | `void` | `public` | `-` | 消息发布方法，向单成员或全体广播协作信息。 |
| `sendMessage(String fromMemberId, String toMemberId, String type, String taskId, String content)` | `void` | `public` | `-` | 消息发布方法，向单成员或全体广播协作信息。 |
| `broadcastMessage(String fromMemberId, String type, String taskId, String content)` | `void` | `public` | `-` | 消息发布方法，向单成员或全体广播协作信息。 |
| `listTaskStates()` | `List<AgentTeamTaskState>` | `public` | `-` | 查询方法，读取当前快照或历史记录。 |
| `claimTask(String taskId, String memberId)` | `boolean` | `public` | `-` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `releaseTask(String taskId, String memberId, String reason)` | `boolean` | `public` | `-` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `reassignTask(String taskId, String fromMemberId, String toMemberId)` | `boolean` | `public` | `-` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `heartbeatTask(String taskId, String memberId)` | `boolean` | `public` | `-` | 运行保活与恢复方法，用于检测超时并回收任务。 |
| `run(String objective)` | `AgentTeamResult` | `public` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `run(AgentRequest request)` | `AgentTeamResult` | `public` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `ensurePlanApproved(String objective, AgentTeamPlan plan, List<AgentTeamMember> members)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `dispatchTasks(String objective, AgentTeamTaskBoard board)` | `DispatchOutcome` | `private` | `-` | 任务派发方法，负责轮次调度与执行分配。 |
| `call()` | `AgentTeamMemberResult` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `waitForFuture(Future<AgentTeamMemberResult> future)` | `AgentTeamMemberResult` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `runMemberTask(PreparedDispatch dispatch, String input)` | `AgentResult` | `private` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `buildDispatchInput(String objective, RuntimeMember member, AgentTeamTask task)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `publishMessageInternal(AgentTeamMessage message)` | `void` | `private` | `-` | 消息发布方法，向单成员或全体广播协作信息。 |
| `resolveMember(String requestedId)` | `RuntimeMember` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `snapshotMembers()` | `List<AgentTeamMember>` | `private` | `-` | 查询方法，读取当前快照或历史记录。 |
| `currentBoard()` | `AgentTeamTaskBoard` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `rememberTaskStates(List<AgentTeamTaskState> taskStates)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `currentObjective()` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `resolveMemberView(String memberId)` | `AgentTeamMember` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `fireTaskStateChanged(AgentTeamTaskState state, AgentTeamMember member, String detail)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `validateKnownMemberId(String memberId, boolean allowReserved, String fieldName)` | `void` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `isReservedMember(String memberId)` | `boolean` | `private` | `-` | 布尔判定方法，返回条件是否满足。 |
| `fireBeforePlan(String objective, List<AgentTeamMember> members)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `fireAfterPlan(String objective, AgentTeamPlan plan)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `fireBeforeTask(String objective, AgentTeamTask task, AgentTeamMember member)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `fireAfterTask(String objective, AgentTeamMemberResult result)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `fireAfterSynthesis(String objective, AgentResult synthesis)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `normalize(String raw)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `safe(String value)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `firstNonBlank(String... values)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `safeShort(String value)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `toText(Object value)` | `String` | `private` | `-` | 转换方法，在内部对象与公开对象之间映射。 |

### class `AgentTeam.DispatchOutcome`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java:874`
- 职责：AgentTeam 内部派发汇总对象，记录成员结果和轮次数。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `results` | `List<AgentTeamMemberResult>` | `private` | `final` | 结果字段，存储执行输出或汇总产物。 |
| `rounds` | `int` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `DispatchOutcome(List<AgentTeamMemberResult> results, int rounds)` | `(constructor)` | `private` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |

### class `AgentTeam.PreparedDispatch`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java:862`
- 职责：AgentTeam 内部派发单元，绑定 task/member 用于执行轮次。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `taskId` | `String` | `private` | `final` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `task` | `AgentTeamTask` | `private` | `final` | 任务相关字段，保存任务定义或运行态信息。 |
| `member` | `RuntimeMember` | `private` | `final` | 成员相关字段，描述团队角色或成员映射。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `PreparedDispatch(String taskId, AgentTeamTask task, RuntimeMember member)` | `(constructor)` | `private` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |

### class `AgentTeam.RuntimeMember`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeam.java:824`
- 职责：AgentTeam 内部成员运行态对象，包装成员定义并缓存执行引用。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `private` | `final` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `name` | `String` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `description` | `String` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `agent` | `Agent` | `private` | `final` | Agent 执行实例引用。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `RuntimeMember(String id, String name, String description, Agent agent)` | `(constructor)` | `private` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `from(AgentTeamMember member)` | `RuntimeMember` | `private` | `static` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `toPublicMember()` | `AgentTeamMember` | `private` | `-` | 转换方法，在内部对象与公开对象之间映射。 |

### class `AgentTeamAgentRuntime`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamAgentRuntime.java:14`
- 职责：该类型用于 Agent Teams 运行链路中的结构定义或执行逻辑。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `template` | `AgentTeamBuilder` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentTeamAgentRuntime(AgentTeamBuilder template)` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `run(AgentContext context, AgentRequest request)` | `AgentResult` | `public` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `runStream(AgentContext context, AgentRequest request, AgentListener listener)` | `void` | `public` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `prepareTeam(AgentListener listener)` | `AgentTeam` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `copyBuilder(AgentTeamBuilder source)` | `AgentTeamBuilder` | `private` | `-` | 转换方法，在内部对象与公开对象之间映射。 |
| `toAgentResult(AgentTeamResult result)` | `AgentResult` | `private` | `-` | 转换方法，在内部对象与公开对象之间映射。 |

### class `AgentTeamBuilder`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamBuilder.java:12`
- 职责：构建 AgentTeam 的入口，组装 lead/planner/synthesizer/member/options。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `leadAgent` | `Agent` | `private` | `-` | Agent 执行实例引用。 |
| `plannerAgent` | `Agent` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |
| `synthesizerAgent` | `Agent` | `private` | `-` | Agent 执行实例引用。 |
| `planner` | `AgentTeamPlanner` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |
| `synthesizer` | `AgentTeamSynthesizer` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `options` | `AgentTeamOptions` | `private` | `-` | 运行配置对象，影响行为和策略。 |
| `messageBus` | `AgentTeamMessageBus` | `private` | `-` | 消息相关字段，承载协作通信数据。 |
| `planApproval` | `AgentTeamPlanApproval` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `builder()` | `AgentTeamBuilder` | `public` | `static` | 构建入口方法，用于创建并返回目标对象。 |
| `getLeadAgent()` | `Agent` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getPlannerAgent()` | `Agent` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getSynthesizerAgent()` | `Agent` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getPlanner()` | `AgentTeamPlanner` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getSynthesizer()` | `AgentTeamSynthesizer` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getMembers()` | `List<AgentTeamMember>` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getOptions()` | `AgentTeamOptions` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getMessageBus()` | `AgentTeamMessageBus` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getPlanApproval()` | `AgentTeamPlanApproval` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getHooks()` | `List<AgentTeamHook>` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `leadAgent(Agent leadAgent)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `plannerAgent(Agent plannerAgent)` | `AgentTeamBuilder` | `public` | `-` | 规划方法，根据目标/成员生成任务计划。 |
| `synthesizerAgent(Agent synthesizerAgent)` | `AgentTeamBuilder` | `public` | `-` | 汇总方法，将多成员结果合并为最终输出。 |
| `planner(AgentTeamPlanner planner)` | `AgentTeamBuilder` | `public` | `-` | 规划方法，根据目标/成员生成任务计划。 |
| `synthesizer(AgentTeamSynthesizer synthesizer)` | `AgentTeamBuilder` | `public` | `-` | 汇总方法，将多成员结果合并为最终输出。 |
| `member(AgentTeamMember member)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `members(List<AgentTeamMember> members)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `options(AgentTeamOptions options)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `messageBus(AgentTeamMessageBus messageBus)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `planApproval(AgentTeamPlanApproval planApproval)` | `AgentTeamBuilder` | `public` | `-` | 规划方法，根据目标/成员生成任务计划。 |
| `hook(AgentTeamHook hook)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `hooks(List<AgentTeamHook> hooks)` | `AgentTeamBuilder` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `build()` | `AgentTeam` | `public` | `-` | 构建入口方法，用于创建并返回目标对象。 |
| `buildAgent()` | `Agent` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `get()` | `AgentMemory` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |

### interface `AgentTeamControl`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamControl.java:5`
- 职责：团队运行期控制接口，统一成员、任务、消息操作能力。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamEventHook`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamEventHook.java:12`
- 职责：该类型用于 Agent Teams 运行链路中的结构定义或执行逻辑。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `listener` | `AgentListener` | `private` | `final` | 集合字段，用于维护批量数据或索引映射。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentTeamEventHook()` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `AgentTeamEventHook(AgentListener listener)` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `afterPlan(String objective, AgentTeamPlan plan)` | `void` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `beforeTask(String objective, AgentTeamTask task, AgentTeamMember member)` | `void` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `afterTask(String objective, AgentTeamMemberResult result)` | `void` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `onMessage(AgentTeamMessage message)` | `void` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `buildTaskSummary(AgentTeamTask task, String status)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `normalizeStatus(AgentTeamTaskStatus status)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `resolvePercent(String status, Integer statePercent)` | `int` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `emit(AgentEventType type, String message, Map<String, Object> payload)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `firstNonBlank(String... values)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `isBlank(String value)` | `boolean` | `private` | `-` | 布尔判定方法，返回条件是否满足。 |

### interface `AgentTeamHook`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamHook.java:7`
- 职责：生命周期钩子接口，支持在规划/任务/汇总阶段埋点与审计。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamMember`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamMember.java:9`
- 职责：成员定义对象，包含成员身份与绑定 Agent 实例。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `name` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `description` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `agent` | `Agent` | `private` | `-` | Agent 执行实例引用。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `resolveId()` | `String` | `public` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `normalize(String raw)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |

### class `AgentTeamMemberResult`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamMemberResult.java:9`
- 职责：单个任务执行结果对象，记录产出、耗时、错误和状态。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `taskId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `memberId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `memberName` | `String` | `private` | `-` | 成员相关字段，描述团队角色或成员映射。 |
| `task` | `AgentTeamTask` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `taskStatus` | `AgentTeamTaskStatus` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `output` | `String` | `private` | `-` | 结果字段，存储执行输出或汇总产物。 |
| `error` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `rawResult` | `AgentResult` | `private` | `-` | 结果字段，存储执行输出或汇总产物。 |
| `durationMillis` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `isSuccess()` | `boolean` | `public` | `-` | 布尔判定方法，返回条件是否满足。 |

### class `AgentTeamMessage`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamMessage.java:8`
- 职责：团队消息模型，承载 from/to/type/taskId/content。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `fromMemberId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `toMemberId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `type` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `taskId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `content` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `createdAt` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### interface `AgentTeamMessageBus`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamMessageBus.java:5`
- 职责：消息总线抽象，定义 publish/snapshot/historyFor/clear。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamOptions`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamOptions.java:8`
- 职责：团队运行配置对象，控制并发、容错、消息注入、超时回收等行为。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `parallelDispatch` | `boolean` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `maxConcurrency` | `int` | `private` | `-` | 并发或阈值配置字段，影响调度上限。 |
| `continueOnMemberError` | `boolean` | `private` | `-` | 成员相关字段，描述团队角色或成员映射。 |
| `broadcastOnPlannerFailure` | `boolean` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |
| `failOnUnknownMember` | `boolean` | `private` | `-` | 成员相关字段，描述团队角色或成员映射。 |
| `includeOriginalObjectiveInDispatch` | `boolean` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `includeTaskContextInDispatch` | `boolean` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `includeMessageHistoryInDispatch` | `boolean` | `private` | `-` | 消息相关字段，承载协作通信数据。 |
| `messageHistoryLimit` | `int` | `private` | `-` | 消息相关字段，承载协作通信数据。 |
| `enableMessageBus` | `boolean` | `private` | `-` | 布尔开关字段，控制功能启用或治理策略。 |
| `allowDynamicMemberRegistration` | `boolean` | `private` | `-` | 布尔开关字段，控制功能启用或治理策略。 |
| `requirePlanApproval` | `boolean` | `private` | `-` | 布尔开关字段，控制功能启用或治理策略。 |
| `maxRounds` | `int` | `private` | `-` | 并发或阈值配置字段，影响调度上限。 |
| `taskClaimTimeoutMillis` | `long` | `private` | `-` | 超时配置字段，用于控制等待和回收策略。 |
| `enableMemberTeamTools` | `boolean` | `private` | `-` | 布尔开关字段，控制功能启用或治理策略。 |

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamPlan`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamPlan.java:10`
- 职责：Planner 输出后的计划模型，包含任务列表。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `tasks` | `List<AgentTeamTask>` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `rawPlanText` | `String` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |
| `fallback` | `boolean` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### interface `AgentTeamPlanApproval`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamPlanApproval.java:5`
- 职责：计划审批回调，允许在派发前人为/策略拦截。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### interface `AgentTeamPlanner`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamPlanner.java:5`
- 职责：规划器接口，输入目标和成员，输出任务计划。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamResult`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamResult.java:11`
- 职责：一次团队运行的完整结果快照，含计划、成员结果、任务状态、消息、轮次。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `objective` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `plan` | `AgentTeamPlan` | `private` | `-` | 规划相关字段，保存 planner 输入/输出。 |
| `memberResults` | `List<AgentTeamMemberResult>` | `private` | `-` | 成员相关字段，描述团队角色或成员映射。 |
| `taskStates` | `List<AgentTeamTaskState>` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `messages` | `List<AgentTeamMessage>` | `private` | `-` | 消息相关字段，承载协作通信数据。 |
| `rounds` | `int` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `output` | `String` | `private` | `-` | 结果字段，存储执行输出或汇总产物。 |
| `synthesisResult` | `AgentResult` | `private` | `-` | 结果字段，存储执行输出或汇总产物。 |
| `totalDurationMillis` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### interface `AgentTeamSynthesizer`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamSynthesizer.java:7`
- 职责：汇总器接口，将成员结果整合为最终输出。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamTask`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTask.java:10`
- 职责：任务定义模型，包含 id/memberId/task/context/dependsOn。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `memberId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `task` | `String` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `context` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `dependsOn` | `List<String>` | `private` | `-` | 集合字段，用于维护批量数据或索引映射。 |

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `AgentTeamTaskBoard`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskBoard.java:11`
- 职责：任务状态机与依赖调度核心，实现 READY/IN_PROGRESS/COMPLETED 等流转。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `PERCENT_PLANNED` | `int` | `private` | `static final` | 规划相关字段，保存 planner 输入/输出。 |
| `PERCENT_READY` | `int` | `private` | `static final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `PERCENT_IN_PROGRESS` | `int` | `private` | `static final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `PERCENT_TERMINAL` | `int` | `private` | `static final` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentTeamTaskBoard(List<AgentTeamTask> tasks)` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `initialize(List<AgentTeamTask> tasks)` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `normalizedTasks()` | `List<AgentTeamTask>` | `public` | `synchronized` | 规范化与校验方法，保证输入可用和行为一致。 |
| `nextReadyTasks(int maxCount)` | `List<AgentTeamTaskState>` | `public` | `synchronized` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `getTaskState(String taskId)` | `AgentTeamTaskState` | `public` | `synchronized` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `claimTask(String taskId, String memberId)` | `boolean` | `public` | `synchronized` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `releaseTask(String taskId, String memberId, String reason)` | `boolean` | `public` | `synchronized` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `reassignTask(String taskId, String fromMemberId, String toMemberId)` | `boolean` | `public` | `synchronized` | 任务认领/释放/重分配方法，维护任务所有权。 |
| `heartbeatTask(String taskId, String memberId)` | `boolean` | `public` | `synchronized` | 运行保活与恢复方法，用于检测超时并回收任务。 |
| `recoverTimedOutClaims(long timeoutMillis, String reason)` | `int` | `public` | `synchronized` | 运行保活与恢复方法，用于检测超时并回收任务。 |
| `markInProgress(String taskId, String claimedBy)` | `void` | `public` | `synchronized` | 状态写入方法，推进任务状态机到下一个阶段。 |
| `markCompleted(String taskId, String output, long durationMillis)` | `void` | `public` | `synchronized` | 状态写入方法，推进任务状态机到下一个阶段。 |
| `markFailed(String taskId, String error, long durationMillis)` | `void` | `public` | `synchronized` | 状态写入方法，推进任务状态机到下一个阶段。 |
| `markStalledAsBlocked(String reason)` | `void` | `public` | `synchronized` | 状态写入方法，推进任务状态机到下一个阶段。 |
| `hasWorkRemaining()` | `boolean` | `public` | `synchronized` | 布尔判定方法，返回条件是否满足。 |
| `hasFailed()` | `boolean` | `public` | `synchronized` | 布尔判定方法，返回条件是否满足。 |
| `snapshot()` | `List<AgentTeamTaskState>` | `public` | `synchronized` | 查询方法，读取当前快照或历史记录。 |
| `size()` | `int` | `public` | `synchronized` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `refreshStatuses()` | `void` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `resolveTaskKey(String taskId)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `normalizeDependencies(List<String> dependencies)` | `List<String>` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `normalizeId(String raw)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `normalizeMemberId(String memberId)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `isSameMember(String currentClaimedBy, String expectedMemberId)` | `boolean` | `private` | `-` | 布尔判定方法，返回条件是否满足。 |
| `copy(AgentTeamTaskState state)` | `AgentTeamTaskState` | `private` | `-` | 转换方法，在内部对象与公开对象之间映射。 |
| `percentOf(AgentTeamTaskState state)` | `int` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `selectUpdatedAt(AgentTeamTaskState state)` | `long` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `trimToNull(String value)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `firstNonBlank(String... values)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |

### class `AgentTeamTaskState`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskState.java:8`
- 职责：任务运行态对象，记录 claim、heartbeat、输出、错误、耗时。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `taskId` | `String` | `private` | `-` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `task` | `AgentTeamTask` | `private` | `-` | 任务相关字段，保存任务定义或运行态信息。 |
| `status` | `AgentTeamTaskStatus` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `claimedBy` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `startTime` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `endTime` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `durationMillis` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `lastHeartbeatTime` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `phase` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `detail` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `percent` | `Integer` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `updatedAtEpochMs` | `long` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `heartbeatCount` | `int` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |
| `output` | `String` | `private` | `-` | 结果字段，存储执行输出或汇总产物。 |
| `error` | `String` | `private` | `-` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `isTerminal()` | `boolean` | `public` | `-` | 布尔判定方法，返回条件是否满足。 |

### enum `AgentTeamTaskStatus`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/AgentTeamTaskStatus.java:3`
- 职责：任务状态枚举。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

- 无显式方法（或主要由 Lombok 生成 getter/setter/builder）。

### class `InMemoryAgentTeamMessageBus`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/InMemoryAgentTeamMessageBus.java:7`
- 职责：内存消息总线实现，适合单进程场景。

**变量（字段）**

- 无显式字段（或仅由 Lombok/编译器生成）。

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `publish(AgentTeamMessage message)` | `void` | `public` | `synchronized` | 消息发布方法，向单成员或全体广播协作信息。 |
| `snapshot()` | `List<AgentTeamMessage>` | `public` | `synchronized` | 查询方法，读取当前快照或历史记录。 |
| `historyFor(String memberId, int limit)` | `List<AgentTeamMessage>` | `public` | `synchronized` | 查询方法，读取当前快照或历史记录。 |
| `clear()` | `void` | `public` | `synchronized` | 内部辅助方法，服务于该类的核心执行逻辑。 |

### class `LlmAgentTeamPlanner`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamPlanner.java:11`
- 职责：基于 Agent 的默认规划器实现，模型输出计划，失败时可回退简单计划。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `plannerAgent` | `Agent` | `private` | `final` | 规划相关字段，保存 planner 输入/输出。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `LlmAgentTeamPlanner(Agent plannerAgent)` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `plan(String objective, List<AgentTeamMember> members, AgentTeamOptions options)` | `AgentTeamPlan` | `public` | `-` | 规划方法，根据目标/成员生成任务计划。 |
| `buildPlannerPrompt(String objective, List<AgentTeamMember> members)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `fallbackTasks(String objective, List<AgentTeamMember> members)` | `List<AgentTeamTask>` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |

### class `LlmAgentTeamSynthesizer`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/LlmAgentTeamSynthesizer.java:11`
- 职责：基于 Agent 的默认汇总器实现，汇总成员结果为最终答复。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `synthesizerAgent` | `Agent` | `private` | `final` | Agent 执行实例引用。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `LlmAgentTeamSynthesizer(Agent synthesizerAgent)` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |

### class `AgentTeamToolExecutor`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolExecutor.java:13`
- 职责：team_* 工具执行器，将成员工具调用路由到 AgentTeamControl。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `control` | `AgentTeamControl` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `memberId` | `String` | `private` | `final` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `defaultTaskId` | `String` | `private` | `final` | 唯一标识/关联标识字段，用于实体定位或引用。 |
| `delegate` | `ToolExecutor` | `private` | `final` | 运行期状态或配置字段，参与该类的核心行为。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `execute(AgentToolCall call)` | `String` | `public` | `-` | 执行入口方法，驱动主流程并返回执行结果。 |
| `handleSendMessage(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleBroadcast(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleListTasks()` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleClaimTask(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleReleaseTask(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleReassignTask(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `handleHeartbeatTask(JSONObject args)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `parseArguments(String raw)` | `JSONObject` | `private` | `-` | 解析方法，将文本/参数转换为结构化对象。 |
| `resolveTaskId(JSONObject args, boolean required)` | `String` | `private` | `-` | 规范化与校验方法，保证输入可用和行为一致。 |
| `findTaskState(String taskId)` | `AgentTeamTaskState` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `firstString(JSONObject args, String... keys)` | `String` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `baseResult(String action, boolean ok)` | `JSONObject` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |

### class `AgentTeamToolRegistry`

- 源码：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/team/tool/AgentTeamToolRegistry.java:15`
- 职责：team_* 工具注册表，定义并暴露团队内置协作工具。

**变量（字段）**

| 名称 | 类型 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `TOOL_SEND_MESSAGE` | `String` | `public` | `static final` | 消息相关字段，承载协作通信数据。 |
| `TOOL_BROADCAST` | `String` | `public` | `static final` | 运行期状态或配置字段，参与该类的核心行为。 |
| `TOOL_LIST_TASKS` | `String` | `public` | `static final` | 任务相关字段，保存任务定义或运行态信息。 |
| `TOOL_CLAIM_TASK` | `String` | `public` | `static final` | 任务相关字段，保存任务定义或运行态信息。 |
| `TOOL_RELEASE_TASK` | `String` | `public` | `static final` | 任务相关字段，保存任务定义或运行态信息。 |
| `TOOL_REASSIGN_TASK` | `String` | `public` | `static final` | 任务相关字段，保存任务定义或运行态信息。 |
| `TOOL_HEARTBEAT_TASK` | `String` | `public` | `static final` | 任务相关字段，保存任务定义或运行态信息。 |
| `tools` | `List<Object>` | `private` | `final` | 集合字段，用于维护批量数据或索引映射。 |

**函数（方法）**

| 方法 | 返回 | 可见性 | 修饰符 | 说明 |
| --- | --- | --- | --- | --- |
| `AgentTeamToolRegistry()` | `(constructor)` | `public` | `-` | 构造函数，初始化该类型的必要依赖与默认状态。 |
| `getTools()` | `List<Object>` | `public` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `supports(String toolName)` | `boolean` | `public` | `static` | 能力探测方法，判断是否支持某个功能或工具。 |
| `buildTools()` | `List<Object>` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createSendMessageTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createBroadcastTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createListTasksTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createClaimTaskTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createReleaseTaskTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createReassignTaskTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `createHeartbeatTaskTool()` | `Tool` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
| `stringProperty(String description)` | `Tool.Function.Property` | `private` | `-` | 内部辅助方法，服务于该类的核心执行逻辑。 |
