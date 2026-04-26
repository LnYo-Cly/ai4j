---
sidebar_position: 3
---

# Flowgram API 与运行时

本页说明 AI4J 围绕字节开源 `Flowgram.ai` 前端工作流库所实现的后端 runtime、任务 API 以及配置边界。

---

## 1. 默认任务 API

当前 Flowgram REST 接口由 `FlowGramTaskController` 暴露，默认路径前缀是：

- `/flowgram`

接口列表：

- `POST /flowgram/tasks/run`
- `POST /flowgram/tasks/validate`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

---

## 2. 每个接口做什么

### 2.1 `POST /tasks/run`

提交一个工作流 schema 与 inputs，创建并运行任务。

适合：

- 前端画布点击“运行”
- 后端集成测试
- demo 验证

### 2.2 `POST /tasks/validate`

只校验 schema，不真正执行。

适合：

- 前端保存前校验
- 节点编辑器联调
- CI 检查流程定义合法性

### 2.3 `GET /tasks/{taskId}/result`

读取任务最终结果状态。

更关注：

- 是否结束
- 是否成功
- 最终 result 是什么
- 当前 trace 投影快照是什么

### 2.4 `GET /tasks/{taskId}/report`

读取更完整的报告。

更关注：

- workflow 状态
- 每个节点的输入输出
- 起止时间
- 整体执行细节
- 前端可直接消费的 trace 视图

### 2.5 `POST /tasks/{taskId}/cancel`

取消任务。

适合：

- 前端提供“停止运行”
- 宿主平台接入任务中断

---

## 3. 运行时装配关系

从 starter 自动装配可以确认当前主链路是：

1. `FlowGramTaskController`
2. `FlowGramRuntimeFacade`
3. `FlowGramRuntimeService`
4. `FlowGramLlmNodeRunner` + `FlowGramNodeExecutor`
5. `FlowGramTaskStore`

职责可以这样理解：

- `Controller`：对外 HTTP 接口
- `RuntimeFacade`：协议适配、任务调度、访问控制、结果拼装
- `RuntimeService`：真正执行工作流
- `LlmNodeRunner`：跑 `LLM` 节点
- `NodeExecutor`：跑自定义或内置业务节点
- `TaskStore`：保存任务状态与快照

---

## 4. 当前默认配置

```yaml
ai4j:
  flowgram:
    enabled: true
    default-service-id: glm-coding
    stream-progress: false
    task-retention: 1h
    report-node-details: true
    trace-enabled: true
    api:
      base-path: /flowgram
    task-store:
      type: memory
      table-name: ai4j_flowgram_task
      initialize-schema: true
    cors:
      allowed-origins: []
    auth:
      enabled: false
      header-name: Authorization
```

### 4.1 `default-service-id`

指定 Flowgram `LLM` 节点默认使用哪个 AI 服务。

### 4.2 `stream-progress`

表示是否暴露更细粒度的运行进度流。当前文档阶段先按关闭理解。

### 4.3 `task-retention`

任务结果保留时间，影响任务所有权与清理策略。

### 4.4 `report-node-details`

是否在 report 中包含节点级细节。默认是 `true`。

### 4.5 `trace-enabled`

是否在 `report/result` 返回里附带 `FlowGramTraceView`。

默认值：

- `true`

它的定位是：

- 给前端画布和任务详情页的运行时 projection
- 不是直接暴露底层 OTel span

### 4.6 `task-store.type`

当前支持：

- `memory`
- `jdbc`

### 4.7 `task-store.table-name`

当你使用 `jdbc` 时，指定任务表名。

默认值：

- `ai4j_flowgram_task`

### 4.8 `task-store.initialize-schema`

是否在启动时自动初始化任务表。

默认值：

- `true`

## 4.9 JDBC TaskStore 的装配条件

如果你配置：

```yaml
ai4j:
  flowgram:
    task-store:
      type: jdbc
```

则还需要宿主 Spring 容器里存在 `DataSource` Bean。

此时 starter 会自动装配：

- `JdbcFlowGramTaskStore`

适合场景：

- 任务状态跨进程保留
- 运行结果需要落库
- Flowgram 平台后端多实例部署

### 4.10 Spring Boot + MySQL 示例

最常见的接法就是让 Spring Boot 自己提供 `DataSource`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

ai4j:
  flowgram:
    task-store:
      type: jdbc
      table-name: ai4j_flowgram_task
