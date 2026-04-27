# Custom Nodes

这一页是 `Flowgram` 自定义节点的 canonical 入口。

最重要的结论先说：

自定义节点不是只写后端执行器，也不是只画前端节点，而是至少同时包含三件事：

- 前端节点 schema 和表单
- 前后端 type 映射
- 后端 `FlowGramNodeExecutor`

## 1. 为什么要先从这页进入

因为很多人会把“节点能画出来”和“节点能在后端真正跑起来”混成一件事。

在 AI4J `Flowgram` 这条线里：

- `Flowgram.ai` 负责前端节点画布
- AI4J 负责后端执行层

所以自定义节点天然就是前后端共同设计的问题。

## 2. 两个必分开的半边

### 前端半边

- 定义节点 type
- 定义默认数据、表单和 schema
- 让 `Flowgram.ai` 画布知道这个节点怎么渲染、怎么提交

### 后端半边

- 实现 `FlowGramNodeExecutor`
- 注册进 Spring 容器
- 让 runtime 能真正识别和执行该节点

## 3. 和相邻页面的边界

- [Built-in Nodes](/docs/flowgram/built-in-nodes)
  先判断是否真的需要自定义节点
- [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
  先理解画布 schema 如何到达后端 runtime
- [Runtime](/docs/flowgram/runtime)
  先理解节点最终跑在什么执行层里

## 4. 继续深入时该看哪里

如果你要继续沿着 canonical 主线往下走，建议先看：

- [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
- [Runtime](/docs/flowgram/runtime)
