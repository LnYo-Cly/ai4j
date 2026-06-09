# 收口记录：AI4J agent handoff policy R-008 fix

## 摘要

R-008 已修复：`HandoffPolicy.FAIL` 的 allowed-tools 和 max-depth 违规现在会从 agent 运行中 fail fast，不再被普通工具错误路径包装为 `TOOL_ERROR` 后继续执行。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、Regression SSoT / Cadence Ledger、当前 harness task materials |
| 新增文件 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/subagent/HandoffPolicyException.java` |
| 删除文件 | none |
| 不在范围内 | live-provider 行为、插件生态新功能、docs-site 内容重写、Agent Team 调度策略 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted regression | `mvn -pl ai4j-agent "-Dtest=HandoffPolicyTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` | pass | 11 tests |
| RG-002 | `mvn -pl ai4j-agent -am -DfailIfNoTests=false -DskipTests=false test` | pass | extension API 12, core 103, agent 74 tests |
| RG-003 | `mvn -pl ai4j-coding -am -DfailIfNoTests=false -DskipTests=false test` | pass | coding 59 tests |
| RG-004 | `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` | pass | CLI 261 tests |
| RG-007 | `mvn -DskipTests package` | pass | 11 reactor projects |
| Static check | `git diff --check` | pass | CRLF warnings only |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self regression review | none | ready for human confirmation | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Java PR workflow 首次绿色运行和 required branch protection 仍未确认 | project coordinator | yes | R-001 继续保留在 Regression SSoT |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
