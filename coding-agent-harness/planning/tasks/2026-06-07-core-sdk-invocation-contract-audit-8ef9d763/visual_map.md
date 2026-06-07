# Visual Map / 可视化图谱

Visual Map Contract: v1.0

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示审计阶段和门禁 | yes | `task_plan.md` | no |
| MAP-02 | data-flow | 展示当前 Core SDK 调用主线 | yes | `design.md`; `findings.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 审计与设计\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-07-core-sdk-invocation-contract-audit-8ef9d763` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Core SDK 调用合同审计和 `design.md` | `design.md`; `findings.md`; `progress.md` | `harness task-phase 2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-07-core-sdk-invocation-contract-audit-8ef9d763 --message "<summary>"` | agent | missing | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | dashboard workbench confirmation | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。
允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。
允许的 `Kind`：`init`, `execution`, `gate`。
允许的 `Actor`：`agent`, `human`, `coordinator`。

## MAP-02 - 当前调用主线

```mermaid
flowchart LR
  Config["Configuration\nprovider / HTTP / vector / MCP config"] --> Service["AiService\nsingle-instance factory"]
  Service --> Chat["IChatService"]
  Service --> Registry["AiServiceRegistry\nmulti-instance route"]
  Chat --> Request["ChatCompletion"]
  Request --> Provider["Provider ChatService"]
  Provider --> Response["ChatCompletionResponse"]
  Memory["ChatMemory"] --> Request
  Tool["ToolUtil\nfunctions / mcpServices"] --> Provider
  Rag["RagService\nRagResult.context / trace"] --> Request
```
