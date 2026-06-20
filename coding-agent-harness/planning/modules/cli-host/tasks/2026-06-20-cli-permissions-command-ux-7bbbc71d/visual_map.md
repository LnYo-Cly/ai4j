# CLI permissions command UX - Visual Map

Visual Map Contract: v1.0

本文件是任务图表集合，不只是阶段路线图。只有对人或 agent 理解任务有实际帮助的图才放进来。

## 图表索引（Map Index）

| ID | Type | Purpose | Required For Understanding | Source Evidence | Promotion Candidate |
| --- | --- | --- | --- | --- | --- |
| MAP-01 | phase | 展示本任务生命周期与 review gate | yes | `task_plan.md` | no |
| MAP-02 | implementation-flow | 展示 `/permissions` 从 CLI/TUI 到 ACP/docs/tests 的实现链路 | yes | `execution_strategy.md` | no |
| MAP-03 | boundary-map | 展示 approval、ACP、sandbox 与 no-raw-output 边界 | yes | `references/cli-permissions-command-ux-plan.md` | no |

## 阶段关系图（Phase Graph）

```mermaid
flowchart LR
  INIT01["INIT-01 创建任务包\nkind=init"] --> EXEC01["EXEC-01 实现 /permissions\nkind=execution"]
  EXEC01 --> GATE01["GATE-01 Agent 提交审查\nkind=gate"]
  GATE01 --> GATE02["GATE-02 人工审查确认\nkind=gate"]
```

## 阶段表（Phase Table，表头供 checker 解析）

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Exit Command | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | task package 已创建并启动 | `task_plan.md`; `execution_strategy.md`; `progress.md` | `harness task-start 2026-06-20-cli-permissions-command-ux-7bbbc71d` | agent | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | `/permissions` 已接入 CLI/TUI/ACP/docs/tests | `references/cli-permissions-command-ux-plan.md`; implementation diff; targeted tests; broad CLI tests; docs build | `harness task-phase 2026-06-20-cli-permissions-command-ux-7bbbc71d EXEC-01 --state done --completion 100 --evidence present` | agent | present | final static checks pending | coordinator |
| GATE-01 | gate | EXEC-01 | done | 100 | Agent Review Submission | `review.md`; progress update; lesson routing; final Harness status | `harness task-review 2026-06-20-cli-permissions-command-ux-7bbbc71d --message "CLI permissions command UX ready for review"` | agent | present | must pass final checks first | coordinator |
| GATE-02 | gate | GATE-01 | planned | 0 | Human Review Confirmation | review packet 和人工确认 | dashboard workbench confirmation | human | missing | Agent 不能代办人工确认 | human |

允许的 `State`：`planned`, `in_progress`, `review`, `blocked`, `done`, `skipped`。

允许的 `Evidence Status`：`missing`, `partial`, `present`, `waived`。

允许的 `Kind`：`init`, `execution`, `gate`。

允许的 `Actor`：`agent`, `human`, `coordinator`。

`Completion` 使用 `0..100` 的整数；`done` 应为 `100`，`planned` 应为 `0`，`skipped` 不计入 dashboard 总完成度。dashboard 的实现完成度只由非 skipped 的 `execution` 阶段计算；`init` 和 `gate` 阶段表达生命周期门禁、下一步命令和责任人，不拉低实现完成度。

## 支持性图表（Supporting Maps）

### MAP-02：实现与验证链路

```mermaid
flowchart LR
  Dev["feature/cli-permissions-command-ux worktree"] --> Slash["SlashCommandController\nroot + completion"]
  Slash --> Runner["CodingCliSessionRunner\ndispatch + renderPermissionsOutput"]
  Runner --> ACP["AcpSlashCommandSupport\navailable command + renderer"]
  Runner --> Help["CodeCommand/help/palette\n用户入口同步"]
  Runner --> Formatter["CodexStyleBlockFormatter\npermissions info block"]
  ACP --> Tests["CLI targeted tests"]
  Help --> Docs["docs-site command reference\ntools approvals"]
  Docs --> DocsBuild["npm --prefix docs-site run build"]
  Tests --> Review["review.md + progress.md"]
  DocsBuild --> Review
```

### MAP-03：边界图

```mermaid
flowchart TB
  User["用户输入 /permissions"] --> Summary["只读权限摘要"]
  Summary --> Approval["approvalMode\nauto/safe/manual"]
  Summary --> Source["source hint\n--approval / AI4J_APPROVAL / ai4j.approval / default"]
  Summary --> ACP["ACP\nsession/request_permission"]
  Summary --> Sandbox["sandbox\n改变执行位置，不替代审批"]
  Summary --> Safe["no raw tool input\nno prompt / key / output"]
```
