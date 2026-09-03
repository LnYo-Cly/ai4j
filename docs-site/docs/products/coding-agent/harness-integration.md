---
title: Coding Agent Harness 集成
description: 把现有 Coding Agent 接入持久化 Harness，让项目级 Task 跨 CLI、TUI、ACP、worker 和重启继续执行。
tags: [coding-agent, harness]
---

# Coding Agent Harness 集成

这一集成面向类似 Codex 或 Claude Code 的 Coding Agent：用户输入一个复杂目标，Agent 自己分析、拆分、修改代码、运行测试、处理构建输出，并可以长时间跨进程维护一个大型代码库。

Harness 不会把 Coding Agent 变成另一个产品，也不要求 SDK 引入 Harness Anything 的 `ha` 命令。它只把现有 `CodingAgent` 放进统一的持久化 Task/Execution 外层。

## 1. 项目级 Task，不绑定 CLI Session

Coding 场景中，正确的关系通常是：

```text
project/repository
  └─ Task: “完成支付模块重构”
       ├─ Execution: 第一个 CLI/TUI 输入
       ├─ Execution: 后台 worker 继续
       ├─ Execution: 另一个 CLI session 恢复
       └─ Execution: 重启后的 Harness 恢复

Agent Session：保存某一个 Agent runtime 的上下文，可被多个 Execution 使用，
但它不是 Task 的所有者，也不是项目本身。
```

用户可以新开终端、切换 TUI/ACP、让后台 worker 接手一个 READY Execution，而 Task 仍然属于项目工作空间。Harness 的 lease 和 fencing 防止两个 worker 同时推进同一个 Execution 或同一个 Session。

## 2. 最小接入

`CodingAgent` 的 workspace 工具、MCP、Skill、CodeAct、compact、进程注册表、subagent 和既有权限策略继续由 `ai4j-coding` 负责：

```java
CodingAgent codingAgent = existingCodingAgent(projectRoot);

CodingAgentHarness harness = CodingAgentHarness.builder()
        .codingAgent(codingAgent)
        .persistence(HarnessPersistence.file(
                projectRoot.resolve(".ai4j/harness")))
        .contract(HarnessContract.builder()
                // 是否要求审批、是否需要外部 review，由项目策略决定。
                .requiresApprovedReview(false)
                .build())
        .autoResume(false)
        .build();

HarnessRunResult first = harness.run(HarnessRunRequest.builder()
        .scopeKey("repo:" + repositoryId)
        .sessionId("coding-session:" + clientSessionId)
        .idempotencyKey("prompt:" + promptId)
        .input("实现支付模块重构，并运行相关测试")
        .build());
```

这里没有预先创建一个固定的 `Task`。第一片运行时，Agent 可以通过 `harness_task_manage(create)` 根据用户目标创建 Task，然后再拆分代码搜索、接口设计、实现、测试和文档等子任务。

如果 CLI 已经从项目数据库或用户选择中得知 Task ID，可以直接传 `taskId`；否则省略它，让 Agent 动态决定。

## 3. 长时间自主推进

一个 slice 结束并不等于项目任务完成。Coding 宿主可以在 `CONTINUATION_REQUIRED` 时恢复同一个 Execution：

```java
HarnessRunResult current = first;
while (current.getStatus() == HarnessRunStatus.CONTINUATION_REQUIRED) {
    current = harness.resume(current.getExecution().getExecutionId());
}

if (current.getStatus() == HarnessRunStatus.WAITING) {
    // 等待用户审批、CI、远程构建或外部事件；不要 busy loop。
    publishCodingWait(current);
}
```

对于后台 worker，可以让它从持久化 ledger 中取可运行 Task：

```java
List<HarnessRunResult> results = harness.runReady(
        HarnessRunBudget.builder()
                .maxExecutions(1)
                .build());
```

`HarnessRunBudget` 只控制本次调度切片，不给所有 Coding Agent 强加统一的 24 小时或轮次上限。项目可以把 Agent 的 `maxSteps`、wall-clock、token budget 设得很大，也可以让 worker 无限次处理 `CONTINUATION_REQUIRED`，直到 Task 的提交、Gate 和外部审核真正允许完成。

