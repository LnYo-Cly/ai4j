# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | CLI/TUI status context review | n/a | allowed within this task |
| worker subagent | not-needed | n/a | coordinator decision | 2026-06-21 | narrow CLI/TUI slice | feature/cli-tui-status-context-bar | not used |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 切片小，targeted tests 可覆盖；PR/CI 提供额外审查。 | self review。 |
| Would a worker subagent materially help? | no | 改动集中在一个 renderer、一个测试文件和一个 docs 页面；并行会增加冲突。 | coordinator 实现。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-21 | TUI status context bar | feature/cli-tui-status-context-bar | 用户已授权整体继续；本切片无需额外 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 保持小切片，避免 renderer 重构。 |
| Subagent 模式 | none | 无需拆分。 |
| 审查模型 | self-check + targeted tests + PR checks | 覆盖 TUI 输出和 docs build。 |
| Worktree 策略 | dedicated worktree | `.worktrees/feature/cli-tui-status-context-bar`。 |
| 冲突控制 | coordinator owns TUI renderer/test/docs | 不改其他模块任务。 |
| 证据深度 | L1 | deterministic tests + docs build。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=TuiSessionViewTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | targeted TUI tests pass。 |
| L1 | `npm --prefix docs-site run build` | `progress.md` | docs build pass。 |
| L0 | `git diff --check` | `progress.md` | no whitespace issues。 |
| L0 | token fragment scan | `progress.md` | no token fragments committed。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` / `review.md` | failures=0，materialsReady=true。 |

## 暂停 / 升级条件

- 需要新增 public API 或跨模块 runtime 字段。
- TUI 输出需要真实终端/tmux 才能判断而无法用 unit test 覆盖。
- docs build 因全局配置失败，需要非本切片修复。
- Harness scanner 报 missing materials。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | cli-host |
| Module Plan | coding-agent-harness/planning/modules/cli-host/module_plan.md |

共享模块状态通过 Harness lifecycle 同步；任务证据只写 task-local package。
