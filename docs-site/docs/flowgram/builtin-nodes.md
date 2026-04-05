---
sidebar_position: 4
---

# Flowgram 内置节点

本页整理当前从自动装配与测试中可以确认的 Flowgram 内置节点能力。

---

## 1. 结构节点

### `Start`

职责：

- 定义流程输入 schema；
- 提供工作流入口输出。

常见做法：

- 在 `data.outputs` 里声明前端或调用方可传入的字段；
- 后续节点通过 `ref` 引用这些字段。

### `End`

职责：

- 定义流程最终输出结构；
- 收集上游节点结果，形成最终任务输出。

常见做法：

- 在 `data.inputs` 定义输出 schema；
- 在 `inputsValues` 中引用上游节点字段。

---

## 2. `LLM`

职责：

- 发起模型调用；
- 返回文本类结果给后续节点。

关键点：

- 依赖 `FlowGramLlmNodeRunner`
- 默认通过 `ai4j.flowgram.default-service-id` 解析服务
- 也可以在节点输入中显式传 `serviceId` / `aiServiceId`

适合：

- 内容生成
- 总结改写
- 分类提取
- 节点级自然语言处理

---

## 3. `Variable`

职责：

- 做轻量变量赋值、模板渲染与局部结果整理。

从测试可确认它支持：

- `assign`
- `template`
- `${start_0.result}` 这类模板表达式

适合：

- 组装后续提示词
- 统一字段名
- 生成摘要字符串

---

## 4. `Code`

职责：

- 执行一段脚本并返回结构化结果。

当前测试使用的是：

- `language = javascript`
- `script.content` 中定义 `main(input)` 函数

适合：

- 字段加工
- JSON 转换
- 轻量业务规则

注意：

- JDK8 下脚本能力依赖运行环境中的脚本引擎；
- 测试中对 Nashorn 做了可用性判断。

---

## 5. `Tool`

职责：

- 调用已有工具并将结果转回节点输出。

从测试可确认输入侧至少可传：

- `toolName`
- `argumentsJson`

适合：

- 已有工具总线接入
- 本地函数或外部能力封装

当前边界：

- 已有 `TOOL` 节点，但没有单独的 `MCP` 专属节点；
- 如果要接 `MCP`，更适合先封装成工具层或自定义节点。

---

## 6. `HTTP`

职责：

- 直接发起 HTTP 请求，把返回结果交给下游节点。

从测试可确认它支持：

- `method`
- `url`
- `headersValues`
- `paramsValues`
- `timeout`
- `retryTimes`
- `body.bodyType`

当前测试里返回的典型输出包括：

- `statusCode`
- `body`

适合：

- 接第三方接口
- 调你自己的业务服务
- 在工作流里快速串接外部系统

---

## 7. `KnowledgeRetrieve`

职责：

- 承接知识检索能力。

当前自动装配条件是：

- Spring 容器里存在 `AiServiceRegistry`
- 同时存在单一可用的 `VectorStore` Bean

如果你的应用同时启用了多个向量库实现，需要：

- 显式声明一个 `@Primary` 的 `VectorStore`
- 或自己覆盖 `FlowGramKnowledgeRetrieveNodeExecutor`

当前执行器要求的关键输入至少包括：

- `serviceId`
- `embeddingModel`
- `dataset` 或兼容旧写法 `namespace`
- `query`

可选参数包括：

- `topK`
- `finalTopK`
- `delimiter`
- `filter`

因此它更适合：

- RAG 类画布
- 先检索再交给 LLM 总结
- 作为 Flowgram 知识库节点承接 Pinecone / Qdrant / Milvus / pgvector 任一后端

当前输出除了兼容旧字段：

- `matches`
- `context`
- `count`

还会补充：

- `hits`
- `citations`
- `sources`
- `trace`
- `retrievedHits`
- `rerankedHits`

其中 `hits` / `retrievedHits` / `rerankedHits` 的单条记录里，还会包含：

- `rank`
- `retrieverSource`
- `retrievalScore`
- `fusionScore`
- `rerankScore`
- `scoreDetails`

如果你要把这些结果真正展示到前端，建议把：

- `context` 给下游 `LLM`
- `citations` 给来源区
- `trace` / `retrievedHits` / `rerankedHits` 给调试区

不要把三层信息混成一个文本块。

---

## 8. 当前内置节点注册来源

starter 自动注册的执行器包括：

- `FlowGramHttpNodeExecutor`
- `FlowGramVariableNodeExecutor`
- `FlowGramCodeNodeExecutor`
- `FlowGramToolNodeExecutor`
- `FlowGramKnowledgeRetrieveNodeExecutor`（条件注册）

额外说明：

- `LLM` 节点由 `FlowGramLlmNodeRunner` 负责；
- `Start / End` 是工作流结构节点，不属于这批执行器。

---

## 9. 建议使用顺序

如果你第一次接 Flowgram，建议先按下面顺序尝试：

1. `Start -> End`
2. `Start -> Variable -> End`
3. `Start -> HTTP -> End`
4. `Start -> LLM -> End`
5. `Start -> KnowledgeRetrieve -> LLM -> End`

这样最容易定位问题出在 schema、节点执行还是模型调用。

---

## 10. 当前没有内置的节点类型

当前 starter 没有默认提供：

- `Agent` 专属节点
- `MCP` 专属节点

如果你要接这两类能力，建议看：

- [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)
- [引用、Trace 与前端展示](/docs/ai-basics/rag/citations-trace-and-ui-integration)
- [自定义节点扩展](/docs/flowgram/custom-node-extension)
