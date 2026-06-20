---
sidebar_position: 7
---

# Agent、Tool、知识库与 MCP 接入

这一页专门回答一个最容易被写虚的问题：

- Flowgram 当前已经内置了哪些能力接法
- 哪些能力只是“可以扩展”，但还没有内置节点

最重要的结论先写死：

- `LLM`、`TOOL`、`KNOWLEDGE` 当前已经有正式接法
- `Agent` 没有内置专属节点
- `MCP` 没有内置专属节点

不要把“理论上能接”写成“当前已经内置支持”。

## 1. `LLM` 节点：通过 AI4J 服务注册体系接模型

`LLM` 节点不是自己绕开 AI4J 去直连模型服务。

starter 默认会注册：

- `Ai4jFlowGramLlmNodeRunner`
- `RegistryBackedFlowGramModelClientResolver`

### 1.1 模型 client 是怎样解析的

`RegistryBackedFlowGramModelClientResolver` 会按这个顺序找服务标识：

- 节点输入里的 `serviceId`
- 节点输入里的 `aiServiceId`
- `ai4j.flowgram.default-service-id`

如果三者都没有，会直接报错。

这说明 Flowgram 的 LLM 节点不是“只要填个 modelName 就行”，它同时依赖：

- 哪个 chat service
- 该 service 下用哪个 model

### 1.2 这带来的好处

好处很实际：

- Flowgram 不需要自己维护 provider 适配
- 模型切换仍走 AI4J 统一服务注册层
- 前端节点协议和底层 provider 解耦

### 1.3 LLM 节点适合放什么

更适合：

- 总结、改写、提取、分类
- 检索后的生成
- 单节点智能步骤

不适合：

- 整张流程图都塞进一个大 prompt
- 把多步工具策略重新放回 LLM 节点内部

## 2. `TOOL` 节点：把工具总线变成工作流能力

`TOOL` 节点当前由：

- `FlowGramToolNodeExecutor`

驱动。

### 2.1 输入约定

至少需要：

- `toolName`

可选：

- `argumentsJson`

如果没有显式给 `argumentsJson`，executor 会把剩余输入整体序列化成 JSON 作为工具参数。

### 2.2 执行链怎么走

当前逻辑是：

1. 优先尝试内置 demo tool
2. 否则走 `ToolUtilExecutor`
3. 构造 `AgentToolCall`
4. 执行后把原始输出和解析结果都写回节点 outputs

### 2.3 输出为什么比表面上更有用

当前输出不仅有：

- `toolName`
- `rawOutput`
- `result`

如果工具输出可解析成 JSON map，还会继续提供：

- `data`
- map 内各字段的平铺结果

这让下游节点能直接引用工具字段，而不是先手动 parse 一层字符串。

### 2.4 什么情况下优先用 TOOL，而不是 HTTP

优先用 `TOOL` 的情况：

- 你已经有 AI4J / Java 工具能力
- 想复用统一工具治理
- 希望参数和返回语义更明确

优先用 `HTTP` 的情况：

- 能力已经是远程 HTTP 服务
- 你只需要最薄的远程调用桥

## 3. `KNOWLEDGE` 节点：把 RAG 链路收成正式节点

`KNOWLEDGE` 节点当前由：

- `FlowGramKnowledgeRetrieveNodeExecutor`

实现。

### 3.1 它不是无条件注册

starter 只有在存在：

- `AiServiceRegistry`
- 单一 `VectorStore`

时，才会自动注册 `FlowGramKnowledgeRetrieveNodeExecutor`。

这说明它是“条件内置能力”，不是任何环境下都默认可用。

### 3.2 输入 contract 很明确

至少要求：

- `serviceId`
- `embeddingModel`
- `dataset` 或 `namespace`
- `query`

可选：

- `topK`，默认 `5`
- `finalTopK`，默认等于 `topK`
- `delimiter`，默认 `\n\n`
- `filter`

### 3.3 它底层接的不是某一个向量库

从实现看，节点内部走的是：

- `IEmbeddingService`
- `DenseRetriever`
- `RagService`
- `VectorStore`
- `Reranker`
- `RagContextAssembler`

这意味着底层可以是不同向量存储后端，而节点协议不需要跟着改。

### 3.4 输出比“返回 context”丰富得多

当前至少会返回：

