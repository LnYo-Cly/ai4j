# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | P1-A implementation only | `.worktrees/feature/agent-blueprint-schema-loader` / `feature/agent-blueprint-schema-loader` | allowed only after explicit approval |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | yes | P1-A creates a public-ish config contract; read-only review can challenge scope creep, Java 8, security/docs drift without changing files. | Implementation complete 后调用 reviewer 检查 task package、code、tests、docs。 |
| Would a worker subagent materially help? | no | 当前任务只做规划落盘，不进入代码实现；无需写入型 worker。后续真正实施 P1-A 前如用户要求并行，再单独申请 worker 授权。 | 继续由 coordinator 维护任务包和后续 worktree 准备。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | planning-only task package update | n/a | 当前只记录规划，不派写入 worker；实施阶段如需并行再重新申请。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责范围控制、依赖选择、最终集成和审查提交。 |
| Subagent 模式 | reviewer-only by default | 默认不启用写入 worker；实施后可用只读 reviewer 做 contract review。 |
| 审查模型 | self-check + read-only adversarial review | Blueprint 是后续公共配置合同，需要挑战非目标和安全边界。 |
| Worktree 策略 | dedicated worktree | 会改 `ai4j-agent`、docs-site 和治理文件，必须隔离到 feature worktree。 |
| 冲突控制 | coordinator owns shared files | `docs/05-TEST-QA/**`、`docs-site/sidebars.ts`、module_plan 由 coordinator 最终同步。 |
| 证据深度 | L1 + docs build + Harness | Loader/validator 属于 deterministic API surface；不需要 live provider。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | C-001..C-007 | read-only | review findings with severity and no-finding statement | coordinator |
| worker（可选） | C-001..C-007 | `ai4j-agent/**`、`docs-site/docs/agent/**`、`docs-site/sidebars.ts`、task package only | commit SHA、tests、residuals、changed files | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; inspect YAML fixtures and docs examples for tokens | `progress.md` | 无 whitespace error，无 token/secret 示例。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | Targeted loader/validator tests passed。 |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | Agent module regression passed。 |
| L1 | `cd docs-site; npm run build` | `progress.md` | Docusaurus build passed。 |
| L1 | `npx --yes coding-agent-harness status --json .` | `progress.md` | Harness status pass；若 active/review queue 未 closed，状态解释清楚。 |
| L2 | GitHub PR CI after push | `review.md` / walkthrough | Required checks pass before merge。 |
| L3 | 不适用 | n/a | P1-A 不需要 live provider 或 credential-release 验证。 |

## 暂停 / 升级条件

- 所需工作超出 `ai4j-agent` + docs-site + harness/regression 文档范围。
- 必须修改 `ai4j` core provider API、`ai4j-cli`、FlowGram starter 或新增 Maven 模块。
- YAML dependency 无法满足 Java 8 或安全基线。
- validator 需要读取真实 provider credentials 或本地 profile 才能判断合法性。
- reviewer 发现 P0/P1/P2 级范围漂移、安全或兼容性问题。
- 环境无法提供 targeted/module/docs/Harness 验证证据。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
