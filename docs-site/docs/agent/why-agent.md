# Why Agent

如果 `Core SDK` 解决的是“模型、工具、MCP、记忆这些能力怎么接起来”，那么 `Agent` 解决的就是“这些能力怎样在一个可持续运行的 runtime 里协同工作”。

## 1. 什么时候该进入 Agent

下面这些信号，说明你已经不该只停留在基础 SDK 调用：

- 你需要多轮推理，而不是一次请求一次回答
- 你希望模型按需决定是否调用工具、何时继续、何时停止
- 你需要把 tool result、阶段状态、任务上下文回灌到下一轮推理
- 你开始关心 workflow、subagent、team、trace，而不是只关心 prompt

一句话判断：

> 当问题从“怎么调模型”升级为“怎么组织运行时”时，就该进入 `Agent`。

## 2. Agent 解决的不是聊天壳，而是 runtime 问题

`ai4j-agent/` 这层真正收敛的是五类 runtime 问题：

- `runtime loop`：一轮结束后要不要继续
- `tool execution`：哪些工具可见、由谁执行、如何治理
- `memory`：用户输入、模型输出、工具结果怎样跨步保留
- `orchestration`：单 Agent、StateGraph、SubAgent、Team 该怎么选
- `observability`：过程如何被 trace、调试和审计

这也是它和“封一个聊天助手 builder”的根本区别。

## 3. 模块路径和真实边界

主模块路径：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent`

优先记住这些包：

- `model`：`AgentModelClient`、`ChatModelClient`、`ResponsesModelClient`
- `runtime`：`ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime`
- `tool`：`AgentToolRegistry`、`ToolExecutor`、`ToolUtilRegistry`
- `memory`：`AgentMemory`、`InMemoryAgentMemory`、`JdbcAgentMemory`
- `workflow`：`SequentialWorkflow`、`StateGraphWorkflow`
- `subagent`：`SubAgentDefinition`、`StaticSubAgentRegistry`、`HandoffPolicy`
- `team`：`AgentTeam`、`AgentTeamTaskBoard`、`AgentTeamMessageBus`
- `trace`：`AgentTraceListener`、`TraceExporter`、`Jsonl/OpenTelemetry/Langfuse`

## 4. 和相邻章节的边界

### 和 Core SDK 的边界

`Core SDK` 负责能力基座：

- 模型访问
- Function Call
- Skill
- MCP
- ChatMemory
- RAG / Vector / Search

`Agent` 负责运行时组织：

- step loop
- memory 回灌
- tool governance
- orchestration
- trace

所以 `Agent` 不是“更高级的 SDK 页面”，而是上层 runtime。

### 和 Coding Agent 的边界

`Agent` 是通用智能体运行时。

`Coding Agent` 是把这套运行时带进“本地代码仓任务”后的产品化层，额外补上：

- workspace-aware tools
- approvals
- session persistence
- CLI / TUI / ACP host

如果你做业务 Agent、平台 Agent、工作流 Agent，先看 `Agent`。
如果你做本地 coding assistant，先看 `Coding Agent`。

### 和 Flowgram 的边界

`Agent` 偏“模型在 runtime 中决定下一步”。

`Flowgram` 偏“你先把节点图、schema 和任务 API 设计清楚，再交给后端稳定执行”。

如果任务天然像流程图，优先看 `Flowgram`。
如果任务需要更自由的推理回路，优先看 `Agent`。

## 5. 这层的优势是什么

- 它建立在现有 `ai4j` 能力之上，不需要重新发明模型接入层
- 它把 runtime、memory、tool、orchestration、trace 明确拆包，便于解释和扩展
- 它兼容 Java 8 项目，不要求你先引入更重的第三方 agent 框架
- 它既支持最小单 Agent，也支持 handoff、team、workflow 这些更复杂的协作面

## 6. 推荐阅读顺序

1. [Architecture](/docs/agent/architecture)
2. [Quickstart](/docs/agent/quickstart)
3. [Model Client Selection](/docs/agent/model-client-selection)
4. [Memory and State](/docs/agent/memory-and-state)
5. [Tools and Registry](/docs/agent/tools-and-registry)
6. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
7. [StateGraph](/docs/agent/orchestration/stategraph)
8. [Subagent Handoff](/docs/agent/orchestration/subagent-handoff)
9. [Teams](/docs/agent/orchestration/teams)
10. [Trace](/docs/agent/observability/trace)

如果你希望先跑起来，再回来理解设计，下一页建议直接看 [Agent Quickstart](/docs/agent/quickstart)。
