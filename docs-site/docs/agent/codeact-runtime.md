---
sidebar_position: 7
---

# CodeAct 运行时

`CodeActRuntime` 不是“ReAct 再加一个代码工具”，而是一条不同的执行链。

它把模型输出的稳定中间表示，从：

- `tool_calls`

改成了：

- `{"type":"code","language":"...","code":"..."}`
- `{"type":"final","output":"..."}`

这意味着 CodeAct 的本质不是 native function-calling，而是一个由 runtime 强制约束的 prompt 协议，再通过 `CodeExecutor` 把代码和工具桥接起来。

## 1. 先抓住 3 个关键设计决策

理解 `CodeActRuntime`，先抓住 3 个真正决定行为的点。

### 1.1 它不是 native tool-calling 路线

`CodeActRuntime.buildPrompt(...)` 构造 `AgentPrompt` 时，并没有把工具 schema 塞进 prompt 的 `tools` 字段。

它真正做的是：

- 合并 `systemPrompt`
- 注入一段 `runtimeInstructions(...)`
- 把工具名和说明拼成文本 guide

也就是说，CodeAct 不是让模型直接发 provider 原生 `tool_calls`，而是让模型：

1. 先输出一个 JSON code message
2. 再由 runtime 解释这段 code message
3. 再由 `CodeExecutor` 在宿主里调工具

这和 ReAct 的根本区别，不在“是否会调用工具”，而在“模型和工具之间有没有经过代码这一层中间表示”。

### 1.2 代码执行不是额外附属功能，而是主循环核心

在 `runInternal(...)` 里，只要模型输出 `type=code`，运行链就会进入：

```text
parseMessage
  -> build AgentToolCall(name=code)
  -> codeExecutor.execute(CodeExecutionRequest)
  -> memory.addSystemMessage(CODE_RESULT / CODE_ERROR)
  -> next step or final output
```

所以 CodeAct 不是“模型写一段代码，顺便执行一下”，而是 runtime 把代码执行本身纳入了主循环。

### 1.3 最终输出不一定经过模型总结

默认 `CodeActOptions.reAct = false`。

这意味着代码执行成功后，runtime 会优先尝试直接返回：

- `result`
- `stdout`
- `toolOutput`

很多场景下，最终答案根本不会再回模型润色，而是代码结果直接成为 `AgentResult.outputText`。

所以要判断一段输出是“模型总结的”还是“执行器直接回的”，必须看 `reAct` 和执行结果结构，不能只看最终文本。

## 2. 它到底解决什么问题

ReAct 适合：

- 模型判断该不该调工具
- 调一次或少量几次工具
- 工具结果直接回灌给模型继续推理

但有一类任务，用纯文本 tool loop 会逐渐不稳定：

- 同一轮内要循环调用多个工具
- 需要在工具结果之间做聚合、排序、转换
- 需要构造临时中间变量，而不是只拼自然语言
- 希望“先执行，再决定怎么回答”

CodeAct 的价值，就是把这种任务从“模型直接推理”转换成“模型生成可执行过程”。

## 3. 和 ReAct 的真正边界

| 运行时 | 模型的主输出 | 工具如何进入链路 | 谁决定最终答案 |
| --- | --- | --- | --- |
| `ReActRuntime` | 文本 + native tool calls | runtime 直接执行 tool call | 通常由模型 |
| `CodeActRuntime` | JSON code/final message | 代码经 `CodeExecutor` 间接调工具 | 可能是执行器，也可能是模型 |

因此什么时候切到 CodeAct，不是看“任务复杂不复杂”，而是看：

- 你需要的是“工具调用”
- 还是“工具调用之间的显式可执行过程”

如果后者更重要，就该进入 CodeAct。

## 4. `runInternal()` 的真实生命周期

从源码看，`CodeActRuntime.runInternal(...)` 可以拆成 6 个阶段。

### 4.1 Phase 1: 初始化和 step loop

开始时它会：

- 读 `AgentOptions.maxSteps`
- 读 `CodeActOptions.reAct`
- 校验 `memory`
- 校验 `codeExecutor`
- 把用户输入写进 `AgentMemory`

然后进入 step loop。

这里要特别注意：

- `maxSteps <= 0` 时没有步数上限

所以 CodeAct 默认并不是生产安全的保守配置。和 ReAct 一样，如果不显式限制步数，失败修复或空转都可能持续发生。

