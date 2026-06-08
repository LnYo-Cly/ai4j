# 回归节奏总账 - ai4j-sdk

> 本文件定义“什么改动触发哪些回归 gate”。Regression SSoT 记录 gate 本身；本文件记录触发规则和批次执行历史。
>
> 本文件是新版 `coding-agent-harness/` 的投影副本。详细历史批次仍保留在 `docs/05-TEST-QA/Cadence-Ledger.md`，迁移完成前不得覆盖或删除旧文件。

## 使用约定

- 新增、删除或调整 Regression Gate 时，同步更新本文件。
- 改动范围命中多条规则时，按并集触发，不做最小化猜测。
- 如果因为时间、环境或权限跳过触发项，必须写入批次日志和残余路由。
- 默认 closeout 只要求 local-required gate；live-provider 或 credential gate 只能在任务/发布明确需要时 opt-in。

## 节奏术语

| 节奏 | 含义 | 默认证据 |
| --- | --- | --- |
| touched-surface | 任务修改对应模块或 surface 时，在 closeout 前运行 | 任务 `progress.md` 中的命令输出摘要 |
| PR | PR 命中 workflow path 时由 CI 运行 | workflow run 或 queued/pending 证据 |
| merge-batch | 合入 `dev` 或 `main` 前后刷新确定性全量基线 | SRB 行和命令/workflow 证据 |
| opt-in-live | 真实 provider、hosted demo、签名、发布或浏览器真人代理证据 | 脱敏 L3-L5 证据和 env var 名称，不记录密钥值 |

## 触发规则

| 改动范围 | 必跑 Local Gate | Live / 凭证 Gate | 节奏 | 最低证据深度 | 触发说明 | 负责人 |
| --- | --- | --- | --- | --- | --- | --- |
| PR to `dev` or `main` touching Java modules or root build files | RG-010, RG-001, RG-002, RG-003, RG-004, RG-005, RG-006, RG-007 | `none` by default | PR | L1/L2 | Mirrors `.github/workflows/java-regression.yml`: Java 8 package smoke plus module test matrix. | project coordinator |
| `ai4j-extension-api/` manifest, discovery, enable/expose, or extension resource contract changes | RG-010, RG-007 | `none` by default | touched-surface | L1 plus L2 when shared build changes | Extension API contract plus cross-module packaging; third-party extension behavior must stay deterministic by default. | project coordinator |
| `ai4j/` provider, protocol, RAG, vector, MCP, image, audio, realtime, agentflow connector changes | RG-001, RG-007 | LV-001 only when real provider behavior is in scope | touched-surface; opt-in-live if provider contract changed | L1 plus L3 when approved | Core SDK contract plus cross-module packaging; provider calls must not be implicit default evidence. | project coordinator |
| `ai4j-agent/` workflow, memory, trace, subagent/team orchestration changes | RG-002, RG-007 | LV-002 only when live model behavior is in scope | touched-surface; opt-in-live if model behavior matters | L1 plus L3 when approved | Runtime behavior plus dependency alignment. | project coordinator |
| `ai4j-coding/` tools, outer-loop, checkpoint, shell/apply-patch, compaction changes | RG-003, RG-007 | LV-002 only when live coding-agent/provider orchestration is in scope | touched-surface; opt-in-live if model behavior matters | L1 plus L3 when approved | Coding runtime plus cross-module packaging; RG-004 is added when CLI consumes the changed behavior. | project coordinator |
| `ai4j-cli/` CLI, TUI, ACP, session, provider/model command, rendering changes | RG-004, RG-007 | LV-002 only when the CLI is validated against a real model/provider | touched-surface; opt-in-live if needed | L1 plus L3 when approved | Host behavior plus packaged artifact alignment. | project coordinator |
| `ai4j-spring-boot-starter/` property binding or auto-configuration changes | RG-005, RG-007 | `none` by default | touched-surface | L1/L2 | Starter wiring plus repo-wide packaging. | project coordinator |
| `ai4j-flowgram-spring-boot-starter/` changes | RG-006, RG-007 | LV-003 when full demo/backend behavior is in scope | touched-surface; opt-in-live for end-to-end demo | L1/L2 plus L4 when approved | FlowGram starter APIs, task store, trace bridge, and packaging. | project coordinator |
| `ai4j-flowgram-demo/` backend changes | RG-006, RG-007 | LV-003 when demo scenario is in scope | touched-surface; opt-in-live for end-to-end demo | L1/L2 plus L4 when approved | Demo backend consumes starter contracts and can affect web-demo integration. | project coordinator |
| `docs-site/` content, config or workflow changes | RG-008 | `none` by default | touched-surface; PR/push workflow if configured path matches | L2 | Docs-site build/type safety is the owning gate. | project coordinator |
| `ai4j-flowgram-webapp-demo/` frontend changes | RG-009 | LV-003 when paired with a real backend/demo scenario | touched-surface; opt-in-live for end-to-end demo | L2 plus L4 when approved | Frontend demo lint/type/build baseline; browser scenario is separate evidence. | project coordinator |
| root `pom.xml`, `ai4j-bom/`, shared build plugins, release/publishing logic | RG-010, RG-001, RG-002, RG-003, RG-004, RG-005, RG-006, RG-007 | CR-001 for release candidate or publish validation | touched-surface, PR, merge-batch; opt-in-live for release | L1/L2 plus L3-L5 when approved | Shared dependency, plugin, or publishing changes can break all Java surfaces. | project coordinator |
| `AGENTS.md`, `docs/11-REFERENCE/`, SSoT, workflow governance, or `coding-agent-harness/` context changes | affected harness/status checks | affected product gates only if executable behavior changes | touched-surface governance review | L0/L1 doc verification | Governance docs alone do not imply code regression; keep v1/v2 docs aligned. | project coordinator |
| any merge to `dev` or `main` | RG-010, RG-001..RG-009 | only opt-in gates explicitly required by the merged task/release | merge-batch | L1/L2; L3-L5 only when approved | Branch integration should refresh the full deterministic control surface. | project coordinator |

