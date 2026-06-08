# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 Wave 1 扩展 API 与后续 runtime adapter 的边界 | yes | `ai4j-extension-api/src/main/java`; `findings.md` | no |

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
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-08-ai4j-extension-system-wave-1-a924bf99` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | `ai4j-extension-api` 模块、公共合同、测试、CI/BOM/回归/harness context 同步 | diff、RG-010、RG-007、progress、findings | `harness task-phase 2026-06-08-ai4j-extension-system-wave-1-a924bf99 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-08-ai4j-extension-system-wave-1-a924bf99 --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-08-ai4j-extension-system-wave-1-a924bf99 --confirm 2026-06-08-ai4j-extension-system-wave-1-a924bf99` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

按需添加，不要求每类都存在：

- architecture：模块、组件、服务结构。
- sequence：前端、后端、服务、数据库、agent 时序。
- data-flow：数据流转和所有权。
- state：状态机或生命周期。
- topology：repo、服务、worker、worktree 拓扑。
- decision：方案分叉和决策树。

## 架构边界图

```mermaid
flowchart LR
  ThirdParty["third-party extension jar"] --> ServiceLoader["ServiceLoader discovery"]
  ServiceLoader --> Manifest["ExtensionManifest"]
  Manifest --> Registry["ExtensionRegistry"]
  Registry --> Enable["explicit enable(id)"]
  Enable --> Apply["ExtensionContext apply"]
  Apply --> Resources["tool / command / skill / prompt / guardrail specs"]
  Resources --> Expose["explicit exposeTool(name)"]
  Expose --> Snapshot["ExtensionRuntimeSnapshot"]

  Snapshot -. Wave 2 .-> AgentAdapter["ai4j-agent adapter"]
  Snapshot -. Wave 2 .-> CliAdapter["ai4j-cli inspect/command adapter"]
  Snapshot -. Wave 2 .-> SpringAdapter["Spring Boot properties/beans"]
```
