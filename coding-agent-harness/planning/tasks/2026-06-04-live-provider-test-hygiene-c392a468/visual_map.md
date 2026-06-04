# Visual Map / 可视化图谱

Visual Map Contract: v1.0

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | decision | 展示 default local 与 opt-in live 测试路径 | yes | `docs/11-REFERENCE/testing-standard.md` | no |

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
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-04-live-provider-test-hygiene-c392a468` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | live provider category/profile、env-only tests、回归文档和验证证据已完成 | diff、commands、`progress.md`、`artifacts/INDEX.md` | `harness task-phase 2026-06-04-live-provider-test-hygiene-c392a468 EXEC-01 --state done --completion 100 --evidence present` | agent | present | R-008 outside scope | coordinator |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission | `review.md`、progress update、lesson routing | `harness task-review 2026-06-04-live-provider-test-hygiene-c392a468 --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-04-live-provider-test-hygiene-c392a468 --confirm 2026-06-04-live-provider-test-hygiene-c392a468` | human | missing | Agent 不能代办人工确认 | human |

## 测试路径图

```mermaid
flowchart TD
  Change["Code/test change"] --> Default["Default local regression"]
  Default --> Exclude["Surefire excludes LiveProviderTest"]
  Exclude --> Local["Deterministic tests run without provider credentials"]
  Change --> NeedLive{"Need real provider behavior?"}
  NeedLive -->|no| Close["Close with local evidence"]
  NeedLive -->|yes| Live["Run -P live-provider-tests -Dtest=<LiveTest>"]
  Live --> Env{"Env credentials present?"}
  Env -->|no| Skip["JUnit Assume skip; record opt-in residual"]
  Env -->|yes| Execute["Run real provider test; record sanitized L3 evidence"]
```

## 支持性图表（Supporting Maps）

- R-008 单独路由到 Regression SSoT，不属于 MAP-02 的 live-provider hygiene path。
