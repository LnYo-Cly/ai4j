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
| Should a reviewer subagent be used? | no | 本轮范围窄、主要是 agent runtime 执行边界 + deterministic tests；先用 self adversarial review，PR 后交给 human/CI。 | 在 `review.md` 中做 confidence challenge。 |
| Would a worker subagent materially help? | no | 实现、docs、治理文件共享面较多，并行 worker 容易冲突；当前更适合 coordinator 串行收口。 | 不派 worker。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | n/a | n/a | 串行执行，避免共享文件冲突。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责 P0-D 从设计、实现、验证到 PR 收口。 |
| Subagent 模式 | reviewer-only via self review | 不启动 worker；review 由 self adversarial + 后续 human confirmation。 |
| 审查模型 | adversarial self-check | 重点挑战执行链顺序、真实 sandbox 语义误导、Java 8/API 兼容和测试证据。 |
| Worktree 策略 | dedicated worktree | `feature/agent-approval-permission-policy` 是唯一写入 worktree；main dirty 同任务差异需归并后清理。 |
| 冲突控制 | coordinator owns shared files | docs-site sidebar/roadmap、Regression SSoT、Cadence Ledger、module_plan 由 coordinator 串行更新。 |
| 证据深度 | L1 + L2-lite | L1 targeted/unit tests；L2-lite module regression + docs-site build + harness status。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-008 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`；API surface review | `progress.md` | 无 whitespace error；无 provider token；Java 8 语法。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentApprovalPermissionPolicyTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` / surefire reports | P0-D deterministic tests 通过。 |
| L2-lite | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | agent runtime 模块回归通过。 |
| L2-lite | `cd docs-site; npm run build` | `progress.md` | 文档站构建通过。 |
| Governance | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0；dirty warning 在提交前可接受，提交后应 clean。 |

## 暂停 / 升级条件

- 需要改变 `ToolExecutor` 方法签名或模型 tool schema 格式。
- 需要新增 `ai4j-extension-api` 公共扩展合同。
- 需要实现真实 sandbox provider 或远端 runner。
- 需要改 `ai4j-coding` / `ai4j-cli` 行为。
- 模块回归暴露和本轮无关的大面积失败，需要拆成独立修复或记录 residual。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
