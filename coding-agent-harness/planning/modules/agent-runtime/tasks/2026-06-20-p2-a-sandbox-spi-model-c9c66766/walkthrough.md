# 收口记录：P2-A Sandbox SPI model

## 摘要

P2-A 新增 `io.github.lnyocly.ai4j.agent.sandbox`，为真实 sandbox provider、session、command、result、artifact 和 event 提供最小 Java 8 合同。该任务没有接入真实 VM/容器/远端环境，也没有改变现有本地工具执行语义。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`docs-site`、Regression docs、Harness task package |
| 新增代码 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/*.java` |
| 新增测试 | `AgentSandboxSpiModelTest` |
| 新增文档 | `docs-site/docs/agent/sandbox-spi.md` |
| 不在范围内 | 真实 provider、AgentSession binding、plugin provider contribution、coding routing、CLI `/sandbox` |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted P2-A | `mvn -pl ai4j-agent -am "-Dtest=AgentSandboxSpiModelTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 4 tests | `progress.md` |
| Broad agent | `mvn -pl ai4j-agent -am -DskipTests=false test` | pass, extension API 25, core 103, agent 115 tests | `progress.md` |
| Docs build | `npm --prefix docs-site run build` | pass after local dependency install | `progress.md` |
| Regression docs | update RG-002/RG-008/SRB-055 | present | `docs/05-TEST-QA/*` |

## 审查结论

无阻塞 P2-A 的 material finding。残余均属于后续任务：P2-B session binding、P2-C plugin provider contribution、P3 coding routing、P4 CLI/TUI UX。

## Lessons Reflection

本任务不提升共享 lesson；稳定结论已写入 docs-site 和 task-local materials。

Closeout Status: pending-pr-ci-merge
