# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | architecture | 展示 Package / Manifest / Extension / Resource 和 AI4J 模块消费关系 | yes | `references/ai4j-extension-system-design.md` | yes |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 任务启动\nkind=init"] --> EXEC01["EXEC-01 架构规划\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Pi 调研、AI4J Extension System 设计、Feature SSoT 更新和 L0 验证 | `references/*.md`; `findings.md`; `progress.md`; `review.md` | `harness task-phase 2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f EXEC-01 --state done --completion 100 --evidence present` | agent | present | independent reviewer deferred to implementation task | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f --confirm 2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

### MAP-02 - Extension System Architecture

```mermaid
flowchart TB
  Package["AI4J Package\nMaven artifact / local package / future marketplace"]
  Manifest["ai4j-package.yml\nid/version/capabilities/permissions/configPrefix"]
  Extension["Ai4jExtension\nruntime code entrypoint"]
  Resources["Resources\nskills / prompts / themes / schemas"]

  Package --> Manifest
  Package --> Extension
  Package --> Resources

  Loader["Extension Loader\nServiceLoader + manifest validation"]
  Enable["Enable Gate\nexplicit enabled package set"]
  Expose["Expose Gate\nallowlist tools/resources"]

  Manifest --> Loader
  Extension --> Loader
  Resources --> Loader
  Loader --> Enable
  Enable --> Expose

  Expose --> Agent["ai4j-agent\nTool / Guardrail"]
  Expose --> Coding["ai4j-coding\nSkill / Prompt / Context / SubAgent"]
  Expose --> CLI["ai4j-cli\nCommand / Inspect / TUI"]
  Expose --> Spring["spring starter\nconfiguration binding"]
```
