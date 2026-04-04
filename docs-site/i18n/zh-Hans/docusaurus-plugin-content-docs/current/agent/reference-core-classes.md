---
sidebar_position: 14
---

# Agent 核心类参考手册（源码对齐版）

本页按“模块 -> 类 -> 关键字段/方法 -> 默认行为”组织，适合作为开发时速查表。

---

## 1. 顶层入口（`agent`）

### 1.1 `Agent`

职责：统一运行入口。

关键方法：

- `run(AgentRequest)`：同步调用
- `runStream(AgentRequest, AgentListener)`：流式调用
- `newSession()`：创建独立会话（内存隔离）

### 1.2 `AgentSession`

职责：承载一次会话上下文。

关键方法：

- `run(String input)`
- `run(AgentRequest)`
- `runStream(...)`
- `getContext()`：可在高级场景下读取/修改会话 context

### 1.3 `AgentBuilder`

必填：

- `modelClient(...)`
- `model(...)`

常用参数分组：

- 运行时：`runtime(...)`、`options(...)`
- 提示词：`systemPrompt(...)`、`instructions(...)`
- 采样：`temperature/topP/maxOutputTokens`
- 工具：`toolRegistry(...)`、`toolExecutor(...)`
- CodeAct：`codeExecutor(...)`、`codeActOptions(...)`
- SubAgent：`subAgent(...)`、`handoffPolicy(...)`
- 观测：`traceExporter(...)`、`traceConfig(...)`、`eventPublisher(...)`

构建默认值（`build()` 时补齐）：

- `runtime` -> `ReActRuntime`
- `memorySupplier` -> `InMemoryAgentMemory::new`
- `toolRegistry` -> `StaticToolRegistry.empty()`
- `toolExecutor` -> `ToolUtilExecutor`
- `codeExecutor` -> `GraalVmCodeExecutor`

### 1.4 `Agents`

工厂方法：

- `builder()`
- `react()`
- `codeAct()`
- `deepResearch()`
- `team()`

### 1.5 `AgentContext`

职责：运行时共享参数容器。

主要字段：

- 模型：`modelClient`、`model`、`reasoning`、`toolChoice`
- 提示：`systemPrompt`、`instructions`
- 工具：`toolRegistry`、`toolExecutor`
- 执行：`options`、`codeActOptions`、`codeExecutor`
- 会话：`memory`
- 观测：`eventPublisher`
- 扩展：`extraBody`

---

## 2. Runtime 层（`agent.runtime`）

### 2.1 `BaseAgentRuntime`

职责：统一 step-loop 模板。

核心流程：

1. 读 `AgentMemory` 组 prompt
2. 调模型
3. 解析 tool calls
4. 执行工具并写回 memory
5. 继续下一步，直到终止条件

核心参数来源：`AgentOptions`

- `maxSteps`
- `stream`

### 2.2 `ReActRuntime`

默认运行时，适用于大多数“模型 + 工具”场景。

### 2.3 `CodeActRuntime`

代码驱动执行：模型输出代码块，由 `CodeExecutor` 执行。

关键特性：

- 支持 `python/js`
- 可在代码中调用工具
- `CodeActOptions.reAct` 控制执行后是否再回模型总结

### 2.4 `DeepResearchRuntime`

规划优先的研究型运行时，适合证据收集与结构化汇总。

---

## 3. Model 层（`agent.model`）

### 3.1 `AgentModelClient`

统一模型接口：

- `create(AgentPrompt)`
- `createStream(AgentPrompt, AgentModelStreamListener)`

### 3.2 `AgentPrompt`

运行时给模型的标准输入结构，包含：

- `model`
- `items`
- `systemPrompt`
- `instructions`
- `tools`
- `stream`
- `reasoning` 等扩展字段

### 3.3 `AgentModelResult`

模型层标准输出：

- `outputText`
- `toolCalls`
- `memoryItems`
- `rawResponse`

### 3.4 适配器

- `ResponsesModelClient`
- `ChatModelClient`

可按同样接口扩展第三方模型。

---

## 4. Tool 层（`agent.tool`）

### 4.1 注册器

- `AgentToolRegistry`
- `StaticToolRegistry`
- `ToolUtilRegistry`
- `CompositeToolRegistry`

### 4.2 执行器

- `ToolExecutor`
- 默认：`ToolUtilExecutor`

### 4.3 Tool 数据结构

- `AgentToolCall`
- `AgentToolResult`

---

## 5. Memory 层（`agent.memory`）

- `AgentMemory`
- `InMemoryAgentMemory`
- `MemoryCompressor`
- `WindowedMemoryCompressor`
- `MemorySnapshot`

推荐在长会话中开启压缩策略。

---

## 6. Workflow 层（`agent.workflow`）

- `SequentialWorkflow`
- `StateGraphWorkflow`
- `AgentNode`
- `WorkflowContext`
- `WorkflowAgent`

用途：把多个 Agent 节点编排成可复用流程。

---

## 7. SubAgent 层（`agent.subagent`）

核心类：

- `SubAgentDefinition`
- `SubAgentRegistry`
- `StaticSubAgentRegistry`
- `SubAgentToolExecutor`
- `HandoffPolicy`
- `HandoffFailureAction`
- `HandoffContext`

适合主从委派，不适合强协作任务板场景。

---

## 8. Team 层（`agent.team`）

### 8.1 编排核心

- `AgentTeam`
- `AgentTeamBuilder`
- `AgentTeamPlanner`
- `AgentTeamSynthesizer`
- `LlmAgentTeamPlanner`
- `LlmAgentTeamSynthesizer`

`AgentTeamBuilder` 关键入口：

