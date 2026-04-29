---
sidebar_position: 1
---

# Agentic 工作流平台总览

这一章讲的不是单独的 `Flowgram.ai` 前端库，而是 AI4J 围绕它补出来的一整套 Java 后端执行体系。

先把边界讲清楚：

- `Flowgram.ai` 是字节开源的前端工作流画布 / editor 库
- AI4J 提供的是后端 runtime、Spring Boot starter、任务 API、节点执行器、trace 投影，以及 demo 接入面

所以这里讨论的重点不是“怎么画流程图”，而是“流程图交给谁执行、怎样校验、怎样取消、怎样观察、怎样扩展成平台后端”。

## 1. 这条子系统到底是什么

如果只看页面标题，`Flowgram` 很容易被误解成“Agent 的一个可视化壳”。源码里并不是这样。

它更接近下面这个定义：

> 一个以显式工作流图为契约、以异步任务 API 为控制面、以节点执行器为扩展点的 Java 工作流后端。

这意味着它的核心不是 prompt，而是 3 个更硬的对象：

- 工作流 schema
- task lifecycle
- node executor contract

和普通 `Agent` 运行时相比，`Flowgram` 先固定流程，再执行节点；`Agent` 则更偏向先给目标，再让模型在循环里决定下一步。

## 2. 模块边界和真实代码落点

这一章至少要按 4 层理解，而不是把 demo 和 runtime 混成一层：

### 2.1 前端画布层

- `Flowgram.ai`
- `ai4j-flowgram-webapp-demo/`

这层负责：

- 节点编辑
- 工作流 JSON 序列化
- 运行时 UI
- 调后端 task API

### 2.2 Spring Boot 平台接入层

- `ai4j-flowgram-spring-boot-starter/`

关键类：

- `FlowGramAutoConfiguration`
- `FlowGramTaskController`
- `FlowGramRuntimeFacade`
- `FlowGramTaskStore`

这层负责把 runtime 包装成一个能被平台直接消费的 HTTP 服务。

### 2.3 执行引擎层

- `ai4j-agent/.../flowgram/FlowGramRuntimeService`

这层负责：

- schema 解析与校验
- task record 创建
- 节点调度
- 状态流转
- report / result 组装

### 2.4 AI 能力复用层

- `ai4j-agent/.../flowgram/Ai4jFlowGramLlmNodeRunner`
- `ai4j` / `ai4j-agent` 里的模型、RAG、Tool 等基座能力

其中最关键的一点是：LLM 节点不是自己重新实现一套推理栈，而是复用 `AgentBuilder` 和 `ReActRuntime`，只是在 Flowgram 场景里把它约束成单步节点执行。

## 3. 三个最重要的设计决策

理解这套子系统，先抓住 3 个设计决策，比先背 API 更重要。

### 3.1 它的控制面是 task API，不是一次性同步调用

`FlowGramTaskController` 默认暴露：

- `POST /flowgram/tasks/run`
- `POST /flowgram/tasks/validate`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

这说明它的默认产品形态是“异步任务后端”，而不是“HTTP 进来，模型一次性返回字符串”。

### 3.2 工作流图是正式契约，不是前端临时 JSON

前端不会把画布对象原样扔给后端。

`ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts` 会先做归一化：

- 去掉 `Comment`、`Group`、`BlockStart`、`BlockEnd` 这类只属于画布的节点
- 把前端类型映射成后端执行类型，例如 `start -> START`、`llm -> LLM`
- 清理无效边
- 只把真正可执行的节点图送进后端

这一步很重要，因为它把“编辑态模型”和“执行态模型”明确分开了。

### 3.3 LLM 节点复用了 Agent，但没有把工作流重新变回自由推理

`Ai4jFlowGramLlmNodeRunner` 每次执行一个 LLM 节点时，会动态构造一个 `Agent`：

- 默认 runtime 是 `ReActRuntime`
- 默认 `AgentOptions` 是 `maxSteps(1)`、`stream(false)`
- 模型名从 `modelName` / `model` / `modelId` 里取
- prompt 从 `prompt` / `message` / `input` 里取

所以 Flowgram 并不是和 Agent 彻底分家，而是把 Agent 作为“单节点智能执行器”嵌进显式工作流里。

## 4. 一次真实运行的主链路

最有价值的理解方式，是沿着一次 `run` 请求往下看：

