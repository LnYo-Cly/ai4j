---
sidebar_position: 5
---

# 前端工作流如何在后端执行

这页专门把一条最容易讲散的链路讲完整：

- 前端画布上的节点图
- 如何变成后端可执行 schema
- 后端如何运行
- 前端如何看到 report 和 result

---

## 1. 从画布到后端的真实链路

当前参考实现的完整路径是：

```text
Flowgram 前端编辑器
  -> document.toJSON()
  -> serializeWorkflowForBackend(...)
  -> POST /flowgram/tasks/validate
  -> POST /flowgram/tasks/run
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> LLM runner / node executors
  -> GET /flowgram/tasks/{taskId}/report
  -> GET /flowgram/tasks/{taskId}/result
```

这条链路里，最关键的中间层就是 schema 归一化。

---

## 2. 前端不会直接把原始画布 JSON 发给后端

在 `ai4j-flowgram-webapp-demo` 里，真正提交前会走：

- `serializeWorkflowForBackend`

对应文件：

- `src/utils/backend-workflow.ts`

它当前会做这些转换：

- 去掉只用于 UI 的节点
- 规范节点 type
- 处理 loop 节点数据
- 清理无效边
- 输出后端 runtime 更容易执行的 schema

UI-only 节点当前包括：

- `Comment`
- `Group`
- `BlockStart`
- `BlockEnd`

这意味着：

- 画布里能看到的东西，不一定都会进入后端执行

---

## 3. 为什么要做 type 归一化

前端节点类型当前多是小写，例如：

- `llm`
- `tool`
- `knowledge`

但后端 runtime 识别的执行器类型通常是大写：

- `LLM`
- `TOOL`
- `KNOWLEDGE`

所以 `backend-workflow.ts` 里的 `BACKEND_TYPE_MAP` 是这条链路的关键桥接点。

如果你新增节点而不补映射，前端画布看起来能画，后端却不一定能跑。

---

## 4. 前端如何发起运行

前端当前不是自己散落地写多个 fetch，而是通过 runtime plugin 统一封装。

关键文件：

- `src/plugins/runtime-plugin/create-runtime-plugin.ts`
- `src/plugins/runtime-plugin/runtime-service/index.ts`
- `src/plugins/runtime-plugin/client/server-client/index.ts`

当前 server 模式下，前端会：

1. 先做本地表单校验
2. 调 `/flowgram/tasks/validate`
3. 校验通过后调 `/flowgram/tasks/run`
4. 轮询 `/flowgram/tasks/{taskId}/report`
5. 结束后拉 `/flowgram/tasks/{taskId}/result`

这是标准的“异步任务 + report/result 分离”模型。

---

## 5. 后端接到请求后做什么

后端主链路分三层。

### 5.1 `FlowGramTaskController`

只负责 REST 暴露：

- `run`
- `validate`
- `report`
- `result`
- `cancel`

### 5.2 `FlowGramRuntimeFacade`

负责平台层逻辑：

- request DTO 转 runtime input
- caller 解析
- access check
- task ownership
- task store 更新

### 5.3 `FlowGramRuntimeService`

负责真正执行流程图：

- 校验任务
- 注册执行器
- 运行节点图
- 聚合 report/result

---

## 6. 节点真正在哪执行

节点不是在 Controller 里跑的，而是在 runtime 中按类型分发。

当前内置执行路径包括：

- `FlowGramLlmNodeRunner` 处理 `LLM`
- `FlowGramHttpNodeExecutor` 处理 `HTTP`
- `FlowGramVariableNodeExecutor` 处理 `VARIABLE`
- `FlowGramCodeNodeExecutor` 处理 `CODE`
- `FlowGramToolNodeExecutor` 处理 `TOOL`
- `FlowGramKnowledgeRetrieveNodeExecutor` 处理 `KNOWLEDGE`

所以“前端画布能画这个节点”和“后端真的能执行这个节点”是两件事，必须同时成立。

---

## 7. 一个自定义节点怎么跑通前后端

以 `TRANSFORM` 节点为例，最小链路是：

1. 前端新增 `WorkflowNodeType.Transform`
2. 前端新增 `TransformNodeRegistry`
3. 前端把 `transform -> TRANSFORM` 加入 `BACKEND_TYPE_MAP`
4. 后端实现 `FlowGramNodeExecutor#getType() = "TRANSFORM"`
5. 把执行器注册进 Spring 容器
6. 前端提交 workflow schema
7. 后端按 `TRANSFORM` 分发执行器
8. report/result 返回这个节点的 inputs/outputs

这就是“画布上的节点在后端真正执行”的完整定义。

---

## 8. 报告和结果如何回到前端

前端 server client 会把后端返回的：

- workflow status
- node status
- node inputs
- node outputs
- errors

转换成 Flowgram runtime interface 需要的：

- `IReport`
- `NodeReport`
- `Snapshot`
- `WorkflowMessages`

这一步同样不是可有可无，因为前端状态条、节点运行态、错误提示都依赖它。

---

## 9. 平台化时最容易漏掉的三个点

### 9.1 只做了前端节点，没做后端执行器

结果是：

- 能拖进画布
- 但任务运行时报 unknown type

### 9.2 做了后端执行器，没做前端 type 映射

结果是：

- 后端有执行器
- 但前端发过去的 type 对不上

### 9.3 report/result 没有和前端节点输出对齐

结果是：

- 后端其实跑了
- 但前端看不到正确输出

---

## 10. 推荐阅读

1. [Flowgram Custom Nodes](/docs/flowgram/custom-nodes)
2. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)

