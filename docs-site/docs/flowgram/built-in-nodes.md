# Built-in Nodes

`Built-in Nodes` 这一页最重要的不是罗列节点名，而是把“哪些能力属于 runtime 内核，哪些能力属于 starter 注册 executor”讲清楚。

如果这个边界没看懂，后面做自定义节点时很容易把扩展点放错地方。

## 1. 先把两类内置节点分开

当前所谓“内置节点”，其实至少分两层。

### 1.1 Runtime 内核节点

这些类型由 `FlowGramRuntimeService` 直接理解：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

它们代表的是流程结构和核心控制语义。

### 1.2 Starter 注册节点

这些类型通常通过 `FlowGramNodeExecutor` 注入：

- `VARIABLE`
- `HTTP`
- `CODE`
- `TOOL`
- `KNOWLEDGE`

它们代表的是业务能力和集成能力。

这个区分很重要，因为：

- 前者通常要靠 runtime 内核理解
- 后者原则上可以被替换、扩展或移除

## 2. 所有 executor 节点共享的输入解析模型

很多节点看起来输入结构不同，但实际都共享一个核心能力：值解析。

### 2.1 `FlowGramNodeValueResolver`

starter 侧多个 executor 都会用 `FlowGramNodeValueResolver` 解析输入值。它支持几类表达方式：

- `REF`
- `CONSTANT`
- `TEMPLATE`
- `EXPRESSION`

### 2.2 它能引用什么

解析时可以从这些根对象取值：

- `locals`
- `inputs` / `taskInputs` / `$inputs`
- 当前节点已解析输入
- 之前节点的输出

这意味着节点配置并不是死 JSON，而是带运行时引用能力的轻量表达层。

### 2.3 为什么这点重要

这解释了为什么 Flowgram 节点配置能做到：

- 取前面节点结果
- 做模板拼接
- 传递局部变量
- 让一个 HTTP / Tool / Code 节点复用上游输出

如果没有这层 resolver，节点之间只能靠硬编码字段传递。

## 3. `START` / `END`: 结构边界，不是业务节点

### `START`

`START` 的意义不是“做事”，而是：

- 定义根图入口
- 承接 task inputs
- 为整条执行链提供初始上下文

根图必须恰好一个 `Start`，否则校验直接失败。

### `END`

`END` 的意义是：

- 定义工作流的正式收口点
- 产出最终结果

根图至少需要一个 `End`。如果执行完没有到达任何 `End`，runtime 会把整条任务视为失败。

## 4. `LLM`: 受约束的智能节点

`LLM` 节点由 `Ai4jFlowGramLlmNodeRunner` 驱动。

### 输入要求

至少需要：

- 模型名：`modelName` / `model` / `modelId`
- prompt：`prompt` / `message` / `input`

可选还支持：

- `systemPrompt`
- `instructions`
- `temperature`
- `topP`
- `maxOutputTokens`

### 默认行为

- 每次节点执行动态创建一个 Agent
- 默认 runtime 是 `ReActRuntime`
- 默认 `maxSteps(1)`
- 默认 `stream(false)`

### 输出结构

- `result`
- `outputText`
- `rawResponse`
- `metrics`

因此这个节点更适合“单步智能加工”，而不是承载整套复杂业务流程。

## 5. `CONDITION` / `LOOP`: 控制流节点

这两类节点的价值在于让“流程结构”留在图里，而不是塞进 prompt 或代码里。

### `CONDITION`

它负责根据条件选择下一条边，因此它属于 runtime 级控制语义，不适合下沉成普通 executor。

### `LOOP`

`LOOP` 不只是一个标签。runtime 会递归执行它的 block 子图，并且对 loop 子图做同样的结构校验。

这说明 loop 在当前实现中是一级语义，不是前端展示效果。

## 6. `VARIABLE`: 轻量赋值和字段整理

`FlowGramVariableNodeExecutor` 的行为比名字更具体。

### 输入方式

它会读取 `assign` 列表，每一项通常是：

- `left`
- `right`

### 运行行为

- 先用 `FlowGramNodeValueResolver` 解析 `assign`
- 把每个 `left` 作为输出 key
- 把对应 `right` 写入 outputs

