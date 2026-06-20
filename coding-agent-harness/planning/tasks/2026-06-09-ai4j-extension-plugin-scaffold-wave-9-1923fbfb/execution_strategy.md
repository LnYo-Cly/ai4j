# 执行策略

## Subagent Authorization

| Role | Status | Permission | Authorized By | Authorized At | Scope | Worktree / Branch | Reuse |
| --- | --- | --- | --- | --- | --- | --- | --- |
| reviewer subagent | allowed by default | read-only | harness task policy | task creation | current task review | n/a | allowed within this task |
| worker subagent | not authorized | write only after user approval | pending | pending | pending | pending | allowed only within approved task/scope |

## Subagent Delegation Decision

| Question | Decision | Reason | Next Action |
| --- | --- | --- | --- |
| Should a reviewer subagent be used? | no | 本轮范围窄，主要风险可由 targeted tests、临时脚手架 smoke 和 harness material check 覆盖。 | 自审并记录证据。 |
| Would a worker subagent materially help? | no | 变更集中在一个 CLI 命令、一个测试文件和少量文档；并行会增加共享文件冲突。 | coordinator 串行完成。 |

## User Authorization Decision

| Gate | State | Decided By | Decided At | Scope | Worktree / Branch | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| worker subagent | not-needed | coordinator | 2026-06-09 | n/a | n/a | 不拆 worker。 |

## 决策表

| 决策 | 选择 | 说明 |
| --- | --- | --- |
| 主执行者 | coordinator | coordinator 负责实现、验证、治理记录和提交。 |
| Subagent 模式 | none | 任务范围集中，直接串行更稳。 |
| 审查模型 | self-check + harness status | 新行为有 targeted tests 和生成项目 smoke，提交审查仍走 task-review。 |
| Worktree 策略 | same checkout | 当前 branch 是主集成分支，未授权 worker。 |
| 冲突控制 | coordinator owns shared files | Feature / Regression / Cadence 只由 coordinator 更新。 |
| 证据深度 | L1 + L2 | CLI targeted tests 属 L1；monorepo package/docs-site build 属 L2；临时脚手架 Maven smoke 覆盖生成项目。 |

## 子代理 / Worker 合同

| 角色 | 输入包 | 写入范围 | 交接要求 | 负责人 |
| --- | --- | --- | --- | --- |
| n/a | C-001..C-005 | n/a | n/a | coordinator |

## 证据计划

| 证据层级 | 计划命令或检查 | 记录位置 | 完成条件 |
| --- | --- | --- | --- |
| L0 | `git diff --check` | `progress.md` | 无 whitespace error |
| L1 | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | `progress.md` | CLI targeted tests passed |
| L1 | 生成临时插件骨架后运行 `mvn test` | `progress.md` | 生成项目可编译并通过 validator test；若本地仓库依赖不可用，先 install `ai4j-extension-api` |
| L2 | `mvn -DskipTests package` | `progress.md` | 10-module reactor package passed |
| L2 | docs-site `npm run typecheck` + `npm run build` | `progress.md` | 文档站类型检查和构建通过 |
| L0 | `npx.cmd --yes coding-agent-harness status --json .` | `progress.md` | 无 validation failure，dirty-state 只允许出现在提交前 |

## 暂停 / 升级条件

- 需要新增或修改 `ai4j-extension-api` 公共 API。
- 脚手架需要覆盖非空目录或删除用户文件。
- 文档需要暗示远程插件安装、自动启用或自动信任第三方插件。
- 目标回归出现不是本任务引入、但阻止验证解释的失败。
