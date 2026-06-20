# 收口记录：Memory Compact Session API polish

## 摘要

本任务为 `ai4j-agent` 增加 session-first compact 易用入口：`SessionCompactPlan` 描述常见 compact 策略，`AgentSession.compact(SessionCompactPlan)` 执行并返回 `SessionCompactReport`，让用户和后续 CLI/TUI 可以直接读取 summary、保留/丢弃数量和 `ContextReport`。原有 `AgentSession.compact(CompactPolicy)` 保持返回 `AgentSession`，不破坏已有代码。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`docs-site`、Harness task package、Regression SSoT / Cadence Ledger |
| 新增文件 | `SessionCompactPlan.java`、`SessionCompactReport.java` |
| 删除文件 | 无 |
| 不在范围内 | CLI `/compact`、真实 provider/model compact、远端 runner、TUI 呈现 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted compact tests | `mvn -pl ai4j-agent -am "-Dtest=AgentMemoryCompactContextProjectorTest" -DskipTests=false -DfailIfNoTests=false test` | pass | 8 tests, 0 failures/errors |
| Agent broad gate | `mvn -pl ai4j-agent -am -DskipTests=false test` | pass | extension API 25, core 103, agent 126 tests |
| Docs build | `npm --prefix docs-site run build` | pass | generated `docs-site/build` |
| Diff hygiene | `git diff --check` | pass | no output |
| Token hygiene | token fragment scan | pass | no tracked workspace token hits |
| Harness status | `npx --yes coding-agent-harness status --json .` | pass before review submission | failures=0; dirty state expected before commit |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self architecture/regression review | 0 open material findings | 可提交 PR；PR CI/human review 继续验证 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| PR 远端 CI 尚未运行 | coordinator | yes | 创建 PR 后监控并修复失败 |
| CLI/TUI 尚未使用 `SessionCompactReport` | cli-host future task | yes | 后续 `/compact` UX 任务 |
| 模型驱动 compact policy 未实现 | agent-runtime future task | yes | 后续插件/策略任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，`checked-none:bounded-api-polish` |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 代码入口 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/AgentSession.java` |
| docs-site | `docs-site/docs/agent/memory-compact-context.md` |
