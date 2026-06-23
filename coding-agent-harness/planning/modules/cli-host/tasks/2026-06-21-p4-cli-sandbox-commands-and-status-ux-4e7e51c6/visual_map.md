# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示执行阶段和依赖关系 | yes | `task_plan.md` | no |

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
| INIT-01 | init | none | done | 100 | 任务计划和执行策略已确认 | `task_plan.md`; `execution_strategy.md` | `harness task-start 2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | CLI /sandbox 实现、completion、runtime rebind、targeted/broad tests、回归治理 | diff、targeted Maven、broad Maven、RG-004/SRB-059 | `harness task-phase 2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 EXEC-01 --state done --completion 100 --evidence present` | agent | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | review | 100 | Agent Review Submission 已写入 `review.md` | `review.md`、`progress.md`、`lesson_candidates.md`、`walkthrough.md` | `harness task-review 2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 --message "P4 CLI sandbox UX ready for review"` | agent | present | none | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6 --confirm 2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6` | human | partial | Agent 不能代办人工确认 | human |

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


## Runtime Binding Map

```mermaid
sequenceDiagram
  participant User
  participant CLI as ai4j-cli /sandbox
  participant Resolver as CliSandboxSessionResolver
  participant Daytona as DaytonaSandboxProvider
  participant Factory as CodingCliAgentFactory
  participant Coding as CodingAgentBuilder

  User->>CLI: /sandbox enable daytona
  CLI->>Resolver: open(command, env)
  Resolver->>Daytona: createSession(spec)
  Daytona-->>Resolver: SandboxSession
  Resolver-->>CLI: session + non-secret binding
  CLI->>Factory: prepare(options, terminal, state, pausedMcp, SandboxSession)
  Factory->>Coding: builder.sandbox(session)
  CLI-->>User: sandbox enabled + shellExec=remote-sandbox
```
