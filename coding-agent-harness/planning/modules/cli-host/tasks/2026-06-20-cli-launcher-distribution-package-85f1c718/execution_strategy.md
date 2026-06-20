# CLI launcher distribution package - 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | CLI distribution implementation | `.worktrees/feature/cli-launcher-distribution` / `feature/cli-launcher-distribution` | not needed for this bounded slice |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 当前变更集中在 Maven assembly、launcher 模板、docs-site 与 deterministic tests；self review + package smoke 足以覆盖本切片，PR/CI 继续提供外部 gate。 | 不调用 reviewer；把残余风险写入 `review.md`。 |
| Would a worker subagent materially help? | no | 任务是单一发行包切片，跨 `pom.xml`、`src/main/dist`、docs 和 governance 的顺序一致性比并行更重要。 | coordinator 直接实现、验证、提交、PR。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 | CLI distribution package | `.worktrees/feature/cli-launcher-distribution` / `feature/cli-launcher-distribution` | 用户已授权继续任务队列；本切片无需 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责 Maven/package/docs/governance 串行一致性。 |
| Subagent 模式 | none | 不使用 worker；PR/CI 作为后续审查 gate。 |
| 审查模型 | self-check + PR/CI | 发行包布局通过 tests、package smoke、archive inspection、launcher smoke 和 docs build 验证。 |
| Worktree 策略 | dedicated worktree | 任务在 `.worktrees/feature/cli-launcher-distribution` 内实现，不污染 roadmap/root checkout。 |
| 冲突控制 | coordinator owns shared files | `.gitignore`、docs-site、Regression SSoT、Cadence Ledger 和 task package 均由 coordinator 串行维护。 |
| 证据深度 | L1 + package smoke + docs build | 不涉及真实 provider；不需要 L3 live tests。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| reviewer | not used | read-only if later requested | no-finding / findings report | coordinator |
| worker | not used | not applicable | not applicable | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=CliDistributionLayoutTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | layout、example config、secret-pattern、assembly descriptor tests 通过 |
| L1 | `mvn -pl ai4j-cli -am -DskipTests=false test` | `progress.md` | CLI broad tests 通过 |
| L1 | `mvn -pl ai4j-cli -am -DskipTests package` | `progress.md` | fat jar、dist zip、dist tar.gz 生成 |
| L1 | archive inspection + `ai4j.cmd --help` smoke with `AI4J_CLI_JAR` | `progress.md` | 必需文件存在、版本过滤完成、Windows launcher 能启动 help |
| L1 | `npm --prefix docs-site run build` | `progress.md` | docs-site build 通过 |
| L0 | exact user-token fragment scan and generic secret scan | `progress.md` | 用户 token 片段无匹配；generic secret 命中仅允许历史占位符/误报并记录 |
| L0 | `git diff --check` | `progress.md` | 无 whitespace / conflict marker 问题 |
| L0 | `npx --yes coding-agent-harness status --json .` | `progress.md` | failures=0，任务材料齐全 |

## 暂停 / 升级条件

- 需要真实 provider token、发布凭证、GitHub Release 上传或 checksum 签名。
- 需要把在线安装脚本切换为依赖尚不存在的 release asset。
- 需要修改 CLI runtime 命令分发、provider/model 默认值或用户配置存储。
- Windows launcher 需要写入系统 PATH 或用户 profile。
- Unix launcher 必须在真实 Linux/macOS shell 中验证，超出当前 Windows 本地 smoke 能力。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | cli-host |
| Module Plan | coding-agent-harness/planning/modules/cli-host/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
