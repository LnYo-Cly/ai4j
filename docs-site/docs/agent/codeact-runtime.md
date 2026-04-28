---
sidebar_position: 7
---

# CodeAct 运行时

`CodeActRuntime` 解决的不是“让模型顺便写点代码”，而是把“模型产出代码、运行时代码执行、代码内部继续调工具、执行结果再决定后续推理”收敛成一个稳定的 runtime。

这意味着 CodeAct 不是 ReAct 的别名，也不是普通 function call 的语法糖。它是一条独立执行链。

## 1. CodeAct 解决什么问题

普通 ReAct 适合“模型直接决定调用哪个工具，再根据工具结果继续回答”。

但有一类任务仅靠直接工具调用很难写好：

- 需要在本轮里循环调用多个工具
- 需要在代码中做中间态计算、聚合、排序、格式化
- 需要先取原始结果，再用脚本做转换
- 需要模型把“计划”落成可执行代码而不是直接自然语言

CodeAct 的价值，就是把“推理”和“执行”之间增加了一层代码媒介。

## 2. 它和 ReAct 的边界

可以把两者理解成两种不同的运行策略。

| 运行时 | 模型直接输出什么 | Runtime 主要做什么 | 适合什么任务 |
| --- | --- | --- | --- |
| `ReActRuntime` | 文本 + tool calls | 调工具、回灌 memory、继续 loop | 一般问答、检索、工具调用 |
| `CodeActRuntime` | JSON 包裹的代码或最终答案 | 执行代码、把结果写回 memory、决定是否再总结 | 计算、批量工具编排、数据转换 |

如果任务需要显式脚本执行与中间计算，应该进入 CodeAct；如果只是单步工具调用或简单多轮推理，停留在 ReAct 更合适。

## 3. 核心对象与职责

CodeAct 主线涉及下面几个核心类：

| 类 | 角色 | 关键职责 |
| --- | --- | --- |
| `CodeActRuntime` | CodeAct 主循环 | 解析模型 JSON、执行代码、决定是否继续 loop |
| `CodeActOptions` | 运行策略开关 | 当前核心开关是 `reAct` |
| `CodeExecutor` | 执行器抽象 | 真正执行 Python / JavaScript 代码 |
| `CodeExecutionRequest` | 代码执行入参 | 传入语言、代码、工具名、工具执行器、user |
| `CodeExecutionResult` | 代码执行结果 | 暴露 `result`、`stdout`、`error`、`success` |
| `NashornCodeExecutor` | Java 8 默认执行器 | 以 Nashorn 运行 ES5 风格 JavaScript |
| `GraalVmCodeExecutor` | 高版本 Java 默认执行器 | 承载 Python / JavaScript 多语言执行 |

在 `AgentBuilder.build()` 中，如果你没有显式提供 `codeExecutor(...)`，默认选择逻辑是：

- Java 8：`NashornCodeExecutor`
- 高于 Java 8：`GraalVmCodeExecutor`

这不是文档约定，而是 `AgentBuilder.createDefaultCodeExecutor()` 的实际行为。

## 4. 模型输出协议

`CodeActRuntime` 约定模型输出一个 JSON 对象，主要有两种形态。

### 4.1 请求执行代码

```json
{"type":"code","language":"python","code":"..."}
```

在 Java 8 / Nashorn 路径下，对应语言通常是：

```json
{"type":"code","language":"js","code":"..."}
```

### 4.2 输出最终答案

```json
{"type":"final","output":"..."}
```

### 4.3 解析失败时会发生什么

`CodeActRuntime.parseMessage(...)` 会尝试从模型输出中提取首个 JSON object。

这带来两个事实：

- 从契约上讲，模型应只输出 JSON 对象
- 从实现上讲，如果输出里混入了额外文本，但仍能提取到合法 JSON，runtime 仍可能继续运行

如果最终无法解析出合法 JSON，runtime 会把该输出当作普通文本结果处理，而不是继续执行代码。

所以“只输出 JSON”是正确用法，而“解析失败回退为普通文本”是容错兜底，不应当依赖它设计主流程。

## 5. 执行链路

`CodeActRuntime` 的执行步骤可以概括为：

