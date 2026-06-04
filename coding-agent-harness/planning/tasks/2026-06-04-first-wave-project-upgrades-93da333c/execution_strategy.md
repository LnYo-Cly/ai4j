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
| Should a reviewer subagent be used? | no | 本轮变更是局部 release 配置与 Git 边界清理，已通过路径复查、Maven package 和 harness status 验证；不涉及安全、架构或跨模块 API 变更。 | 使用 self review，人工确认仍由用户处理。 |
| Would a worker subagent materially help? | no | 文件范围集中，worker 并行会增加共享 POM 与 task 材料冲突成本。 | coordinator 在 same checkout 中完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-04 | first wave low-risk config slice | same checkout / main | 未使用可写 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排顺序、冲突判断和最终收口。 |
| Subagent 模式 | none | 本轮不需要 worker 或独立 reviewer。 |
| 审查模型 | self-check | 风险集中在配置可移植性，证据通过命令和 diff 覆盖。 |
| Worktree 策略 | same checkout | 低风险短切片，且用户要求本地 commit 不远程 push。 |
| 冲突控制 | coordinator owns shared files | `.gitignore`、POM 和 task 材料由 coordinator 单独修改。 |
| 证据深度 | L1 | package smoke 覆盖 Maven 聚合构建；未做 live release signing。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001 至 C-004 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `rg -n "D:\\Develop\\DevelopEnv\\GnuPG|gpg\\.exe" -g 'pom.xml' -g '**/pom.xml'` | `progress.md` | 只剩属性名和默认值，不出现本机绝对路径。 |
| L1 | `mvn -DskipTests package` | `progress.md` | Maven reactor 全部 SUCCESS。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | status 为 pass，failures 和 warnings 为 0。 |
| L2 | release signing dry run | `review.md` | 本轮未执行；列为残余风险。 |
| L3 | CI / 发布前外部审查 | `review.md` | 本轮不适用。 |

## 暂停 / 升级条件

- 需要执行真实 release signing 或接入 CI secret。
- 需要启用 module-parallel、subagent-worker 或新增治理文件。
- 需要修改 Regression SSoT / Cadence Ledger 的固定 gate。
- 用户要求 push 到远端或执行人工 review confirmation。
