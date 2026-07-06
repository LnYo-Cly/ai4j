# 收口记录：agent trace streaming error structure

## 摘要

已完成：流式模型文本在 replay capture 中累积到 `outputText`，raw response 继续保留在 `outputs`；`NodeReplayer` mock replay 可以回退到 `outputText`；`TOOL_ERROR` 默认增加 `errorType`；docs-site 示例同步完成。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent` replay / runtime / tests；`docs-site` agent 文档 |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | live provider smoke、新的 planner / adapter 抽象、RAG/检索改造 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted agent tests | `mvn -pl ai4j-agent -am "-Dtest=NodeIoCaptureReplayTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` | PASS, 12 tests | `progress.md` |
| Full agent module tests | `mvn -pl ai4j-agent -am -DskipTests=false test` | PASS, 212 tests | `progress.md` |
| Docs-site build | `npm run build` in `docs-site/` after `npm ci` restored ignored deps | PASS | `progress.md` |
| Monorepo package smoke | `mvn -DskipTests package` | PASS, 11 reactor projects | `progress.md` |
| Diff hygiene | `git diff --check` | PASS, no whitespace errors | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-check | 无阻塞发现；capture/replay 与 tool error 结构都已被测试覆盖 | 保持最小改动，不引入新抽象 | `progress.md`、`git diff` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| docs-site `npm ci` 报出既有 npm audit vulnerabilities | repo owner | 接受 | 不在本任务范围 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，无需新增共享 lesson |
| 经验候选详情文件 | 不适用 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 进度记录 | `progress.md` |
| Regression SSoT | `../../../docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `../../../docs/05-TEST-QA/Cadence-Ledger.md` |

Closeout Status: closed
