# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 设计审计范围小，证据来自源码和文档扫描；self-review 足够。 | 使用 self-review。 |
| Would a worker subagent materially help? | no | 不改业务代码，不需要并行 worker。 | coordinator 直接执行。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-07 | task package only | same checkout | 设计审计不使用 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责审计、设计文档和收口。 |
| Subagent 模式 | none | 不需要并行实现。 |
| 审查模型 | self-check | 不改公开 API 或业务代码，使用源码证据和 harness scanner 验证。 |
| Worktree 策略 | same checkout | 仅写 harness task package。 |
| 冲突控制 | coordinator owns shared files | 不触碰业务源码和 docs-site 正文。 |
| 证据深度 | L0 | design-only 审计，不触发 Java executable gate。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-009 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | source/doc targeted scan; `git diff --check`; `npx.cmd --yes coding-agent-harness status --json .` | `progress.md` | no template leftovers, design package present, harness status pass after commit |

## 暂停 / 升级条件

- 需要新增公开 API。
- 需要修改业务源码或 docs-site 正文。
- 发现现有对象链事实与设计结论冲突。
- harness status 出现材料缺失或阻塞队列。
