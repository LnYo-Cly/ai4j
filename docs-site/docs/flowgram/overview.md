---
sidebar_position: 1
---

# FlowGram 总览

AI4J 的 FlowGram 方向不是前端画布本身，而是围绕 FlowGram.ai 画布补出来的 Java 后端执行层。

先把边界讲清楚：

- `FlowGram.ai` 是前端工作流画布 / editor。
- `ai4j-flowgram-spring-boot-starter` 提供 Java 后端 task API、runtime facade、节点执行和 trace bridge。
- `ai4j-flowgram-demo` 和 `ai4j-flowgram-webapp-demo` 是演示和联调用的 demo surface。

## 一句话定位

AI4J FlowGram 解决的是：

> 把前端画出来的工作流图，转换成后端可验证、可运行、可取消、可观测的 Java task。

它不是把 Agent 套一层可视化 UI，而是以显式 workflow schema、task lifecycle 和 node executor contract 为中心。

## 什么时候用 FlowGram

| 场景 | 是否适合 |
| --- | --- |
| 任务天然是节点图、条件分支、循环和稳定输入输出 | 适合 |
| 前端需要给用户拖拽、编辑、运行工作流 | 适合 |
| 平台需要 validate、run、report、result、cancel API | 适合 |
| 每一步都希望由模型自由决定下一步 | 优先看 Agent |
| 本地代码仓读写、shell、patch、审批 | 优先看 Coding Agent |
| 只是一次模型调用 | 只需要 Core SDK |

## 系统分层

| 层 | 模块 / 入口 | 职责 |
| --- | --- | --- |
| 前端画布 | `ai4j-flowgram-webapp-demo`、FlowGram.ai | 编辑节点、连线、表单和运行 UI |
| Spring Boot 接入 | `ai4j-flowgram-spring-boot-starter` | task API、配置、鉴权扩展、task store |
| 执行引擎 | `FlowGramRuntimeService` | 解析 schema、创建 task、调度节点、收口 result |
| 节点扩展 | `FlowGramNodeExecutor` | HTTP、VARIABLE、CODE、TOOL、KNOWLEDGE 等节点扩展 |
| AI 能力 | `ai4j`、`ai4j-agent` | LLM 节点、Tool、Knowledge、trace 复用 |

## 默认 task API

starter 默认围绕 `/flowgram` 暴露 task 控制面：

| API | 作用 |
| --- | --- |
| `POST /flowgram/tasks/validate` | 校验工作流 schema |
| `POST /flowgram/tasks/run` | 启动异步 task |
| `GET /flowgram/tasks/{taskId}/report` | 查看节点状态、trace 和过程报告 |
| `GET /flowgram/tasks/{taskId}/result` | 获取最终结果 |
| `POST /flowgram/tasks/{taskId}/cancel` | 取消任务 |

这说明 FlowGram 的默认形态是异步任务后端，不是“一次 HTTP 请求直接返回模型文本”。

## 与 Agent 的关系

FlowGram 和 Agent 可以复用同一套 AI4J 能力，但推进方式不同。

| 维度 | Agent | FlowGram |
| --- | --- | --- |
| 推进方式 | 模型和 runtime 决定下一步 | 工作流图决定下一步 |
| 结构 | 自由循环、tool loop、handoff | 显式节点、边、条件和 task lifecycle |
| 适合 | 多步推理、动态工具选择 | 稳定流程、平台化编排、可视化操作 |
| 观测 | Agent event / trace | task report / node status / trace bridge |

LLM 节点可以复用 Agent runtime，但 FlowGram 不会把整个工作流重新变成自由推理。

## 上线前要确认什么

默认配置更适合 demo 和内网联调，正式上线前应确认：

- API 是否有鉴权或网关保护。
- task store 是否从 memory 切到符合需求的持久化方案。
- report 是否允许返回 node details 和 trace。
- HTTP / CODE / TOOL / KNOWLEDGE 节点是否有白名单、超时和审计。
- 前端编辑态 schema 到后端执行态 schema 的转换是否稳定。
- cancel、失败重试、异常展示和日志脱敏是否覆盖。

更多检查项见 [Production Checklist](/docs/operations/production-checklist)。

## 推荐阅读顺序

1. [Why FlowGram](/docs/flowgram/why-flowgram)
2. [Use Cases and Paths](/docs/flowgram/use-cases-and-paths)
3. [Quickstart](/docs/flowgram/quickstart)
4. [Architecture](/docs/flowgram/architecture)
5. [Runtime](/docs/flowgram/runtime)
6. [Frontend / Backend Integration](/docs/flowgram/frontend-backend-integration)
7. [API and Runtime](/docs/flowgram/api-and-runtime)
8. [Built-in Nodes](/docs/flowgram/built-in-nodes)
9. [Custom Nodes](/docs/flowgram/custom-nodes)

如果你要做的是本地代码仓任务，应该从 [Coding Agent](/docs/coding-agent/overview) 开始；如果你要做的是通用业务智能体，应该从 [Agent](/docs/agent/overview) 开始。
