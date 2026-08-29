---
title: Durable Agent Harness 运行时
description: 使用 ai4j-harness 为已有 Agent 增加动态 Task、持久化 Execution、checkpoint、等待恢复、依赖和完成治理能力。
tags: [concept, harness]
---

# Durable Agent Harness 运行时

`ai4j-harness` 是一个可选的 SDK 模块。它把 Harness Anything 的核心管理思想移植成 Java 运行时边界，但不引入 `ha` CLI，也不把具体业务流程写死在 SDK 中。

它的定位可以先用一句话概括：

> `Agent` 负责一次切片内的思考和执行，`Harness` 负责跨请求、跨进程、跨时间的工作状态、恢复和治理。

## 1. 它不会替换已有 Agent

没有配置 Harness 时，现有的 `Agent`、`AgentSession`、`ToolExecutor`、MCP、Function Call、Skill、A2A、Subagent、Agent Team、Memory、上下文压缩、Sandbox、Permission、Hook 和 Plugin 的行为保持不变。

启用 Harness 后，已有 Agent 仍然负责：

| 已有 Agent 能力 | 责任边界 |
| --- | --- |
| 模型调用和协议适配 | `ai4j` / `ai4j-agent` |
| ReAct、CodeAct、Workflow 等单次运行策略 | `ai4j-agent` |
| 工具声明、MCP、Function Call、Skill | `ai4j-agent` 和业务侧 |
| Memory、上下文投影、上下文压缩 | `ai4j-agent` / `ai4j-coding` |
| Sandbox、Permission、Hook、Plugin、Subagent、Agent Team | 现有 Agent/Coding Runtime |
| 一次 Agent 调用在什么时候停止 | Agent 的 step、token 和 wall-clock 配置，加上本次 Harness budget |

Harness 只在外层增加：

| Harness 能力 | 作用 |
| --- | --- |
| Task | 运行时动态记录一项可持续工作的目标，不要求开发者预先声明固定任务清单 |
| Execution | 一次输入或一次恢复所对应的持久化执行实例 |
| Checkpoint | 保存跨切片恢复所需的 Agent/Adapter 状态和摘要 |
| Wait/Wakeup | 保存用户输入、审批、异步服务、外部事件或定时等待 |
| Lease/Fencing | 防止多个 worker 同时修改同一个执行或同一个 Session |
| Task relation | 表达父子任务和依赖 DAG，并拒绝循环依赖 |
| Fact/Decision/Evidence/Relation | 把长期工作的事实、决定、证据和关系变成可查询记录 |
| Submission/Review/Gate | 把“Agent 说完成了”和“系统允许完成”分开 |
| File/JDBC store | 把上述状态保存到磁盘或数据库，而不是只放 JVM 内存 |

## 2. 核心对象关系

Harness deliberately 不把 Task 和 Session 强行绑定：

```text
业务输入 message / 外部事件 / worker 调度
                 |
                 v
        HarnessRunRequest
                 |
                 v
        Execution（一次切片）
          |             |
          |             +--> Session ID：恢复 Agent 上下文
          |
          +--> Task ID：可选，运行中可以由 Agent 创建并绑定
                 |
                 v
        Agent 或 HarnessExecutionAdapter
                 |
                 v
        一个有边界的 Agent slice
                 |
                 v
     checkpoint + durable outcome + wait/wakeup
```

这几个身份的区别很重要：

| 身份 | 典型含义 | 是否必须绑定 |
| --- | --- | --- |
| Task | “完成退款调查”“维护支付模块”“制作一集短剧”这样的长期工作 | 一个 Task 可以有多个 Execution |
| Execution | 本次收到消息、恢复等待或 worker 继续运行的具体切片 | 一个 Execution 最多有一个当前 Task，但可以没有 Task |
| Agent Session | Agent 的 memory、event log 和 run identity | 一个 Session 可以参与多个独立 Execution |
| scopeKey | 在同一个 Harness ledger 内做租户、项目或工作空间分区 | 可选；它不是 Conversation 或 Session |