### 4.2 Phase 2: 组 prompt，但不是注册工具

`buildPrompt(...)` 只会把这些字段放进 `AgentPrompt`：

- `model`
- `items`
- `systemPrompt`
- `instructions`
- 采样参数
- `reasoning`
- `store`
- `user`
- `extraBody`

它不会注入 `tools`。

取而代之的是 `runtimeInstructions(...)` 会拼一段强约束协议，要求模型：

- 只能输出单个 JSON 对象
- 需要执行时输出 `type=code`
- 完成时输出 `type=final`
- 使用特定语言
- 用工具名或 `callTool(...)` 调工具

所以 CodeAct 是“工具文档文本化 + 执行桥接”，不是“provider 原生工具暴露”。

### 4.3 Phase 3: 解析模型输出

模型返回文本后，runtime 会调用 `parseMessage(...)`：

- 先从文本里提取首个 JSON object
- 再读取 `type/language/code/output`

这里有个很关键的容错边界：

- 契约要求模型只输出 JSON
- 但实现允许“外面包了点别的文本，只要能抽出一个 JSON 也继续跑”

如果 JSON 抽不出来，`message == null`，runtime 会直接把原文本当最终输出。

这意味着：

- 解析失败的退路不是重试协议
- 而是直接退回“普通文本 Agent”

所以如果模型经常偏离 JSON 协议，CodeAct 的行为会迅速退化。

### 4.4 Phase 4: `type=code` 时进入执行桥

当 `message.type == "code"` 且 `message.code != null` 时，runtime 会：

1. 构造一个逻辑上的 `AgentToolCall`
   - `name = "code"`
   - `callId = code_execution_<step>`
2. 发一个 `TOOL_CALL` 事件
3. 构造 `CodeExecutionRequest`
4. 调用 `codeExecutor.execute(...)`

这个 `AgentToolCall(name=code)` 的作用，不是给模型用，而是为了把 CodeAct 的执行行为也纳入统一的 tool event / trace 体系。

换句话说，CodeAct 在观测层看起来像“执行了一个 code tool”，但这个 tool 实际上是 runtime 内建桥接。

### 4.5 Phase 5: 把执行结果重新写回 memory

执行结果会先被 `buildToolOutput(...)` 组装成 JSON，可能包含：

- `result`
- `stdout`
- `error`

然后 runtime 会把它写成一条 system message：

- 成功：`CODE_RESULT: {...}`
- 失败：`CODE_ERROR: {...}`

这一步很关键，因为后续模型看到的不是 JVM 对象，而是已经被转成文本协议的执行反馈。

CodeAct 的“自我修复”能力，本质上就建立在这条反馈链上。

### 4.6 Phase 6: 选择直接结束还是继续一轮

执行结果出来后，runtime 会走两条不同分支。

#### `reAct = false`

runtime 会尝试直接返回：

1. `resolveDirectOutput(...)`
2. `resolveFallbackOutput(...)`

只要其中之一非空，就直接结束，不再继续调模型。

#### `reAct = true`

runtime 不直接把成功执行结果当最终答案，而是：

- 设 `finalizeRequested = true`
- 把 `CODE_RESULT` 留在 memory 里
- 让模型下一轮输出 `type=final`

如果模型在 finalize mode 里还继续产 `type=code`，runtime 会再插入一条 system message：

```text
FINALIZE_MODE: Do not output code. Use the latest CODE_RESULT ...
```

这说明 `reAct=true` 并不只是“多跑一轮”，而是 runtime 会主动收紧协议，强迫模型从执行阶段切到总结阶段。

## 5. `runtimeInstructions(...)` 暴露了真实执行边界

这段方法比很多文档都更真实，因为它直接告诉了模型允许做什么。

### 5.1 Java 8 路径其实是 JavaScript 模式

当执行器是 `NashornCodeExecutor` 时，runtime 明确要求：

- 用 `js`
- 语法兼容 Nashorn ES5
- 不要 `Promise`
- 不要 `async/await`
- 不要模板字符串
- 不要 `let/const`
- 不要箭头函数

这说明 Java 8 下的 CodeAct 其实不是“功能缩水的 Python 模式”，而是另一套明确的语言契约。

### 5.2 高版本 Java 默认其实偏 Python 模式

当执行器不是 Nashorn 时，runtime 默认要求模型输出 Python。

