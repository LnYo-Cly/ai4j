---
title: Harness 2.0 治理、自解释引擎与多节点 Fleet
description: 说明 ai4j-harness 2.0 的 CQRS 事件账本、WriterEpoch 栅栏、内容钉扎 Consent、SupervisoryAgenda、因果 ReadSet、Witness 独立复跑、可证伪 Decision 与 5 拓扑 Fleet。
tags: [concept, harness, fleet, governance]
---

# Harness 2.0 治理、自解释引擎与多节点 Fleet

`ai4j-harness` 在早期“Task + Execution + Lease + File/JDBC 持久化”基础上进一步升级为面向长期复杂多节点工程的 **Harness 2.0 治理与分布式协作底座**：以 CQRS 仅追加事件账本、人机权责硬隔离、因果读集合与多节点 Fleet 拓扑保证长程任务可审计、可解释、防脑裂。

:::tip 先决背景
如果你尚未了解基础的 `Task` / `Execution` / `Wait` 状态机与 `HarnessCommandGateway`，建议先阅读 [Agent Harness 运行时](/docs/agent/harness-runtime) 与 [Harness Tools 与持久化](/docs/agent/harness-tools-and-persistence)。
:::

---

## 1. CQRS 事件账本与 `WriterEpoch` 栅栏

所有状态变更继续以 `HarnessCommandGateway` 为唯一命令入口，但在持久化与投影侧引入了清晰的 CQRS 与栅栏保护：

- **仅追加事件账本**：通过 `HarnessEventJournal` / `HarnessEventStore` 顺序持久化领域事件，由 `HarnessProjectionEngine` 将事件流投影为实体快照；支持通过 `HarnessCommandGateway.rebuildEntitiesFromEvents(...)` 从事件账本重建实体状态。
- **`WriterEpochFence` 写入纪元栅栏**：通过 `claimWriterEpoch(...)` 与 `executeFencedMutation(...)` 确保只有持有最新纪元租约的写节点才能提交变更；一旦老节点网络分区恢复或被强摘，旧 epoch 写入会被栅栏直接拒绝。

---

## 2. 审查与批准分离：`ExecutionReview` vs `ContentPinnedConsent`

为杜绝“Agent 自己审批自己”或“评审通过后被暗中篡改再放行”，Harness 2.0 在 `GovernanceReviewService` 严格区分了两步动作：

1. **过程审查（`ExecutionReviewRecord`）**：由 `submitExecutionReview(...)` 提交执行评估与结构化 `ReviewFinding` 列表，可由审查者角色或人类维护者产出。
2. **内容钉扎人类同意（`ContentPinnedConsentRecord`）**：调用 `reviewConsent(...)` 时强校验三大硬规则：
   - **禁止 Agent 越权**：若提交者为 Agent Actor，直接抛出 `GovernanceReviewService.ERROR_CONSENT_AGENT_FORBIDDEN`（`"CONSENT_AGENT_FORBIDDEN"`），必须由人类 / Owner 主体签发；
   - **内容摘要钉扎（`submissionDigest`）**：同意记录必须与被审交付物摘要一致，否则拒绝并抛出 `ERROR_CONSENT_DIGEST_MISMATCH`；
   - **逐条处置覆盖（`FindingDisposition`）**：所有审查发现的问题必须逐条给出明确处置结论，遗漏任意一条会抛出 `ERROR_CONSENT_FINDINGS_UNCOVERED`。

---

## 3. 主动升级、督导议程与递归自解释引擎

在 `AgendaQueryService` 与 `HarnessCommandGateway` 中，阻塞不再只是静默等待：

- **一等升级对象（`EscalationRecord`）**：
  - `escalate(HarnessEscalateSpec, actor)`：发起带结构化选项（`options` / `recommend`）的升级请求，并可在安全运行态同步挂载 `WaitRecord`；
  - `answerEscalation(...)`：由人类或 Owner 裁决回复；
  - `followUpEscalation(...)`：确认收到裁决并推进后续恢复。
- **双队列督导议程（`SupervisoryAgenda`）**：通过 `projectAgenda(actorId, scopeKey)` 实时投影两个独立队列：
  - `awaitingRuling`：等待人类拍板的升级项；
  - `answeredPendingFollowUp`：人类已批示、等待执行侧确认跟进的事项。
- **递归自解释引擎（`explain`）**：调用 `explain(entityRef)` 返回 `BlockerExplanation`（包含 `root` 与递归树 `blockers` / `BlockerNode`），自动追溯某个 Task 或 Execution 为何卡死在未满足前置依赖、待裁决 `Wait`、失败 `Witness` 或陈旧读集合上。

---

## 4. 因果 `ReadSet`、独立 `Witness` 与可证伪 `Decision`

