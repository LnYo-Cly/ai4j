---
sidebar_position: 1
---

# Agentic 工作流平台总览

这一专题的准确定位是：基于字节开源 `Flowgram` 工作流开发框架，AI4J 在其之上实现了 runtime、节点执行和后端 API，从而可以开发自己的 `AI Agent` 工作流平台。

所以这里讲的重点不是 “Flowgram 这个名字本身”，而是 AI4J 如何把它落成一套可运行、可扩展、可对接前端画布的 `Agentic 工作流平台`。

---

## 1. 它解决什么问题

Flowgram 主要面向三类场景：

- 想把 AI 能力封装成可视化节点流；
- 想把普通 HTTP / Tool / LLM / 变量处理组合成可配置流程；
- 想给前端流程编辑器提供一个稳定的后端执行 API。

如果你的需求是：

- 直接写 Java 代码做智能体推理与工具循环，请看 `Agent`；
- 直接在本地仓库里当 coding assistant 使用，请看 `Coding Agent`；
- 想做“可视化节点编排 + 后端执行服务”，再来看 `Flowgram`。

---

## 2. 组成结构

当前 Flowgram 的可用形态主要有两层：

### 2.1 Starter

`ai4j-flowgram-spring-boot-starter`

它负责：

- 自动装配运行时；
- 自动注册内置节点执行器；
- 暴露 `/flowgram` REST API；
- 接管任务运行、校验、结果查询与取消。

### 2.2 Demo

`ai4j-flowgram-demo`

它提供：

- 一个可直接启动的 Spring Boot 示例；
- 一个默认的 `LLM` 节点调用入口；
- 一个模拟接口 `GET /flowgram/demo/mock/weather`，便于测试 `HTTP` 节点。

### 2.3 WebApp Demo

`ai4j-flowgram-webapp-demo`

它提供：

- 基于字节 Flowgram 编辑器的前端画布示例；
- `runtime plugin` 的 server 模式接法；
- 前端 schema 到后端 runtime schema 的适配示例。

---

## 3. 当前 API 入口

后端默认通过下面这些接口暴露任务能力：

- `POST /flowgram/tasks/run`
- `POST /flowgram/tasks/validate`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

默认根路径来自：

- `ai4j.flowgram.api.base-path`

默认值是：

- `/flowgram`

---

## 4. 当前内置能力

从自动装配代码和测试可以确认，当前 Flowgram 已覆盖这些节点能力：

- `Start`
- `End`
- `LLM`
- `Variable`
- `Code`
- `Tool`
- `HTTP`
- `KnowledgeRetrieve`

其中：

- `HTTP`、`Variable`、`Code`、`Tool`、`KnowledgeRetrieve` 由 starter 自动注册执行器；
- `LLM` 节点由 `FlowGramLlmNodeRunner` 负责；
- `Start / End` 属于工作流运行时的结构节点；
- 你也可以自己注册新的节点类型。

---

## 5. 配置入口

Flowgram 的关键配置集中在：

```yaml
ai4j:
  flowgram:
    enabled: true
    default-service-id: glm-coding
    stream-progress: false
    report-node-details: true
    task-retention: 1h
    api:
      base-path: /flowgram
    task-store:
      type: memory
    cors:
      allowed-origins: []
    auth:
      enabled: false
      header-name: Authorization
```

当前默认任务存储是内存版。

---

## 6. 你应该从哪一页开始

建议先按场景判断自己属于哪条线，再进入具体 API 或扩展页。

优先看：

1. [Flowgram 使用路径与场景选择](/docs/flowgram/use-cases-and-paths)
2. [快速开始](/docs/flowgram/quickstart)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
4. [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)

### 6.1 普通使用者

先看：

1. [Flowgram 使用路径与场景选择](/docs/flowgram/use-cases-and-paths)
2. [快速开始](/docs/flowgram/quickstart)
3. [API 与运行时](/docs/flowgram/api-and-runtime)
4. [内置节点](/docs/flowgram/builtin-nodes)
5. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
6. [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)

### 6.2 扩展开发者

再看：

1. [Flowgram 使用路径与场景选择](/docs/flowgram/use-cases-and-paths)
2. [自定义节点扩展](/docs/flowgram/custom-node-extension)
3. [前端自定义节点开发](/docs/flowgram/frontend-custom-node-development)
4. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
5. [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)
6. `Agent` 章节里的 `Workflow` / `Trace`

---

## 7. 当前边界

现阶段 Flowgram 文档和实现仍有这些边界：

- 任务存储默认是内存实现；
- `Agent` 与 `MCP` 当前没有内置专属节点；
- 更复杂的权限模型、持久化任务存储、远程节点市场还不在当前 MVP 范围内。

---

## 8. 下一步

建议先从 [Flowgram 使用路径与场景选择](/docs/flowgram/use-cases-and-paths) 开始。