## 4. 长程代码任务的典型过程

以“维护一个大型仓库并重构支付模块”为例：

1. 用户 prompt 进入新的 Execution，Agent 创建项目级 Task。
2. Agent 调用 `harness_task_manage(split)`，建立架构分析、代码修改、测试和迁移文档子任务。
3. 子任务之间用 `add_dependency` 建立 DAG，例如测试依赖实现，迁移文档依赖接口稳定。
4. Agent 用 `harness_fact_record` 记录仓库约束，用 `harness_decision_propose` 记录技术取舍，用 `harness_evidence_record` 记录测试命令、构建结果和代码位置。
5. CodeAct 或普通工具到达 Agent step 边界时，Harness 保存 Coding session state 和 checkpoint，之后继续同一 Task。
6. 远程 CI、长时间构建或人工审批通过 `ASYNC_OPERATION` / `APPROVAL` Wait 进入恢复链，而不是让 CLI 线程一直阻塞。
7. Agent 通过 `harness_submission_request` 提交改动、测试证据、已知缺口和残余风险。
8. 人工或受信系统读取 Submission，执行 review 和 Gate；只有有权限的 Actor 才能完成 Task。

“测试通过”是 Evidence；“Agent 认为可以交付”是 Submission；“项目允许 Task 变成 DONE”是 Review/Gate 后的独立决定。这些概念不能用一条模型输出替代。

## 5. 多个 Coding 客户端共享一个项目

CLI、TUI、ACP 和后台 worker 都可以打开同一个 `.ai4j/harness`：

```text
CLI/TUI/ACP prompt
        |
        +--> FileHarnessStore(project/.ai4j/harness)
        |
        +--> 同一项目 Task / Execution / checkpoint

后台 worker --+
另一个客户端 --+--> lease + fencing，只有合法持有者推进当前 Execution
```

如果需要跨主机或多实例协作，改用：

```java
HarnessPersistence.jdbc(dataSource, "repository-coding");
```

`harnessId` 是共享 ledger 的逻辑名称，不是一次 CLI session 的名称。每个客户端仍然可以有自己的 Agent Session；Task 不会因此被绑定到某一个客户端。

## 6. Coding Agent 的自主性和治理

Coding 项目可以选择尽量少的约束：

```java
HarnessContract contract = HarnessContract.builder()
        .requiresApprovedReview(false)
        .build();
```

这不会取消 checkpoint、lease、依赖、Evidence、UNKNOWN 和恢复能力；它只表示项目不要求每次 Submission 都有外部 review。相反，涉及 `git push`、生产发布、数据库迁移或密钥读取的项目可以把对应业务 Tool 设置为需要审批。

管理工具通过 Function Call 暴露给 Agent，但完成权限仍在 Gateway/Contract。Agent 不应拥有批准自己的 Submission、完成自己的 Task 或确认未知外部副作用的权限。项目宿主可以在自己的 review 服务中以 human/system Actor 调用 Gateway。

## 7. 与 Harness Anything 的关系

使用 Harness Anything 配合外部 Coding Agent 时，`ha` CLI、治理目录和 `AGENTS.md` 是 Agent 外部可见的项目管理面。SDK 集成不复制那套 CLI，而是把同样的核心行为放到 Java 运行时：

| Harness Anything 使用方式 | SDK 对应方式 |
| --- | --- |
| Agent 通过 CLI 创建/更新 Task | Agent 调用 `harness_task_manage`，Gateway 持久化 |
| `harness/` 中保存 plan、fact、decision、evidence | File/JDBC Harness ledger 保存结构化记录和 checkpoint |
| CLI/外部 Agent 继续一个工作包 | `resume`、`deliver`、`runReady` 或业务 worker |
| 人工 review / 完成边界 | Submission、Review、Gate 和 Actor 权限 |
| 多个客户端在同一项目目录工作 | 共享 File/JDBC store，配合 lease/fencing |

因此既有 Coding Agent 能力仍然是基础；Harness 只增加一层跨时间的可恢复管理，不把项目工作流写成 SDK 内部固定任务表。

