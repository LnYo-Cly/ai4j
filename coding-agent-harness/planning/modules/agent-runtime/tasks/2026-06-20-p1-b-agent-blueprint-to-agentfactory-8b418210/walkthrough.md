# 收口记录：P1-B Agent Blueprint to AgentFactory

## 摘要

本任务为 P1-B 增加 `AgentFactory`，使 P1-A 的单 Agent Blueprint 在宿主显式提供依赖后，可以创建真实 `AgentBuilder` / `Agent`。

关键边界：Factory 不读取 provider token，不解析本机 profile，不安装插件，不创建真实 sandbox，不实现 CLI `ai4j run agent.yaml`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`docs-site`、Harness task package、Regression SSoT/Cadence Ledger |
| 新增文件 | `AgentFactory.java`、`AgentFactoryContext.java`、`AgentFactoryException.java`、`AgentBlueprintFactoryTest.java` |
| 更新文件 | `docs-site/docs/agent/agent-blueprint.md`、P1-B task materials、`docs/05-TEST-QA/Regression-SSoT.md`、`docs/05-TEST-QA/Cadence-Ledger.md` |
| 删除文件 | 无 |
| 不在范围内 | CLI `ai4j run agent.yaml`、Team/Workflow Blueprint、真实 Sandbox SPI/provider、live provider tests |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted P1-B | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintFactoryTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 8 tests | `ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.AgentBlueprintFactoryTest.txt` |
| RG-002 broad | `mvn -pl ai4j-agent -am -DskipTests=false test` | pass, extension API 25 + core 103 + agent 111 tests | Maven output / surefire reports |
| RG-008 docs | `npm run build` in `docs-site` | pass, generated static files in `build` | docs-site build output |
| Harness | `npx --yes coding-agent-harness status --json .` | pending final rerun after task materials update | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 blocking findings | 可以进入 PR/CI；remote CI 仍是 merge 前证据 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Remote CI 尚未运行 | coordinator | yes | PR 创建后等待 CI |
| CLI 尚不能直接运行 Blueprint | future P1-C owner | yes | P1-C task |
| 真实 sandbox 尚未实现 | future P2/P3 owner | yes | P2 Sandbox SPI / P3 coding routing |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要提升共享 lesson？ | no；P1-B 的稳定结论已经进入 task-local docs/reference。若后续 Blueprint Factory 边界成为长期工程标准，再另开 lesson sedimentation。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 执行策略 | `execution_strategy.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 可视化图谱 | `visual_map.md` |
| 参考设计 | `references/agent-blueprint-p1b-factory-plan.md` |

Closeout Status: pending-pr-ci-merge
