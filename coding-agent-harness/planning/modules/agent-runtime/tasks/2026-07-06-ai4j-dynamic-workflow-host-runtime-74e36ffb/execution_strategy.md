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
| Should a reviewer subagent be used? | yes | 这个任务最终要落到 runtime contract，review 可以提前抓住 scope drift、JS-runtime 泄漏和 shared-file 越界 | 在 `review.md` 成熟后直接调用只读 reviewer |
| Would a worker subagent materially help? | no | 当前变更主要集中在单一 module 的 host runtime 和少量 docs，先由 coordinator 单线把最小方案钉死，避免 shared table 冲突 | 不启用 worker |

## User Authorization Decision

如果上方 worker 决策是 `ask-user`，implementation 必须暂停，直到这里记录用户答案。
已解决状态只能是 `authorized`、`denied` 或 `not-needed`。选择 `ask-user` 后不得继续保持 `pending`。

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-07-06 | n/a | n/a | 先单线收敛 host runtime contract，再决定是否要 worker 并行 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责顺序、冲突判断和最终收口。 |
| Subagent 模式 | reviewer-only | 先保留 reviewer，worker 暂不启用。 |
| 审查模型 | self-check + reviewer | 先由 coordinator 做 contract 自检，再在实现后做 reviewer 复核。 |
| Worktree 策略 | dedicated worktree | 会改代码的实现必须在独立 worktree 内完成。 |
| 冲突控制 | coordinator owns shared files | shared files 只在 coordinator pass 中更新。 |
| 证据深度 | L1 → L2 | 先本地单测和定向验证，再看是否需要最小 smoke。 |

## 子代理 / Worker 合同

如使用 subagent 或 worker，在这里写清楚输入包、写入范围、handoff 格式和最终集成 owner。

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | C-001, C-002, C-003, C-004, C-005, C-006, C-007, C-008, C-009 | read-only | review report / findings | coordinator |
| worker | n/a | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | 现状审计：workflow / sandbox / codeact / extension API 复用点梳理 | `findings.md` | 明确可复用面与 contract gap |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` + 定向 JUnit | `progress.md` 或 `artifacts/INDEX.md` | envelope 解析和 workflow 组装通过 |
| L2 | 最小端到端 smoke：用固定 envelope 驱动 host runtime，验证执行/拒绝路径 | `artifacts/INDEX.md` | 至少一条真实路径可重复 |
| L3 | 仅在后续需要真实 provider / review gate 时启用 | `review.md` 与 walkthrough | 当前任务不默认要求 |

## 暂停 / 升级条件

- 需要把方案扩到 `ai4j-plugin-dynamic-workflow` 仓库。
- 需要大范围改 `ai4j-extension-api`、`ai4j-cli` 或 core SDK。
- 证明不了 Java-native workflow 编排路径，必须引入脚本执行层。
- reviewer 发现会改变范围或方案的 P0/P1/P2 问题。
- 环境无法提供关键证据，继续执行会变成猜测。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
