---
sidebar_position: 2
---

# Flowgram 使用路径与场景选择

这一页不重复介绍概念，而是直接回答两个问题：

- 你当前最应该从哪条路径进入
- 这个问题到底该落到 `Flowgram`、`Agent`、`Coding Agent` 还是 `MCP`

如果把 Flowgram 当成“另一个 Agent 章节”来读，通常会走错方向。它更像“显式工作流平台后端”。

## 1. 先按目标选路径

最省时间的方式，不是从头到尾线性读，而是先按你手上的任务选入口。

### 路径 A: 先验证后端到底能不能跑

适合你在做的事：

- 先把 Spring Boot demo 跑起来
- 想直接看 `/tasks/run -> /report -> /result`
- 还没有前端画布

先看：

1. [Quickstart](/docs/flowgram/quickstart)
2. [Runtime](/docs/flowgram/runtime)
3. [Architecture](/docs/flowgram/architecture)

这一条路径的目标不是“看懂全部源码”，而是先确认 task lifecycle 和节点执行链是通的。

### 路径 B: 你已经有 Flowgram.ai 画布，要接 AI4J 后端

适合你在做的事：

- 前端已经能画流程
- 现在需要一个 Java 后端执行层
- 关心 schema、轮询、report、result、cancel

先看：

1. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
2. [Architecture](/docs/flowgram/architecture)
3. [Runtime](/docs/flowgram/runtime)

然后再回头看这些真实代码入口：

- `ai4j-flowgram-webapp-demo/src/utils/backend-workflow.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/runtime-service/index.ts`
- `ai4j-flowgram-webapp-demo/src/plugins/runtime-plugin/client/server-client/index.ts`

这一条路径最关键的是先看清“前端编辑态”如何被压成“后端执行态”。

### 路径 C: 你想扩展企业专属节点

适合你在做的事：

- 想接企业内部 HTTP 服务
- 想把某段稳定业务逻辑做成正式节点
- 需要比 prompt 更强的输入输出约束

先看：

1. [Built-in Nodes](/docs/flowgram/built-in-nodes)
2. [Custom Nodes](/docs/flowgram/custom-nodes)
3. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
4. [Runtime](/docs/flowgram/runtime)

这一条路径最重要的原则是：先确认现有节点不够，再扩。

### 路径 D: 你要把知识库、工具、服务接进流程

适合你在做的事：

- 想把 RAG 能力变成 workflow 节点
- 想让某个节点走 Tool 或模型能力
- 想让工作流接 AI4J 其它基座模块

先看：

1. [Built-in Nodes](/docs/flowgram/built-in-nodes)
2. [Agent / Tool / Knowledge Integration](/docs/flowgram/agent-tool-knowledge-integration)
3. [Runtime](/docs/flowgram/runtime)

这里要特别注意：`Flowgram` 不是重新实现一套 AI 能力，它是把 AI4J 基座能力按节点 contract 重新组织。

### 路径 E: 你要把它做成真正的平台后端

适合你在做的事：

- 想接任务权限、租户、任务中心
- 想切 JDBC task store
- 想把 trace 和任务详情页接起来

先看：

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)

再重点看这些类：

- `FlowGramRuntimeFacade`
- `FlowGramTaskStore`
- `DefaultFlowGramTaskOwnershipStrategy`
- `DefaultFlowGramCallerResolver`
- `DefaultFlowGramAccessChecker`

## 2. 什么时候该选 Flowgram，而不是别的子系统

这是最容易混淆的地方。

### 2.1 选 Flowgram

当你的主问题是下面这些时，优先选 `Flowgram`：

- 要把流程画出来，并交给后端稳定执行
- 要有 `validate/run/report/result/cancel` 这样的正式控制面
- 节点输入输出要稳定
- 前端需要节点级状态和 trace

### 2.2 选 Agent

当你的主问题是下面这些时，优先选 `Agent`：

- 任务是开放式多步推理
- 工具调用路径由模型自己决定
- 需要 memory、handoff、team orchestration

一句话说，`Agent` 先给目标，`Flowgram` 先给图。

### 2.3 选 Coding Agent

当你的主问题是下面这些时，优先选 `Coding Agent`：

- 任务对象是本地代码仓
- 需要 shell、文件系统、编辑器、回归闭环
- 要在 workspace 里完成编码任务

`Coding Agent` 是仓库交互运行时，不是给前端画布提供后端执行层。

### 2.4 选 MCP

当你的主问题是下面这些时，优先选 `MCP`：

- 你在解决“能力从哪里来”
- 你要把外部系统封装成标准工具协议
- 你想把某类服务供给多个运行时复用

`MCP` 解决的是能力供给；`Flowgram` 解决的是这些能力在工作流里如何被组织和执行。

## 3. 推荐的演进顺序

如果你要从 demo 走到真实平台，建议按这个顺序推进，而不是一上来就做所有功能。

1. 跑通后端 demo，确认 `/validate`、`/run`、`/result` 主链路
2. 接前端画布，确认 schema 归一化和 report 轮询链路
3. 只用内置节点做一条最小业务流
4. 再补一个真正需要的自定义节点
5. 再切 JDBC task store 和权限接入
6. 最后再优化 trace、任务中心、部署拓扑

这个顺序的核心目的，是把问题从“画布问题”“执行问题”“平台治理问题”分开排查。

## 4. 常见误判

### 4.1 一上来就写自定义节点

很多团队的第一反应是“我要先把业务节点都做出来”。这通常过早。

更常见的真实阻塞其实是：

- 前后端 schema 还没对齐
- 任务轮询链路没通
- report / result 读侧还没确认

在这些问题没解决前，自定义节点只会增加噪音。

### 4.2 把 task store 当成完整持久化引擎

当前实现的 `FlowGramTaskStore` 更偏 metadata / snapshot 持久化；运行态真相仍在 `FlowGramRuntimeService` 进程内。

如果你预期的是“进程重启后原任务还能完整恢复继续执行”，那就不是当前这套实现默认提供的能力。

### 4.3 把 Flowgram 当成“Agent 可视化”

两者共享模型与工具基座，但控制逻辑不同。误把 Flowgram 当成 Agent UI，最终会在契约设计上走偏。

## 5. 按角色给出最短阅读路径

### 前端工程师

先看：

1. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
2. [Architecture](/docs/flowgram/architecture)

然后再读：

- `backend-workflow.ts`
- `WorkflowRuntimeService`

因为你最先要搞懂的是“哪些节点会被送去后端，哪些不会”。

### 后端平台工程师

先看：

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)

然后再读：

- `FlowGramRuntimeFacade`
- `FlowGramRuntimeService`
- `FlowGramTaskStore`

因为你最先要搞懂的是“执行真相在哪里、治理入口在哪里、默认边界是什么”。

### 节点扩展开发者

先看：

1. [Built-in Nodes](/docs/flowgram/built-in-nodes)
2. [Custom Nodes](/docs/flowgram/custom-nodes)

然后再读：

- 你的前端节点 schema
- 对应的 `FlowGramNodeExecutor`

因为节点扩展本质上是前后端契约设计，不是只写一个后端类。

## 6. 如果你只想选一条起步路线

多数团队最稳的起点其实是：

1. 先把后端 runtime 跑通
2. 再把画布接进来
3. 最后再扩节点

这比“同时改前端、后端、节点协议”要稳定得多。
