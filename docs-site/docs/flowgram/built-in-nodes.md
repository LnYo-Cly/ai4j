# Built-in Nodes

这一页是 `Flowgram` 内置节点的 canonical 入口。

它的作用不是列百科，而是先帮你判断：

- 当前现成节点够不够
- 哪些节点属于结构节点，哪些属于执行节点
- 什么时候应该继续扩展自定义节点

## 1. 当前节点能力地图

可以先分三类记：

- 结构节点：`Start`、`End`
- 模型与能力节点：`LLM`、`Tool`、`KnowledgeRetrieve`
- 处理与集成节点：`Variable`、`Code`、`HTTP`

这已经覆盖了很多第一版平台的常见需求。

## 2. 关键节点各自解决什么

### `Start` / `End`

这两类是结构节点。

它们不负责复杂业务逻辑，而是负责：

- 声明工作流入口输入
- 组织最终输出结构

### `LLM`

由 `FlowGramLlmNodeRunner` 驱动。

适合：

- 文本生成
- 总结改写
- 节点级自然语言处理

### `Variable`

适合：

- 局部字段整理
- 模板组装
- 提示词拼接

### `Code`

适合：

- 轻量规则处理
- JSON 转换
- 稳定字段加工

### `Tool`

适合：

- 复用已有工具总线
- 把外部能力封进 workflow

### `HTTP`

适合：

- 调第三方接口
- 串接内部业务服务
- 在工作流里快速接系统边界

### `KnowledgeRetrieve`

这是 Flowgram 和 AI4J RAG 体系接得最深的一类节点。

它当前能承接：

- `VectorStore`
- `Reranker`
- `RagContextAssembler`
- citations / trace / retrievedHits / rerankedHits

所以它不仅是“查一下知识库”，而是可带证据与调试信息的检索节点。

## 3. 为什么这页重要

大多数团队第一次做 Flowgram 平台时，最容易高估“必须自定义节点”的比例。

实际上很多第一版流程，只靠内置节点就能覆盖：

- 输入整理
- 检索
- 模型生成
- 结果收口

因此先看内置节点，能明显降低系统复杂度。

## 4. 什么时候该继续看自定义节点

当你遇到下面这些情况时，再考虑自定义节点：

- 逻辑是稳定规则，不适合塞进 LLM
- 需要企业内部系统专属接入
- 输入输出 schema 需要强约束
- 需要单独监控某个节点执行结果

## 5. 推荐下一步

1. [Custom Nodes](/docs/flowgram/custom-nodes)
2. [Agent / Tool / Knowledge Integration](/docs/flowgram/agent-tool-knowledge-integration)
3. [Runtime](/docs/flowgram/runtime)
