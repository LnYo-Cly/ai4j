# Flowgram Runtime

`runtime` 是 `Flowgram` 子树里关于后端执行层的 canonical 入口。

这里讲的不是 `Flowgram.ai` 前端库本身，而是 AI4J 围绕它补起来的：

- task API
- runtime service
- node execution
- task store
- report / result / trace projection

## 1. 先记住主链

```text
Flowgram.ai front-end
  -> schema / client
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> LLM runner / node executors
  -> task store / report / result
```

这条链决定了 `Flowgram` 的本质是平台后端，而不是单纯 UI 演示。

## 2. 真实代码锚点

后端核心执行：

- `ai4j-agent/.../flowgram/FlowGramRuntimeService`
- `FlowGramLlmNodeRunner`
- `FlowGramNodeExecutor`

Spring Boot 接入：

- `FlowGramAutoConfiguration`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramTaskStore`
- `JdbcFlowGramTaskStore`

## 3. Runtime 这一层主要回答什么

- 任务如何被提交与取消
- schema 如何校验
- 节点如何被调度执行
- 执行状态如何被保存
- result / report / trace projection 如何返回给前端

这也是它和普通 `Agent` 章节最不同的地方。

## 4. Task API 的价值

`Flowgram` 的平台化价值，很大一部分来自 task API，而不是只来自节点执行。

当前主入口包括：

- `run`
- `validate`
- `result`
- `report`
- `cancel`

这意味着前端画布、测试脚本、平台宿主都能围绕统一任务协议工作，而不是各写一套临时接口。

## 5. Task store 怎么看

runtime 只解决“跑起来”还不够。

一旦要进入平台形态，就必须考虑：

- 任务状态能否持久化
- report 是否能跨进程读取
- 失败或取消之后如何恢复观察

对应的关键抽象就是：

- `FlowGramTaskStore`
- `InMemoryFlowGramTaskStore`
- `JdbcFlowGramTaskStore`

如果你的场景是 demo、本地开发，内存 store 就够。
如果你的场景是平台后端或多实例，JDBC store 更贴近真实需求。

## 6. 节点执行层怎么接

节点执行主要分两类：

- `LLM` 节点：走 `FlowGramLlmNodeRunner`
- 其他节点：走 `FlowGramNodeExecutor`

这让系统天然支持两种扩展：

- 换模型接入策略
- 增加或覆盖节点执行器

## 7. 和相邻页面的关系

- [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
  解释前端画布怎样接到 task API
- [Built-in Nodes](/docs/flowgram/built-in-nodes)
  解释现在已有的节点能力
- [Custom Nodes](/docs/flowgram/custom-nodes)
  解释当内置节点不够时怎样扩展

## 8. 推荐下一步

1. [Built-in Nodes](/docs/flowgram/built-in-nodes)
2. [Custom Nodes](/docs/flowgram/custom-nodes)
3. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
