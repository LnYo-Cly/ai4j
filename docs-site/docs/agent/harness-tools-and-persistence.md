---
title: Harness Tools 与持久化
description: 说明 Harness Function Call、Command Gateway、异步工具调用、File/JDBC 持久化、租约和恢复边界。
tags: [concept, harness]
---

# Harness Tools 与持久化

Harness 的管理能力确实会以 Function Call 的形式提供给 Agent，但 Function Call 只是 Agent 进入管理面的入口，不是最终的权限边界，也不是持久化本身。

完整链路是：

```text
模型选择 Harness Function Call
              |
              v
HarnessToolRegistry（声明工具）
              |
              v
HarnessToolExecutor（路由、幂等、Task/审批/等待约束）
              |
              v
HarnessCommandGateway（唯一的持久化命令面）
              |
              v
FileHarnessStore 或 JdbcHarnessStore
```

业务工具也经过同一个 `HarnessToolExecutor`：

```text
HarnessToolExecutor
  ├─ harness_* 管理调用 -> HarnessManagementToolExecutor -> CommandGateway
  └─ 业务 Tool 调用       -> 业务 ToolExecutor
                              └─ Invocation / Wait / Approval / UNKNOWN 记录
```

## 1. 管理工具清单

Harness 自动向已有工具 Registry 添加以下保留名称。业务 Tool 不应使用这些名称。

| 工具 | 作用 | 典型操作 |
| --- | --- | --- |
| `harness_context_get` | 读取当前 Execution 的 Task、可运行 Task、Wait、Fact、Decision、Evidence 和工具调用 | 查看当前长期上下文 |
| `harness_task_manage` | 管理 Task 和依赖 | `create`、`split`、`update`、`transition`、`add_dependency`、`get`、`list`、`runnable` |
| `harness_fact_record` | 记录或失效一个带来源的 Fact | `record`、`invalidate` |
| `harness_decision_propose` | 提出或由有权限的非 Agent 角色解决 Decision | `propose`、`resolve` |
| `harness_evidence_record` | 记录模型、工具、测试、文件或外部系统产生的 Evidence | `record` |
| `harness_relation_manage` | 管理实体间的通用关系 | `create`、`add`、`get`、`list` |
| `harness_control_request` | 请求 checkpoint、用户输入、异步操作、外部事件或审批等待 | `checkpoint`、`wait`、`approval` |
| `harness_submission_request` | 把 Task 提交给外部审核 | `submit` |

这些工具的参数是通用的。订单、客户、代码文件、短剧素材等业务对象应放在 Task metadata、Evidence contentRef、Relation metadata 或业务数据库中，而不是让 SDK 猜测业务字段。

## 2. Agent 会不会绕过 Harness

需要区分两种情况：

### 管理状态的写入

不会让 Agent 直接写 `HarnessStore`。管理 Function Call 经过 `HarnessManagementToolExecutor`，所有写入最后进入 `HarnessCommandGateway`。Gateway 会检查：

- Task、Execution、Session 和 `scopeKey` 是否一致；
- 依赖是否形成环；
- Wait 是否属于当前 Execution；
- 幂等键是否已经使用；
- 当前 Actor 是否有审批、审核、完成或 reconciliation 权限；
- Task 是否已经处于终态；
- lease 和 fencing token 是否仍然有效。

默认情况下，Agent 可以提出 Fact、Decision、Evidence、Task 和 Submission，但不能批准自己的 Submission、完成自己的 Task，也不能替外部副作用做最终 reconciliation。

### Agent 是否一定会主动调用管理工具

不能把模型提示当成语义完整性证明。Harness 会通过 `HarnessPrompts` 告诉 Agent 在复杂工作开始时读取上下文并维护 Task，但模型仍可能漏记一个事实或选择不拆分任务。

因此，对关键业务应同时使用：

