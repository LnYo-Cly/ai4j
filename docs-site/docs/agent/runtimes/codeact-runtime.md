# CodeAct Runtime

`CodeActRuntime` 适合那些“只靠文本多轮推理已经不稳，最好先形成一段可执行程序再让程序调工具”的任务。

## 1. 它和 ReAct 的根本区别

`ReAct` 更像：

- 想一步
- 调一个工具
- 再想一步

`CodeAct` 更像：

- 先产出一段代码
- 让代码内部去调工具
- 再把执行结果回灌给 runtime

当“代码”本身能成为更稳定的中间表示时，`CodeAct` 往往更合适。

## 2. 真实代码路径

关键类：

- `CodeActRuntime`
- `CodeActOptions`
- `CodeExecutor`
- `GraalVmCodeExecutor`
- `NashornCodeExecutor`
- `CodeExecutionRequest`
- `CodeExecutionResult`

`AgentBuilder` 默认会按 Java 版本选代码执行器：

- Java 8：优先 `NashornCodeExecutor`
- 更高版本：优先 `GraalVmCodeExecutor`

## 3. 运行协议是什么

`CodeActRuntime` 不是让模型随便输出代码块，而是要求模型返回 JSON 结构。

核心语义是：

- 需要执行代码时：返回 `{"type":"code", ...}`
- 可以直接结束时：返回 `{"type":"final", ...}`

runtime 再根据 `type` 决定：

- 是发起代码执行
- 还是直接收口输出

这就是它比“让模型随意写代码再正则提取”更稳的原因。

## 4. 代码执行后发生什么

主链路大致是：

1. 模型返回 `code`
2. runtime 组装 `CodeExecutionRequest`
3. `CodeExecutor` 执行代码
4. 工具执行结果会以 `CODE_RESULT` 或 `CODE_ERROR` 回灌 memory
5. 再决定是否进入下一步或直接结束

所以 `CodeAct` 不是独立系统，而是嵌在 agent memory 和 event 流里的。

## 5. `reAct` 选项是什么意思

`CodeActOptions.reAct` 控制的是代码执行之后的收口方式。

### `reAct=false`

默认路径。

执行代码后，如果已经得到可直接返回的结果，runtime 会直接结束。

### `reAct=true`

执行代码后，不直接结束，而是把 `CODE_RESULT` 回灌给模型，再让模型生成最终回答。

适合：

- 你希望模型基于执行结果再做最后一层组织
- 你需要最终输出保持更自然的语言风格

## 6. 什么时候该优先考虑它

- 工具链很多，靠文本一个个调不稳
- 需要中间数据处理、过滤、聚合、格式化
- 你希望把复杂逻辑转成更可测试的代码执行
- 你需要批量调用工具而不是逐条问模型

## 7. 它的价值和风险

价值：

- 复杂工具链更稳定
- 中间逻辑更明确
- 对结构化数据处理更友好

风险：

- 执行环境安全边界更重要
- 代码引擎能力受运行时限制
- 你需要更认真设计沙箱、超时和审计

所以 `CodeAct` 的下一页不是更炫的能力，而是 [CodeAct Custom Sandbox](/docs/agent/runtimes/codeact-custom-sandbox)。