## 共享回归批次日志

| 批次 ID | 日期 | 范围 | 触发条件 | 执行 Gate | 结果 | 证据 | 残余路由 | 下一检查点 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRB-V2-001 | 2026-06-04 | Harness v2 bootstrap mapping | `coding-agent-harness init --locale zh-CN --capabilities core,dashboard .` | RG-001..RG-009 mapped, product gates not executed | partial | scaffold/configure evidence in this task; existing detailed gate history in `docs/05-TEST-QA/Cadence-Ledger.md` | R-001..R-005 remain routed in Regression SSoT | First post-bootstrap code/docs change should execute the affected gate set. |
| SRB-V2-002 | 2026-06-04 | Regression baseline/live split | split deterministic local baseline from live-provider and credential-dependent gates | SSoT/cadence governance only | governance-only | Regression SSoT and Cadence Ledger updated; no Java/frontend executable regression required because this task changed gate definitions only | R-002, R-006, R-007 track normalization follow-up | First executable surface change should use the new local/live split. |
| SRB-V2-003 | 2026-06-04 | Live-provider test hygiene | JUnit category/profile plus env-only credential cleanup for core, agent, and coding live tests | RG-001 pass; RG-002 fail; RG-003 direct pass; LV-001/LV-002 profile smoke skipped without credentials | partial | `mvn -pl ai4j -DskipTests=false test` passed; `mvn -pl ai4j-coding -DskipTests=false test` passed; live profile targeted tests skipped cleanly without env credentials; `mvn -pl ai4j-agent -am -DskipTests=false test` failed in `HandoffPolicyTest` only | R-008 tracks the agent local gate failure; R-002/R-006 closed | Fix R-008 before treating RG-002 or RG-003 `-am` as fully green. |

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
