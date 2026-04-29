---
sidebar_position: 7
---

# Flowgram API 与运行时

这一页不重复讲 runtime 内核本身，而是专门讲“外部调用者实际面对的控制面 contract”。

如果 `runtime.md` 讲的是执行真相，这一页讲的是：

- HTTP API 长什么样
- DTO 是怎样被 adapter 和 facade 变形的
- 哪些配置真正改变了外部行为
- task store、auth、trace 在外部协议里如何体现

## 1. 先看控制面，不要先看内部类名

外部调用者真正面对的是这 5 个入口：

- `POST /flowgram/tasks/validate`
- `POST /flowgram/tasks/run`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

默认根路径来自：

- `ai4j.flowgram.api.base-path`

默认值：

- `/flowgram`

这组接口共同构成 Flowgram 的 task control plane。

## 2. API 背后经过了哪几层

一次 HTTP 请求不会直接进 runtime，而是至少经过这 3 层：

1. `FlowGramTaskController`
2. `FlowGramRuntimeFacade`
3. `FlowGramProtocolAdapter`

外加最终的：

4. `FlowGramRuntimeService`

它们各自的职责不同。

### 2.1 Controller：只暴露 HTTP

Controller 只负责把 REST 请求接进来，并转给 facade。

### 2.2 Facade：平台语义收口

Facade 负责：

- resolve caller
- access check
- ownership 生成
- task store 同步
- trace / node detail 是否返回

### 2.3 ProtocolAdapter：把协议固定住

`FlowGramProtocolAdapter` 负责：

- 把 request DTO 变成 runtime `FlowGramTaskRunInput`
- 把 runtime 输出变成 `run/validate/report/result/cancel` response DTO
- 复制 map/list 值，避免内部对象直接泄漏给外部

因此外部协议的真实形态，不是直接由 runtime 内部类决定的，而是由 adapter 固化的。

## 3. `validate` 的 contract

### 输入

`validate` 接收：

- `schema`
- `inputs`

其中 `schema` 既可以是对象，也可以是字符串 JSON，因为 `FlowGramProtocolAdapter` 会统一做 `schemaToJson(...)`。

### 输出

`validate` 返回的核心字段是：

- `valid`
- `errors`

### 什么时候该调用

适合：

- 前端提交前校验
- CI 验证 workflow schema
- 节点编辑器联调

不适合：

- 用来当作真正执行结果

## 4. `run` 的 contract

### 输入

和 `validate` 一样，也是：

- `schema`
- `inputs`

### 输出

`run` 返回的不是最终结果，而是：

- `taskId`

这点必须强调，因为它决定了整个系统是任务式模型，不是同步 RPC。

### run 发生了什么

`FlowGramRuntimeFacade.run(...)` 会：

1. resolve caller
2. `ensureAllowed(RUN, ...)`
3. 调 `runtimeService.runTask(...)`
4. 创建 ownership
5. 把初始状态写入 `FlowGramTaskStore`

因此 `run` 不只是“启动 runtime”，它同时也是平台元数据创建点。

## 5. `report` 和 `result` 的 contract 差别

这两个接口最容易被误用。

### 5.1 `report`

`report` 更偏执行过程视图。

默认会返回：

- `taskId`
- `inputs`
- `outputs`
- `workflow`
- 可选 `nodes`
- 可选 `trace`

其中 `nodes` 是否返回，受：

- `ai4j.flowgram.report-node-details`

控制，默认是 `true`。

### 5.2 `result`

`result` 更偏最终收口视图。

核心字段是：

- `taskId`
- `status`
- `terminated`
- `error`
- `result`
- 可选 `trace`

### 5.3 为什么要拆成两种响应

因为两类调用方关心的问题不一样：

- 任务面板要看 `report`
- 最终结果消费者更关心 `result`

如果把两者混成一个接口，外部协议会越来越臃肿。

## 6. `cancel` 的 contract

`cancel` 的响应很简单：

- `success`

但它的语义不要理解成“事务式回滚”。当前实现只是：

- 标记 cancel requested
- 对任务 `Future` 发中断

所以它是 best-effort stop。

## 7. HTTP 层能看到哪些错误语义

### 7.1 校验错误

通常通过 `validate` 的 `valid=false` 和 `errors[]` 返回。

### 7.2 不存在的任务

从集成测试可确认，未知任务会返回 404，并带：

- `code = FLOWGRAM_TASK_NOT_FOUND`

这说明 task-not-found 在 HTTP 层已经是显式错误语义，不是简单返回空对象。

### 7.3 访问被拒绝

如果自定义 `FlowGramAccessChecker` 拒绝访问，facade 会抛访问拒绝异常。这是平台侧权限治理真正的拦截点。

## 8. 配置项里哪些会改变外部行为

不是所有配置都一样重要。下面这些会直接影响外部调用体验。

### 8.1 `enabled`

- 是否启用 Flowgram 整个 starter

### 8.2 `default-service-id`

- 决定 LLM 节点在未显式传 `serviceId` / `aiServiceId` 时默认走哪个服务

### 8.3 `stream-progress`

- 当前默认 `false`
- 不能把它误解成“现在已经有完整实时推送控制面”

### 8.4 `task-retention`

- 主要影响 ownership / retention 元数据
- 不等于 durable execution retention

### 8.5 `report-node-details`

- 决定 `report` 是否返回节点级 inputs / outputs

### 8.6 `trace-enabled`

- 决定 `report` / `result` 是否带 `trace`

### 8.7 `task-store.type`

支持：

- `memory`
- `jdbc`

### 8.8 `auth.enabled` / `auth.header-name`

决定 `DefaultFlowGramCallerResolver` 是否从请求头解析 caller。

默认是：

- `auth.enabled = false`

这也是为什么默认调用方通常会被当成匿名 caller。

## 9. JDBC store 在 API 层到底意味着什么

这一点非常容易被写错。

切到：

```yaml
ai4j:
  flowgram:
    task-store:
      type: jdbc
```

再加上 `DataSource` 后，starter 会装配：

- `JdbcFlowGramTaskStore`

这会给你：

- 任务元数据落库
- 状态快照落库
- result snapshot 落库

但不要把它误解成“外部 API 已经变成可跨进程恢复执行的 durable workflow engine”。

当前：

- `report` / `result` 的第一真相仍来自 runtime 进程内 `TaskRecord`
- task store 更像平台记录层

## 10. `report` / `result` 和 trace 的关系

trace 不是一个额外独立接口，而是嵌在 `report` / `result` 里的前端可消费投影。

这层数据来自：

- runtime event
- trace collector
- trace response enricher

它的目标是：

- 给前端画布运行态
- 给任务详情页
- 给节点级调试面板

它不是为了替代 OTel，也不是为了把内部事件原样暴露给客户端。

## 11. 什么时候应该读这页，而不是读 runtime 页

优先读这页的场景：

- 你在写前端或测试脚本
- 你在接 HTTP 客户端
- 你在做 task API 治理
- 你在接 caller / access / ownership / task store

优先读 `runtime.md` 的场景：

- 你在追执行链
- 你在写 executor
- 你在看 graph validation 和 node dispatch

如果只记一句话：

`runtime.md` 解释“系统怎么跑”，这一页解释“外部该怎么和它说话”。
