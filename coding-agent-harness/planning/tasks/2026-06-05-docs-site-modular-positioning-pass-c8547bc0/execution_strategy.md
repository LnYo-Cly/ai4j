# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮只改 docs-site 三个入口页，主要风险是文案边界和构建断链，可由 self-review、docs-site build 和 diff check 覆盖。 | coordinator self-review。 |
| Would a worker subagent materially help? | no | 三页文案需要同一套产品定位语言，拆 worker 会增加口径不一致风险。 | 不申请 worker 授权。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-05 | docs-site modular positioning pass | current checkout | 小范围统一文案任务，不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责模块事实核对、文案口径和最终收口。 |
| Subagent 模式 | none | 无需 worker；review 由 coordinator 自查完成。 |
| 审查模型 | self-check | 变更是 Markdown 内容，docs-site build 和 diff check 足够覆盖主要风险。 |
| Worktree 策略 | same checkout | 只改 3 个 docs-site 文件和当前任务包，使用当前 checkout。 |
| 冲突控制 | coordinator owns shared files | 不触碰 shared registry、Maven 配置或其他任务。 |
| 证据深度 | L1 | docs-site production build 是本轮目标验证。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-006 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md`、`review.md` | 无 whitespace/error marker 问题 |
| L1 | `npm run build` in `docs-site/` | `progress.md`、`review.md` | Docusaurus build 成功 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md`、`artifacts/INDEX.md` | checkState pass、git clean、任务材料 ready |
| L2 | 不执行 | n/a | 本轮无浏览器交互或运行时行为 |
| L3 | 不执行 | n/a | 本轮非发布前生产等价验证 |

## 暂停 / 升级条件

- 需要声明某个 artifact 可独立发布但 Maven 事实无法支持。
- 实际需要修改 Java 模块、Maven 依赖或发布配置。
- docs-site build 暴露需要新增页面或迁移目录才能修复的断链。
- 用户要求把本轮扩展成全站 docs-site 重写。
