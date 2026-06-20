# 执行策略

本任务由 coordinator 在专用 worktree 内完成；未使用写入型 worker subagent。只读审查由 `review.md` 的 self-review、Harness scanner、PR/CI 覆盖。

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | P2-B AgentSession sandbox binding review | n/a | allowed within this task |
| worker subagent | not needed | n/a | coordinator decision | 2026-06-20 | P2-B implementation is narrow and already isolated | `.wt/p2b` / `feature/agent-session-sandbox-binding` | n/a |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 当前风险可由 self-review、targeted tests、broad Maven regression、docs build、Harness scanner 和 PR/CI 覆盖。 | 保持 coordinator self-review；PR 阶段由 CI / reviewer 再检查。 |
| Would a worker subagent materially help? | no | 变更集中在 `ai4j-agent` session/sandbox 模型、一个 docs page、两个 regression docs 和任务包；并行会增加共享文件冲突。 | coordinator 单线完成实现、验证、review gate、PR/CI/merge。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | P2-B AgentSession sandbox binding | `.wt/p2b` / `feature/agent-session-sandbox-binding` | 用户已授权继续任务拆解、worktree、实现、自测、PR/CI/merge；本切片无需额外 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责编排、实现、验证、review gate 和最终 PR。 |
| Subagent 模式 | none | 当前任务范围窄且已在专用 worktree 隔离。 |
| 审查模型 | self-check + Harness scanner + PR/CI | session/snapshot/event 语义用 deterministic tests 验证，远端 CI 做集成确认。 |
| Worktree 策略 | dedicated worktree | `.wt/p2b` / `feature/agent-session-sandbox-binding` 是唯一写入 worktree。 |
| 冲突控制 | coordinator owns shared files | docs-site、Regression SSoT、Cadence Ledger、module task package 串行维护。 |
| 证据深度 | L2 | targeted unit、broad Maven、docs-site build、Harness status/task-review；不需要真实 sandbox。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | `task_plan.md`; P2-A sandbox SPI diff; current session runtime tests | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` / final summary | 无 whitespace error。 |
| L1 | `mvn -pl ai4j-agent -am "-Dtest=AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | sandbox binding snapshot/store/event tests 通过。 |
| L1 | `mvn -pl ai4j-agent -am -DskipTests=false test` | `progress.md` | extension API、core、agent broad regression 通过。 |
| L2 | `npm --prefix docs-site run build` | `progress.md` | Sandbox SPI docs / roadmap 通过 Docusaurus build。 |
| L2 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failure 0；P2-B materialsReady=true。 |
| L2 | `npx --yes coding-agent-harness task-review MODULES/agent-runtime/2026-06-20-p2-b-agentsession-sandbox-binding-e8175553 ... .` | `progress.md` / `review.md` | 任务进入 review queue 或明确报告需要修复的材料。 |
| L3 | GitHub PR checks | PR summary / final delivery | build、java-regression、module-tests、package-smoke 等远端检查通过后 merge。 |

## 暂停 / 升级条件

- 需要实现真实 CubeSandbox / Docker / E2B / K8s / 内部 VM provider（转 provider task）。
- 需要让插件贡献 `SandboxProvider`（转 P2-C）。
- 需要改 `ai4j-coding` file/shell/git/browser/project run/test runner routing（转 P3）。
- 需要改 `ai4j-cli` `/sandbox` UX 或 TUI 布局（转 P4）。
- 需要把 provider token、cookie、API key 或 secret 写入 snapshot / event / docs / tests（立即停止并重设计）。
- Maven broad regression、docs-site build 或 Harness scanner 暴露和本轮无关的大面积失败，需要拆成独立修复或记录 residual。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | agent-runtime |
| Module Plan | coding-agent-harness/planning/modules/agent-runtime/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
