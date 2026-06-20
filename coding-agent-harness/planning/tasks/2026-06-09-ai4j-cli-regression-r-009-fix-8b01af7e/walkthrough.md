# 收口记录：AI4J CLI Regression R-009 Fix

## 摘要

R-009 已修复：ACP 不再把 loop-control 停止摘要当作 `agent_message_chunk` 输出，避免污染客户端收到的模型正文流；JLine multiline transcript 测试改为断言 ANSI 去除后的视觉文本，保留真实终端样式能力。`ai4j-cli` 直接全套测试恢复通过。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、回归治理文档 |
| 新增文件 | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-cli-regression-r-009-fix-8b01af7e/` 任务包 |
| 删除文件 | 无 |
| 不在范围内 | R-008 `ai4j-agent/HandoffPolicyTest` 修复、ACP 新状态事件协议设计、插件生态后续功能 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| R-009 target tests | `mvn -pl ai4j-cli "-Dtest=JlineShellTerminalIOTest,AcpCommandTest" -DfailIfNoTests=false -DskipTests=false test` | pass, 30 tests | `progress.md` |
| CLI direct suite | `mvn -pl ai4j-cli -DfailIfNoTests=false -DskipTests=false test` | pass, 261 tests | `progress.md` |
| RG-004 broad check | `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` | fail only in known upstream R-008 before CLI | `progress.md` |
| RG-007 package smoke | `mvn -DskipTests package` | pass, 11 reactor projects | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 | R-009 closeout accepted; R-008 retained as out-of-scope residual | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| R-008 still blocks broad `-am` RG-004 evidence before CLI executes | project coordinator | yes | Follow-up R-008 fix task |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | checked-none |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |

Closeout Status: closed
