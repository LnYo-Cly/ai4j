# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮是 harness manifest 和文档治理升级，CLI/status/module-list 证据足够覆盖结构正确性；人工确认仍通过 dashboard workbench 完成。 | 使用 self review，提交给人工确认。 |
| Would a worker subagent materially help? | no | 修改集中在全局 harness registry 和模块合同；并行 worker 会增加共享文件冲突。 | coordinator 顺序执行。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-04 | module-parallel harness governance | same checkout / main | 未使用可写 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责全局 manifest、registry 和模块合同。 |
| Subagent 模式 | none | 不需要并行 worker。 |
| 审查模型 | self-check + human confirmation | 结构性治理变更由 CLI/status 证据覆盖，最终确认由人完成。 |
| Worktree 策略 | same checkout | 只改 harness 文件，不触碰业务代码。 |
| 冲突控制 | coordinator owns shared files | `harness.yaml`、Module Registry、Harness Ledger 由 coordinator 独占。 |
| 证据深度 | L1 | status、module list 和占位扫描覆盖本轮风险。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001 至 C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | 模块材料占位扫描 | `progress.md` | 无模板占位命中。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | pass，failures/warnings 为 0。 |
| L1 | `npx --yes coding-agent-harness module list --json .` | `progress.md` | 返回 10 个模块。 |
| L2 | 业务代码测试 | `review.md` | 本轮未改业务代码，waived。 |
| L3 | 外部审查 | `review.md` | 不适用。 |

## 暂停 / 升级条件

- 需要启用 `subagent-worker` 或创建 worktree。
- 需要修改业务模块源码。
- 需要新增或调整 Regression SSoT 固定 gate。
- 需要人工确认或远端 push。