所以在 Java 17+ 环境里，CodeAct 的主心智模型更接近：

- Python 作为执行中间语言
- Java 作为宿主和工具桥

### 5.3 工具能力是文本暴露，不是 schema 暴露

`runtimeInstructions(...)` 会遍历当前 `toolRegistry`，把工具名和描述拼成：

```text
Available tools: toolA - ..., toolB - ...
```

这意味着 CodeAct 的工具发现不是靠 provider 校验 schema，而是靠 prompt 协议约束。

它的好处是：

- 执行桥更灵活

它的代价是：

- 参数正确性更依赖模型遵循文本说明
- 也更依赖执行器容错

## 6. `CodeExecutionRequest` 真正把什么桥接进来了

每次执行都把这些信息传进 `CodeExecutor`：

- `language`
- `code`
- `toolNames`
- `toolExecutor`
- `user`

因此执行器不是“单纯跑字符串代码”，而是在一个带宿主工具桥的环境里运行。

这意味着代码里能做的事情取决于两层：

1. runtime 给了哪些工具名
2. 执行器如何把这些工具暴露成可调用对象

所以 CodeAct 的能力边界，不能只看模型，也不能只看 runtime，必须同时看 `CodeExecutor`。

## 7. 输出选择逻辑比表面更细

很多人会以为“代码返回什么，最终就是什么”，但当前实现更细。

### 7.1 `resolveDirectOutput(...)` 很保守

只有在以下条件同时成立时才会直接用 `result`：

- `execResult.isSuccess()`
- `result` 非空
- `trim` 后非空
- 不是 `"undefined"`
- 不是 `"null"`

因此“代码执行成功”并不等于“一定有 direct output”。

### 7.2 `reAct=false` 也不一定马上结束

如果 direct output 和 fallback output 都为空，runtime 不会立刻返回，而是继续下一轮。

也就是说，非总结模式下，成功执行后仍可能继续 loop。

这是一个很容易被忽略的点：

- `reAct=false` 不等于“一次执行后一定终止”
- 它只是“如果已经有可返回结果，则优先直接终止”

### 7.3 `stdout` 和 `toolOutput` 也可能成为最终答案

这解释了为什么有些 CodeAct 输出看起来像原始日志或结构化 JSON，而不是自然语言总结。

## 8. 失败恢复是怎么形成的

CodeAct 的恢复能力，不是靠外部 retry 框架，而是靠运行时把失败写回 memory。

失败后链路是：

```text
exec error
  -> CODE_ERROR system message
  -> model sees error on next step
  -> model emits patched code
```

因此失败恢复的质量，主要取决于 4 件事：

- `maxSteps` 是否足够
- 错误信息是否足够具体
- system prompt 是否要求模型修复而不是放弃
- 执行器是否把异常压成了可读文本

这也是为什么 CodeAct 往往比 ReAct 更吃“协议清晰度”。

## 9. 和安全隔离的边界一定要分开

`CodeActRuntime` 负责的是：

- 代码协议
- 循环推进
- 结果回灌

它不负责：

- 进程隔离
- 文件系统限制
- 网络权限治理
- CPU / 内存配额

这些都应该由 `CodeExecutor` 及其宿主环境承担。

所以内置执行器解决的是“可运行”，不是“可直接上生产”。

## 10. 适合什么任务，不适合什么任务

适合：

- 批量工具调用
- 工具结果聚合和结构化转换
- 中间变量很多的任务
- “先执行、再总结”优于“边想边答”的任务

不适合：

- 只需要 1 到 2 次简单工具调用
- 不允许引入代码执行环境
- 对工具参数约束极强、必须依赖 provider 原生 schema 校验的场景

如果任务本质还是普通 tool loop，继续用 ReAct 往往更稳。

## 11. 推荐阅读源码入口

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runtime/CodeActRuntime.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeActOptions.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/CodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/NashornCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/codeact/GraalVmCodeExecutor.java`
- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentBuilder.java`

## 12. 推荐验证用例

- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeTest.java`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/CodeActRuntimeWithTraceTest.java`

## 13. 继续阅读

1. [CodeAct Custom Sandbox](/docs/agent/codeact-custom-sandbox)
2. [Tools and Registry](/docs/agent/tools-and-registry)
3. [Memory and State](/docs/agent/memory-and-state)
4. [Runtime Implementations](/docs/agent/runtime-implementations)