因此，一条新的客服消息通常会创建一个新的 Execution；它可以复用客户的 Agent Session，但不会因为复用了 Session 就自动继承上一条消息的 Task。Coding Agent 的 Task 则通常属于项目，可以被不同 CLI、TUI、ACP 或后台 worker 的 Execution 继续处理。

## 3. 一次 `run` 如何工作

`AgentHarness.run(...)` 每次执行一个有边界的切片：

1. 宿主把业务自己的输入对象放进 `HarnessRunRequest.input`，同时提供稳定的 `sessionId`、可选的 `taskId`、`scopeKey` 和消息级幂等键。
2. Harness 创建或读取一个持久化 Execution。即使当前还没有 Task，也可以先记录一次输入和执行结果。
3. Harness 获取 Execution lease，并为共享的 Agent Session 获取 session lease。
4. Harness 恢复已有 checkpoint 或 Session snapshot，然后把管理工具和强制执行边界叠加到现有 Agent 上。
5. Agent 在自己的模型、工具、MCP、Memory、压缩和权限语义内运行；它可以根据当前输入决定是否创建、拆分、更新或关联 Task。
6. 切片结束后，Harness 原子保存 Agent/Adapter 状态、checkpoint、工具调用、等待状态和 Execution outcome。
7. 宿主根据结果继续 `resume`、调用 `deliver`、等待外部事件，或者把可运行 Task 交给下一个 worker。

```java
// message、messageId、sessionId 都是业务侧自己的概念和字段。
HarnessRunResult result = harness.run(HarnessRunRequest.builder()
        .scopeKey("shop-A")
        .sessionId("customer-A-agent-session")
        .idempotencyKey("message:msg-1001")
        .input(message)
        .build());

if (result.getStatus() == HarnessRunStatus.WAITING) {
    // 业务侧保存 result.getWaitId()，并把需要用户回答或正在处理的状态交给自己的渠道层。
    publishWaitingReply(result.getOutputText(), result.getWaitId());
} else if (result.getStatus() == HarnessRunStatus.CONTINUATION_REQUIRED) {
    // 还有工作但本次 slice 到达边界；可由 worker 继续，而不是把它当作完成。
    enqueueExecution(result.getExecution().getExecutionId());
}
```

`message` 不需要实现 SDK 的固定接口。它可以是电商消息 DTO、HTTP 请求、事件对象、CLI prompt、工单对象或任意业务输入。Harness 只保存输入摘要和 Agent 能够恢复所需的运行状态；完整业务对象是否落库、如何脱敏，由业务系统决定。

## 4. Task 是运行时动态产生的

开发者不需要为“退款”“改地址”“查订单”分别写固定的 `TaskDefinition`，也不需要在应用启动时创建唯一 Task。

当本次输入尚未对应 Task 时，Agent 可以调用自动注入的：

```text
harness_task_manage {
  "operation": "create",
  "title": "核实客户的退款请求",
  "goal": "确认订单、退款资格和执行结果"
}
```

如果当前 Execution 没有 Task，第一次 `create` 会把新 Task 绑定到当前 Execution；之后 Agent 可以在同一次长期工作中：

- `split` 出订单核验、政策核验、退款提交、结果通知等子任务；
- 用 `add_dependency` 表达“退款提交依赖订单核验”；
- 根据新事实 `update` Task 的目标和计划；
- 记录 Fact、Decision、Evidence；
- 在切片边界保存 checkpoint；
- 通过 `harness_submission_request` 提交外部审核，而不是自行宣布完成。

宿主只有在自己已经知道长期工作身份时才传 `taskId`，例如后台重试某个已知的退款工单或继续一个项目级编码 Task。Task 仍然可以有多个 Session 和多个 Execution。

## 5. Agent、宿主和 Harness 各自负责什么

### Agent 自己负责

- 理解输入并选择业务工具；
- 根据工作复杂度决定何时创建或拆分 Task；
- 把重要事实、决定和证据写入 Harness；
- 在需要用户、审批或外部系统时请求 Wait；
- 产出阶段性回答或提交材料；
- 在给定的 Agent step 边界内继续工作。

### 业务开发者负责