| 机制 | 核心类 / 方法 | 工程价值 |
| --- | --- | --- |
| **因果读集合（`ReadSetEntry`）** | `recordFactRead`、`recordDecisionRead`、`reconfirmReadSet`、`supersedeFact` | 记录 Agent 做决策时依赖了哪些 `Fact` 与 `Decision`。若上游 Fact 已被 `supersedeFact`/失效或 Decision 已被修订，继续提交关键动作会因 `KnowledgeLedgerService.ERROR_STALE_READ_SET`（`"STALE_READ_SET"`）被拦截，强制重新确认 |
| **独立复跑验证（`WitnessRunner`）** | `WitnessRunSpec`、`witnessRerun`、`GateRunRecord`、`EvidenceRecord` | 由独立非自评的 Witness 执行门禁检查或测试命令并落盘 `EvidenceRecord`，验证不通过返回 `ERROR_WITNESS_FAILED`（`"WITNESS_FAILED"`） |
| **可证伪决策（`DecisionRecord`）** | `chosenOption`/`chosen`、`rejected`、`claims`、`fulfillments`、`amendDecision`、`reckonDecision` | 要求决策同时写清“备选放弃原因与预期断言（`claims`）”，事后通过 `reckonDecision` 对照 `fulfillments` 清算决策是否被现实证伪 |

---

## 5. 多 Agent 调度、自定义扩展类型与巡检计划

Harness 2.0 补充了面向长周期运行时的编排与领域扩展原语：

- **安全点转向（`SteerRecord`）**：通过 `steer(...)` 向正在运行或等待下一轮切片的 Execution 注入方向纠偏指令，在安全边界生效而不破坏原子性。
- **跨实体交接（`HandoffRecord`）与小队运行（`SquadRunRecord`）**：通过 `exportHandoff` / `claimHandoff` / `revokeHandoff` 显式移交工作包与上下文；通过 `startSquadRun` 协调多成员小队执行。
- **待办到工作的分层晋升**：`TodoRecord`（`createTodo`）-> 晋升为结构化 `TaskRecord`（`promoteTodoToTask`）-> 派生具体工作包 `WorkRecord`（`createWork`）。
- **垂直领域注册**：允许宿主调用 `registerVerticalKind`（`VerticalKind<T>`）与 `registerFactType`（`FactType<T>`）注册强类型业务实体与事实模板。
- **双模式定时巡检（`ScheduleMode`）**：通过 `createSchedule` 注册周期任务，区分 `ScheduleMode.DETECT`（只读检测异常并告警）与 `ScheduleMode.REMEDIATE`（在边界内自动修复）。

---

## 6. 五拓扑多节点 Fleet（Center / Edge / AutoClaim）

在 `io.github.lnyocly.ai4j.harness.fleet` 中，AI4J 提供统一的多节点调度与边缘离线协同协议，通过 `FleetTopology` 区分 5 种运行拓扑：

| 拓扑枚举 | 定位与适用场景 |
| --- | --- |
| `LOCAL` | 单机本地单进程 / 单机目录账本 |
| `SHARED_CLUSTER` | 多 Worker 共享同一个数据库 / `HarnessStore` 的同构集群 |
| `FLEET_CENTER` | 控制中心节点，运行 `FleetCenterService`（可暴露 `HttpFleetServer`）统筹任务分派与三方合并 |
| `FLEET_EDGE` | 边缘/开发机节点，通过 `FleetGateway` / `HttpFleetTransport` 向中心注册并在断网时本地演进 |
| `FLEET_PROXY` | 代理中继节点，负责跨网络边界转发 Fleet 控制面与同步载荷 |

核心协同原语：

- **节点接入与亲和调度**：边缘节点经 `edgeJoin` 注册自身能力与标签 `NodeAffinity`（`nodeId` + `tags`）；工作包由 `AutoClaimWorker`（支持确定性单步 `pullAndClaimOnce` / `runOnce` 与事件唤醒 `trigger` / `awaitTriggerAndRunOnce`）按亲和度自动认领。
- **传输抽象 `FleetTransport`**：内置单机测试用的 `InMemoryFleetTransport`，以及面向跨机通信的 `HttpFleetServer`（基于 JDK 内置 `HttpServer`）与 `HttpFleetTransport`（基于 OkHttp），且可配合 `ai4j-cli` 的 `CliFleetEdgeDescriptor` 接入 CLI 边缘节点。
- **三方合并同步（`ThreeWayEntityMerger`）**：Edge 与 Center 状态存在离线并发修改时，通过 `base` / `local` / `center` 三方基线对比与可配置的 `EdgeSyncConflictPolicy` 进行收敛合并。
- **节点摘除与孤儿接管**：当边缘节点宕机失联时，管理面调用 `unregisterNode(..., force = true)`：内部自动递增 `WriterEpochFence` 使失联节点旧租约立即失效，导出其名下未完成任务的 `OrphanHandoffExport`，再由健康节点通过 `takeoverOrphan` 无缝接管。

## 下一步建议

- 了解建立在 Harness 账本之上的常驻记忆、两段式心跳与自演化 RSI：阅读 [Ambient 常驻、心跳与门控自演化 RSI](/docs/agent/ambient-and-rsi)
- 查看 CLI 如何桥接与控制 Harness / Fleet 边缘节点：阅读 [Coding Agent 与 Harness 集成](/docs/products/coding-agent/harness-integration)
