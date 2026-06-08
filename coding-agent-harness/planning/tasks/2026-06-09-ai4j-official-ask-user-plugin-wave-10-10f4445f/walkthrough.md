# 收口记录：AI4J official ask-user plugin wave 10

## 摘要

本任务新增官方 `ai4j-plugin-ask-user` 插件模块，并将它接入 AI4J reactor、BOM、CI matrix、README、docs-site、Regression SSoT、Cadence Ledger 和 harness module registry。插件以 host-mediated JSON envelope 表达“Agent 需要问用户”的请求，不实现 UI、stdin 阻塞、答案持久化或恢复执行协议。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-plugin-ask-user`、根 POM、`ai4j-bom`、docs-site、README、CI、harness context/module/task、Regression SSoT、Cadence Ledger、Feature SSoT、reference standards |
| 新增文件 | `ai4j-plugin-ask-user/**`、`docs-site/docs/core-sdk/extension/ask-user-plugin.md`、`coding-agent-harness/planning/modules/ask-user-plugin/**` |
| 删除文件 | 无 |
| 不在范围内 | 远程插件市场、runtime jar hot load、CLI 自动安装依赖、真实 UI、stdin 阻塞、答案持久化、Agent 恢复执行协议 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| 插件模块测试 | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | pass, extension API 12 tests plus Ask User plugin 6 tests | `progress.md` |
| 全仓 packaging smoke | `mvn -DskipTests package` | pass, 11 reactor projects | `progress.md` |
| docs-site typecheck | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` in `docs-site/` | pass | `progress.md` |
| docs-site build | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` in `docs-site/` | pass | `progress.md` |
| diff whitespace | `git diff --check` | pass | `progress.md` |
| harness status | `npx.cmd --yes coding-agent-harness status --json .` | pass-with-dirty-warning before commit: 0 failures, 1 expected dirty-state warning | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可在最终验证通过后提交；人工确认不由 agent 代办 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 人工 review confirmation 未由用户侧执行 | human | yes | 推送后由用户决定是否运行 `review-confirm` 或退回 |
| ask-user 只返回 host-mediated request envelope，不提供真实 UI / resume contract | owner | yes | 后续更高层 host runtime 或 CLI/TUI 任务处理 |
| 远程插件市场、runtime jar hot load、CLI install 未实现 | owner | yes | 当前文档明确不承诺 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结果 | no-candidate-accepted；本任务没有新增可复用 harness 流程规则 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 模块计划 | `../../modules/ask-user-plugin/module_plan.md` |
