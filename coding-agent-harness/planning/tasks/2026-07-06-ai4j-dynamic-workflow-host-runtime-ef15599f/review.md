# ai4j dynamic workflow host runtime - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | `ai4j-agent` dynamic workflow runtime、host tool wrapper、docs-site host runtime 章节、task-local governance |

## 审查范围

- 审查类型：adversarial / security / regression / architecture
- 范围内：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/dynamicworkflow/**`、`AgentBuilder.dynamicWorkflow(...)` opt-in、`ai4j-agent/src/test/java/.../dynamicworkflow/**`、`docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md`、Regression SSoT / Cadence Ledger。
- 范围外：独立插件仓库源码、`ai4j-extension-api` public contract、后台 workflow manager、resume journal、per-agent worktree isolation、真实 provider/live API 调用。
- 来源材料：当前 worktree diff、JUnit 输出、docs-site typecheck/build 输出、Regression SSoT / Cadence Ledger diff、task plan / progress。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | ARS-20260706-dynamic-workflow-host-runtime |
| Submitted At | 2026-07-06 15:45 Asia/Shanghai |
| Submitted By | Codex coordinator |
| Task Key | 2026-07-06-ai4j-dynamic-workflow-host-runtime-ef15599f |
| Materials Checklist Hash | local-review-20260706-dw-host |
| Evidence Summary | Targeted Maven dynamic workflow tests passed; docs-site typecheck/build passed; self-review closed Nashorn Java interop/bridge exposure finding with code hardening and tests. |
| Open Findings Count | 0 |
| Scanner Version | harness local CLI + self-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` uses `no-candidate-accepted` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes for the scoped MVP / no for broader future workflow-manager capabilities
- 如果不是 100%，剩余漏洞或证据缺口：本轮目标是 host-side MVP；后台 workflow 管理、resume、真实并发 worker/worktree isolation、model tier routing 仍是明确范围外后续项。
- Fix loop count：2
- 当前结论：第一轮实现后自审发现 Nashorn 默认 Java interop 与 raw bridge binding 风险；已改为默认 `--no-java`、隐藏 `load`/`quit`、把 host bridge 收进闭包并删除全局 binding，同时新增 2 个安全回归测试。现有证据足够收口本轮 MVP。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-001 | P1 | 初版 Nashorn runtime 直接暴露 Java interop 和 raw Java bridge binding，模型生成脚本理论上可尝试访问宿主 Java 对象或全局加载入口。 | `NashornDynamicWorkflowExecutor` prelude/bindings self-review；新增 `hidesJavaInteropAndRawBridgeByDefault` / `rejectsJavaTypeByDefault`。 | 默认使用 `--no-java` 创建 engine，移除危险全局，host bridge 只通过闭包持有，并补安全回归。 | no | mitigated | no | 如未来替换 Node/GraalJS runtime，必须重放同类“无任意 host interop”测试。 |

## 非阻塞备注（Non-Material Notes）

- `parallel([...])` 当前保持 fan-out 分组和结果顺序，但在 Nashorn MVP 中按确定性顺序执行 JS function；真实并发、隔离 worktree 和 worker lifecycle 属于后续 host/coding-agent 层能力。
- `allowJavaInterop(true)` 仅为可信宿主扩展保留，不应作为默认配置或文档推荐。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | command | TARGET:. | `mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DfailIfNoTests=false -DskipTests=false test` passed; 11 tests, 0 failures/errors. |
| E-002 | command | TARGET:docs-site | `npm run typecheck` passed. |
| E-003 | command | TARGET:docs-site | `npm run build` passed; generated static files in `build`. |
| E-004 | diff | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/dynamicworkflow | Runtime API, parser, executor, result DTOs, host tool executor, and safety defaults reviewed. |
| E-005 | diff | TARGET:docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md | Host runtime section documents plugin/host boundary, opt-in AgentBuilder wiring, primitives, Nashorn/ES5 boundary, and Java interop default. |

## 无重要发现声明

本轮已检查上述证据，F-001 已修复并有回归覆盖；当前没有 open P0/P1/P2 重要发现阻塞本轮目标。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| Nashorn runtime 不是完整 Node/modern JS runtime，只覆盖 ES5 + 轻量 normalizer。 | coordinator | yes | docs-site 已记录；复杂脚本需自定义 `DynamicWorkflowExecutor`。 |
| `parallel()` 目前不是物理并发执行。 | coordinator | yes | 后续在 `ai4j-coding` / host worker bridge 中设计 worktree-isolated fan-out。 |
| 未做 live provider / MiniMax / GLM 真实模型 E2E。 | coordinator | yes | 本轮本地 deterministic gate 足够；live-provider 属 opt-in，不提交 secrets。 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | no | self-review 已完成；若用户需要，可再走人工确认。 | n/a |
| Missing Materials | no | 任务本地 brief、plan、progress、visual_map、review、walkthrough、lesson decision 均已补齐。 | n/a |
| Blocked | no | 无 open blocking finding。 | n/a |
| Lessons | no | 本轮 lesson candidate 判定为 checked-none；安全经验已内化为测试和文档，不单独推广共享 lesson。 | n/a |
| Confirmed / Finalized | no | 尚未有人类 review-confirm；本轮作为 agent closeout 交付。 | 如用户要求人工确认，则走 Dashboard / review-confirm。 |
| Soft-deleted / Superseded | no | 任务仍为 active。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新，路径 `task_plan.md`
- Progress：见 `progress.md` 中 2026-07-06 15:31/15:45 记录
- 发现记录：已写入 `findings.md`
- Regression SSoT：新增/调整 `RG-012` dynamic workflow host runtime gate
- Lessons：checked-none: safety hardening captured as regression tests and docs rather than shared lesson
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

信心来自窄边界实现、deterministic JUnit 覆盖、docs-site type/build 验证、自审发现已闭环、以及 Regression SSoT/Cadence Ledger 中的固定门禁记录。发布前若要声明真实 provider E2E 或后台工作流能力，需要单独任务和 live-provider opt-in 证据。