- 定义输入 DTO、输出 DTO、消息幂等键和外部事件格式；
- 决定客户、订单、工单、项目、仓库等实体如何关联到 `scopeKey`、Task metadata 或业务数据库；
- 实现业务 Tool 和异步服务调用；
- 决定哪些工具必须已有 Task、哪些工具需要审批；
- 决定一次 slice 的时间、轮次、token 和费用预算；
- 定义人工接管、会话关闭、重试和超时规则；
- 为完成提交配置 Gate，提供人工或系统审核入口；
- 选择 File 或 JDBC，并负责数据库、备份、worker、回调和运维。

### Harness Runtime 负责

- 原子保存和恢复长期状态；
- 对 Execution、Session 和工具调用做租约、幂等和并发隔离；
- 维护等待、唤醒、checkpoint、依赖、证据和完成门禁；
- 将过期租约导致的不确定结果标记为 `UNKNOWN`，不擅自假设外部副作用成功或失败；
- 在任务取消或人工接管后隔离迟到的 Agent/异步结果；
- 让旧 Agent 在未配置 Harness 时不受影响。

业务规则不要塞入 `HarnessTaskSpec` 或 `HarnessContract` 的固定字段。例如“24 小时关闭 Conversation”是客服系统规则，不是 Harness 的通用 Task 状态；Harness 只提供可持久化的 Wait、Execution 和事件记录，让业务规则可以在其上实现。

## 6. 配置一个 Harness

标准 Agent 使用 `AgentHarness`：

```java
AgentHarness harness = AgentHarness.builder()
        .agent(existingAgent) // 现有 ai4j Agent，保留原来的能力装配
        .persistence(HarnessPersistence.file(
                projectRoot.resolve(".ai4j/harness")))
        .contract(HarnessContract.builder()
                .taskRequiredTool("submitRefund")
                .approvalRequiredTool("submitRefund")
                .build())
        .build();

try {
    HarnessRunResult result = harness.run(HarnessRunRequest.builder()
            .scopeKey("shop-A")
            .sessionId("customer-A-agent-session")
            .input(message)
            .build());
} finally {
    // 长期服务中通常把 Harness 作为应用生命周期 bean，在停机时关闭一次。
    harness.close();
}
```

`HarnessContract` 是治理规则，不是业务任务模板。上面的规则表达的是：没有 Task 不能调用 `submitRefund`，并且调用前需要审批；它没有规定 Task 的标题、订单字段或客服会话字段。

对于 `ai4j-coding`，使用 `CodingAgentHarness` 可以保留 workspace 工具、CodeAct、compact、进程、MCP、subagent 和现有审批语义，详见 [Coding Agent Harness 集成](/docs/products/coding-agent/harness-integration)。

## 7. 不引入 CLI

Harness Anything 的 `ha` 命令适合人和外部 Coding Agent 操作项目治理目录；SDK 不复制这个 CLI。

SDK 提供的是：

- Java `AgentHarness` / `CodingAgentHarness` 入口；
- Java `HarnessCommandGateway`，供宿主、worker、Webhook、人工后台和测试使用；
- 可选的 Harness Function Call 工具，让 Agent 能在运行中管理自己的 Task、事实和等待；
- File/JDBC 持久化实现。

开发者可以在自己的 HTTP 服务、消息消费者、CLI、TUI、后台 worker 或调度器中调用这些 API。这样同一个 Harness ledger 既可以被多个 Agent worker 共享，也可以由人工后台修改 Task 或投递 Wait，而不要求用户安装 `ha`。

## 8. 其它运行时

标准 `Agent` 和 `CodingAgent` 已经有直接适配器。业务如果有自己的不可替换运行时，可以实现 `HarnessExecutionAdapter`，只需要负责：

- 根据 checkpoint 打开或恢复自己的运行态；
- 执行一个有边界的 slice；
- 导出可序列化的 Adapter state；
- 应用宿主投递的 Wait 结果。

Task、Execution、lease、wait、checkpoint、依赖、审查和完成门禁仍由 Harness 统一管理。这个扩展点是为了接入已有 runtime，不要求普通业务开发者额外实现一套 Agent。

