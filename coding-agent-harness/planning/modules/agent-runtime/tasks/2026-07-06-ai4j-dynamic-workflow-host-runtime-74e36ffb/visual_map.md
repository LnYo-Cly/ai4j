# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 plugin envelope 到 host runtime 的数据流 | yes | `task_plan.md`, `findings.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 实现切片\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | planned | 0 | 有边界的实现、文档切片和验证证据 | diff、commands、worker handoff 或 artifact path | `harness task-phase 2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb EXEC-01 --state done --completion 100 --evidence present` | agent | missing | [risk] | [owner] |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb --message "<summary>"` | agent | missing | [risk] | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb --confirm 2026-07-06-ai4j-dynamic-workflow-host-runtime-74e36ffb` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### 架构图（Architecture Map）

```mermaid
flowchart LR
  P["ai4j-plugin-dynamic-workflow"] -->|returns envelope| E["ai4j.dynamic_workflow.request"]
  E --> R["AI4J host runtime in ai4j-agent"]
  R --> C["workflow compiler / dispatcher"]
  C --> W["AgentWorkflow / SequentialWorkflow / StateGraphWorkflow"]
  W --> S["SandboxProvider / SandboxSession (when needed)"]
  W --> T["ToolCallDecision / BaseAgentRuntime routing"]
  W --> O["workflow result / persist / cancel / reject"]
```

### 方案选择

- 选中：Java-native workflow compiler / dispatcher first
- 备选：JS executor 仅在后续证明 envelope 语义无法稳定表达时再考虑

## 支持性说明

- plugin 只负责产出 envelope；host 侧负责执行策略、审批和隔离。
- workflow globals（`agent` / `parallel` / `pipeline` / `phase` / `log`）应该先映射到 host 侧可控能力，再决定是否需要脚本执行层。
