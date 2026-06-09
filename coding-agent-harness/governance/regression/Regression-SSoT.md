# 回归 SSoT - ai4j-sdk

> 回归覆盖面、固定 gate、证据深度和未关闭风险的单一事实源。新增 gate、改变触发规则或调整证据深度时必须更新。
>
> 本文件是新版 `coding-agent-harness/` 的投影副本。现有历史和当前详细记录仍保留在 `docs/05-TEST-QA/Regression-SSoT.md`，迁移完成前不得覆盖或删除旧文件。

## 回归分层

| 分层 | 用途 | 默认使用方式 | 凭证 / 网络策略 | 证据 |
| --- | --- | --- | --- | --- |
| local-required | 确定性的本地与 CI 基线 | 触达对应 surface 时必跑；merge batch 刷新全量 | 不需要真实 provider、不依赖开发者本机凭证、不提交密钥 | L1-tests 或 L2-local-smoke |
| live-provider-opt-in | 真实 provider、限流敏感或模型运行时行为 | 仅在任务/发布明确需要真实 provider 证据时运行 | 需要人工或 operator 明确批准；只记录 env var 名称和脱敏证据 | L3-live |
| credential-release-opt-in | 签名、发布、部署、托管 demo 或浏览器真人代理证据 | 仅 release candidate、发布或端到端 demo 验证时运行 | 凭证必须在 git 外；不得依赖本机绝对路径或默认私钥 | L3-live 到 L5-hard-gate |

默认 closeout 只应要求 `local-required` 证据。需要 live 或凭证 gate 时，任务必须在 `progress.md` 与 walkthrough 里记录原因、命令、env var 名称、脱敏结果和跳过条件。

## 活跃本地回归 Gate

| Gate ID | 覆盖面 | 主入口 | 触发场景 | 证据深度 | 上次验证 | 当前结果 | 负责人 | 残余路由 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| RG-010 | extension API module | `mvn -pl ai4j-extension-api -DskipTests=false test` | `ai4j-extension-api/` manifest, discovery, enable/expose, runtime inspection snapshot, and resource contract changes | L1-tests | 2026-06-09 pass, 12 tests | pass | project coordinator | none |
| RG-011 | official Ask User plugin module | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | `ai4j-plugin-ask-user/` official plugin package changes | L1-tests | 2026-06-09 pass, 6 plugin tests plus extension API dependency tests | pass | project coordinator | none |
| RG-001 | core SDK module | `mvn -pl ai4j -am -DskipTests=false test` | `ai4j/` provider, protocol, RAG, vector, MCP, image, audio, realtime, agentflow connector changes | L1-tests | 2026-06-04 pass | pass-with-residual | project coordinator | R-001 |
| RG-002 | agent runtime module | `mvn -pl ai4j-agent -am -DskipTests=false test` | `ai4j-agent/` workflow, memory, trace, subagent/team orchestration changes | L1-tests | 2026-06-09 pass | pass-with-residual | project coordinator | R-001 |
| RG-003 | coding runtime module | `mvn -pl ai4j-coding -am -DskipTests=false test` | `ai4j-coding/` tools, outer-loop, checkpoint, shell/apply-patch changes | L1-tests | 2026-06-09 pass | pass-with-residual | project coordinator | R-001 |
| RG-004 | CLI/TUI/ACP host | `mvn -pl ai4j-cli -am -DskipTests=false test` | `ai4j-cli/` CLI, TUI, ACP, session, rendering changes | L1-tests | 2026-06-09 pass | pass-with-residual | project coordinator | R-001 |
| RG-005 | Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | `ai4j-spring-boot-starter/` property binding or auto-configuration changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-006 | FlowGram starter and task APIs | `mvn -pl ai4j-flowgram-spring-boot-starter -am -DskipTests=false test` | `ai4j-flowgram-spring-boot-starter/` or `ai4j-flowgram-demo/` changes | L1-tests | ci-wired-pending-first-run | pass-with-residual | project coordinator | R-001 |
| RG-007 | monorepo package build | `mvn -DskipTests package` | root `pom.xml`, `ai4j-bom/`, shared build plugins, release/publishing logic, cross-module impact | L2-local-smoke | 2026-06-09 pass, 11 reactor projects | pass | project coordinator | none |
| RG-008 | docs-site build | `npm run typecheck`, then `npm run build` in `docs-site/` | `docs-site/` content, config or workflow changes | L2-local-smoke | 2026-06-09 pass for plugin author cookbook | pass | project coordinator | R-005 |
| RG-009 | FlowGram webapp demo build | `npm run lint`, `npm run ts-check`, then `npm run build` in `ai4j-flowgram-webapp-demo/` | `ai4j-flowgram-webapp-demo/` frontend changes | L2-local-smoke | bootstrap-mapped | pass-with-residual | project coordinator | R-003, R-007 |

