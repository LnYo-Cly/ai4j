---
sidebar_position: 4
---

# 前端画布与后端 Runtime 对接

这一页讲的不是“如何再写一个前端编辑器”，而是：

- 前端画布如何把工作流 schema 交给后端；
- 后端 runtime 如何执行任务；
- 两边的数据协议在哪里被适配。

AI4J 当前已经给出两端参考：

- 后端：`ai4j-flowgram-spring-boot-starter`
- 前端：`ai4j-flowgram-webapp-demo`

---

## 1. 整体对接结构

```text
Flowgram 前端画布
  -> runtime plugin
  -> server client
  -> /flowgram/tasks/*
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> node executors / LLM runner
```

如果你要做自己的 Agentic 工作流平台，这就是当前最重要的一条主链路。

---

## 2. 前端不是直接硬调五个接口

在 `ai4j-flowgram-webapp-demo` 里，前端运行时是通过 runtime plugin 接进去的。

关键文件：

- `src/plugins/runtime-plugin/create-runtime-plugin.ts`
- `src/plugins/runtime-plugin/runtime-service/index.ts`
- `src/plugins/runtime-plugin/client/server-client/index.ts`

当前 runtime plugin 支持两种模式：

- `browser`
- `server`

如果你要对接 AI4J 后端 runtime，应使用 `server` 模式。

在这个模式下：

- 前端会绑定 `WorkflowRuntimeServerClient`
- 通过它调用 `/flowgram/tasks/run`
- `/flowgram/tasks/validate`
- `/flowgram/tasks/{taskId}/report`
- `/flowgram/tasks/{taskId}/result`
- `/flowgram/tasks/{taskId}/cancel`

---

## 3. 前端发给后端前会做一次 schema 归一化

这一步很关键。

关键文件：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`

它当前做了几件事：

- 去掉只用于画布展示的节点类型，例如 `Comment`、`Group`、`BlockStart`、`BlockEnd`
- 把前端节点类型映射成后端 runtime 识别的类型
- 清理无效边
- 处理 loop 节点的输入补齐

当前可以直接确认的类型映射包括：

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

这意味着：

- 前端节点 type 不一定等于后端最终执行 type
- 你加自定义节点时，前后端都要同步考虑

---

## 4. 后端执行链路怎么接

后端入口主要是三层：

### 4.1 Controller

- `FlowGramTaskController`

负责暴露 REST API。

### 4.2 Facade

- `FlowGramRuntimeFacade`

负责：

- 请求转运行输入
- caller 解析
- 权限检查
- task store 写入
- 结果与报告输出

### 4.3 Runtime

- `FlowGramRuntimeService`

负责：

- 校验任务
- 执行节点图
- 聚合任务结果
- 返回 report / result

---

## 5. 前端测试运行的真实调用方式

在 `WorkflowRuntimeService` 里，前端当前流程是：

1. 先做本地表单校验
2. 调 `/tasks/validate`
3. 调 `/tasks/run`
4. 按固定轮询间隔拉 `/tasks/{taskId}/report`
5. 最后拉 `/tasks/{taskId}/result`

这说明当前平台形态是：

- 异步任务执行
- 前端轮询 report / result
- 不是单次同步返回全部结果

如果你后面要接企业平台，这个轮询模型需要在产品层面先接受。

---

## 6. 如何配置后端地址

`WorkflowRuntimeServerClient` 会把请求发到：

- 当前页面相对路径，或
- `protocol + domain + port`

也就是说，前端可以：

- 同域部署，直接走相对路径
- 前后端分离部署，显式指定 domain / port

如果你做正式平台，建议把：

- API host
- 协议
- 端口
- 反向代理规则

做成独立环境配置，而不是写死在 demo 里。

---

## 7. 安全与任务归属怎么接

后端 starter 当前已经预留了几层平台化接口：

- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`
- `FlowGramTaskStore`

默认行为是：

- caller 可匿名
- access checker 默认全部放行
- task store 默认内存版

这说明：

- demo 可以直接跑
- 真正的平台接入必须替换这几层

尤其是：

- 多租户
- 任务归属
- 权限控制
- 持久化任务查询

不要直接拿默认实现上线。

---

## 8. 推荐接入顺序

1. 先用 `ai4j-flowgram-demo` 跑通后端 API
2. 再用 `ai4j-flowgram-webapp-demo` 跑通 server 模式
3. 确认 schema 归一化后的节点类型与后端一致
4. 再补 caller、auth、task store、权限
5. 最后再加你的自定义节点与业务面板

---

## 9. 继续阅读

1. [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)
2. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
3. [Flowgram API 与运行时](/docs/flowgram/api-and-runtime)
4. [自定义节点扩展](/docs/flowgram/custom-node-extension)
5. [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)
