# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | optional | read-only | coordinator | 2026-06-20 | docs/harness review | same branch | allowed |
| worker subagent | not-needed | none | coordinator | 2026-06-20 | docs-only task | n/a | not needed |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责拆解、文档、验证、PR。 |
| Worktree 策略 | dedicated worktree | 用户明确要求创建 worktree，本任务使用 `.worktrees/docs/ai4j-agent-architecture-roadmap`。 |
| 分支 | `docs/ai4j-agent-architecture-roadmap` | 文档和 Harness 任务拆解专用分支。 |
| 证据深度 | L1/L2 | docs build + harness status；不改 Java 代码无需 Maven 全量。 |
| Token 处理 | process-only if needed | 用户提供的 provider token 不写入文件、不写入 git、不写入日志。当前任务不需要使用。 |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git status --short --branch` | `progress.md` | 仅包含本任务改动。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failure 0。 |
| L2 | `cd docs-site; npm run build` | `progress.md` | Docusaurus build success。 |
| L2 | `git diff --check` | `progress.md` | 无 whitespace error。 |

## 暂停 / 升级条件

- 需要修改 Java API 或实现代码。
- docs-site build 暴露现有非本任务错误且无法隔离。
- 需要真实 provider token 测试。
- PR 创建失败且三次重试仍失败。
