# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |
| MAP-02 | sequence | 展示 R-001 远端 CI 与 branch protection 收口路径 | yes | `progress.md` / GitHub Actions run / branch protection API | no |

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
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | Java regression workflow、ai4j-cli Linux CI 修复、远端 green run、branch protection 和回归治理记录 | diff、commands、remote run、branch protection API、progress update | `harness task-phase 2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission materials ready | `review.md`、progress update、lesson routing、walkthrough | `harness task-review 2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690 --message "<summary>"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690 --confirm 2026-06-09-ai4j-java-regression-ci-r-001-verification-daa85690` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02 - R-001 CI Gate Closeout

```mermaid
sequenceDiagram
  participant Agent as Coordinator
  participant Workflow as java-regression.yml
  participant Actions as GitHub Actions
  participant Branches as main/dev protection
  participant SSoT as Regression SSoT

  Agent->>Workflow: add stable aggregate java-regression and FlowGram demo matrix
  Agent->>Actions: push workflow and test-stabilization commits
  Actions-->>Agent: run 27202972949 success
  Agent->>Branches: require strict java-regression context
  Branches-->>Agent: API confirms main/dev protection
  Agent->>SSoT: close R-001 and record SRB-042/SRB-V2-009
```

按需添加，不要求每类都存在：

- architecture：模块、组件、服务结构。
- sequence：前端、后端、服务、数据库、agent 时序。
- data-flow：数据流转和所有权。
- state：状态机或生命周期。
- topology：repo、服务、worker、worktree 拓扑。
- decision：方案分叉和决策树。
