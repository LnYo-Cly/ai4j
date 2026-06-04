# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮是小范围 docs-site 文案和 sidebar 改动，主要风险可通过构建、断链检查和 self-review 覆盖。 | 使用 coordinator self-review，并在 `review.md` 中记录证据。 |
| Would a worker subagent materially help? | no | 只有 4 个文件，且首页、Why、Feature Map 需要统一口径；拆分 worker 反而会增加文案不一致风险。 | 不申请 worker 授权。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-04 | docs-site Wave 1 entrance files | current checkout | 小范围统一文案任务，不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责文案口径、链接边界和最终收口。 |
| Subagent 模式 | none | 本轮无需 worker；review 由 coordinator 自查完成。 |
| 审查模型 | self-check | docs-site build、diff check、link-sensitive Docusaurus build 能覆盖主要风险。 |
| Worktree 策略 | same checkout | 当前任务只改 docs-site 入口文件和任务包，冲突面可控。 |
| 冲突控制 | coordinator owns shared files | `sidebars.ts` 和入口文档由 coordinator 一次性维护。 |
| 证据深度 | L1 | 文档站构建属于本轮足够的目标验证；不需要浏览器 E2E 或 Java 测试。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-006 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md`、`review.md` | 无 whitespace/error marker 问题 |
| L1 | `npm run build` in `docs-site/` | `progress.md`、`review.md` | Docusaurus build 成功，无新增断链 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | 任务 lifecycle 能被 harness 识别；dirty warning 只在提交前出现 |
| L2 | 不执行 | n/a | 本轮没有交互 UI 或运行时行为变更 |
| L3 | 不执行 | n/a | 本轮不是发布前生产等价验证 |

## 暂停 / 升级条件

- `npm run build` 暴露断链且需要新增或迁移深页才能修复。
- 实际需要修改超过 4 个 docs-site 目标文件。
- 需要声明某个 preview/experimental 能力为稳定承诺。
- 用户要求把本轮扩展成全站重写或视觉 redesign。
- harness lifecycle 命令无法识别任务，导致无法进入 review 队列。