```

在这种模式下，不需要你手动声明 `JdbcFlowGramTaskStore` Bean。

starter 会在容器里检测到：

- `task-store.type=jdbc`
- 可用的 `DataSource`

然后自动装配 `JdbcFlowGramTaskStore`。

---

## 5. 一个典型运行流程

以“Start -> LLM -> End”为例，运行时会依次完成：

1. 接收前端或调用方传来的 schema
2. 校验是否有且只有一个 `Start`
3. 校验是否至少存在一个 `End`
4. 建立任务上下文和 node outputs
5. 按边关系驱动节点执行
6. 将节点 outputs 传给下游节点
7. 将最终结果写入 task store
8. 收集 `FlowGramRuntimeEvent`
9. 聚合成 `FlowGramTraceView`
10. 通过 `result` / `report` 对外暴露结果

如果使用 `JdbcFlowGramTaskStore`，这些状态会直接写入数据库，而不是只停留在进程内存里。

---

## 6. Trace Projection：给前端的运行时视图

starter 当前会默认装配：

- `FlowGramRuntimeTraceCollector`

它会监听 `FlowGramRuntimeService` 发出的 runtime event：

- `TASK_STARTED`
- `TASK_FINISHED`
- `TASK_FAILED`
- `TASK_CANCELED`
- `NODE_STARTED`
- `NODE_FINISHED`
- `NODE_FAILED`
- `NODE_CANCELED`

然后按 `taskId` 聚合成 `FlowGramTraceView`，最后经由：

- `FlowGramRuntimeFacade`
- `FlowGramProtocolAdapter`

挂到：

- `FlowGramTaskReportResponse.trace`
- `FlowGramTaskResultResponse.trace`

### 6.1 `trace` 里有什么

当前 `FlowGramTraceView` 主要字段：

- `taskId`
- `status`
- `startedAt`
- `endedAt`
- `summary`
- `events`
- `nodes`

`events` 的单项字段：

- `type`
- `timestamp`
- `nodeId`
- `status`
- `error`

`nodes` 的单项字段：

- `nodeId`
- `status`
- `terminated`
- `startedAt`
- `endedAt`
- `durationMillis`
- `error`
- `eventCount`
- `metrics`

`summary` 当前至少会带：

- `durationMillis`
- `eventCount`
- `nodeCount`
- `completedNodeCount`
- `failedNodeCount`
- `metrics`

其中 `summary.metrics` 会聚合整个任务当前可见的 LLM 指标：

- `promptTokens`
- `completionTokens`
- `totalTokens`
- `inputCost`
- `outputCost`
- `totalCost`
- `currency`

`nodes[nodeId].metrics` 用来表达节点级指标，当前重点是：

- LLM 节点耗时
- LLM token 统计
- 成本估算结果

同时，`report.workflow.nodes[nodeId].outputs.metrics` 也会在后端自动补齐。

这意味着如果底层 `rawResponse.usage` 存在：

- `/tasks/{taskId}/report` 的节点输出里可以直接读 token
- `/tasks/{taskId}/report` 和 `/tasks/{taskId}/result` 的 `trace` 里也能直接读聚合后的 metrics

如果 provider 本次失败或没有返回 usage，这些 token/cost 字段会保持为空，而不是伪造数值。

### 6.2 这层 projection 解决什么问题

它主要解决前端画布的三类调试需求：

1. 当前任务整体状态是什么
2. 某个节点是否已经开始、结束或失败
3. 右侧调试面板如何渲染时间线和节点状态
4. 当前运行累计消耗了多少 token / 成本

所以它不是“再造一套后端 trace 系统”，而是把 runtime event 整理成前端直接可消费的数据结构。

这里还多做了一层后端 enrichment：

- `FlowGramRuntimeTraceCollector`
  - 负责把 runtime event 折叠成时间线和节点快照
- `FlowGramRuntimeFacade`
  - 在返回 `report/result` 前补齐 `trace.summary.metrics`
  - 同时把节点 `rawResponse.usage` 回填成 `outputs.metrics` 和 `trace.nodes[nodeId].metrics`

这样前端调试面板只需要消费 projection，不需要自己再解析不同 provider 的原始 usage 结构。

### 6.3 与 OpenTelemetry 的边界

如果你的平台同时需要：

- 后端 observability
- FlowGram 前端调试

建议边界明确分开：

- 后端监控系统：接 OTel
- 前端画布：接 `FlowGramTraceView`

不要让前端直接去消费原始 OTel span。

---

## 7. 与 Agent / Coding Agent 的关系

Flowgram 不直接替代 `Agent` 或 `Coding Agent`，更像是它们的上层编排壳：

- `LLM` 节点底层仍可走 AI4J 模型能力；
- `Tool` 节点可以承接工具调用；
- 更复杂的推理策略仍建议在 `Agent` 层完成；
- `Coding Agent` 适合本地仓库交互，不适合直接拿来替代画布运行时。

---

## 8. 推荐阅读

1. [内置节点](/docs/flowgram/built-in-nodes)
2. [自定义节点扩展](/docs/flowgram/custom-nodes)
3. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
4. `Agent / Workflow 与 StateGraph`
