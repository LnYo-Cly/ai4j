---
sidebar_position: 5
---

# Runtime 实现详解（ReAct / CodeAct / DeepResearch）

如果你要做可扩展 Agent，Runtime 是最关键的“策略层”。

本页会按源码结构解释：

- `BaseAgentRuntime` 的主循环怎么跑
- `ReActRuntime / CodeActRuntime / DeepResearchRuntime` 分别做了什么
- 如何写一个你自己的 Runtime

## 1. Runtime 抽象层

`AgentRuntime` 只有两个方法：

- `run(AgentContext, AgentRequest)`
- `runStream(AgentContext, AgentRequest, AgentListener)`

这意味着：

- 你可以完全自定义执行策略
- 也可以继承 `BaseAgentRuntime` 复用通用循环

## 2. BaseAgentRuntime：通用循环骨架

`BaseAgentRuntime.runInternal(...)` 的核心流程：

1. 读取 `AgentOptions`（`maxSteps/stream`）
2. 把用户输入写入 `AgentMemory`
3. 进入 while 循环（`maxSteps <= 0 || step < maxSteps`）
4. `buildPrompt()` 构造 `AgentPrompt`
5. `executeModel()` 调模型
6. 把模型返回的 memory items 回写到 memory
7. 判断是否有 tool calls
   - 无 -> 产出 `FINAL_OUTPUT`，结束
   - 有 -> 执行工具，写入 `function_call_output`，继续下一轮
8. 达到显式步数上限后返回最后结果

## 2.1 事件发布点（排障很重要）

每一轮会发布：

- `STEP_START`
- `MODEL_REQUEST`
- `MODEL_RESPONSE`
- `TOOL_CALL`（有工具时）
- `TOOL_RESULT`（有工具时）
- `STEP_END`
- 最终结束前 `FINAL_OUTPUT`

这正是 Trace 能构建 `RUN/STEP/MODEL/TOOL` 的基础。

## 2.2 Prompt 构建规则

`buildPrompt(...)` 会把这些上下文拼起来：

- `memory.getItems()`
- `systemPrompt + runtimeInstructions()`
- `instructions`
- `tools/toolChoice/parallelToolCalls`
- 采样参数与扩展参数

其中 `runtimeInstructions()` 是 Runtime 自身的“策略提示”，用于约束模型行为。

## 3. ReActRuntime：最轻量默认策略

`ReActRuntime` 只做两件事：

- `runtimeName() -> "react"`
- `runtimeInstructions()` 返回 “必要时使用工具，最终回答简洁”

它复用了 `BaseAgentRuntime` 的全部循环，是最通用、最稳健的默认选择。

## 4. CodeActRuntime：代码驱动的工具编排

`CodeActRuntime` 会要求模型返回严格 JSON：

- 需要执行代码：
  - `{"type":"code","language":"python|js","code":"..."}`
- 最终答案：
  - `{"type":"final","output":"..."}`

## 4.1 与 BaseAgentRuntime 的关键差异

1. 不走“模型直接发 tool call”路径，而是走“code 工具”路径。
2. 每步模型输出先被解析为 `CodeActMessage`。
3. 如果是 code：
   - 触发 `TOOL_CALL(name=code)`
   - 调 `CodeExecutor.execute(...)`
   - 将执行结果写入 memory 的 `CODE_RESULT/CODE_ERROR` system message
4. 根据 `CodeActOptions.reAct` 决定收敛策略。

## 4.2 `CodeActOptions.reAct` 两种模式

- `reAct=false`（默认）
  - 代码执行成功后，结果可直接作为最终输出返回。
  - 延迟低，适合结构化计算型任务。
- `reAct=true`
  - 代码执行结果会回送模型，再由模型输出自然语言最终答案。
  - 更可读，适合面向用户的最终文本。

## 4.3 失败后自动修复是否支持

支持，但是“循环式自修复”，不是硬编码重试：

- 只要 `maxSteps` 还没耗尽
- 模型拿到 `CODE_ERROR` 信息后继续输出新 code
- Runtime 就会继续执行下一轮

这符合当前主流 CodeAct 的实践：**LLM 负责修复策略，Runtime 负责执行闭环**。

## 4.4 CodeAct 指令注入

`CodeActRuntime.runtimeInstructions(...)` 会自动注入：

- JSON 输出协议
- `__codeact_result` 约定
- `callTool("name", args)` 与同名函数调用方式
- 当前可用工具列表（来自 `toolRegistry`）

所以你看到的“可用工具说明”是由 Runtime 自动拼入系统层提示的。

## 5. DeepResearchRuntime：先规划再执行

`DeepResearchRuntime` 在调用 `super.run(...)` 前会做 `preparePlan(...)`：

1. 使用 `Planner` 生成步骤列表
2. 将计划以 system message 形式写入 memory
3. 再进入 BaseAgentRuntime 循环

默认 `Planner.simple()` 只返回单步；你可以注入自己的 planner 做更复杂拆解。

## 6. CodeExecutor 与 GraalVmCodeExecutor

`CodeExecutor` 接口很简单：

- 入参：`CodeExecutionRequest`
  - `language/code/toolNames/toolExecutor/user/timeoutMs`
- 返回：`CodeExecutionResult`
  - `stdout/result/error`

默认实现 `GraalVmCodeExecutor`：

- 支持 `python/js`
- Python 走 GraalPy
- JS 优先 Polyglot，上下文不可用时回退 `ScriptEngine`
- 自动注入工具桥（`callTool` + 工具同名函数）

## 7. 如何自定义 Runtime（推荐模板）

建议继承 `BaseAgentRuntime`，只覆写你真正关心的行为。

```java
public class MyRuntime extends BaseAgentRuntime {

    @Override
    protected String runtimeName() {
        return "my-runtime";
    }

    @Override
    protected String runtimeInstructions() {
        return "Always verify tool output before final answer.";
    }

    @Override
    protected AgentPrompt buildPrompt(AgentContext context, AgentMemory memory, boolean stream) {
        AgentPrompt prompt = super.buildPrompt(context, memory, stream);
        // 你可以在这里对 prompt 做二次加工
        return prompt;
    }
}
```

接入：

```java
Agent agent = Agents.builder()
        .runtime(new MyRuntime())
        .modelClient(modelClient)
        .model("your-model")
        .build();
```

## 8. Runtime 选型建议（实践版）

- 绝大多数业务：`ReActRuntime`
- 工具调用链复杂（循环/聚合/批处理）：`CodeActRuntime`
- 研究型任务（先规划再检索）：`DeepResearchRuntime`
- 强定制策略：自定义 Runtime

## 9. 对应测试

- `CodeActRuntimeTest`
- `CodeActRuntimeWithTraceTest`
- `StateGraphWorkflowTest`（Runtime + Workflow 组合）

如果你准备扩展 Runtime，建议先跑这几个测试，再改你的策略代码。