1. 从 `AgentMemory` 组装 prompt
2. 自动拼接 CodeAct 专用系统指令
3. 请求模型，拿到文本输出
4. 解析 JSON：
   - `type=final`：直接结束
   - `type=code`：进入代码执行
   - 解析失败：按普通文本结束
5. 构造一个名为 `code` 的 `AgentToolCall`
6. 调用 `CodeExecutor.execute(CodeExecutionRequest)`
7. 把执行结果转成 `CODE_RESULT: ...` 或 `CODE_ERROR: ...` 的 system message 写回 memory
8. 根据 `CodeActOptions.reAct` 决定：
   - 直接返回执行结果
   - 还是继续下一轮，让模型输出最终自然语言答案

简化后的链路如下：

```text
model output JSON
  -> parseMessage(...)
  -> CodeExecutionRequest(language, code, toolNames, toolExecutor, user)
  -> codeExecutor.execute(...)
  -> memory.addSystemMessage(CODE_RESULT / CODE_ERROR)
  -> direct final output or next model step
```

## 6. Runtime 自动追加了哪些指令

`CodeActRuntime.runtimeInstructions(...)` 会根据执行器类型自动补一段运行时协议。

### 6.1 Java 8 / Nashorn 路径

当 `context.getCodeExecutor()` 是 `NashornCodeExecutor` 时，runtime 会明确要求模型：

- 使用 JavaScript
- 输出 `{"type":"code","language":"js","code":"..."}`
- 遵循 Nashorn 兼容语法
- 不使用 `Promise`、`async/await`、模板字符串、`let/const`、箭头函数

这相当于把 Java 8 的执行器边界提前暴露给模型，避免模型生成宿主无法运行的语法。

### 6.2 高版本 Java / GraalVM 路径

当不是 Nashorn 时，runtime 默认要求模型生成 Python：

- 输出 `{"type":"code","language":"python","code":"..."}`
- 使用 Python 语法
- 通过工具名或 `callTool(...)` 调工具

这也是为什么很多 CodeAct 示例在 Java 17+ 环境里默认展示 Python，而不是 JavaScript。

## 7. 代码里如何调工具

`CodeExecutionRequest` 会把以下能力传给执行器：

- `toolNames`
- `toolExecutor`
- `user`

因此代码运行时不是在“孤立脚本”里，而是在一个带工具桥接能力的执行环境里。

当前推荐的两种调用方式是：

1. 直接用工具名调用
2. 使用 `callTool("toolName", args)`

例如：

```python
cities = ["Beijing", "Shanghai", "Shenzhen"]
lines = []

for city in cities:
    weather = queryWeather(location=city, type="daily", days=1)
    lines.append(f"{city}: {weather}")

__codeact_result = "\n".join(lines)
```

这里要注意两个实现细节：

- 如果代码显式返回值，runtime 会优先把返回值作为结果
- 如果无法直接 `return`，应当赋值给 `__codeact_result`

## 8. `reAct` 开关到底控制什么

`CodeActOptions` 当前只有一个关键开关：

```java
CodeActOptions.builder().reAct(true).build()
```

它控制的是“代码执行成功后，是否还要再回模型做一轮自然语言整理”。

### 8.1 `reAct = false`

这是默认行为。

当代码执行成功时，runtime 会优先按下面顺序生成最终输出：

1. `result.getResult()`
2. `stdout`
3. 结构化 `toolOutput`

也就是说，只要执行器已经拿到了足够结果，runtime 会直接返回，不再走一轮模型总结。

适合场景：

- 代码结果本身已经是最终答案
- 追求低延迟、低 token 成本
- 输出格式已经在代码里被整理好

### 8.2 `reAct = true`

当代码执行成功时，runtime 会把 `CODE_RESULT: ...` 写回 memory，并设置 `finalizeRequested = true`。

下一轮会额外插入一条系统消息，要求模型：

- 不要再输出代码
- 使用最新 `CODE_RESULT`
- 只输出 `{"type":"final","output":"..."}`

适合场景：

- 代码结果需要再转成人类可读总结
- 需要自然语言解释、归纳、风险提示
- 希望模型把原始数据整理成正式回答

