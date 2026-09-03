---
title: 长程电商客服 Agent
description: 使用 ai4j Agent、Harness、异步业务工具和业务 Conversation 状态实现可恢复的售前售后客服。
tags: [solution, harness, agent]
---

# 长程电商客服 Agent

本方案以一个电子设备外设店铺为例：服务持续运行，客户 A、B、C 同时咨询售前、退款、改地址、补差价、物流和售后问题。每个客户的 Agent 上下文相互隔离；复杂售后任务可以跨多天、跨多个消息和外部系统继续。

这页的关键不是给 SDK 增加一个 `CustomerMessage` 固定模型，而是展示业务消息如何进入通用 Harness。

## 1. 四种状态不要混为一谈

客服系统至少有四个不同边界：

| 对象 | 业务含义 | 谁负责生命周期 |
| --- | --- | --- |
| Conversation | 渠道上的一次客户会话，包含客服接待、人工接管、关闭等规则 | 客服业务系统 |
| Agent Session | 模型上下文和 Agent memory 的稳定身份 | Agent/Harness 配合，业务提供映射 |
| Harness Task | “完成退款申请”“处理换货”这样的可持续工作 | Harness 记录，业务定义关联方式，Agent 可运行时创建 |
| Harness Execution | 处理一条消息、一个 Webhook 或一次恢复的具体 slice | Harness Runtime |

Conversation 不是 Agent Session。一个客户可以有多个 Conversation；一个 Conversation 也可能产生多个 Execution。一个退款 Task 还可能在 Conversation 被人工接管后由人工修改，再由系统做后续记录，但不应因此自动让机器人重新回复。

## 2. 业务入口是普通消息处理器

业务方定义自己的消息和 Conversation 表。例如：

```java
public final class CustomerMessage {
    private String messageId;
    private String customerId;
    private String conversationId;
    private String text;
    private long receivedAtEpochMs;
    // channel、attachments、order references 等仍由业务自己定义。
}
```

消息处理器是业务代码，不是 Harness API：

```java
public Reply handle(CustomerMessage message) {
    Conversation conversation = conversationStore.require(message.getConversationId());

    // HANDOFF_HUMAN 是永久的 Conversation 业务状态，不是 Harness 的通用 Task 状态。
    if (conversation.isHumanHandoff() || conversation.isClosed()) {
        return Reply.noBotResponse();
    }

    String sessionId = sessionMapping.agentSessionId(conversation, message);
    String taskId = taskMapping.currentTaskId(conversation).orElse(null);

    HarnessRunRequest.Builder request = HarnessRunRequest.builder()
            .scopeKey("shop:" + conversation.getShopId())
            .sessionId(sessionId)
            .idempotencyKey("message:" + message.getMessageId())
            .input(message);
    if (taskId != null) {
        request.taskId(taskId);
    }

    HarnessRunResult result = customerSupportHarness.run(request.build());
    return replyMapper.toReply(result, conversation);
}
```

这里没有把 `conversationId`、`customerId`、`orderId` 强行加进 SDK 的公共类型。业务只需把自己的消息对象放进 `input`，把自己选择的稳定 Session ID 和可选 Task ID 放进通用请求。

对于第一条消息，如果业务没有已知 Task，可以不设置 `taskId`。Agent 在识别出“这是一个需要持续处理的退款问题”后调用 `harness_task_manage(create)`，Harness 会把运行时创建的 Task 绑定到当前未绑定 Execution。开发者无需提前创建一个固定的“退款 Task”。

## 3. 启动一个长期运行的客服服务

Harness 应该作为服务生命周期对象创建一次，而不是每条消息创建一次：

```java
Agent customerSupportAgent = existingCustomerSupportAgent();

HarnessContract supportContract = HarnessContract.builder()
        .taskRequiredTool("submitRefund")
        .approvalRequiredTool("submitRefund")
        .taskRequiredTool("changeShippingAddress")
        .build();

AgentHarness customerSupportHarness = AgentHarness.builder()
        .agent(customerSupportAgent)
        .persistence(HarnessPersistence.jdbc(
                dataSource,
                "shop-A-customer-support"))
        .contract(supportContract)
        .build();
```

现有 Agent 的售前知识库检索、订单查询、物流查询、MCP、Skill、权限和上下文压缩继续由现有 Agent 配置负责。Harness 只负责让这些调用跨 Execution 可恢复、可审计并受 Task/Approval 边界约束。

## 4. 客户 A 的退款消息如何走完整链路

假设客户 A 发送：`“我的订单为什么还没有退款？”`，业务按上面的入口提交一条 `CustomerMessage`。

### 4.1 第一片：识别工作并询问订单

1. Harness 写入 `Execution-A1`，使用客户 A 的 Session ID；此时可以没有 Task。
2. Agent 读取 `harness_context_get`，发现当前没有退款 Task。
3. Agent 创建 Task：“核实客户的退款请求”，并将其绑定到 `Execution-A1`。
4. Agent 调用订单查询业务 Tool。
5. 如果订单不明确，Agent 通过已有的 `ask_user` 或 Host Input 能力请求用户选择订单；Harness 把它变成 `USER_INPUT` Wait，并保存 checkpoint。
6. 本次结果是 `WAITING`，业务把问题发回渠道，同时保存 `waitId`。

此时“Agent 正在等待客户回答”不是“Conversation 被关闭”，也不是“Task 已完成”。

### 4.2 客户 A 回答后继续

