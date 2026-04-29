---
sidebar_position: 4
---

# 前端画布与后端 Runtime 对接

这一页讲的不是“再做一个前端编辑器”，而是 `Flowgram.ai` 画布怎样和 AI4J 的 Java 后端执行层对齐。

如果只说“前端调几个接口”会漏掉最关键的事实：前后端之间真正对齐的不是按钮，而是 3 个契约。

- workflow schema 契约
- task lifecycle 契约
- report / result / trace 读侧契约

## 1. 先看完整链路

当前参考实现的主链路是：

```text
Flowgram.ai canvas
  -> runtime plugin
  -> WorkflowRuntimeServerClient
  -> /flowgram/tasks/*
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> node executors / LLM node runner
  -> report / result / trace
```

这条链说明了一件事：AI4J 负责的不是前端画布本身，而是画布背后的正式执行后端。

## 2. 前端并不是“直接调五个接口”

在 `ai4j-flowgram-webapp-demo` 里，前端运行时是通过 runtime plugin 接进去的，而不是在组件里手写 `fetch`。

关键文件：

- `src/plugins/runtime-plugin/create-runtime-plugin.ts`
- `src/plugins/runtime-plugin/runtime-service/index.ts`
- `src/plugins/runtime-plugin/client/server-client/index.ts`

### 2.1 当前支持两种模式

- `browser`
- `server`

如果你要接 AI4J Java 后端，应使用 `server` 模式。

### 2.2 `server` 模式下谁负责发请求

前端会绑定 `WorkflowRuntimeServerClient`，由它统一调用：

- `POST /flowgram/tasks/validate`
- `POST /flowgram/tasks/run`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

这意味着前端最好把后端看成一个 task API server，而不是“某个节点自己临时发个请求”。

## 3. 前端发给后端前，schema 会先被重写

这一层是整个对接最容易被忽略、但实际最关键的部分。

关键文件：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

### 3.1 UI-only 节点不会进后端

当前会被过滤掉的至少包括：

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

这些对象对编辑器有意义，但对执行器没有意义。

### 3.2 前端类型会映射成后端执行类型

当前可直接确认的映射有：

- `start -> START`
- `end -> END`
- `llm -> LLM`
- `http -> HTTP`
- `code -> CODE`
- `condition -> CONDITION`
- `loop -> LOOP`
- `variable -> VARIABLE`
- `tool -> TOOL`
- `knowledge -> KNOWLEDGE`

这说明：

- 前端展示类型不一定等于后端协议类型
- 前端 schema 和后端 runtime 之间存在一个明确的适配层

### 3.3 这一步为什么重要

如果没有这层适配，后端就会被迫理解画布内部细节，最终造成：

- UI 结构侵入执行层
- 前端迭代牵动后端 contract
- 自定义节点难以维护

因此最稳的做法，是把“执行前归一化”视为正式协议步骤，而不是 demo 小技巧。

## 4. 后端侧不是只有 controller

后端真正参与对接的至少有 3 层。

### 4.1 `FlowGramTaskController`: HTTP 暴露层

Controller 默认挂在：

- `${ai4j.flowgram.api.base-path:/flowgram}`

它只负责暴露 REST 入口，不负责执行业务。

### 4.2 `FlowGramRuntimeFacade`: 平台治理层

Facade 做的事情比 controller 多得多：

- 把 request 转成 runtime 输入
- 解析 caller
- 做 access check
- 创建 task ownership
- 把状态写入 `FlowGramTaskStore`
- 根据配置决定是否带 node details / trace

如果你后面要接权限、任务中心或审计，这一层才是真正的后端边界。

### 4.3 `FlowGramRuntimeService`: 真正执行层

Runtime 负责：

- schema 校验
- task record 创建
- 节点图执行
- report / result 生成

这说明前后端对接不是“前端 JSON -> controller -> done”，中间隔着明确的平台层和执行层。

## 5. 前端运行时到底怎样驱动一次任务

`WorkflowRuntimeService` 当前的主流程非常明确。

### 5.1 提交前先做本地表单校验

前端会先遍历所有节点表单，确保基础表单约束先过。

### 5.2 再调 `/tasks/validate`

这一步负责发现后端视角的错误，例如：

- 图结构不合法
- 节点类型没注册
- 必填输入没绑定
- 引用路径无效

