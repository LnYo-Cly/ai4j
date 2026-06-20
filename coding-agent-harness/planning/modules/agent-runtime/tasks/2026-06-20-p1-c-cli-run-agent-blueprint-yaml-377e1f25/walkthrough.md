# 收口记录：P1-C CLI run Agent Blueprint YAML

## 摘要

本任务为 `ai4j-cli` 增加 `run <agent.yaml>`，让单 Agent Blueprint 可以通过 CLI host 运行一次。它承接 P1-A loader/validator 和 P1-B `AgentFactory`，但保持边界清楚：YAML 不保存 token，`AgentFactory` 不读取 profile secret，CLI host 才负责解析运行时 provider/profile/model 配置。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、`docs-site`、Harness task package、Regression SSoT/Cadence Ledger |
| 新增文件 | `AgentBlueprintRunCommand.java`、`AgentBlueprintRunOptions.java`、`AgentBlueprintRunModelClientFactory.java`、`DefaultAgentBlueprintRunModelClientFactory.java`、`AgentBlueprintRunCommandTest.java` |
| 更新文件 | `Ai4jCli.java`、`CliProviderConfigManager.java`、`Ai4jCliTest.java`、`docs-site/docs/agent/agent-blueprint.md`、`docs-site/docs/agent/sdk-roadmap.md`、P1-C task materials、Regression SSoT/Cadence Ledger |
| 删除文件 | 无 |
| 不在范围内 | live provider tests、真实 Sandbox SPI/provider、Team/Workflow Blueprint、TUI 全量体验、安装打包 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted P1-C | `mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 35 tests | `ai4j-cli/target/surefire-reports/` |
| RG-004 broad | `mvn -pl ai4j-cli -am -DskipTests=false test` | pass, CLI 283 tests plus upstream modules | `progress.md` |
| RG-008 docs | `npm --prefix docs-site run build` | pass after local ignored dependency install | `progress.md` |
| Harness | `npx --yes coding-agent-harness status --json .` | pass, failures=0 before task-review | `progress.md` |
| Diff hygiene | `git diff --check` | pass, no whitespace errors | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | missing/incompatible profile could silently fall back to default | Added guard and `shouldRejectMissingProfileInsteadOfFallingBackToDefault` | `review.md` / targeted tests |
| self review | 0 open blocking findings after targeted tests | Continue broad/docs/Harness verification | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Remote CI 尚未运行 | coordinator | yes | PR 创建后等待 CI |
| live provider 行为未测试 | future live gate owner | yes | 仅在 opt-in live gate 中执行，凭证不入库 |
| 真实 sandbox 尚未实现 | future P2/P3 owner | yes | P2 Sandbox SPI / P3 coding routing |
| `ai4j` 一键安装和 TUI 体验尚未完成 | future P4 owner | yes | P4 CLI/TUI packaging/layout/rendering |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要提升共享 lesson？ | no；P1-C 结论保留在 task-local materials 和 docs-site。若后续 CLI Blueprint run 成为长期工程标准，再另开 lesson sedimentation。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 执行策略 | `execution_strategy.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 可视化图谱 | `visual_map.md` |
| 文档入口 | `docs-site/docs/agent/agent-blueprint.md` |

Closeout Status: pending-harness-pr-ci-merge

