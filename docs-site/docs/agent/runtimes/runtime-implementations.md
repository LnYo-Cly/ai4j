# Runtime Implementations

`AgentRuntime` 是 `ai4j-agent` 中最关键的策略抽象。

同样一个 Agent，之所以要区分 `ReActRuntime`、`CodeActRuntime`、`DeepResearchRuntime`，原因不在于“名字不同”，而在于不同任务需要不同的稳定中间表示与推进方式：

- 文本推理循环
- 代码驱动的工具编排
- 规划增强型任务推进

这页回答三个问题：

- 当前 runtime 抽象到底覆盖什么
- 三个内置 runtime 在实现上究竟差在哪里
- 什么时候该切 runtime，而不是继续堆 prompt

## 1. Runtime 抽象的职责边界

源码入口：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentRuntime.java`

接口定义如下：

```java
public interface AgentRuntime {
    AgentResult run(AgentContext context, AgentRequest request) throws Exception;
    void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception;
    default AgentResult runStreamResult(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        runStream(context, request, listener);
        return null;
    }
}
```

从这个接口可以直接看出，runtime 只负责一件事：

> 给定 `AgentContext` 和一次请求，定义这次执行如何推进、何时结束、何时发事件。

它不负责：

- provider 接入
- 工具 schema 发现
- 持久化细节
- CLI / ACP 产品宿主逻辑

## 2. `BaseAgentRuntime` 提供了什么

源码：

- `runtime/BaseAgentRuntime`

`BaseAgentRuntime` 是通用循环骨架。`ReActRuntime` 直接复用它，`DeepResearchRuntime` 在进入它之前插入 planning，`CodeActRuntime` 则在关键路径上重写了执行语义。

### 2.1 主循环

`runInternal(...)` 的主要步骤是：

1. 解析 `AgentOptions`
2. 把用户输入写入 `AgentMemory`
3. 发出 `STEP_START`
4. 通过 `buildPrompt(...)` 构造 `AgentPrompt`
5. 调用 `AgentModelClient.create(...)` 或 `createStream(...)`
6. 把模型返回的 `memoryItems` 写回 memory
7. 归一化 `toolCalls`
8. 走 `AgentToolCallSanitizer` 做结构校验
9. 通过 `ToolExecutor.execute(...)` 执行工具
10. 把结果回写 memory
11. 没有工具调用时输出 `FINAL_OUTPUT`，否则进入下一轮

### 2.2 内建的运行时能力

`BaseAgentRuntime` 已经处理了：

- `maxSteps`
- stream / non-stream 模式
- tool call 归一化
- tool 校验错误转 `TOOL_ERROR`
- 并行工具调用
- 标准事件发射

这意味着很多“我想做个 runtime”场景，其实只需继承它，而不是从零实现整个主循环。

### 2.3 它不处理什么

它不处理更高层产品化控制逻辑，例如：

- auto-continue outer loop
- compact-after-continue
- blocked stop reason
- workspace session checkpoint

这些更偏 `ai4j-coding` 的 outer loop，而不是通用 Agent runtime。

## 3. `ReActRuntime`

源码：

- `runtime/ReActRuntime`

它是最轻量的内置 runtime，本质上只覆写了两点：

- `runtimeName() -> "react"`
- `runtimeInstructions() -> "Use tools when necessary. Return concise final answers."`

### 3.1 适用任务

- 通用业务问答
- 少量到中等复杂度工具调用
- 不需要代码执行环境
- 希望最少装配成本

### 3.2 优势

- 默认路径最短
- 语义最直观
- 与 `BaseAgentRuntime` 完全对齐，便于理解和扩展

### 3.3 限制

当任务更适合“先写一段临时代码，再在代码里多次调用工具”时，纯文本 ReAct 往往不够稳定。

## 4. `CodeActRuntime`

源码：

- `runtime/CodeActRuntime`
- `codeact/CodeExecutor`
- `codeact/GraalVmCodeExecutor`
- `codeact/NashornCodeExecutor`
- `codeact/CodeActOptions`

它的核心思想不是“模型直接输出工具调用”，而是让模型输出一个严格 JSON 协议，决定是继续产代码还是给出最终答案。

### 4.1 中间表示

`CodeActRuntime` 期望模型返回：

- 需要执行代码时：

```json
{"type":"code","language":"python|js","code":"..."}
```

- 需要返回最终答案时：

```json
{"type":"final","output":"..."}
```

这就是它与 ReAct 的根本差异：稳定中间表示从“tool calls”变成了“code message”。

### 4.2 执行流程

`CodeActRuntime.runInternal(...)` 的主链是：

1. 模型输出 JSON
2. 解析为 `CodeActMessage`
3. 如果 `type=code`，构造一个虚拟 `TOOL_CALL(name=code)`
4. 调用 `CodeExecutor.execute(...)`
5. 把结果转成 `CODE_RESULT` 或 `CODE_ERROR` system message 写回 memory
6. 根据模式决定是直接结束，还是继续让模型产出最终自然语言

### 4.3 `CodeActOptions.reAct`

源码：

- `codeact/CodeActOptions`

当前只有一个关键开关：

- `reAct=false`：代码成功后结果可以直接成为最终输出
- `reAct=true`：代码执行结果先回灌模型，再由模型产出最终答案

两者的取舍是：

- `reAct=false` 延迟更低，更像执行型工具链
- `reAct=true` 最终文本更自然，更适合面向最终用户

### 4.4 代码执行器与 Java 版本

`AgentBuilder` 会按 Java 版本解析默认代码执行器：

- Java 8：`NashornCodeExecutor`
- 更高版本：`GraalVmCodeExecutor`

这意味着 Java 8 环境下，CodeAct 的默认语言约束和宿主能力与高版本运行时并不完全相同。对生产环境来说，代码执行器选择本身就是 runtime 选型的一部分。

### 4.5 适用任务

- 工具链复杂
- 需要在本轮内部做批量处理、聚合、结构化转换
- 文本 tool loop 不稳定，但代码中间表示更稳定

### 4.6 限制

- 对模型输出格式要求更高
- 需要代码执行环境
- 沙箱、超时、资源隔离要更谨慎

## 5. `DeepResearchRuntime`

源码：

- `runtime/DeepResearchRuntime`
- `runtime/Planner`

这里最需要写清楚的是：**当前实现是规划增强型 runtime，不是完整研究平台。**

### 5.1 当前实现到底做了什么

`DeepResearchRuntime` 在进入 `BaseAgentRuntime` 之前，先执行 `preparePlan(...)`：

1. 如果 request input 是字符串，则调用 `Planner.plan(goal)`
2. 将 plan 组装成：

```text
Plan:
1. ...
2. ...
```

3. 通过 `AgentInputItem.systemMessage(planText)` 写入 memory
4. 再调用 `super.run(...)` 或 `super.runStream(...)`

也就是说，它当前的本质是：

> 先把计划写进 memory，再按 ReAct 风格继续执行。

### 5.2 默认 planner 的限制

默认 `Planner.simple()` 的实现其实只会返回一个单步列表，也就是把原目标本身作为唯一 plan item。

这意味着默认 `DeepResearchRuntime` 并不会自动获得复杂研究拆解能力。要让它真正承担研究任务，你通常需要自定义 `Planner`。

### 5.3 适用任务

- 你希望在真正执行前显式插入计划
- 你希望模型先感知任务分解，再开始工具循环
- 你准备提供更强的 planner 实现

### 5.4 当前边界

它目前不是下面这些东西的替代品：

- 多阶段研究工作流引擎
- 自动证据评分器
- 多子任务并发研究平台

如果文档不写清这一点，用户很容易高估当前实现能力。

## 6. 选型矩阵

| Runtime | 稳定中间表示 | 最适合的任务 | 主要代价 |
| --- | --- | --- | --- |
| `ReActRuntime` | 文本推理 + tool calls | 通用工具型 Agent | 复杂工具链下稳定性有限 |
| `CodeActRuntime` | 代码消息 JSON | 批处理、聚合、复杂工具编排 | 需要代码执行环境与更严格协议 |
| `DeepResearchRuntime` | 计划文本 + ReAct loop | 需要先规划再推进的任务 | 默认 planner 很轻，需要自行增强 |

一个实用的选择顺序通常是：

1. 默认从 `ReActRuntime` 开始
2. 当工具链复杂到文本 loop 不稳时切 `CodeActRuntime`
3. 当任务的核心是先规划后推进时再考虑 `DeepResearchRuntime`

## 7. 自定义 Runtime 的推荐方式

最推荐的做法通常是继承 `BaseAgentRuntime`，只改真正需要变化的部分。

### 7.1 常见可覆写点

- `runtimeName()`
- `runtimeInstructions()`
- `buildPrompt(...)`
- `executeModel(...)`
- `runInternal(...)`

### 7.2 推荐模板

```java
public class MyRuntime extends BaseAgentRuntime {