1. `HarnessContract` 对关键业务 Tool 设置 `taskRequiredTool` 和 `approvalRequiredTool`；
2. 宿主在入口、Webhook 和人工后台使用 `HarnessCommandGateway` 做必要状态写入；
3. 用 Submission/Gate/外部审核决定是否允许完成；
4. 对有副作用的业务 API 使用外部 idempotency key，并在 `UNKNOWN` 时查询真实业务系统。

这保证了 Agent 不能通过“不调用某个管理工具”直接绕开真正的执行和完成边界，同时也承认 Harness 不可能从模型的自然语言中自动推断所有业务事实。

## 3. 异步 Function Call 的持久化流程

业务方可以继续实现同步 `ToolExecutor`；Harness 会把同步结果记录为已完成的 Tool Invocation。需要等待远程服务时，实现可选的 `AsyncToolExecutor`：

```java
final class SubmitRefundExecutor implements AsyncToolExecutor {
    @Override
    public AgentToolExecution start(AgentToolCall call) {
        String operationId = refundApi.submitAsync(call.getArguments());
        CompletableFuture<AgentToolResult> completion =
                refundApi.completion(operationId);
        return AgentToolExecution.pending(
                operationId,
                null,
                "退款申请已提交，等待支付系统结果",
                1000L,
                completion);
    }
}
```

在 Harness 边界内，实际顺序是：

1. 为本次工具调用预留持久化 `ToolInvocation`；
2. 调用业务 `AsyncToolExecutor.start`，立即拿到 `operationId`；
3. 创建 `ASYNC_OPERATION` Wait 和 checkpoint；
4. Agent 返回 `WAITING`，当前 Execution 变为 `WAITING`；
5. 如果 CompletionStage 仍在当前进程中，Harness 可以自动接收完成；
6. 如果进程重启，业务 Webhook 根据保存的 `operationId` 找到 Wait，并调用 `harness.deliver(waitId, result)`；
7. Wait、Wakeup 和恢复后的 Agent/Adapter 状态先原子持久化，Execution 再变为 `READY`，随后继续一个新的 slice。

```java
HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
        .scopeKey("shop-A")
        .sessionId("customer-A-agent-session")
        .input(customerMessage)
        .build());

if (waiting.getStatus() == HarnessRunStatus.WAITING) {
    saveOperationBinding(waiting.getOperationId(), waiting.getWaitId());
}

// 由支付系统 Webhook、消息队列消费者或人工后台执行；不依赖原来的 JVM Future。
HarnessRunResult resumed = harness.deliver(
        loadWaitIdByOperation(operationId),
        refundResult);
```

多个并行异步工具调用会创建多个 Wait；只有全部 Wait 被投递后，Execution 才能继续。取消或人工接管后到达的迟异步结果不会重新打开已经取消的工作，而会被记录为隔离的迟到结果。

如果外部服务已经执行但 JVM 在记录结果前崩溃，Harness 会保留 `UNKNOWN`，不会自动重试一个可能造成重复扣款或重复退款的操作。业务方必须用 `operationId` 或外部系统查询接口做 reconciliation；这也是为什么重要业务 API 必须支持幂等。

审批也遵守同一条副作用边界：如果现有 Agent 的 Permission 层在 Harness 已预留 Invocation 后要求审批，Harness 会在一个事务中把该 Invocation 与 `APPROVAL` Wait 关联并置为 `WAITING`。批准后，恢复的 Agent 可以用新的 provider `callId` 重试；只有同一 Execution、同一工具、等价参数且没有歧义时才会重新使用原 Invocation，并在真正执行前原子地恢复为 `STARTED`。拒绝则记为失败，不会执行工具。

## 4. Wait 类型

| Wait 类型 | 谁投递 | 示例 |
| --- | --- | --- |
| `USER_INPUT` | 用户渠道层 | Agent 询问“要退款哪个订单？” |
| `ASYNC_OPERATION` | 外部服务 Webhook、消息队列或回调消费者 | 退款、支付、物流、远程构建 |
| `APPROVAL` | 人工或有权限的系统 Actor | 高风险退款、发布、推送 |
| `EXTERNAL_EVENT` | 业务事件消费者 | 库存变更、人工处理完毕、CI 事件 |
| `TIME` / `RETRY` | 业务调度器或定时 worker | 到期检查、退避重试 |