- `matches`
- `hits`
- `context`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`
- `count`

所以 `KNOWLEDGE` 节点并不是“拿一段文本给 LLM”，它本身已经保留了检索证据、重排轨迹和引用信息。

### 3.5 推荐怎么消费这些输出

更合理的分层是：

- `context` 给后续 `LLM` 节点
- `citations` / `sources` 给用户可见来源区
- `trace` / `retrievedHits` / `rerankedHits` 给内部调试区

这样你既能做产品化回答，也不会丢掉调试证据链。

## 4. `Agent` 当前没有内置专属节点

这一点必须说得非常明确。

当前 starter 没有默认的：

- `AGENT` 节点
- ReAct 专属节点
- CodeAct 专属节点
- Agent Team 专属节点

所以“Flowgram 可以跑 Agent”这句话，如果不补上下文，就是不准确的。

### 4.1 当前更现实的接法一：自定义节点

最推荐的是：

- 自己实现 `FlowGramNodeExecutor`
- 在节点内部调用 ReAct Agent、CodeAct Runtime、Agent Team 或其它 Agent runtime

这样做的好处是：

- 输入输出 contract 可控
- 前端不必理解 Agent 内部细节
- 平台可以继续统一接权限、日志、任务治理

### 4.2 当前更现实的接法二：HTTP 调外部 Agent 服务

如果你的 Agent 已经是独立服务，也可以：

- 用 `HTTP` 节点直接调用

这种方式改造成本低，但缺点也明确：

- 类型约束更弱
- 节点级观测能力更弱
- 平台很难感知更细的 Agent 内部状态

## 5. `MCP` 当前也没有内置专属节点

同样要讲清楚：

- 当前没有“开箱即用的 MCP 节点”
- 也没有“自动把 MCP server 转成 Flowgram 节点”的 starter 级自动装配

这并不代表 MCP 不能接，而是代表你要自己决定接入层级。

### 5.1 推荐接法一：先封成 Tool，再走 `TOOL` 节点

这是目前最稳的方式。

路径是：

1. 服务端把 MCP 调用封装成 Java 工具能力
2. 再让 `TOOL` 节点调用它

优势：

- 前端节点协议稳定
- 平台不需要直接理解 MCP 协议
- 权限、日志、超时、失败重试更容易统一治理

### 5.2 推荐接法二：直接做自定义节点

如果你确实需要更强的 MCP 参数和行为控制，可以：

- 自定义一个 `FlowGramNodeExecutor`
- 在 executor 内直接调用 MCP client

这更灵活，但你也要自己负责：

- 节点 schema
- 错误处理
- 超时 / 重试
- 安全边界

## 6. 几种更稳的组合方式

当前比较稳的组合通常是：

- `KNOWLEDGE -> LLM`
- `TOOL -> LLM`
- `HTTP -> VARIABLE -> LLM`
- `custom agent node -> END`

这些组合的共同点是：把复杂能力收在节点后面，而不是把复杂协议直接暴露给前端画布。

## 7. 什么时候该把能力做成节点，什么时候不该

### 更适合做成节点

- 输入输出需要长期稳定
- 这个能力会被多个流程复用
- 你希望在节点级做观测、审计、治理

### 不一定要做成节点

- 只是一次性实验
- 能力还没有稳定输入输出
- 你还不确定它属于 Tool、HTTP 还是 Agent 行为

节点越平台化，就越应该 contract first，而不是实现 first。

## 8. 当前能力版图要怎么表述才准确

比较准确的说法是：

- `LLM` 节点：已内置，复用 AI4J 模型服务注册体系
- `TOOL` 节点：已内置，复用工具调用链
- `KNOWLEDGE` 节点：条件内置，复用 RAG 抽象
- `Agent`：可扩展接入，但无内置专属节点
- `MCP`：可扩展接入，但无内置专属节点

这种表述能让用户清楚区分：

- 当前 starter 已经帮他做了什么
- 哪些地方还需要自己实现

## 9. 一个最重要的设计判断

Flowgram 这条线不应该把所有下层协议原样暴露给前端画布。

更好的做法通常是：

- 把 provider、Tool、RAG、MCP、Agent 这些复杂能力先收敛到后端 contract
- 再以稳定节点类型暴露给前端

这样画布面对的是平台节点，而不是后端协议拼盘。