渠道层收到客户 A 的下一条消息后，业务先根据 Conversation 的未完成 Wait 判断它是否是回答，例如选择 `order-42`：

```java
HarnessRunResult resumed = customerSupportHarness.deliver(
        conversationStore.openWaitId(conversationId),
        "order-42");
```

Harness 会把 Wait 标记为 `DELIVERED`，把回答写入对应的 checkpoint/Session 恢复状态，并运行下一个有边界的 slice。Agent 可以继续查询退款政策、检查资格，然后调用 `submitRefund`。

### 4.3 退款服务是异步的

如果支付系统返回异步 `operationId`，业务的 `AsyncToolExecutor` 立即返回 pending 结果。Harness 会持久化：

```text
ToolInvocation(submitRefund, STARTED -> WAITING)
Execution-A2 = WAITING
Wait(type=ASYNC_OPERATION, operationId=refund-operation-42)
Checkpoint(Agent Session + 当前 Task 计划)
```

支付系统 Webhook 到达时，不需要原来的客服请求线程仍然存在：

```java
String waitId = supportWaitStore.findByOperationId("refund-operation-42");
HarnessRunResult afterPayment = customerSupportHarness.deliver(
        waitId,
        RefundResult.accepted("refund-9001"));
```

Agent 随后可以阶段性回复：“退款申请已经提交，是否还有其他包裹需要退回？”如果客户继续聊天，则是新的 Execution，但仍可复用业务选择的 Agent Session；退款 Task 是否继续关联，由业务和 Agent 根据上下文决定。

## 5. 多个客户并行与隔离

客户 A、B、C 可以共享一个店铺 Agent 配置和一个 JDBC Harness ledger，但必须提供不同的 Session ID：

```text
scopeKey = shop:shop-A
sessionId = customer:customer-A:agent
sessionId = customer:customer-B:agent
sessionId = customer:customer-C:agent
```

不同客户的 Session snapshot 不共享 Agent memory。不同消息通常形成不同 Execution，因此 A 的新消息不会因为复用 Session 就继承 B 的 Task，也不会把 B 的消息投喂到 A 的上下文。

同一个 Session 的两个消息如果同时到达，Harness 的 session lease 会阻止两个 Agent slice 同时修改同一份 memory。业务入口应在 Conversation/Session 层维护消息顺序，冲突时排队或稍后重试；店铺内不同客户的 Session 可以并行运行。

## 6. 会话、Task 和人工接管

### Conversation 的 24 小时规则由业务实现

很多客服系统会在 24 小时无消息后关闭 Conversation，或者由人工主动关闭。这个计时和关闭规则属于业务的 Conversation 表，不属于 Harness Contract，也不应由 Harness 自动猜测。

### 人工接管是永久单向转换

人工接管表示从这一刻起当前 Conversation 不再由机器人回复。业务可以把 Conversation 状态设置为 `HANDOFF_HUMAN`，把后续消息直接路由到真人客服：

```java
conversationStore.markHumanHandoff(conversationId, humanAgentId);
return Reply.noBotResponse();
```

即使 Harness 中还有未完成 Task 或 open Wait，业务入口也不能再调用 `customerSupportHarness.run` 处理这个 Conversation。人工客服可以通过业务后台修改订单、补充事实、添加 Evidence、更新 Task 或关闭 Conversation；这些操作应使用有权限的 human/system Actor 和 `HarnessCommandGateway`，而不是模拟 Agent。

当 Conversation 被关闭后，它不会重新回到机器人。客户下次发起新的 Conversation 时，业务可以建立新的 Agent Session 或用新的 Conversation Session ID，并根据业务需要把已关闭工单的摘要作为新输入。是否复用客户级长期记忆，是业务的 Session 映射决策，不是 Harness 自动继承。

## 7. 客服限制如何配置

Harness 不写死“客服最多 10 轮”或“所有 Agent 最多运行 30 分钟”。限制由业务根据任务类型和服务目标选择：

```java
HarnessRunBudget budget = supportBudgetPolicy.forMessage(
        message,
        currentTask,
        conversation);

HarnessRunResult result = customerSupportHarness.run(
        HarnessRunRequest.builder()
                .scopeKey("shop:" + shopId)
                .sessionId(sessionId)
                .taskId(taskId)
                .idempotencyKey("message:" + message.getMessageId())
                .input(message)
                .budget(budget)
                .build());
```

业务可以按自己的规则统计一个 Task 的累计耗时、澄清次数、工具失败次数、用户等待时长和风险等级。当规则认为 Agent 继续处理不合适时，业务触发人工接管；当只是一次 slice 到达边界时，则使用 `CONTINUATION_REQUIRED` 继续运行，而不是误转人工。

## 8. 这个方案解决什么，不解决什么

它解决：

- 消息线程结束、服务重启或 worker 更换后仍能恢复 Agent 工作；
- 同一店铺多个客户的 Agent Session 隔离；
- 一个退款 Task 跨多个消息、异步支付和人工操作保存状态；
- 工具调用的等待、幂等、审批、UNKNOWN 和迟到结果处理；
- Agent 自主拆分任务并记录长期事实、决定和证据；
- Task 完成需要外部审核/Gate，而不是模型一句“完成了”。

它不替业务决定：

- 什么是 Conversation、何时 24 小时关闭；
- 什么条件触发人工接管；
- 一个客户应该映射一个还是多个 Agent Session；
- 订单、退款、物流和客服系统的数据库结构；
- 真人操作后的业务退款、改址和工单流程。

