---
sidebar_position: 9
---

# 前端工作流如何在后端执行

这页不再泛泛讲“前后端对接”，而是专门把一条 workflow 从编辑态到执行态的完整管线拆开。

如果你想知道：

- `document.toJSON()` 之后发生了什么
- 哪些画布元素不会进入后端
- `report` 和 `trace` 是怎样回到前端的

就应该读这一页。

## 1. 先看完整执行管线

当前参考实现的主链路是：

```text
Flowgram.ai editor
  -> document.toJSON()
  -> normalizeWorkflowForBackend(...)
  -> serializeWorkflowForBackend(...)
  -> POST /tasks/validate
  -> POST /tasks/run
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> node execution
  -> report/result/trace projection
  -> front-end runtime snapshot
```

真正的关键不在某一个接口，而在“编辑态对象什么时候被压成执行态对象”。

## 2. Stage 1: 编辑态工作流先从画布导出 JSON

前端 runtime 在提交任务前，会先从 `WorkflowDocument` 导出当前工作流 JSON。

这个阶段拿到的对象仍然带着编辑器视角的结构。

这意味着它可能包含：

- 纯 UI 节点
- 只对编辑器有意义的 block 边界
- 前端内部 type 命名

如果此时直接发给后端，后端 contract 会被前端内部结构污染。

## 3. Stage 2: `normalizeWorkflowForBackend(...)` 做执行态归一化

真正的桥接点在：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

### 3.1 它先过滤 UI-only 节点

当前明确会被去掉的有：

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

这是一个很重要的设计决策：画布上的结构，不等于执行结构。

### 3.2 它再做类型归一化

例如：

- `llm -> LLM`
- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`

这一步把“前端节点注册表里的 type”转换成“后端 runtime 识别的 type”。

### 3.3 它还会做局部数据修正

例如 loop 节点会补齐输入 schema 和 `inputsValues.loopFor`。这说明前端传给后端的不只是删字段，还会做协议补齐。

### 3.4 它会清理无效边

只有 source / target 节点都存在且有效时，边才会进入后端 schema。

这让后端不必承担画布态脏数据清洗的全部成本。

## 4. Stage 3: runtime plugin 组织调用顺序

前端并不是“点击运行后直接 POST /run”，而是由 `WorkflowRuntimeService` 组织一条标准链路。

### 4.1 先做本地表单校验

这一步会遍历所有节点表单，确保最基础的编辑器约束先过。

### 4.2 再调 `/tasks/validate`

这一步把错误升级到后端 schema 视角，例如：

- 节点类型后端不支持
- 必填绑定缺失
- 根图没有 `End`

### 4.3 通过后再调 `/tasks/run`

此时后端返回的是 `taskId`，不是最终输出。

### 4.4 再按固定间隔轮询 `report`

当前前端常量：

- `SYNC_TASK_REPORT_INTERVAL = 500`

这意味着系统默认是“轮询观察模型”，不是“服务端主动持续推送模型”。

### 4.5 任务结束后再取 `result`

最终结果不会靠推断 report 得到，而是通过 `result` 正式获取。

## 5. Stage 4: 后端 controller / facade / runtime 分层进入执行

### 5.1 Controller 阶段

`FlowGramTaskController` 只负责接住 REST 请求，把它交给 `FlowGramRuntimeFacade`。

### 5.2 Facade 阶段

`FlowGramRuntimeFacade` 负责平台化语义：

- caller 解析
- access check
- ownership 创建
- task store 更新
- trace / node details 组装

### 5.3 Runtime 阶段

`FlowGramRuntimeService` 才负责：

- schema 校验
- `TaskRecord` 创建
- graph dispatch
- node status / workflow status 更新
- report / result 聚合

因此“前端工作流如何在后端执行”这句话，真实含义是：它先进入平台控制面，再进入执行引擎。

## 6. Stage 5: 节点输入在 executor 之前已经被 runtime 解析

这是自定义节点最容易误解的一点。

`executeCustomNode(...)` 在调用 executor 之前，会先：

- 读取节点 `inputsValues`
- 用 runtime 内部逻辑解析 `REF` / `CONSTANT` / `TEMPLATE` / `EXPRESSION`
- 应用输入 schema 默认值

然后才把结果放进：

- `FlowGramNodeExecutionContext.inputs`

所以自定义 executor 拿到的 `context.inputs`，通常已经是“执行态输入”，不是原始前端配置对象。

## 7. Stage 6: 后端把执行态再投影成前端读侧

后端执行完以后，不会把内部对象原样扔回前端。

### 7.1 `FlowGramProtocolAdapter` 负责基础响应协议

它会把 runtime 输出变成：

- `FlowGramTaskReportResponse`
- `FlowGramTaskResultResponse`

### 7.2 `trace` 再做一次前端投影

如果开启了 `traceEnabled`，facade 还会把 runtime event 聚合成 `FlowGramTraceView`，附加到 report / result 上。

这层数据更适合前端直接渲染：

- 顶部状态
- 节点高亮
- 时间线
- token / cost 指标

### 7.3 前端 runtime 再把它折叠成 snapshot

最终前端运行态不是直接拿 HTTP response 就结束，而是会进一步组装成：

- `validation`
- `report`
- `trace`
- `result`
- `errors`
- `status`

组成一个持续演进的 `WorkflowRuntimeSnapshot`。

## 8. 这条管线里最常见的断点

### 8.1 断在归一化阶段

表现：

- 前端画布看起来正常
- 后端收到的节点类型不对

通常原因：

- 忘了加 `BACKEND_TYPE_MAP`
- 节点被误当成 UI-only 类型

### 8.2 断在 validate 阶段

表现：

- `/tasks/validate` 返回结构化错误

通常原因：

- 多个 `Start`
- 没有 `End`
- 必填输入没绑
- 节点 type 未注册

### 8.3 断在 run 后 report 轮询阶段

表现：

- 有 `taskId`
- 但前端一直看不到结束状态

通常原因：

- executor 卡住
- 外部 HTTP / model 调用超时
- 轮询逻辑没正确处理 terminated 状态

### 8.4 断在读侧映射阶段

表现：

- 后端其实跑了
- 前端节点却没有高亮或没显示结果

通常原因：

- 前端没正确消费 `report.nodes`
- 只看 `result`，没看 `report` / `trace`
- 输出字段和前端 UI 假定路径不一致

## 9. 这个执行管线为什么有价值

它让系统天然分成三段：

- 编辑器问题
- 执行问题
- 读侧展示问题

这比“所有东西都塞进一次请求里”更容易调试，也更适合平台化。

如果你继续做复杂节点或前端面板，下一步建议看：

1. [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)
2. [Flowgram 自定义节点扩展](/docs/flowgram/custom-node-extension)
