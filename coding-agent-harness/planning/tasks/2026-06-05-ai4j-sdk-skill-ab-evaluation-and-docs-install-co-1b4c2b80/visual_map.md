# Visual Map / 可视化图谱

Visual Map Contract: v1.0

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示评测与 README 任务生命周期 | yes | `task_plan.md` | no |
| MAP-02 | decision | 展示 A/B 评测输入输出 | yes | `artifacts/ab-evaluation.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 范围与上下文\nkind=init"] --> EXEC01["EXEC-01 A/B 评测与 README\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## A/B 输入输出图

```mermaid
flowchart TD
  TASKS["T1 provider\nT2 starter\nT3 docs-site"] --> A["A: no skill"]
  TASKS --> B["B: with ai4j-sdk skill"]
  B --> SCORE["28/30\nexpected effect met"]
  A --> BASE["7/30\nmissing repo-specific behavior"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | A/B 评测报告和 README 安装入口已完成 | `docs-site/README.md`; `artifacts/ab-evaluation.md`; build output | `harness task-phase 2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`; `progress.md`; `lesson_candidates.md` | `harness task-review 2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80 --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80 --confirm 2026-06-05-ai4j-sdk-skill-ab-evaluation-and-docs-install-co-1b4c2b80` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数。