    @Override
    protected String runtimeName() {
        return "my-runtime";
    }

    @Override
    protected String runtimeInstructions() {
        return "Always verify tool outputs before finalizing.";
    }

    @Override
    protected AgentPrompt buildPrompt(AgentContext context, AgentMemory memory, boolean stream) {
        AgentPrompt prompt = super.buildPrompt(context, memory, stream);
        return prompt;
    }
}
```

接入方式：

```java
Agent agent = Agents.builder()
        .runtime(new MyRuntime())
        .modelClient(modelClient)
        .model("gpt-4.1")
        .build();
```

## 8. Runtime 与 Workflow / Outer Loop 的边界

这也是经常被混淆的地方。

### 8.1 Runtime 回答什么

- 单个 Agent 在一次 run 中如何推进
- 本轮与下一轮之间如何闭环
- 工具结果如何回灌

### 8.2 Workflow / StateGraph 回答什么

- 多个节点或阶段如何显式流转
- 状态图上的边和条件如何组织

### 8.3 Coding Agent Outer Loop 回答什么

- 产品级 auto-continue
- checkpoint / compact
- workspace session 生命周期
- blocked / approval / host integration

如果把这三层混在一起，就很难准确评估某个 runtime 该负责什么。

## 9. 继续阅读

1. [Architecture](/docs/agent/architecture)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [CodeAct Runtime](/docs/agent/codeact-runtime)
5. [CodeAct Custom Sandbox](/docs/agent/codeact-custom-sandbox)