“等待用户输入”与“客服 Conversation 仍然开放”不是同一个概念。Harness 只负责保存 Wait；客服业务决定如何把 Wait 映射到消息渠道，以及 Conversation 是否因为人工接管而永久停止机器人。

## 5. File 持久化

本地项目或单机 Coding Agent 可以使用：

```java
HarnessPersistence persistence = HarnessPersistence.file(
        projectRoot.resolve(".ai4j/harness"));

AgentHarness harness = AgentHarness.builder()
        .agent(agent)
        .persistence(persistence)
        .build();
```

目录由 Harness 管理，主要包含：

```text
.ai4j/harness/
  state.json       # 当前完整状态快照
  journal.jsonl    # append-only 恢复日志
  .lock            # 跨进程文件锁
```

File store 每次更新都先生成新版本、追加 journal、替换 snapshot，并在日志过大且快照安全后压缩日志。它适合项目目录内的长期本地工作流；多实例生产服务、网络文件系统和高并发跨主机 worker 应使用 JDBC 或业务方实现自己的 `HarnessStore`。

注意：仓库根目录的 `harness/` 是项目维护者使用的私有 Harness Anything 账本，`.harness/` 是其生成投影；它们不等于 SDK 运行时的 `.ai4j/harness/`，也不应互相读写。

## 6. JDBC 持久化

多实例服务使用：

```java
HarnessPersistence persistence = HarnessPersistence.jdbc(
        dataSource,
        "shop-A-customer-support");

AgentHarness harness = AgentHarness.builder()
        .agent(customerSupportAgent)
        .persistence(persistence)
        .build();
```

两个参数的含义是：

| 参数 | 含义 |
| --- | --- |
| `dataSource` | 业务应用提供的 JDBC `DataSource`，负责连接池、数据库地址、凭证和事务环境 |
| `harnessId` | 一个逻辑 Harness ledger 的稳定名称，用于在同一数据库中隔离不同项目、店铺或 Agent 系统 |

`"shop-A-customer-support"` 不是 Conversation ID、Session ID，也不会自动创建客服规则。使用同一个 `harnessId` 的 worker 共享同一套 Task/Execution/Wait 状态；使用不同值则是不同的 ledger。ledger 内部还可以用 `scopeKey` 对店铺、项目或子系统进一步分区。

JDBC store 使用完整状态行和 journal 行，并在状态更新时使用事务、行锁和版本条件更新。应用应让所有需要共享长期状态的实例连接到同一个数据库和同一个 `harnessId`，并为数据库备份、清理策略、迁移窗口和连接池设置运维规则。

当前自动建表 DDL 使用 `TEXT` 保存 JSON 状态，这与 SDK 现有 `JdbcAgentMemory` 的约定一致，已在 H2 回归测试中验证，适合 MySQL、MariaDB、PostgreSQL、H2 和 SQLite 一类数据库。Oracle、SQL Server 或带有不同大字段类型的数据库不应仅凭 JDBC 连接成功就假定自动 DDL 可用；业务方应在目标数据库上预建等价的两张表，或为该数据库实现自己的 `HarnessStore`，并把 schema 初始化作为部署迁移的一部分。

## 7. 没有生产 In-Memory Harness Store

Harness 的长期事实、Task、Execution、Wait、checkpoint、lease 和审计不能只放在 JVM 堆里，所以 SDK 不提供生产用的 `InMemoryHarnessStore`。

Agent 侧的 `AgentMemory` 仍然可以按现有 SDK 方式配置；它是模型上下文状态，不等于 Harness ledger。要实现长程恢复，必须让 Harness 使用 File、JDBC 或业务方实现的持久化 `HarnessStore`。测试也应使用临时 File store 或测试数据库，这样可以真正覆盖重启恢复、并发和 journal 行为。
