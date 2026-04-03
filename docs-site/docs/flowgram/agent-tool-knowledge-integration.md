---
sidebar_position: 7
---

# Agent、Tool、知识库与 MCP 接入

这一页专门回答一个平台化问题：

- 这套 Agentic 工作流平台，当前已经能接什么？
- `Agent`、`Tool`、知识库、`MCP` 分别该怎么接？

最重要的结论先说：

- `LLM`、`Tool`、`KnowledgeRetrieve` 当前已经有运行时接法
- `Agent` 目前没有内置专属节点
- `MCP` 目前也没有内置专属节点

所以不要把“理论上可以接”写成“当前已经内置”。

---

## 1. LLM 节点怎么接

`LLM` 节点当前由：

- `FlowGramLlmNodeRunner`

负责。

starter 默认会注册：

- `Ai4jFlowGramLlmNodeRunner`

它通过：

- `RegistryBackedFlowGramModelClientResolver`
- `AiServiceRegistry`

去解析模型服务。

也就是说，`LLM` 节点不是自己直连模型，而是复用 AI4J 的服务注册体系。

当前最重要的配置是：

- `ai4j.flowgram.default-service-id`

如果节点输入里显式指定了 `serviceId` 或 `aiServiceId`，则可以覆盖默认值。

---

## 2. Tool 节点怎么接

`TOOL` 节点当前执行器是：

- `FlowGramToolNodeExecutor`

从实现可以直接确认，当前输入侧至少支持：

- `toolName`
- `argumentsJson`

如果没有显式传 `argumentsJson`，剩余输入会被自动打包成 JSON 参数。

输出侧会包含：

- `toolName`
- `rawOutput`
- `result`

如果返回值是可解析 JSON，还会展开：

- `data`
- JSON 内的字段

这条链路适合：

- 已有 Java 工具能力
- 本地函数执行器
- 被包装成工具调用的企业能力

---

## 3. 知识库节点怎么接

知识库节点当前执行器是：

- `FlowGramKnowledgeRetrieveNodeExecutor`

它不是默认无条件注册，而是要求 Spring 容器里同时存在：

- `AiServiceRegistry`
- 单一可用的 `VectorStore`

因此它当前是“有条件的知识检索节点”，并且已经从已废弃的 `PineconeService` 直接依赖解耦到统一向量存储抽象。

从实现可以直接确认，当前至少要求这些输入：

- `serviceId`
- `embeddingModel`
- `dataset` 或兼容旧写法 `namespace`
- `query`

可选输入包括：

- `topK`
- `finalTopK`
- `delimiter`
- `filter`

输出侧至少包括：

- `matches`
- `context`
- `count`

当前还会返回：

- `hits`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`

这条链路适合：

- 私域知识库问答
- 先检索再总结
- 先知识召回再交给 LLM 节点生成

在实现层，知识检索节点现在走的是：

- `IEmbeddingService`
- `DenseRetriever`
- `RagService`
- `VectorStore`

这意味着底层既可以是 `Pinecone`，也可以切到 `Qdrant`、`pgvector`、`Milvus`，而不需要重写节点协议。

如果你要把知识检索节点的结果真正做成产品界面，建议按三层消费：

- `context` 给下游 `LLM`
- `citations/sources` 给用户可见来源区
- `trace/retrievedHits/rerankedHits` 给内部调试区

---

## 4. Agent 目前怎么接

当前 starter 没有内置 `AGENT` 专属节点。

也就是说：

- 没有默认的 ReAct 节点
- 没有默认的 CodeAct 节点
- 没有默认的多 Agent 节点

如果你要把 Agent 接进 Agentic 工作流平台，当前更现实的做法有两种：

### 4.1 自定义节点

自己实现：

- `FlowGramNodeExecutor`

然后在节点内部调用你的：

- ReAct Agent
- CodeAct Runtime
- Agent Team
- StateGraph 工作流

这是最推荐的接法，因为：

- 输入输出可控
- 能做平台级鉴权和审计
- 可以把 Agent 运行结果包装成稳定节点输出

### 4.2 HTTP 节点调用外部 Agent 服务

如果你的 Agent 已经是独立服务，也可以：

- 用 `HTTP` 节点直接调

这种方式改造成本更低，但平台侧可观测性和强类型约束会差一些。

---

## 5. MCP 目前怎么接

当前 starter 也没有内置专属 `MCP` 节点。

所以文档上应明确写成：

- 当前没有“直接把 MCP server 挂成 Flowgram 内置节点”的自动装配能力

如果你要接 MCP，建议走下面两条路径之一。

### 5.1 把 MCP 能力封装到 Tool 层

也就是：

- 先在服务端把 MCP 调用封装成 Java 工具能力
- 再让 `TOOL` 节点调用它

这样好处是：

- 前端节点模型稳定
- 平台不需要感知 MCP 协议细节
- 权限、日志、失败重试都更容易统一治理

### 5.2 直接做自定义节点

如果你需要更强的 MCP 参数控制，可以：

- 自定义一个节点执行器
- 在节点内部直接调 MCP client

这更灵活，但你要自己负责：

- 节点 schema
- 错误处理
- 超时与重试
- 安全边界

---

## 6. 推荐的组合方式

当前更稳的组合一般是：

- `KnowledgeRetrieve -> LLM`
- `TOOL -> LLM`
- `HTTP -> Variable -> LLM`
- `自定义 Agent 节点 -> End`

而不是一开始就试图把所有协议层都直接暴露给前端画布。

---

## 7. 当前边界

这几条边界要明确写进平台文档：

- `LLM` 节点已内置
- `TOOL` 节点已内置
- `KnowledgeRetrieve` 节点条件注册
- `Agent` 没有内置专属节点
- `MCP` 没有内置专属节点

这样用户才能清楚区分：

- 当前 starter 已经帮你做了什么
- 还有哪些需要你自己扩展

---

## 8. 继续阅读

1. [内置节点](/docs/flowgram/builtin-nodes)
2. [自定义节点扩展](/docs/flowgram/custom-node-extension)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
4. [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
