# 执行策略

本任务由 coordinator 在专用 worktree 内完成；未使用写入型 worker subagent。只读审查由 `review.md` 的 self-review、Harness scanner、后续 PR/CI 覆盖。

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | P2-A Sandbox SPI model review | n/a | allowed within this task |
| worker subagent | not needed | n/a | coordinator decision | 2026-06-20 | P2-A implementation is narrow and already isolated | `.wt/p2a` / `feature/agent-sandbox-spi-model` | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本任务的关键风险已经由 task-local self-review、targeted tests、broad Maven regression、docs-site build 和 Harness scanner 覆盖；当前修复只涉及任务材料 schema。 | 保持 coordinator self-review；PR 阶段由 CI / reviewer 再检查。 |
| Would a worker subagent materially help? | no | P2-A 只新增 provider-neutral SPI model、fake provider tests、docs-site 页面和回归记录；文件关系紧密，且已在独立 worktree 完成，拆给 worker 会增加共享 docs / task package 冲突。 | coordinator 单线完成材料修复、review gate、push、PR、CI、merge。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | P2-A Sandbox SPI model | `.wt/p2a` / `feature/agent-sandbox-spi-model` | 用户已授权继续任务拆解、worktree、实现、自测、PR/CI/merge；本切片无需额外 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排、实现、验证、review gate 和最终 PR。 |
| Subagent 模式 | none | 当前任务范围窄且已在专用 worktree 隔离，不派写入 worker。 |
| 审查模型 | self-check + Harness scanner + PR/CI | SPI 合同由本地测试和 docs build 验证，PR 后继续由远端 CI 覆盖。 |
| Worktree 策略 | dedicated worktree | `.wt/p2a` / `feature/agent-sandbox-spi-model` 是唯一写入 worktree。 |
| 冲突控制 | coordinator owns shared files | docs-site sidebar/roadmap、Regression SSoT、Cadence Ledger、module task package 由 coordinator 串行维护。 |
| 证据深度 | L2 | 覆盖 targeted unit、broad Maven、docs-site build、Harness status/task-review；不需要真实 sandbox。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | `task_plan.md`; `references` from architecture planning task; current P2-A diff | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / final summary | 无 whitespace error。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | fake provider / command / cancel / defensive-copy tests 通过。 |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | extension API、core、agent broad regression 通过。 |
| L2 | `npm --prefix docs-site run build` | `progress.md` | Sandbox SPI docs page/sidebar/roadmap 能通过 Docusaurus build。 |
| L2 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failure 0；P2-A materialsReady=true。 |
| L2 | `npx --yes coding-agent-harness task-review MODULES/agent-runtime/2026-06-20-p2-a-sandbox-spi-model-c9c66766 ... .` | `progress.md` / `review.md` | 任务进入 review queue 或明确报告需要修复的材料。 |
| L3 | GitHub PR checks | PR summary / final delivery | build、java-regression、module-tests、package-smoke 等远端检查通过后 merge。 |

## 暂停 / 升级条件

- 需要实现真实 CubeSandbox / Docker / E2B / K8s / 内部 VM provider。
- 需要把 sandbox 绑定到 `AgentSession` snapshot/event log（转 P2-B）。
- 需要让插件贡献 `SandboxProvider`（转 P2-C）。
- 需要改 `ai4j-coding` file/shell/git/browser/project run/test runner routing（转 P3）。
- 需要改 `ai4j-cli` `/sandbox` UX 或 TUI 布局（转 P4）。
- Maven broad regression、docs-site build 或 Harness scanner 暴露和本轮无关的大面积失败，需要拆成独立修复或记录 residual。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
