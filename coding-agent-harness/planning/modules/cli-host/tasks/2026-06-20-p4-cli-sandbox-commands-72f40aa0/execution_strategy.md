# 执行策略

## Subagent Authorization

任务开始时先读这一段，并向用户说明当前授权状态。这里是授权记录，不是执行沙箱。

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not-needed | write only after user approval | coordinator decision | 2026-06-20 12:02 | not used for this narrow CLI slice | n/a | not needed unless implementation splits later |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no for planning, optional before PR | 现阶段只是在 task package 里补齐计划；实现后可用 self adversarial review，若触及 public SPI 再升级只读 reviewer。 | 实现完成后在 `review.md` 做 Confidence Challenge。 |
| Would a worker subagent materially help? | no | 当前切片集中在 `ai4j-cli` 的 dispatch/factory/completion/docs，文件高度耦合，拆 worker 会增加冲突和协调成本。 | coordinator 单线程执行；如后续拆真实 provider/runner，再新建 worker task。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-20 12:02 | no worker for planning/first implementation slice | `feature/cli-sandbox-commands` | 用户已授权继续，但本切片不需要 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | 统一管理 `CodingCliSessionRunner`、factory overload、completion 和 docs，避免共享文件冲突。 |
| Subagent 模式 | none for planning / self-review before closeout | 当前不并行；实现完成后使用 self adversarial review。 |
| 审查模型 | self-check + targeted tests + optional reviewer if SPI changes | P4 不应改 sandbox SPI；若实际实现需要改 public agent/coding API，必须升级审查。 |
| Worktree 策略 | dedicated worktree | 已使用 `G:\My_Project\java\ai4j-sdk\.worktrees\feature\cli-sandbox-commands`。 |
| 冲突控制 | coordinator owns shared files | 仅 coordinator 修改 `docs/05-TEST-QA/**`、docs-site sidebar/command docs 和 task package。 |
| 证据深度 | L1/L2 | CLI unit tests 为 L1；docs-site build 为 L2；真实 provider/live sandbox 不在本轮。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-012 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check`; review diff for fake-provider overclaim | `progress.md` / `review.md` | 无 whitespace 错误；无 docs/API 过度承诺 |
| L1 | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,DefaultCodingCliAgentFactoryTest" -DskipTests=false -DfailIfNoTests=false test` | `progress.md` | slash completion、parser/factory 相关测试通过；如测试集调整需说明原因 |
| L1 | `mvn -pl ai4j-cli -am -DskipTests=false test` | `progress.md` | CLI broad regression 通过，或有非本任务 residual |
| L2 | `npm --prefix docs-site run build` | `progress.md` | docs-site 内容变更后 build 通过 |
| L2 | `npx --yes coding-agent-harness status --json .` | `progress.md` | 0 failures；若 review-confirm 仍待人工，按 dashboard 队列解释 |

## 暂停 / 升级条件

- 需要改 `SandboxProvider` / `SandboxSession` public SPI 才能继续时，暂停并新开 agent-runtime/coding-runtime 任务。
- 需要连接真实 sandbox 后端、凭据、网络 endpoint 或云资源时，暂停并新开 provider/runner 任务。
- 发现 attach 后只能静默落回 direct-host 时，必须修正为显式禁用/错误，不能继续。
- docs-site 示例需要使用未存在 API 时，暂停并改成真实 API 或明确标记为 roadmap。
- targeted tests 不能覆盖状态切换时，升级到额外 CLI runtime unit test 或手动 smoke。

## Module Preset Strategy

| Field | Value |
| --- | --- |
| Module Key | cli-host |
| Module Plan | coding-agent-harness/planning/modules/cli-host/module_plan.md |

Keep shared module decisions in the module plan or module context files. Keep task-specific evidence in this task directory.
