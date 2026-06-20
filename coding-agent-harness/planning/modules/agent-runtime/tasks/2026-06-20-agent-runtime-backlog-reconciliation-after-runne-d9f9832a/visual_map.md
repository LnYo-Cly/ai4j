# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示本任务执行阶段和证据门禁 | yes | `task_plan.md` | no |
| MAP-02 | dependency | 展示 P0-P5 已合并基座与下一步实现切片的关系 | yes | `findings.md` / `module_plan.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 backlog 校准\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | PR #118 后 agent-runtime backlog 与 module plan 已校准 | `findings.md`; `module_plan.md`; path/PR evidence | `harness task-phase 2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a --message "Agent runtime backlog reconciliation ready for review"` | agent | present | must pass final status first | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a --confirm 2026-06-20-agent-runtime-backlog-reconciliation-after-runne-d9f9832a` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

```mermaid
flowchart TB
  P0["P0 Session / Memory / Hook / Permission\nmerged on dev"] --> P1["P1 YAML Blueprint / AgentFactory / CLI run\nmerged on dev"]
  P1 --> P2["P2 Sandbox SPI + Session Binding\nmerged on dev"]
  P2 --> P3["P3 Coding Sandbox Routing\nmerged in coding-runtime"]
  P3 --> P4["P4 CLI Sandbox Commands\nmerged via PR #116"]
  P2 --> P5["P5 Remote Agent Runner SPI\nmerged via PR #118"]
  P0 --> NEXT["Next: Memory/Compact Session API polish"]
  P5 --> NEXT
  NEXT --> PLUGIN["Then: Plugin contribution contract expansion"]
  NEXT --> DOCS["Then: docs-site real API completeness pass"]
```
