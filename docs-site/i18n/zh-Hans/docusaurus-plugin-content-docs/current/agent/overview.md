---
sidebar_position: 1
---

# Agent 架构总览

本章节面向要把 Agent 真正上线的开发者，目标是让你快速回答三个问题：

1. AI4J Agent 由哪些模块组成？
2. 应该如何选择运行时（ReAct / CodeAct / DeepResearch）？
3. 单 Agent、SubAgent、Agent Teams、StateGraph 的边界分别是什么？

---

## 1. Agent 能力地图（从核心到扩展）

AI4J Agent 不是一个只能聊天的封装，而是一套可拆分、可替换、可观测的工程化框架：

- 模型适配层：统一 Chat/Responses 两类协议；
- 运行时层：ReAct、CodeAct、DeepResearch；
- 工具层：Function Tool、MCP Tool、自定义 ToolExecutor；
- 记忆层：会话记忆、窗口压缩、摘要压缩；
- 编排层：顺序流、StateGraph、SubAgent、Agent Teams；
- 治理与观测层：handoff policy、trace/exporter、事件监听；
- 交付入口层：SDK 内嵌接入、`ai4j-cli` coding-agent CLI/TUI。

核心入口类：

- `Agent`
- `AgentBuilder`
- `AgentSession`
- `AgentContext`
- `Agents`

---

## 2. 模块结构与职责

### 2.1 `agent`

- 负责组装运行时、模型、工具、记忆；
- `Agent.newSession()` 用于创建独立会话上下文；
- `AgentBuilder.build()` 会自动补齐默认组件。

### 2.2 `agent.runtime`

- `BaseAgentRuntime`：统一步骤循环（step loop）；
- `ReActRuntime`：默认通用运行时；
- `CodeActRuntime`：代码驱动工具调用；
- `DeepResearchRuntime`：规划优先、研究型任务。

### 2.3 `agent.model`

- `AgentModelClient` 统一模型调用接口；
- `ResponsesModelClient` 与 `ChatModelClient` 分别适配不同协议；
- `AgentPrompt` / `AgentModelResult` 作为运行时与模型适配层之间的稳定契约。

### 2.4 `agent.tool`

- `AgentToolRegistry`：决定“暴露给模型哪些工具”；
- `ToolExecutor`：决定“工具调用如何执行”；
- 默认执行器 `ToolUtilExecutor` 支持 Function/MCP。

### 2.5 `agent.memory`

- 默认 `InMemoryAgentMemory`；
- 支持 `MemoryCompressor` 和窗口压缩策略。

### 2.6 `agent.workflow`

- 顺序编排：`SequentialWorkflow`；
- 状态图编排：`StateGraphWorkflow`；
- 可做分支、循环、条件路由。

### 2.7 `agent.subagent`

- 主 Agent 通过 handoff 工具调用子代理；
- `HandoffPolicy` 管理超时、深度、失败回退等治理策略。

### 2.8 `agent.team`

- 多成员协作（Lead + Members + 任务板 + 消息总线）；
- 支持任务依赖、轮次调度、消息广播、任务认领/转派。

### 2.9 `agent.trace`

- 统一追踪事件和 span；
- 可接内存导出器、控制台导出器或自定义 exporter。

---

## 3. 运行时怎么选

### ReActRuntime（默认）

适合大多数业务 Agent：

- 文本任务 + 工具调用；
- 多轮推理但不需要代码执行环境；
- 接入与维护成本最低。

### CodeActRuntime

适合“批量工具调用 + 代码组织”任务：

- 一次生成代码，代码内部多次调用工具；
- 对复杂数据加工更稳定；
- 可开启 `CodeActOptions.reAct=true` 形成“代码执行后再总结”的双阶段模式。

### DeepResearchRuntime

适合研究型任务：

- 先规划，再分阶段收集证据，再汇总；
- 更强调结构化输出和来源一致性。

---

## 4. 单 Agent 到多 Agent 的演进建议

推荐按复杂度逐步演进，而不是一开始就上 Teams：

1. 单 Agent（ReAct）：先稳定模型与工具白名单；
2. CodeAct：处理复杂数据加工或多工具批处理；
3. StateGraph：任务需要显式分支/循环；
4. SubAgent：主从委派、严格 handoff 治理；
5. Agent Teams：多角色协作、共享任务板、成员间通信；
6. Trace + 在线观测：为排障与审计提供依据。

---

## 5. 最小示例（可运行）

```java
Agent agent = Agents.react()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("你是一个严谨的助手")
        .instructions("需要时再调用工具")
        .toolRegistry(java.util.Arrays.asList("queryWeather"), null)
        .build();

AgentResult result = agent.run(AgentRequest.builder()
        .input("请给出北京今天的天气摘要")
        .build());

System.out.println(result.getOutputText());
```

---

## 6. Agent 章节阅读顺序

建议按下面顺序读完：

1. `Coding Agent CLI 与 TUI`
2. `多 Provider Profile 实战`
3. `Coding Agent 命令参考手册`
4. `Provider 配置样例`
5. `自定义 Agent 开发指南`
6. `System Prompt 与 Instructions`
7. `Model Client 选择与适配`
8. `Runtime 实现详解`
9. `Memory 管理`
10. `Workflow StateGraph`
11. `CodeAct`（含自定义沙箱）
12. `SubAgent 与 handoff policy`
13. `Agent Teams`
14. `Trace 可观测`
15. `核心类参考手册`

---

## 7. 关键测试索引

你可以直接运行这些测试来理解行为：

- `WeatherAgentWorkflowTest`
- `StateGraphWorkflowTest`
- `CodeActRuntimeTest`
- `CodeActRuntimeWithTraceTest`
- `SubAgentRuntimeTest`
- `SubAgentParallelFallbackTest`
- `HandoffPolicyTest`
- `AgentTeamTest`
- `AgentTeamTaskBoardTest`
- `DoubaoAgentTeamBestPracticeTest`

如果你希望把 Agent 模块接入生产，建议至少跑通：

1. 一套单 Agent 回归；
2. 一套 CodeAct 回归；
3. 一套 Team/Workflow 回归；
4. 一套 Trace 验证回归。