## 9. 执行结果如何被组装

`CodeActRuntime.buildToolOutput(...)` 会把执行结果组织成一个 JSON 对象，可能包含：

- `result`
- `stdout`
- `error`

对应的 memory 回写形式是：

- 成功：`CODE_RESULT: {"result":"...","stdout":"..."}`
- 失败：`CODE_ERROR: {"error":"...","stdout":"..."}`

随后 runtime 再用下面的优先级决定最终输出：

1. 成功且 `result` 非空时，直接返回 `result`
2. 否则使用 `stdout`
3. 再否则使用结构化 `toolOutput`
4. 错误时优先返回 `CODE_ERROR: ...`

这解释了一个常见现象：有时你以为“模型总结了结果”，实际上答案只是代码返回值直接透传。

## 10. 失败恢复是怎么实现的

CodeAct 的失败恢复不是外部补丁，而是 runtime 内建的循环语义。

当代码执行失败时：

1. 执行器返回 `error`
2. runtime 写入 `CODE_ERROR: ...`
3. memory 保留这条错误信息
4. 下一轮模型可以基于错误重新生成代码

这意味着 CodeAct 的修复能力来自两层：

- runtime 会把错误回灌，而不是吞掉
- 模型是否能修复，取决于 prompt、错误信息质量和 `maxSteps`

因此生产上至少要显式设置：

```java
.options(AgentOptions.builder().maxSteps(4).build())
```

否则可能出现：

- 一步内来不及修复
- 或 `maxSteps=0` 导致无界循环

## 11. 一份正确的接入示例

```java
Agent agent = Agents.codeAct()
        .modelClient(new ResponsesModelClient(responsesService))
        .model("doubao-seed-1-8-251228")
        .systemPrompt("You are a weather assistant. Use Python only when tool orchestration is needed.")
        .toolRegistry(
                java.util.Arrays.asList("queryWeather"),
                java.util.Collections.<String>emptyList()
        )
        .options(AgentOptions.builder()
                .maxSteps(4)
                .build())
        .codeActOptions(CodeActOptions.builder()
                .reAct(true)
                .build())
        .build();
```

这个配置表达的是：

- 运行时切换到 `CodeActRuntime`
- 工具白名单只有 `queryWeather`
- 代码执行成功后，再回模型整理最终回答
- 总步数限制为 4，避免无界重试

## 12. 什么时候要自定义 `CodeExecutor`

内置执行器解决的是“跑得起来”，不是“隔离到可以直接进生产”。

你应该在下面这些场景自定义 `CodeExecutor`：

- 需要进程级或容器级隔离
- 需要限制 CPU、内存、文件系统、网络
- 需要审计代码执行输入输出
- 需要多租户环境下的资源配额
- 需要替换默认语言支持或运行镜像

这也是 [CodeAct Custom Sandbox](/docs/agent/codeact-custom-sandbox) 存在的意义。

## 13. 边界与限制

CodeAct 很强，但边界也要说清。

### 13.1 它不负责强安全隔离

默认执行器不是安全沙箱。真正的权限边界、资源约束、文件与网络策略，应在自定义执行器中完成。

### 13.2 它不是 workflow 引擎

CodeAct 擅长“在一次 runtime loop 中用代码编排工具”，但它不替代显式状态图、节点依赖和长期任务编排。

### 13.3 它对提示词和工具契约更敏感

相比 ReAct，CodeAct 对以下因素更依赖：

- 工具 schema 是否稳定
- 错误信息是否清楚
- 系统提示词是否明确要求 JSON 协议
- 模型是否擅长生成当前执行器可运行的代码

如果这些前提不稳，CodeAct 会比 ReAct 更容易偏航。

## 14. 推荐阅读源码入口

如果你要继续看源码，优先从下面几处入手：

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeActOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/GraalVmCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`

## 15. 推荐验证用例

建议直接对照这些测试看行为契约：

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`

## 16. 下一步读什么

读完这一页后，建议继续：

1. [CodeAct Custom Sandbox](/docs/agent/codeact-custom-sandbox)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Runtime Implementations](/docs/agent/runtimes/runtime-implementations)
