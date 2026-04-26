---
sidebar_position: 2
---

# Agentic 工作流平台使用路径与场景选择

如果你已经知道自己要做的是“AI Agent 工作流平台”或“可视化流程编排平台”，但还不确定应该先跑 demo、先对接前端画布，还是先写自定义节点，这一页就是入口。

目标是先按角色和任务形态分流，再进入具体 API 文档。

---

## 1. 三类最常见使用者

### 1.1 普通使用者

典型目标：

- 把 demo 跑起来
- 调通一次 `/flowgram/tasks/run`
- 看明白任务结果和报告

先看：

1. [Agentic 工作流平台快速开始](/docs/flowgram/quickstart)
2. [Flowgram Runtime](/docs/flowgram/runtime)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)

### 1.2 平台接入方

典型目标：

- 把基于 Flowgram 的 AI4J runtime 当成后端运行服务接进自己的前端画布
- 统一调用 `/run`、`/validate`、`/result`、`/report`
- 接业务鉴权、任务管理和结果展示

先看：

1. [Flowgram Runtime](/docs/flowgram/runtime)
2. [Flowgram 内置节点](/docs/flowgram/built-in-nodes)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
4. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
5. [FAQ](/docs/faq)

### 1.3 节点扩展开发者

典型目标：

- 新增自定义节点类型
- 给企业内部系统做专属节点
- 约定前端 schema 和后端执行器

先看：

1. [Flowgram Custom Nodes](/docs/flowgram/custom-nodes)
2. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
3. [Flowgram 内置节点](/docs/flowgram/built-in-nodes)
4. [Flowgram Runtime](/docs/flowgram/runtime)
5. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
6. [Agent、Tool、知识库与 MCP 接入](/docs/flowgram/agent-tool-knowledge-integration)

---

## 2. 一个更直接的判断方法

| 当前任务 | 推荐入口 | 不建议一开始就看 |
| --- | --- | --- |
| 先把 demo 跑起来 | 快速开始 | 自定义节点扩展 |
| 对接前端画布 | API 与运行时 | 一开始就看全部 Agent 文档 |
| 新增企业内部节点 | 自定义节点扩展 | 只靠 LLM 节点硬塞逻辑 |
| 熟悉有哪些现成能力 | 内置节点 | 先写自己的执行器 |

核心原则：

- 先把“能跑”打通
- 再做“能接”
- 最后做“能扩”

---

## 3. 什么时候该选 Agentic 工作流平台，而不是 Agent

更适合这套 Agentic 工作流平台的场景：

- 任务天然是流程图
- 前端会画节点和边
- 节点输入输出 schema 要稳定
- 希望后端只负责运行和报告

更适合 Agent 的场景：

- 需要多轮推理
- 需要模型自己决定工具调用路径
- 需要复杂 handoff 或多 Agent 编排

简化理解：

- Flowgram runtime 偏“显式流程”
- Agent 偏“推理驱动流程”

---

## 4. 什么时候不该先自定义节点

如果你还没有完成下面三件事，先不要急着写新节点：

1. demo 已经跑通
2. `/run -> /result` 主链路已经打通
3. 你已经能清楚区分 LLM 节点逻辑和固定业务逻辑

很多团队一开始就写自定义节点，最后发现其实只是想先验证任务执行链路，这会浪费时间。

---

## 5. 推荐演进顺序

建议按下面顺序推进：

1. `demo`
2. 最小无 LLM 流程
3. 最小 LLM 流程
4. 前端调用 `/validate` 和 `/run`
5. 引入内置节点
6. 再新增自定义节点
7. 最后再补任务治理、鉴权、持久化

这个顺序能明显降低排障复杂度。

---

## 6. 与 MCP / Agent / Coding Agent 的边界

### 与 MCP

MCP 解决的是“工具能力从哪里来”。

这套平台能力解决的是“流程节点如何组织和运行”。

### 与 Agent

Agent 解决的是“推理循环与工具调用策略”。

Flowgram runtime 解决的是“显式节点图执行”。

### 与 Coding Agent

Coding Agent 面向本地代码仓交互，不是给前端画布用的工作流后端。

---

## 7. 推荐阅读

1. [Agentic 工作流平台快速开始](/docs/flowgram/quickstart)
2. [Flowgram Runtime](/docs/flowgram/runtime)
3. [Flowgram 内置节点](/docs/flowgram/built-in-nodes)
4. [Flowgram Custom Nodes](/docs/flowgram/custom-nodes)
5. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
6. [Flowgram Custom Nodes](/docs/flowgram/custom-nodes)
7. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
