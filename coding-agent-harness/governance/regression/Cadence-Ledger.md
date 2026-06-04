# 回归节奏总账 - ai4j-sdk

> 本文件定义“什么改动触发哪些回归 gate”。Regression SSoT 记录 gate 本身；本文件记录触发规则和批次执行历史。
>
> 本文件是新版 `coding-agent-harness/` 的投影副本。详细历史批次仍保留在 `docs/05-TEST-QA/Cadence-Ledger.md`，迁移完成前不得覆盖或删除旧文件。

## 使用约定

- 新增、删除或调整 Regression Gate 时，同步更新本文件。
- 改动范围命中多条规则时，按并集触发，不做最小化猜测。
- 如果因为时间、环境或权限跳过触发项，必须写入批次日志和残余路由。

## 触发规则

| 改动范围 | 必跑 Gate | 可选 / 条件 Gate | 触发说明 | 负责人 |
| --- | --- | --- | --- | --- |
| PR to `dev` or `main` touching Java modules or root build files | RG-001, RG-002, RG-003, RG-004, RG-005, RG-006, RG-007 | `none` | Mirrors `.github/workflows/java-regression.yml`. | project coordinator |
| `ai4j/` provider, protocol, RAG, vector, MCP, image, audio, realtime, agentflow connector changes | RG-001, RG-007 | live-provider opt-in when credentials and task scope allow | Core SDK contract plus cross-module packaging. | project coordinator |
| `ai4j-agent/` workflow, memory, trace, orchestration changes | RG-002, RG-007 | live-provider opt-in when model behavior matters | Runtime behavior plus dependency alignment. | project coordinator |
| `ai4j-coding/` tools, outer-loop, checkpoint, shell/apply-patch changes | RG-003, RG-007 | RG-004 when CLI surface consumes the changed behavior | Coding runtime plus cross-module packaging. | project coordinator |
| `ai4j-cli/` CLI, TUI, ACP, session, rendering changes | RG-004, RG-007 | `none` | Host behavior plus packaged artifact alignment. | project coordinator |
| `ai4j-spring-boot-starter/` property binding or auto-configuration changes | RG-005, RG-007 | `none` | Starter wiring plus repo-wide packaging. | project coordinator |
| `ai4j-flowgram-spring-boot-starter/` or `ai4j-flowgram-demo/` changes | RG-006, RG-007 | RG-009 when web-demo contract can be affected | Backend FlowGram surface, packaging and likely web-demo contract impact. | project coordinator |
| `docs-site/` content, config or workflow changes | RG-008 | docs-site typecheck when TypeScript/config changes | Docs-site build is the owning gate. | project coordinator |
| `ai4j-flowgram-webapp-demo/` frontend changes | RG-009 | lint and ts-check | Frontend demo build/lint/type surface. | project coordinator |
| `AGENTS.md`, `docs/11-REFERENCE/`, SSoT, workflow governance, or `coding-agent-harness/` context changes | affected harness/status checks | affected product gates only if executable behavior changes | Governance docs alone do not imply code regression; keep v1/v2 docs aligned. | project coordinator |
| any merge to `dev` or `main` | RG-001..RG-009 | live-provider opt-in gates if release scope requires them | Branch integration should refresh the full control surface. | project coordinator |

## 共享回归批次日志

| 批次 ID | 日期 | 范围 | 触发条件 | 执行 Gate | 结果 | 证据 | 残余路由 | 下一检查点 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRB-V2-001 | 2026-06-04 | Harness v2 bootstrap mapping | `coding-agent-harness init --locale zh-CN --capabilities core,dashboard .` | RG-001..RG-009 mapped, product gates not executed | partial | scaffold/configure evidence in this task; existing detailed gate history in `docs/05-TEST-QA/Cadence-Ledger.md` | R-001..R-005 remain routed in Regression SSoT | First post-bootstrap code/docs change should execute the affected gate set. |

## 归档索引

> 批次日志超过 50 行或完成阶段收口时，移入 `coding-agent-harness/governance/regression/_archive/Cadence-Ledger-archive-YYYY-QN.md`。

| 归档文件 | 覆盖批次 | 移入日期 | 说明 |
| --- | --- | --- | --- |
| N/A | N/A | N/A | 尚无 v2 归档；历史批次仍在 `docs/05-TEST-QA/Cadence-Ledger.md`。 |

## 结果状态

- `pass`：触发 gate 全部通过。
- `pass-with-residual`：通过主判断，但有已路由残余。
- `fail`：至少一个必跑 gate 失败。
- `partial`：只执行了部分触发 gate，必须说明缺口。
- `skipped-with-reason`：未执行，必须写明原因、负责人和补跑条件。
- `inconclusive`：执行了但证据不足，不能作为通过依据。

## 路由规则

1. `fail`、`partial`、`skipped-with-reason`、`inconclusive` 都必须路由到 Regression SSoT、任务计划或 Harness Ledger。
2. 全量共享批次的定义变化必须经过负责人确认，并记录在 Harness Ledger。
3. 发布前采用最近一次相关批次作为依据；过期证据不能直接复用。
4. 批次日志只记录执行事实，具体失败分析写入 Regression SSoT 或 review。
