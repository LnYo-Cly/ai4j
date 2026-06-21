# CLI Host 模块计划

## 模块身份

- 模块 Key：`cli-host`
- 负责人：coordinator
- 分支：`main`
- 写入范围：`ai4j-cli/**`
- 共享面：CLI docs、root build config、coding runtime integration contracts
- 依赖模块：`coding-runtime`

## 边界

- 可以编辑：CLI host 源码、测试、模块 POM。
- 禁止编辑：coding runtime 内核、core SDK、starter 模块，除非任务明确批准。
- 外部依赖：terminal capabilities、ACP client behavior、本地 session 文件。

## 步骤

| 步骤 ID | 名称 | 状态 | 任务计划 | 依赖 |
| --- | --- | --- | --- | --- |
| T-CLI-MEMORY-COMPACT-COMMAND-UX-D56C15FD | CLI memory compact command UX | active | coding-agent-harness/planning/modules/cli-host/tasks/2026-06-20-cli-memory-compact-command-ux-d56c15fd/task_plan.md | none |
| T-P4-CLI-SANDBOX-COMMANDS-AND-STATUS-UX-4E7E51C6 | P4 CLI sandbox commands and status UX | review | coding-agent-harness/planning/modules/cli-host/tasks/2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6/task_plan.md | T-CLI-MEMORY-COMPACT-COMMAND-UX-D56C15FD |

## 活跃任务

| 任务 | 状态 | 负责人 | 证据 | 备注 |
| --- | --- | --- | --- | --- |
| `2026-06-08-ai4j-extension-cli-inspect-wave-2-35a94c8e` | review | coordinator | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test`; `mvn -DskipTests package` | Wave 2 只交付 classpath extension list/inspect；完整 RG-004 仍受 R-008 上游 agent residual 阻塞 |
| `2026-06-09-ai4j-extension-command-execution-wave-5-3b0bed77` | review-pending | coordinator | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test`; `mvn -DskipTests package` | Wave 5 adds `extension run --enable <id> <command>` for explicit human-invoked extension commands. |
| `2026-06-09-ai4j-extension-scaffold-author-experience-wave-1-50e4b9e6` | in_progress | coordinator | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 21 tests; broader checks pending | Wave 11 strengthens generated scaffold README author contract and docs cookbook without changing extension runtime semantics. |
| `2026-06-21-p4-cli-sandbox-commands-and-status-ux-4e7e51c6` | review | coordinator | targeted 61 tests; broad `mvn -pl ai4j-cli -am -DskipTests=false test` with CLI 298 tests | Adds `/sandbox status|enable daytona|attach daytona|disable` and runtime binding for shell exec through `SandboxSession`; live Daytona rerun skipped because env credential absent. |

## 验证

| 检查 | 命令或证据 | 必需 |
| --- | --- | --- |
| 模块测试 | `mvn -pl ai4j-cli -DskipTests=false test` | yes |
| 依赖构建 | `mvn -pl ai4j-cli -am -DskipTests package` | risk-based |

## 交接

- 分支：`feature/<name>` 或 `.worktrees/feature/<name>`。
- Commit SHA：worker handoff 必须提供。
- 检查：记录 CLI targeted test 或 smoke command。
- 变更文件：只列 `ai4j-cli/**` 及批准的共享文件。
- 残余风险：平台或终端差异未验证时必须说明。
- 需要 coordinator 同步：影响 coding-runtime、docs 或 release config 时同步。
