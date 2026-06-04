# 回归 SSoT - ai4j-sdk

> 回归覆盖面、固定 gate、证据深度和未关闭风险的单一事实源。新增 gate、改变触发规则或调整证据深度时必须更新。
>
> 本文件是新版 `coding-agent-harness/` 的投影副本。现有历史和当前详细记录仍保留在 `docs/05-TEST-QA/Regression-SSoT.md`，迁移完成前不得覆盖或删除旧文件。

## 活跃回归 Gate

| Gate ID | 覆盖面 | 主入口 | 触发场景 | 证据深度 | 上次验证 | 当前结果 | 负责人 | 残余路由 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RG-001 | core SDK module | `mvn -pl ai4j -DskipTests=false test` | `ai4j/` provider, protocol, RAG, vector, MCP, image, audio, realtime, agentflow connector changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001, R-002 |
| RG-002 | agent runtime module | `mvn -pl ai4j-agent -DskipTests=false test` | `ai4j-agent/` workflow, memory, trace, subagent/team orchestration changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001, R-002 |
| RG-003 | coding runtime module | `mvn -pl ai4j-coding -DskipTests=false test` | `ai4j-coding/` tools, outer-loop, checkpoint, shell/apply-patch changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-004 | CLI/TUI/ACP host | `mvn -pl ai4j-cli -DskipTests=false test` | `ai4j-cli/` CLI, TUI, ACP, session, rendering changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-005 | Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` | `ai4j-spring-boot-starter/` property binding or auto-configuration changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-006 | FlowGram starter and task APIs | `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test` | `ai4j-flowgram-spring-boot-starter/` or `ai4j-flowgram-demo/` changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-007 | monorepo package build | `mvn -DskipTests package` | root `pom.xml`, `ai4j-bom/`, shared build plugins, release/publishing logic, cross-module impact | L2-local-smoke | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-008 | docs-site build | `npm run build` | `docs-site/` content, config or workflow changes | L2-local-smoke | 2026-04-29 pass | pass | project coordinator | R-004, R-005 |
| RG-009 | FlowGram webapp demo build | `npm run build` | `ai4j-flowgram-webapp-demo/` frontend changes | L2-local-smoke | bootstrap-mapped | pass-with-residual | project coordinator | R-003 |

## 未关闭回归残余

| 残余 ID | Gate ID | 问题 | 严重级别 | 负责人 | 创建日期 | 路由 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R-001 | RG-001..RG-007 | Java PR workflow exists, but first green run and required branch protection are still pending. | P1 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-002 | RG-001, RG-002 | Live-provider validation relies on local credentials and is not normalized into explicit opt-in gates. | P2 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-003 | RG-009 | FlowGram webapp demo `test` scripts are placeholders; build/lint/type gates are the baseline. | P2 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-004 | RG-008 | Docs-site build on Windows may hit `EPERM` file locks during output/cache cleanup. | P2 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-005 | RG-008 | Docs-site typecheck on Windows may need `NODE_OPTIONS=--max-old-space-size=8192`. | P2 | project coordinator | 2026-04-27 | `docs/05-TEST-QA/Regression-SSoT.md` | open |

## 证据深度说明

| 等级 | 名称 | 说明 |
| --- | --- | --- |
| L1-tests | 自动化测试或静态检查通过，但没有运行时验证。 |
| L2-local-smoke | 本地环境完成关键路径冒烟。 |
| L3-live | 真实或准真实环境完成端到端验证。 |
| L4-browser-human-proxy | 浏览器或 UI 自动化覆盖接近真人操作的关键流程。 |
| L5-hard-gate | 结构化判定、可重复运行，并以非零退出或明确 verdict 阻断发布。 |

## 归档索引

> 废弃 gate、已关闭残余和历史批次移入 `coding-agent-harness/governance/regression/_archive/Regression-SSoT-archive-YYYY-QN.md`。活跃表只保留仍会影响当前开发和发布判断的内容。

| 归档文件 | 覆盖范围 | 移入日期 | 说明 |
| --- | --- | --- | --- |
| N/A | N/A | N/A | 尚无 v2 归档；历史批次仍在 `docs/05-TEST-QA/Regression-SSoT.md` 和 `docs/05-TEST-QA/Cadence-Ledger.md`。 |

## 结果状态

- `pass`：本次验证通过，无未路由问题。
- `pass-with-residual`：主路径通过，但存在已路由或已接受残余。
- `fail`：验证失败，阻塞相关合并、发布或收口。
- `inconclusive`：证据不足，不能作为通过依据。
- `paused`：gate 暂停执行，必须写清原因和恢复条件。
- `retired`：gate 已废弃，必须归档并说明替代覆盖面。

## 路由规则

1. Cadence Ledger 决定“什么时候触发哪些 gate”；本文件记录 gate 本身和当前事实。
2. 任何 `fail` 或 `inconclusive` 都必须写入未关闭回归残余，除非立即修复并有新证据。
3. 发布阻塞级问题必须同步到 Harness Ledger 和对应任务计划。
4. 接受风险必须有负责人、原因、期限或复查条件。
5. 提升或降低证据深度时，必须记录原因和最近一次验证证据。
