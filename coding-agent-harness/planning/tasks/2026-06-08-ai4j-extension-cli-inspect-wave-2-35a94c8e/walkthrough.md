# 收口记录：AI4J extension CLI inspect wave 2

## 摘要

本任务完成 AI4J extension 生态 Wave 2 的 CLI 审查入口：`ai4j-cli extension list` 可以列出 classpath 上通过 ServiceLoader 发现的扩展；`ai4j-cli extension inspect <id>` 默认只展示 manifest/source/权限/配置前缀，不执行第三方 `apply()`；`--runtime` 才临时执行扩展并通过只读 `ExtensionInspectionSnapshot` 列出 tools/commands/skills/prompts/guardrails。任务没有实现 install、持久 enable、Spring Boot binding 或 Agent/Coding runtime adapter。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、`ai4j-extension-api`；同步 Regression/Cadence、`cli-host` module plan、task package |
| 新增文件 | `CliExtensionCommand.java`、`ExtensionInspectionSnapshot.java`、CLI test fixture/service descriptor、CLI `LiveProviderTest` marker |
| 删除文件 | 无 |
| 不在范围内 | `extension install`、持久化 enable、Spring Boot properties、Agent/Coding runtime adapter、marketplace、runtime jar download、真实第三方 jar 验证 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Diff hygiene | `git diff --check` | pass，只有 CRLF warnings | `progress.md` |
| Extension API targeted | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass，8 tests | `progress.md` |
| CLI targeted | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass，8 tests | `progress.md` |
| Package smoke | `mvn -DskipTests package` | pass，10 reactor modules | `progress.md` |
| Full RG-004 attempt | `mvn -pl ai4j-cli -am -DskipTests=false test` | fail before CLI module in existing `ai4j-agent` R-008 | `progress.md`; Regression SSoT |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 0 | 可提交人工确认；RG-004 上游 residual 已路由 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 完整 RG-004 被既有 `ai4j-agent` R-008 阻塞 | coordinator | yes | 独立修复 `HandoffPolicyTest` 后重跑完整 CLI gate |
| `--runtime` 临时执行第三方 `apply()` | coordinator | yes | 当前通过默认 manifest-only 和 CLI help 降低误用；sandbox/签名策略后续再做 |
| CLI 输出暂无 `--json` | coordinator | yes | marketplace/install 或脚本化消费前单独评估 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none: cli-extension-inspect-local-contract-no-new-governance-lesson |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 回归 SSoT | `docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `docs/05-TEST-QA/Cadence-Ledger.md` |

Closeout Status: closed