### 一个容易忽略的细节

如果最终没有任何 assign 输出，而当前节点有输入，它会把当前输入整体复制到输出。

这意味着 `VARIABLE` 不只是“定义新变量”，也可以作为一次轻量字段透传 / 重组节点。

## 7. `HTTP`: 工作流里的外部调用节点

`FlowGramHttpNodeExecutor` 是典型的“集成节点”。

### 必填输入

至少需要：

- `api.url`

### 默认行为

- 默认方法 `GET`
- 默认超时 `10000ms`
- 默认重试至少 `1` 次

### 可配置项

- `headersValues`
- `paramsValues`
- `timeout.timeout`
- `timeout.retryTimes`
- `body.bodyType`
- `body.json`
- `body.rawText`

### 请求体支持

当前主要支持：

- `JSON`
- `raw-text`

### 输出结构

- `statusCode`
- `body`
- `headers`
- `contentType`

因此它更适合“把流程接到外部系统”，不适合承担复杂业务编排本身。

## 8. `CODE`: 轻量可编程节点

`FlowGramCodeNodeExecutor` 当前默认基于 `NashornCodeExecutor` 执行脚本。

### 输入要求

至少需要：

- `script.content`

可选：

- `script.language`，默认 `javascript`

### 执行约束

- 超时固定 `8000ms`
- 运行时会注入 `params`
- 支持返回 `main(__flowgram_input)` 结果
- 也支持读取全局 `ret`
- `async main()` 当前明确不支持

### 输出结构

- 脚本返回值会被尝试解析成 JSON
- 若存在 stdout，也会额外写入 `stdout`

这个节点适合稳定、局部、短时的规则处理，不适合承载大型业务脚本平台。

## 9. `TOOL`: 把工具总线接进工作流

`FlowGramToolNodeExecutor` 的定位是把已有工具能力以节点方式暴露出来。

### 必填输入

- `toolName`

### 参数来源

它会优先读取：

- `argumentsJson`

如果没有，则把剩余输入整体序列化成 arguments JSON。

### 执行方式

- 先尝试执行内置 demo tool
- 否则走 `ToolUtilExecutor`

### 输出结构

- `toolName`
- `rawOutput`
- `data`
- `result`

如果工具输出本身是 JSON map，它会进一步把字段平铺进 outputs。

这让下游节点可以直接引用工具结果字段，而不是每次先自己 parse 字符串。

## 10. `KNOWLEDGE`: RAG 检索节点

`FlowGramKnowledgeRetrieveNodeExecutor` 是 Flowgram 和 AI4J RAG 基座结合最深的一类节点。

### 注册条件

这个节点不是无条件存在。starter 只有在存在：

- `AiServiceRegistry`
- 单一 `VectorStore`

时才会自动注册它。

### 必填输入

- `serviceId`
- `embeddingModel`
- `dataset` 或 `namespace`
- `query`

### 可选输入

- `topK`，默认 `5`
- `finalTopK`，默认等于 `topK`
- `delimiter`，默认 `\n\n`
- `filter`

### 输出结构

- `matches`
- `hits`
- `context`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`
- `count`

它不是“返回一段上下文字符串”这么简单，而是保留了检索、重排、引用和 trace 信息，适合做有证据链的工作流节点。

## 11. 什么时候该继续写自定义节点

先看完这些内置节点后，再判断是否真的需要扩展。

更应该自定义节点的情况：

- 逻辑是稳定规则，不适合塞进 LLM
- 你要接企业内部系统
- 输入输出 schema 需要长期固定
- 你希望这个能力成为平台复用节点

不应该急着自定义的情况：

- 只是还没把现有节点组合好
- 实际问题是前后端 schema 没对齐
- 只是想做一次简单字段转换

## 12. 一个最重要的判断原则

在 Flowgram 里，节点不该按“看起来像什么”来分类，而应按“它承担的是控制语义，还是业务能力”来分类。

这也是为什么：

- `START` / `END` / `CONDITION` / `LOOP` 留在 runtime
- `HTTP` / `CODE` / `TOOL` / `KNOWLEDGE` 走 executor

看懂这个分层，后面的扩展路线就不会跑偏。
