# Visual Map / 可视化图谱

Visual Map Contract: v1.0

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示删除 Skill 任务生命周期 | yes | `task_plan.md` | no |
| MAP-02 | decision | 展示对外 Skill 入口收敛 | yes | `docs-site/README.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 删除 ai4j-sdk Skill\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## Skill 入口收敛图

```mermaid
flowchart TD
  BEFORE["Before\n$ai4j-app-builder + $ai4j-sdk"] --> DECISION["Decision\n维护仓库用 AGENTS.md + harness"]
  DECISION --> AFTER["After\n只公开 $ai4j-app-builder"]
  AFTER --> USER["用户自己的 Java / Spring Boot 项目"]
  DECISION --> MAINTAINER["贡献者/维护者阅读 AGENTS.md"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `brief.md`; `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | `$ai4j-sdk` 已删除，README 只保留 app-builder | commit `f891bdd`; validation output; docs-site build | `harness task-phase 2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`; `progress.md`; `lesson_candidates.md` | `harness task-review 2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac --confirm 2026-06-06-remove-ai4j-sdk-maintainer-skill-40e1d2ac` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数。
