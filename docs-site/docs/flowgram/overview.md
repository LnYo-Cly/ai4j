---
sidebar_position: 1
---

# Agentic 工作流平台总览

先把一个关键边界说清楚：

- `Flowgram.ai` 本身是字节开源的前端工作流/画布库
- AI4J 在这之上补的是后端 runtime、Spring Boot starter、节点执行、任务 API，以及前后端对接示例

所以这一章讲的重点不是“Flowgram.ai 前端库本身怎么用”，而是 AI4J 如何围绕它落成一套可运行的工作流平台后端。

这条线对应的是：

- `ai4j-flowgram-spring-boot-starter/`
- `ai4j-flowgram-demo/`
- `ai4j-flowgram-webapp-demo/`

如果 `Agent` 解决的是“通用智能体 runtime 怎么做”，那么 `Flowgram` 这条线解决的就是“如何围绕 `Flowgram.ai` 这样的前端画布，把这些能力落成一套可视化节点工作流平台后端和执行层”。

## 1. 三分钟理解 Flowgram

先记住这四句话：

- `Flowgram` 不是 `Agent` 的另一层 UI 壳
- 它面向的是节点图、任务 API、平台后端和前端画布对接
- 它的强项是稳定流程、明确 schema 和平台化运行
- 它更适合“节点流先于自由推理”的场景

一句话定义可以概括成：

> 一个基于 Flowgram 工作流框架、由 AI4J 提供后端 runtime、节点执行和平台接入能力的可视化工作流平台后端。

## 2. 它到底解决什么问题

当你的任务更适合画成流程图，而不是完全交给模型自由决定下一步时，你需要解决的问题通常是：

- 节点输入输出 schema 怎么稳定
- 后端怎么运行、校验、取消、查询任务
- LLM、HTTP、Tool、变量处理怎么组合成可执行节点流
- 前端画布和后端 runtime 怎么对接
- 节点如何扩展，平台如何演进

`Flowgram` 这一层就是把这些问题平台化。

## 3. 模块路径和组成方式

这一层最好按“前端是谁、后端是谁”来理解：

- 上游前端库：`Flowgram.ai`
- AI4J 后端接入层：`ai4j-flowgram-spring-boot-starter`
- AI4J 后端示例：`ai4j-flowgram-demo`
- AI4J 前后端联调示例：`ai4j-flowgram-webapp-demo`

### 3.1 Starter

`ai4j-flowgram-spring-boot-starter`

它负责：

- 自动装配 runtime
- 注册内置节点执行器
- 暴露 `/flowgram` 任务 API
- 组织节点执行与任务生命周期

### 3.2 Demo

`ai4j-flowgram-demo`

它负责：

- 给你一个可直接启动的 Spring Boot 示例
- 提供最短的后端验证路径
- 用真实 REST API 帮你跑通 `run -> result -> report`

### 3.3 WebApp Demo

`ai4j-flowgram-webapp-demo`

它负责：

- 演示如何把 `Flowgram.ai` 前端画布接到 AI4J 后端 runtime
- 演示前端画布如何接后端 runtime
- 演示 schema 适配和前后端联动

## 4. 这条线的核心能力是什么

从当前 starter、demo 和相关文档看，这条线的重点能力包括：

- 任务 API：`run / validate / result / report / cancel`
- 节点执行：`Start`、`End`、`LLM`、`Variable`、`Code`、`Tool`、`HTTP`、`KnowledgeRetrieve`
- 前后端对接：画布 schema 到 runtime schema 的适配
- 平台后端：Spring Boot 方式接入、配置、任务存储、扩展节点

## 5. 和相邻模块的边界

### 5.1 和 Agent 的边界

`Agent` 更适合：

- 多轮推理
- 工具决策由模型主导
- runtime 自由度更高

`Flowgram` 更适合：

- 节点图天然更稳定的流程
- 输入输出 schema 需要更明确
- 平台需要对前端画布暴露可执行后端 API

一个偏 runtime，自由度更高；一个偏平台流程，结构更强。

同时还要再加一条边界：

- `Flowgram.ai` 负责前端画布与交互
- AI4J `Flowgram` 这条线负责后端 runtime、任务 API 和节点执行

### 5.2 和 Coding Agent 的边界

`Coding Agent` 面向本地代码仓任务交付。

`Flowgram` 面向可视化流程平台后端。

如果你在做“让 agent 帮我读改本地仓库”，先看 `Coding Agent`。如果你在做“给前端流程编辑器一个稳定的执行引擎”，先看 `Flowgram`。

### 5.3 和 Core SDK 的边界

`Core SDK` 提供模型、工具、`MCP`、RAG 等基座能力。

`Flowgram` 则把其中一部分能力按“节点运行 + 后端任务 API”的方式重新组织出来。

## 6. 什么时候该选 Flowgram

更适合：

- 任务天然是流程图
- 前端会画节点
- 希望后端以任务 API 形式稳定执行
- 想把 LLM、HTTP、Tool、知识检索统一装进一个平台后端

不一定优先：

- 完全自由推理的智能体任务
- 本地代码仓交互式编码场景
- 只想直接写少量 Java 调模型代码

## 7. 推荐阅读顺序

1. [Why Flowgram](/docs/flowgram/why-flowgram)
2. [Flowgram Quickstart](/docs/flowgram/quickstart)
3. [Architecture](/docs/flowgram/architecture)
4. [Runtime](/docs/flowgram/runtime)
5. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
6. [Built-in Nodes](/docs/flowgram/built-in-nodes)
7. [Custom Nodes](/docs/flowgram/custom-nodes)
8. [Agent / Tool / Knowledge Integration](/docs/flowgram/agent-tool-knowledge-integration)

## 8. 当前边界

现阶段这条线仍有这些边界：

- 任务存储默认是内存实现
- `Agent` 与 `MCP` 目前没有单独的内置专属节点体系
- 更复杂的权限模型、持久化任务存储、远程节点市场仍不属于当前 MVP 范围

如果你是第一次进入这一章，下一页建议先看 [Why Flowgram](/docs/flowgram/why-flowgram)。
