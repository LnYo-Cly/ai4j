# Custom Nodes

这一页是 `Flowgram` 自定义节点的 canonical 入口。

最重要的结论先说：

> 自定义节点不是只写后端执行器，也不是只把节点画出来，而是前后端共同设计的一套契约。

## 1. 自定义节点至少包含哪三部分

- 前端节点 type、schema、表单和默认数据
- 前后端一致的 type 映射和输入输出协议
- 后端 `FlowGramNodeExecutor`

缺任何一半，节点都不算真正可运行。

## 2. 为什么这层最容易踩坑

因为很多人会把两件事混成一件事：

- “节点能在画布里显示”
- “节点能在后端 runtime 真正执行”

在 AI4J `Flowgram` 体系里：

- `Flowgram.ai` 负责前端节点画布
- AI4J 负责后端执行层

所以自定义节点天然就是前后端共同设计的问题。

## 3. 后端扩展点在哪里

最核心的后端扩展点是：

- `FlowGramNodeExecutor`

只要你实现这个接口，并注册进 Spring 容器，runtime 就能把它纳入可执行节点集合。

这也是为什么 Flowgram 的扩展边界很清晰，不需要侵入 runtime 内核本身。

## 4. 什么时候值得自定义节点

下面这些场景更适合写自定义节点：

- 逻辑是稳定规则，而不是提示词试错
- 要接企业内部系统
- 输入输出 schema 需要严格控制
- 想把某段能力做成可复用平台节点
- 需要对单个节点做更细监控和治理

如果主要是自然语言生成，通常还是优先留在 `LLM` 节点里。

## 5. 最重要的设计原则

- 节点只做一件事
- `type` 稳定，不随展示名变化
- 输入输出字段尽量简洁、稳定、可复用
- 先定义好前后端 schema，再写执行逻辑
- 错误信息要足够清楚，方便平台调试

## 6. 和相邻页面的关系

- [Built-in Nodes](/docs/flowgram/built-in-nodes)
  先判断是否真的需要扩展
- [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
  先理解 schema 如何到达后端 runtime
- [Runtime](/docs/flowgram/runtime)
  先理解节点最终跑在什么执行层里

## 7. 推荐下一步

1. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
2. [Runtime](/docs/flowgram/runtime)
3. [Agent / Tool / Knowledge Integration](/docs/flowgram/agent-tool-knowledge-integration)
