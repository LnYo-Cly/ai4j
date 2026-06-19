# P1-B Agent Blueprint to AgentFactory - 执行策略

## Subagent Authorization

本任务由 coordinator 在专用 worktree 内完成；未使用写入型 worker subagent。只读 review 由 `review.md` 的 self-review 和 Harness scanner 覆盖。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | not used | read-only allowed | harness task policy | task creation | P1-B review packet | n/a | allowed |
| worker subagent | not needed | n/a | coordinator decision | 2026-06-20 | P1-B implementation is narrow and already isolated | `.wt/p1b` / `feature/agent-blueprint-factory` | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | P1-B 变更面集中在 `ai4j-agent` Blueprint factory，已有 deterministic tests、docs build、Harness scanner 和 self-review；无需额外只读 reviewer 才能闭环。 | 保留 self-review，PR 后交给远端 review/CI。 |
| Would a worker subagent materially help? | no | 实现范围已经在一个专用 worktree 内，跨文件依赖紧密；并行 worker 会增加共享 docs / task package 冲突。 | coordinator 单线完成，实现后提交。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | P1-B AgentFactory | `.wt/p1b` / `feature/agent-blueprint-factory` | 用户已授权继续任务拆解和 worktree 开发，本切片无需额外 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 负责实现、docs-site、Harness task package、回归和 PR。 |
| Subagent 模式 | none | 本任务已足够小且共享文件较多，不引入 worker。 |
| 审查模型 | self-check + Harness scanner + PR/CI | deterministic 单测和 broad regression 覆盖核心行为，PR 后继续由远端 CI/维护者审查。 |
| Worktree 策略 | dedicated worktree | 使用 `G:\My_Project\java\ai4j-sdk\.wt\p1b` 和 `feature/agent-blueprint-factory`。 |
| 冲突控制 | coordinator owns shared files | Regression SSoT、Cadence Ledger、module_plan 和 docs-site 都由 coordinator 同步。 |
| 证据深度 | L2 | API 行为变更 + docs-site + Harness task package，需要 targeted、broad、docs build 和 Harness status。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | P1-B task package and worktree | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` / surefire report | 8 tests, 0 failures/errors/skipped。 |
| L2 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | extension API、core、agent broad regression 全通过。 |
| L2 | `npm --prefix docs-site run build` | `progress.md` | Docusaurus build 成功，生成物不提交。 |
| L2 | `npx --yes coding-agent-harness status --json .` | `progress.md` / `review.md` | failures=0；P1-B 材料进入 review 或明确 residual。 |

## 暂停 / 升级条件

- 需要读取或写入真实 provider token / profile secret。
- 需要创建真实 sandbox session 或引入外部 sandbox SDK。
- 需要实现 CLI `ai4j run agent.yaml`。
- Maven broad regression 或 docs-site build 失败且需要跨模块设计变更。
- Harness scanner 报告 material issue，必须先修复 task-local 材料再继续 PR。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | `coding-agent-harness/planning/modules/agent-runtime/module_plan.md` |

共享模块状态只同步到 module plan；P1-B 的实现证据和 review 证据保留在本任务目录。