- `leadAgent(...)`（推荐默认）
- `plannerAgent(...)`（可选覆盖）
- `synthesizerAgent(...)`（可选覆盖）
- `member(...)`
- `options(...)`
- `planApproval(...)`
- `hook(...)`
- `messageBus(...)`
- `teamId(...)`
- `stateStore(...)`
- `storageDirectory(...)`

### 8.2 任务模型与状态

- `AgentTeamTask`：`id/memberId/task/context/dependsOn`
- `AgentTeamTaskBoard`：任务状态流转与依赖计算
- `AgentTeamTaskState`：含 `claimedBy/lastHeartbeatTime/output/error`
- `AgentTeamTaskStatus`：`PENDING/READY/IN_PROGRESS/COMPLETED/FAILED/BLOCKED`

### 8.3 协作与治理

- `AgentTeamMessage`
- `AgentTeamMessageBus`
- `InMemoryAgentTeamMessageBus`
- `FileAgentTeamMessageBus`
- `AgentTeamState`
- `AgentTeamMemberSnapshot`
- `AgentTeamStateStore`
- `InMemoryAgentTeamStateStore`
- `FileAgentTeamStateStore`
- `AgentTeamPlanApproval`
- `AgentTeamHook`
- `AgentTeamControl`

`AgentTeamControl` 当前能力：

- 队员管理：`registerMember/unregisterMember/listMembers`
- 信息管理：`listMessages/listMessagesFor/sendMessage/broadcastMessage/publishMessage`
- 任务管理：`listTaskStates/claimTask/releaseTask/reassignTask/heartbeatTask`

`AgentTeam` 现在额外提供的状态接口：

- `getTeamId()`
- `snapshotState()`
- `loadPersistedState()`
- `restoreState(...)`
- `clearPersistedState()`

默认文件持久化规则：

- 当 builder 只提供 `storageDirectory(...)` 时
- `state` 会落到 `<storageDirectory>/state/<teamId>.json`
- `mailbox` 会落到 `<storageDirectory>/mailbox/<teamId>.jsonl`
- 新建同 `teamId` 的 Team 后，可显式调用 `loadPersistedState()` 恢复运行快照

### 8.4 Team 工具（成员主动协作）

新增包：`agent.team.tool`

- `AgentTeamToolRegistry`
- `AgentTeamToolExecutor`

默认向成员注入的工具：

- `team_send_message`
- `team_broadcast`
- `team_list_tasks`
- `team_claim_task`
- `team_release_task`
- `team_reassign_task`
- `team_heartbeat_task`

控制开关：`AgentTeamOptions.enableMemberTeamTools`

### 8.5 `AgentTeamOptions` 速查

- 调度：`parallelDispatch/maxConcurrency/maxRounds`
- 容错：`continueOnMemberError/broadcastOnPlannerFailure/failOnUnknownMember`
- 上下文：`includeOriginalObjectiveInDispatch/includeTaskContextInDispatch`
- 消息：`enableMessageBus/includeMessageHistoryInDispatch/messageHistoryLimit`
- 治理：`requirePlanApproval/allowDynamicMemberRegistration`
- 任务：`taskClaimTimeoutMillis`
- 协作工具：`enableMemberTeamTools`

### 8.6 `AgentTeamResult` 输出

执行后返回：

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

---

## 9. Trace 层（`agent.trace`）

- `TraceSpan`
  - 基础字段：`traceId/spanId/parentSpanId/name/type/status/startTime/endTime/error`
  - 扩展字段：`attributes/events/metrics`
- `TraceSpanEvent`
  - `timestamp/name/attributes`
- `TraceMetrics`
  - `durationMillis/promptTokens/completionTokens/totalTokens/inputCost/outputCost/totalCost/currency`
- `TraceSpanType`
  - `RUN/STEP/MODEL/TOOL/HANDOFF/TEAM_TASK/MEMORY/FLOWGRAM_TASK/FLOWGRAM_NODE`
- `TraceSpanStatus`
  - `OK/ERROR/CANCELED`
- `TraceConfig`
  - `recordModelInput/recordModelOutput/recordToolArgs/recordToolOutput/recordMetrics/maxFieldLength/masker/pricingResolver`
- `TracePricing`
  - `inputCostPerMillionTokens/outputCostPerMillionTokens/currency`
- `TracePricingResolver`
  - `resolve(model) -> TracePricing`
- `TraceExporter`
  - 统一导出接口：`export(TraceSpan)`
- 内置 exporter
  - `ConsoleTraceExporter`
  - `InMemoryTraceExporter`
  - `CompositeTraceExporter`
  - `JsonlTraceExporter`
  - `OpenTelemetryTraceExporter`
  - `LangfuseTraceExporter`
- `AgentTraceListener`
  - 把 `AgentEvent` 映射成 trace
  - 当前覆盖 `MODEL_REASONING`、`MODEL_RETRY`、`HANDOFF_*`、`TEAM_*`、`MEMORY_COMPRESS`

用途：

- 在线追踪
- 链路分析
- 问题回放
- 向 OTel / 日志文件 / 测试断言输出统一 trace 数据

---

## 10. CodeAct 执行层（`agent.codeact`）

- `CodeActOptions`
- `CodeExecutor`
- `CodeExecutionRequest`
- `CodeExecutionResult`
- `GraalVmCodeExecutor`

可替换为自定义沙箱执行器。

---

## 11. 推荐测试索引

- `AgentTeamTest`
- `AgentTeamTaskBoardTest`
- `DoubaoAgentTeamBestPracticeTest`
- `CodeActRuntimeTest`
- `CodeActRuntimeWithTraceTest`
- `SubAgentRuntimeTest`
- `SubAgentParallelFallbackTest`
- `HandoffPolicyTest`

建议把这些测试作为文档示例的行为真值来源。
