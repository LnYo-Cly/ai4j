# 收口记录：AI4J extension command execution wave 5

## 摘要

本轮把 AI4J 插件 command 从“可 inspect”推进到“可由人显式调用”：CLI 新增 `extension run --enable <extension-id> <command> [arguments...]`，只执行显式启用插件快照中的 command handler，并保持 classpath discovery 不自动执行第三方代码。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、`docs-site`、root/docs-site README、harness task package、regression governance docs |
| 新增文件 | none |
| 删除文件 | none |
| 不在范围内 | CLI install、marketplace、runtime jar hotload、provider plugin、TUI slash command 自动接入、Agent/Coding Agent 新能力 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RG-004 targeted | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass, 13 tests | `progress.md` |
| RG-007 | `mvn -DskipTests package` | pass, 10 reactor modules | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` | pass | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | pass, generated `docs-site/build` | `progress.md` |
| L0 | `git diff --check` | pass | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可提交 Agent Review Submission；人工确认仍需用户/维护者完成 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| TUI slash command palette 未自动接入 extension command | coordinator | yes | 后续独立设计 |
| CLI install / marketplace / hotload 未实现 | coordinator | yes | 当前 docs 明确写为不包含能力 |
| Full RG-004 仍受既有 R-008 阻塞 | coordinator | yes | 后续单独修复 `HandoffPolicyTest` |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | no-candidate-accepted，本轮没有需要 promotion 的通用流程经验 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Feature SSoT row | F-027 |
| Regression gates | RG-004、RG-007、RG-008 |
| Branch / Worktree | `main`，未使用 dedicated worktree |
| Commit | pending until final git commit |
