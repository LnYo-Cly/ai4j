# Flowgram Runtime

`runtime` 是当前 `Flowgram` 子树里关于后端执行层的 canonical 入口。

这里讲的不是 `Flowgram.ai` 前端库本身，而是 AI4J 围绕它补起来的后端 runtime、任务 API、task store、trace projection 和执行链路。

## 1. 这页应该帮你建立什么心智

先记住这一条主链：

```text
Flowgram.ai 前端画布
  -> schema / runtime plugin / server client
  -> AI4J Flowgram task APIs
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> LLM runner / node executors / task store
```

也就是说：

- `Flowgram.ai` 负责前端画布和交互
- AI4J `Flowgram` runtime 负责后端执行和平台任务生命周期

## 2. Runtime 这一层重点关注什么

- `/flowgram/tasks/run`、`validate`、`result`、`report`、`cancel`
- 任务状态保存在哪里
- 节点怎么被执行器真正跑起来
- trace 和 report 如何给前端消费
- 前端 schema 怎样适配到后端 runtime schema

## 3. 和相邻页面怎么分工

- [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
  解释前端 `Flowgram.ai` 画布如何接到 AI4J 后端 runtime
- [Built-in Nodes](/docs/flowgram/built-in-nodes)
  解释运行时里有哪些现成节点
- [Custom Nodes](/docs/flowgram/custom-nodes)
  解释当内置节点不够时该怎么扩展

## 4. 继续深入时该看哪里

如果你要看从画布到后端执行的完整过程，继续看：

- [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)

如果你要继续沿着 canonical 主线往下走，下一页建议看：

- [Built-in Nodes](/docs/flowgram/built-in-nodes)
