# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 query planner 在 RAG 链路中的位置 | yes | code diff | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 实现切片\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 架构图（RAG Query Planning）

```mermaid
flowchart LR
  Q["RagQuery(original)"] --> P["RagQueryPlanner optional"]
  P --> V["RagQueryPlan variants"]
  V --> R["Retriever"]
  R --> F["Variant result fusion"]
  F --> RR["Reranker uses original query"]
  RR --> A["RagContextAssembler uses original query"]
  A --> OUT["RagResult + RagTrace"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | RAG planner API、模型 planner、service 集成、测试和 docs-site 完成 | diff、`progress.md` commands、`review.md` evidence | `harness task-phase 2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission 待 lifecycle CLI 标记 | `review.md`、progress update、lesson routing | `harness task-review 2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb --confirm 2026-07-04-rag-query-planner-pre-retrieval-b40dc7cb` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。

## 支持性图表（Supporting Maps）

无。