## Live / 凭证 Opt-in Gate

| Gate ID | 覆盖面 | 主入口 | 触发场景 | 证据深度 | 上次验证 | 当前结果 | 负责人 | 残余路由 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LV-001 | core SDK real provider contracts | `mvn -pl ai4j -P live-provider-tests -Dtest=<ProviderTest> -DskipTests=false test` | provider/protocol/release tasks that require real provider behavior | L3-live | 2026-06-04 profile-smoke-skipped-no-credentials | pass-with-residual | project coordinator | none |
| LV-002 | agent/coding real provider orchestration | `mvn -pl ai4j-agent -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` or `mvn -pl ai4j-coding -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` | agent, CodeAct, workflow, or team-delivery tasks that require a real model | L3-live | 2026-06-04 profile-smoke-skipped-no-credentials | pass-with-residual | project coordinator | none |
| LV-003 | FlowGram demo end-to-end behavior | backend plus web demo/manual or browser-driven scenario | FlowGram demo release or integration task | L4-browser-human-proxy | mapped-pending-runbook | pass-with-residual | project coordinator | R-003 |
| CR-001 | release signing and Central publishing | release profile dry run or operator-approved publish command | release candidate only | L3-live to L5-hard-gate | mapped-pending-runbook | pass-with-residual | project coordinator | R-001 |

## 未关闭回归残余

| 残余 ID | Gate ID | 问题 | 严重级别 | 负责人 | 创建日期 | 路由 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| R-001 | RG-001..RG-007 | Java PR workflow exists, but first green run and required branch protection are still pending. | P1 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-002 | RG-001, RG-002, LV-001, LV-002 | Live-provider Maven profile/category names and maintained runbook were added: `-P live-provider-tests` plus `io.github.lnyocly.ai4j.test.LiveProviderTest`. | P1 | project coordinator | 2026-04-26 | `docs/11-REFERENCE/testing-standard.md` | closed |
| R-003 | RG-009 | FlowGram webapp demo `test` scripts are placeholders; build/lint/type gates are the baseline. | P2 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-004 | RG-008 | Docs-site build on Windows may hit `EPERM` file locks during output/cache cleanup. | P2 | project coordinator | 2026-04-26 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-005 | RG-008 | Docs-site typecheck on Windows may need `NODE_OPTIONS=--max-old-space-size=8192`. | P2 | project coordinator | 2026-04-27 | `docs/05-TEST-QA/Regression-SSoT.md` | open |
| R-006 | RG-001, RG-002, RG-003, LV-001, LV-002 | Provider/usage tests now use env-only credential reads, clean JUnit assumptions, and live-provider category isolation; remaining `config-api-key`/`sk-test` hits are deterministic local unit fixtures. | P1 | project coordinator | 2026-06-04 | `coding-agent-harness/planning/tasks/2026-06-04-live-provider-test-hygiene-c392a468/review.md` | closed |
| R-007 | RG-009 | RG-009 is mapped locally, but no dedicated CI workflow currently runs the FlowGram webapp lint/type/build baseline. | P2 | project coordinator | 2026-06-04 | `coding-agent-harness/governance/regression/Regression-SSoT.md` | open |
| R-008 | RG-002, RG-003, RG-004 | resolved 2026-06-09: `HandoffPolicy.FAIL` policy violations now propagate as fail-fast handoff errors instead of being converted into normal `TOOL_ERROR` outputs; RG-002, RG-003, and RG-004 broad gates pass. | P1 | project coordinator | 2026-06-04 | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-agent-handoff-policy-r-008-fix-8b30bc13/progress.md` | closed |
| R-009 | RG-004 | resolved 2026-06-09: direct CLI suite now passes; ACP loop-control summaries are no longer forwarded as `agent_message_chunk`, and JLine multiline transcript testing accounts for ANSI-styled visual text. | P1 | project coordinator | 2026-06-09 | `coding-agent-harness/planning/tasks/2026-06-09-ai4j-cli-regression-r-009-fix-8b01af7e/progress.md` | closed |

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
