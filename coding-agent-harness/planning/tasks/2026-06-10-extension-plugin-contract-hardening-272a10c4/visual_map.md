# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示 contract hardening 从计划到人工确认的生命周期门禁 | yes | `task_plan.md`, `progress.md`, `review.md` | no |
| MAP-02 | data-flow | 展示插件契约从作者输入到 runtime/CLI/docs 的验证路径 | yes | `ExtensionManifest`, `ExtensionValidator`, `Ai4jCliTest` | no |

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
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-10-extension-plugin-contract-hardening-272a10c4` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Extension API、CLI、docs-site 和 regression governance 实现完成 | diff、`progress.md` command evidence、`review.md` evidence table | `harness task-phase 2026-06-10-extension-plugin-contract-hardening-272a10c4 EXEC-01 --state done --completion 100 --evidence present` | agent | present | non-tool allowlist remains scoped residual | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-10-extension-plugin-contract-hardening-272a10c4 --message "<summary>"` | agent | present | human confirmation still required | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-10-extension-plugin-contract-hardening-272a10c4 --confirm 2026-06-10-extension-plugin-contract-hardening-272a10c4` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02 插件契约验证路径

```mermaid
flowchart LR
  AUTHOR["Plugin author manifest/spec"] --> MANIFEST["ExtensionManifest strict public id/name checks"]
  AUTHOR --> VALIDATOR["ExtensionValidator + ExtensionToolSchemaValidator"]
  MANIFEST --> REGISTRY["ExtensionRegistry / RuntimeState registration"]
  VALIDATOR --> CLI["ai4j extension validate / scaffold tests"]
  REGISTRY --> AGENT["Agent adapter and official plugin regression"]
  CLI --> DOCS["docs-site trust boundary and author cookbook"]
  DOCS --> REVIEW["review.md + Regression SSoT evidence"]
```
