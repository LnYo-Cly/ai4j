# 收口记录：P2-C Daytona sandbox provider

## 摘要

本轮为 `ai4j-agent` 增加首个真实 sandbox provider：Daytona。它基于既有 `SandboxProvider` / `SandboxSession` SPI，支持通过 env-only credential 创建或附加 Daytona sandbox，启动并轮询到可运行状态，把 `SandboxCommand` 转成 Daytona toolbox execute 请求，并把退出码、stdout/stderr、事件和耗时返回为 `SandboxResult`。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`docs-site`、regression governance、task package |
| 新增文件 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona/**`; `ai4j-agent/src/test/java/io/github/lnyocly/agent/daytona/**` |
| 删除文件 | 无 |
| 文档更新 | `docs-site/docs/agent/sandbox-spi.md`; `docs-site/docs/agent/sdk-roadmap.md`; `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |
| 不在范围内 | CLI `/sandbox`、provider registry/ServiceLoader、E2B/Cube/Docker provider、Daytona cancel/artifact API |

## 关键决策

| 决策 | 结论 | 原因 |
| --- | --- | --- |
| provider 所属模块 | 放在 `ai4j-agent` | 它实现通用 Sandbox SPI，不依赖 CLI/TUI/Spring。 |
| HTTP 实现 | Java 8 `HttpURLConnection` | 避免新增依赖，保持轻量。 |
| 凭证边界 | env-only 推荐，config 兼容 | 防止 key 进入 Blueprint、snapshot、docs 或日志。 |
| 默认 close | `deleteOnClose=false` | 保守避免误删；需要自动清理时显式开启。 |
| live 验证 | `LiveProviderTest` opt-in | 真实 Daytona 不进入默认本地基线。 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Daytona targeted | `mvn -pl ai4j-agent -am -DskipTests=false -Dtest=DaytonaSandboxProviderTest -DfailIfNoTests=false test` | pass，5 tests | `progress.md`; surefire report |
| Agent broad | `mvn -pl ai4j-agent -am -DskipTests=false test` | pass，extension API 25 / core 103 / agent 124 | `progress.md` |
| Docs-site | `npm --prefix docs-site run build` | pass，生成 `docs-site/build`；首次因本地 node_modules 缺 Docusaurus 后执行 `npm --prefix docs-site install` 恢复 | `progress.md` |
| Live Daytona smoke | `mvn -pl ai4j-agent -am -P live-provider-tests -Dtest=DaytonaSandboxLiveSmokeTest ...`（同日已执行） | pass，1 test，真实 create/execute/close | `ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.daytona.DaytonaSandboxLiveSmokeTest.txt` |

## Evidence depth

| Gate | Depth | Status |
| --- | --- | --- |
| RG-002 | L1 tests | pass |
| RG-008 | L2 local_smoke | pass |
| LV-004 | L3 live | pass-with-prior-live-smoke；当前 shell 缺 env，未在 final pass 复跑 |
| SRB-058 | batch log | recorded |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可提交待人工确认 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| `cancel(...)` 未实现 | coordinator | yes | 后续 Daytona process cancellation 切片 |
| artifact 列表未实现 | coordinator | yes | 后续 Daytona file/artifact API 切片 |
| provider registry / 插件贡献 provider 未实现 | coordinator | yes | 后续 extension/provider registry 切片 |
| final pass 未复跑 live smoke | coordinator | yes | 需要用户重新设置 env 后可复跑；已有同日 sanitized pass 证据 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | no-candidate-accepted；本轮经验主要是 Daytona-specific provider 实现，不沉淀为全局 harness lesson。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| Regression SSoT | `docs/05-TEST-QA/Regression-SSoT.md` |
| Cadence Ledger | `docs/05-TEST-QA/Cadence-Ledger.md` |
| Commit | pending until git commit |