### 5.3 通过后再调 `/tasks/run`

后端返回的不是最终结果，而是 `taskId`。这说明运行模型是异步任务。

### 5.4 前端按固定间隔轮询 `report`

当前前端常量：

- `SYNC_TASK_REPORT_INTERVAL = 500`

也就是说，默认是每 `500ms` 轮询一次任务 report。

### 5.5 结束后再取 `result`

最终输出不是从 report 直接推断，而是通过 `/tasks/{taskId}/result` 正式获取。

## 6. `validate`、`report`、`result` 三种响应不要混用

这是接前端时最容易犯的一个错误。

### 6.1 `validate`

适合做：

- 提交前阻断
- 表单错误提示
- schema 合法性确认

不适合做：

- 运行时 UI 展示

### 6.2 `report`

适合做：

- 运行中状态面板
- 节点高亮
- 错误定位
- 进度时间线

如果 `reportNodeDetails = true`，还会带节点级 inputs / outputs。

### 6.3 `result`

适合做：

- 最终输出展示
- 任务结束页
- 成功 / 失败的最终收口

它更偏“最终结果视图”，而 report 更偏“执行过程视图”。

## 7. `trace` 是给前端直接消费的投影，不是原始埋点

当 `ai4j.flowgram.trace-enabled = true` 时：

- `/report`
- `/result`

都会附带 `trace` 字段。

这层数据的来源是：

- `FlowGramRuntimeEvent`
- `FlowGramRuntimeTraceCollector`
- `FlowGramTraceView`

### 7.1 它解决什么问题

前端需要的是：

- 顶部任务状态
- 节点执行时间线
- 哪个节点失败了
- 每个节点耗时和指标

这类 UI 不应直接消费后端内部埋点对象，而应该消费为前端投影过的 `FlowGramTraceView`。

### 7.2 为什么不直接让前端读 OTel

因为两层目标不同：

- OTel 面向后端 observability 平台
- `FlowGramTraceView` 面向前端画布运行时

把两者分开，前后端职责边界会更清楚。

## 8. Protocol Adapter 在这里的意义

`FlowGramProtocolAdapter` 是前后端协议收口点之一。

它负责把：

- HTTP request DTO
- runtime 输入输出模型
- 前端消费的 response DTO

连接起来。

### 为什么它很重要

因为它保证了几个关键事实：

- request 的 `schema` 可以是对象，也可以是 JSON 字符串
- response 会被复制成更安全的 map 结构
- report / result 的外部字段形态由 adapter 固定，而不是被 runtime 内部对象泄漏出去

这使得协议演进比直接暴露内部模型更可控。

## 9. 对接时最常见的 4 个问题

### 9.1 前端节点能显示，但后端不认识

通常原因是：

- 没做类型映射
- 后端没注册对应 executor
- 节点被误当成 UI-only 类型过滤掉

### 9.2 表单看起来填了，后端还是报必填缺失

通常原因是：

- 前端表单字段名和后端读取字段名不一致
- 绑定写在 `data` 里，但没有进入 `inputsValues`
- schema 归一化过程中字段丢失

### 9.3 任务能跑，但 report 看不清节点细节

通常要检查：

- `reportNodeDetails` 是否开启
- 前端是否在读 `workflow.nodes`
- 节点 outputs 是否被 executor 正常写出

### 9.4 重启后拿不到任务详情

这是架构边界，不一定是 bug。

当前运行态真相主要在 `FlowGramRuntimeService` 的进程内 `TaskRecord`。`FlowGramTaskStore` 会保存元数据和快照，但不是完整可恢复执行引擎。

## 10. 权限和多租户接入点在哪里

当前默认是轻安全姿态：

- `auth.enabled = false`
- caller 默认匿名
- access checker 默认放行

如果你要接企业平台，重点接入点在：

- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`

也就是说，权限控制不在前端 runtime plugin，而在后端 facade 这一层。

## 11. 最重要的对接原则

最稳的对接方式，不是让前端知道更多后端细节，而是守住这 3 条边界：

- 画布层负责编辑和展示
- 适配层负责把编辑态压成执行态
- 后端负责 task lifecycle 和节点执行

这 3 条边界稳了，前后端就能各自迭代而不互相拖垮。
