# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | no write delegation | coordinator | 2026-06-20 | single-slice API/docs task | n/a | 后续独立切片再申请 |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 当前任务切片较窄，已用 self adversarial review + targeted regression；PR 后再接受维护者审查。 | 在 `review.md` 记录 confidence challenge。 |
| Would a worker subagent materially help? | no | 代码/API/docs 是同一公共契约，拆 worker 会增加冲突而非提速。 | coordinator 直接完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | extension contract slice | feature/plugin-contribution-contract-expansion | 后续 sandbox routing / CLI UX 可再拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 单一公共契约切片，由 coordinator 保持 API/docs/test 一致。 |
| Subagent 模式 | none | 不需要并行 worker。 |
| 审查模型 | self-check / adversarial review / CI after PR | 公共 API 使用自审 + targeted tests + docs build，PR 后用远端检查补强。 |
| Worktree 策略 | dedicated worktree | 已在 `.worktrees/feature/plugin-contribution-contract-expansion`。 |
| 冲突控制 | coordinator owns shared files | 不编辑其他 worktree；主仓库规划分支不混入本实现。 |
| 证据深度 | L1 + docs build | Java public API + docs 需要 deterministic tests 和 docs-site build。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-009 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error；CRLF warning 可接受。 |
| L1 | `mvn -pl ai4j-extension-api -DskipTests=false test` | `progress.md` | public extension API tests 通过。 |
| L1 | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | `progress.md` | 官方插件示例测试通过。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=ExtensionAgentToolsTest,AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | agent extension bridge targeted tests 通过。 |
| L1 | `npm --prefix docs-site run build` | `progress.md` | docs-site build 通过。 |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0；若 dirty warning，只在提交前记录。 |

## 暂停 / 升级条件

- 需要实现真实 SandboxProvider / AgentRunnerProvider registry。
- 需要新增 Maven module 或改变 `ai4j-agent` / `ai4j-extension-api` 依赖方向。
- 需要把插件安装、远端市场、签名校验等引入本任务。
- docs build 或 Java targeted regression 失败且无法在当前范围修复。
- validator 规则会破坏现有插件兼容性。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
