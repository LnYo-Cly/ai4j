# Visual Map / 可视化图谱

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示本任务生命周期与后续实现 gate | yes | `task_plan.md` | no |
| MAP-02 | command-map | 展示 `/memory` 与 compact/checkpoint 命令分工 | yes | `references/cli-memory-compact-command-ux-plan.md` | no |
| MAP-03 | implementation-flow | 展示实现、测试、docs 和 review 链路 | yes | `execution_strategy.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 创建任务包\nkind=init"] --> EXEC01["EXEC-01 规划与实现\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | task package 已创建并启动 | `task_plan.md`; `execution_strategy.md`; `progress.md` | `harness task-start 2026-06-20-cli-memory-compact-command-ux-d56c15fd` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | in_progress | 20 | 规划已记录；实现尚未开始 | `references/cli-memory-compact-command-ux-plan.md`; final diff; targeted tests; docs build | `harness task-phase 2026-06-20-cli-memory-compact-command-ux-d56c15fd EXEC-01 --state done --completion 100 --evidence present` | agent | partial | implementation pending | coordinator |
| GATE-01 | gate | EXEC-01 | planned | 0 | Agent Review Submission | `review.md`; progress update; lesson routing | `harness task-review 2026-06-20-cli-memory-compact-command-ux-d56c15fd --message "CLI memory command UX ready for review"` | agent | missing | implementation and evidence required first | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | `harness review-confirm 2026-06-20-cli-memory-compact-command-ux-d56c15fd --confirm 2026-06-20-cli-memory-compact-command-ux-d56c15fd` | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02：命令分工

```mermaid
flowchart TB
  User["用户想理解长会话状态"]
  Memory["/memory\n当前 memory / compact 健康概览"]
  Compact["/compact\n主动压缩当前 session memory"]
  Compacts["/compacts\n查看 compact 历史和诊断"]
  Checkpoint["/checkpoint\n查看结构化 checkpoint 内容"]
  Status["/status\n运行状态大盘"]
  Session["/session\nsession 元信息和 store 状态"]

  User --> Memory
  Memory --> Compact
  Memory --> Compacts
  Memory --> Checkpoint
  User --> Status
  User --> Session
```

### MAP-03：实现与验证链路

```mermaid
flowchart LR
  Dev["feature/cli-memory-compact-ux worktree"] --> Slash["SlashCommandController\nroot + completion"]
  Slash --> Runner["CodingCliSessionRunner\ndispatch + renderMemoryOutput"]
  Runner --> ACP["AcpSlashCommandSupport\navailable command + renderer"]
  Runner --> Help["CodeCommand/help/palette\n用户入口同步"]
  ACP --> Tests["CLI targeted tests"]
  Help --> Docs["docs-site command reference"]
  Docs --> DocsBuild["npm --prefix docs-site run build"]
  Tests --> Review["review.md + progress.md"]
  DocsBuild --> Review
```