```text
Flowgram.ai canvas
  -> normalizeWorkflowForBackend(...)
  -> POST /flowgram/tasks/run
  -> FlowGramTaskController
  -> FlowGramRuntimeFacade
  -> FlowGramRuntimeService
  -> built-in logic / FlowGramNodeExecutor / Ai4jFlowGramLlmNodeRunner
  -> task report / task result / trace projection
```

把它拆细一点，大致是 6 步。

### 4.1 前端构造后端可执行 schema

前端运行时在 `WorkflowRuntimeService` 中先做表单校验，再发 `/tasks/validate`，通过后才发 `/tasks/run`。这一步决定了平台交互是“先预检，再启动任务”。

### 4.2 Controller 只做 HTTP 暴露

`FlowGramTaskController` 本身很薄。它的职责不是执行业务，而是把请求转给 `FlowGramRuntimeFacade`。

### 4.3 Facade 负责平台治理逻辑

`FlowGramRuntimeFacade` 处理的事情明显比 controller 多：

- 解析 caller
- 执行 access check
- 创建 task ownership
- 调用 runtime
- 把结果同步到 `FlowGramTaskStore`
- 根据配置决定是否返回 node details 和 trace

这说明真正的平台边界在 facade，而不是 controller。

### 4.4 Runtime 创建任务并异步执行

`FlowGramRuntimeService.runTask(...)` 会：

1. 解析和校验 schema
2. 创建 `taskId`
3. 在进程内 `ConcurrentMap<String, TaskRecord>` 里登记任务
4. 把执行逻辑提交到内部 `ExecutorService`
5. 立即返回 `taskId`

这一步直接决定了它是异步模型。

### 4.5 节点执行按类型分流

`FlowGramRuntimeService` 内核原生理解的节点类型只有：

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

其它能力不是 runtime 内核硬编码，而是走注册式 executor：

- `HTTP`
- `VARIABLE`
- `CODE`
- `TOOL`
- `KNOWLEDGE`

这些主要由 starter 通过 bean 注入后注册到 runtime。

### 4.6 前端通过轮询消费 report / result

`ai4j-flowgram-webapp-demo` 当前默认每 `500ms` 拉一次 report，再在任务结束时取 result。这说明当前默认交互模型是“轮询式异步任务 UI”，不是 SSE / WebSocket first。

## 5. 默认行为和它们的后果

源码里的默认值会直接影响你对这套系统的预期。

### 5.1 默认 API 根路径是 `/flowgram`

`FlowGramProperties.ApiProperties.basePath = "/flowgram"`。

这意味着前后端 demo、测试脚本、运维文档默认都围绕这组路径组织。

### 5.2 默认 task store 是内存

`FlowGramProperties.TaskStoreProperties.type = "memory"`。

后果是：

- demo 启动最轻
- 适合单进程开发联调
- 但不是持久化工作流引擎

如果你要更接近平台场景，应切到 JDBC store。

### 5.3 默认 trace 打开，节点细节也打开

- `traceEnabled = true`
- `reportNodeDetails = true`

这对调试很友好，但也意味着你要自己评估生产环境下的返回体体积和敏感信息暴露范围。

### 5.4 默认 auth 是关闭的

- `auth.enabled = false`
- `DefaultFlowGramAccessChecker` 默认永远返回 `true`

也就是说，starter 默认更偏 demo / 内网集成姿态，而不是开箱即用的强安全平台。

### 5.5 默认任务保留时间是 1 小时

`taskRetention = Duration.ofHours(1)`。

它主要用于 ownership / store 元数据，不等于“任务状态可以完整跨进程恢复 1 小时”。

## 6. 这套体系适合什么，不适合什么

### 更适合

- 任务天然就是流程图
- 前端要给用户一个可视化编排界面
- 节点输入输出 schema 需要稳定
- 平台要有校验、运行、报告、取消这些正式控制面

### 不应优先选它

- 任务核心是自由推理，而不是固定流程
- 你需要的是本地代码仓交互式 agent
- 你只想写一个最简单的模型调用 demo

## 7. 建议阅读顺序

建议按这个顺序进入，不要一上来就跳到自定义节点：

1. [Why Flowgram](/docs/flowgram/why-flowgram)
2. [Architecture](/docs/flowgram/architecture)
3. [Quickstart](/docs/flowgram/quickstart)
4. [Runtime](/docs/flowgram/runtime)
5. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
6. [Built-in Nodes](/docs/flowgram/built-in-nodes)
7. [Custom Nodes](/docs/flowgram/custom-nodes)

如果你只想抓住一句话：

`Flowgram.ai` 负责把流程画出来，AI4J Flowgram 负责把这张图当成正式任务后端跑起来。
