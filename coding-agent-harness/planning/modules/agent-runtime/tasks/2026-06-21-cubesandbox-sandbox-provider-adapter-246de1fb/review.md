# CubeSandbox sandbox provider adapter - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | CubeSandbox adapter implementation, docs, regression governance, material completeness |
| Confucius (`019ee672-860b-7593-9738-d3fd17d84b39`) | subagent / read-only | Java 8 compatibility, CubeSandbox protocol alignment, secret/header safety, test coverage, docs/governance claims |

## 审查范围

- 审查类型：adversarial / security / regression / architecture / docs-readiness。
- 范围内：`ai4j-agent` CubeSandbox provider package、CubeSandbox protocol tests、live opt-in test hook、docs-site CubeSandbox Provider page/sidebar/Sandbox SPI cross-link、Regression SSoT/Cadence Ledger、本任务材料。
- 范围外：`ai4j-coding` 全量 file/git/browser routing、files API、Jupyter/code-interpreter API、snapshot/rollback、browser capability、云端 runner 托管平台、真实 CubeSandbox 集群部署运维。
- 来源材料：当前 diff、CubeSandbox 官方 `openapi.yml` / Go SDK / Python SDK / CubeAPI AgentHub source、Maven test output、docs build output、Harness status、subagent read-only review report。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | pending-task-review |
| Submitted At | pending-task-review |
| Submitted By | agent |
| Task Key | MODULES/agent-runtime/2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb |
| Materials Checklist Hash | pending-task-review |
| Evidence Summary | CubeSandbox provider adapter implemented with protocol-level local HTTP/server tests, docs-site page, regression governance, secret/header safety fixes, and live opt-in pending-env evidence. |
| Open Findings Count | 0 |
| Scanner Version | pending-task-review |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` records targeted/broad/docs/diff/Harness/live evidence |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md`: checked-none for task-specific provider adapter work |
| Walkthrough or closeout link | yes | present | `walkthrough.md` prepared for closeout |

Scanner 会根据必需文件、章节、证据和这个严格提交块派生 `materialsReady`。如果材料未齐，任务应进入缺材料队列，而不是人工审查确认队列。
如果存在开放的 P0/P1/P2 阻塞发现，任务应进入阻塞队列，而不是人工审查确认队列。

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：yes for this task scope; no for out-of-scope full coding-agent sandbox product.
- 如果不是 100%，剩余漏洞或证据缺口：真实 CubeSandbox live smoke 仍因当前 shell 缺少 `AI4J_CUBESANDBOX_LIVE/CUBE_API_URL/CUBE_TEMPLATE_ID` 而 pending-env；`proxyNodeIp + https`、files/Jupyter/snapshot/browser、`ai4j-coding` tool routing 均明确为后续任务。
- Fix loop count：3（初版 adapter -> subagent 协议/安全审查 -> envdPort/header safety/metadata 收紧 -> final validation/material closeout）。
- 当前结论：可以提交进入 Agent Review Submission；本任务只承诺命令级 CubeSandbox provider，不夸大为完整云端 coding-agent 沙箱。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| MF-001 | P1 | envd 数据面端口存在官方实现差异：Go SDK 当前 `newEnvdRequest` 使用 `JupyterPort=49999`，而 CubeAPI AgentHub/Python SDK/envd/files/BYOI 文档使用 `49983`。初版 adapter 只硬编码 49983，可能无法覆盖所有部署。 | subagent review; CubeSandbox `sdk/go/envd.go`; `sdk/go/sandbox.go`; `sdk/python/cubesandbox/_commands.py`; `CubeAPI/src/handlers/agenthub.rs`; local tests | 不硬编码单一路径：新增 `CUBE_ENVD_PORT` / `spec.config.envdPort` / builder `envdPort`，默认保留 envd 文档端口 49983，并新增 49999 override regression。 | no | mitigated | no | 后续 live env 应按实际模板设置 `CUBE_ENVD_PORT` 后跑 `CubeSandboxLiveProviderTest`。 |
| MF-002 | P1 | raw socket HTTP proxy-node path 如果直接拼接 Host/token，可能被 remote domain/token/spec domain 中的 CR/LF 注入 header。 | subagent review; `CubeSandboxClient.dataRequestHeaders`; `CubeSandboxRemote.host`; `CubeSandboxProviderTest.invalidRemoteDomainShouldNotReachRawHostHeader` | 增加 virtual host part 校验、header value CR/LF 拒绝、invalid domain 测试；控制面 Bearer header 也走安全检查。 | no | closed | no | `proxyNodeIp + https` 后续实现时继续保持 header/SNI 校验。 |
| MF-003 | P1 | docs-site 新页面位于被 `.gitignore` 忽略的 `docs-site/docs/**`，若不强制 add，sidebar 会引用未提交页面。 | `git check-ignore -v -- docs-site/docs/agent/cubesandbox-provider.md`; `docs-site/sidebars.ts` | 最终 staging 使用 `git add -f docs-site/docs/agent/cubesandbox-provider.md`；walkthrough 和提交说明明确记录。 | no | mitigated | no | 提交前复查 `git status --short` 中该文件为 staged。 |
| MF-004 | P2 | 初版会把 `workspaceId` 写入 CubeSandbox metadata，并把 `apiUrl/proxyNodeIp` 写入 session labels，可能扩大本地路径或内部拓扑暴露。 | subagent review; `CubeSandboxProvider`; `CubeSandboxConfig.safeConfigLabels`; updated tests | 移除远端 metadata 中的 `ai4jWorkspaceId`；session labels 不再投影 `apiUrl/proxyNodeIp`；测试断言这些 key 不出现。 | no | closed | no | 后续如要更多 diagnostic labels，必须 opt-in 且过滤敏感/拓扑信息。 |

允许的 `Severity`：`P0`, `P1`, `P2`, `P3`。
允许的 `Open`：`yes`, `no`。
允许的 `Disposition`：`open`, `mitigated`, `closed`, `deferred`, `accepted-risk`, `not-reproducible`, `out-of-scope`。
允许的 `Blocks Release`：`yes`, `no`。

## 非阻塞备注（Non-Material Notes）

- Java 8 静态兼容性未发现 Java 9+ API；Maven 编译使用 target/source 1.8 完成。
- 本地协议级测试不是 mock Java 方法：它启动 HTTP server，实际解析 create/connect/delete 请求、raw socket Host、Connect envelope 与 stdout/stderr/error frame。
- 当前 live test 是真实 opt-in hook，但本 shell 缺少 live CubeSandbox env vars，结果只能记录 skipped/pending-env。
- `proxyNodeIp + https` 直连未实现，当前抛明确异常；这比静默使用错误 TLS/SNI 更安全。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | review | TARGET:subagent-notification/019ee672-860b-7593-9738-d3fd17d84b39 | Read-only adversarial review found envd port, raw header injection, ignored docs page, and metadata/labels concerns; all blocking items were fixed or mitigated in code/docs/materials. |
| E-002 | command | TARGET:. | `mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test` passed with 8 tests after envdPort/header/metadata fixes. |
| E-003 | command | TARGET:. | `mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest,AgentSandboxSpiModelTest,AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` planned/final gate for CubeSandbox + SPI binding. |
| E-004 | command | TARGET:. | `mvn -pl ai4j-agent -am -DskipTests=false test` planned/final broad agent gate. |
| E-005 | command | TARGET:docs-site | `npm --prefix docs-site run build` planned/final docs-site gate; new page must be force-added because ignored by `.gitignore`. |
| E-006 | command | TARGET:. | `mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test` expected to skip unless live env vars are present; current shell env probe shows required vars absent. |

## 无重要发现声明

本轮已检查上述证据，未发现仍开放且阻塞本任务目标的重要发现。已关闭/缓解的问题均有代码或流程证据；live CubeSandbox 真实执行未发生，作为 LV-002 opt-in residual 记录，不冒充通过。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 当前 shell 缺少真实 CubeSandbox endpoint/template/key，无法完成 L3 live execution。 | user/operator | yes | 设置 `AI4J_CUBESANDBOX_LIVE=true`、`CUBE_API_URL`/`E2B_API_URL`、`CUBE_TEMPLATE_ID`、必要 key/proxy/envdPort 后运行 `CubeSandboxLiveProviderTest`。 |
| `proxyNodeIp + https` 直连未实现。 | coordinator | yes | 后续 CubeSandbox provider hardening 任务设计 TLS/SNI/Host preservation。 |
| files/Jupyter/snapshot/browser/coding tool routing 不在本任务范围。 | coordinator | yes | 后续 sandbox expansion / `ai4j-coding` routing 任务。 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | 实现、文档、回归治理和审查材料补齐后可执行 `task-review` 等待人工确认。 | 人工确认或退回。 |
| Missing Materials | no | 必需材料已补齐；`walkthrough.md`、`review.md`、`lesson_candidates.md` 已去模板占位。 | n/a |
| Blocked | no | 无 open P0/P1/P2 blocking finding；live env 缺失按 opt-in residual 处理。 | n/a |
| Lessons | no | `lesson_candidates.md` 判定本任务无新增通用治理 lesson。 | n/a |
| Confirmed / Finalized | no | 尚未人工确认；closeout/ledger 仍等待 review/merge lifecycle。 | 人工确认、PR CI/merge、task complete。 |
| Soft-deleted / Superseded | no | 任务仍有效。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：无需扩大 scope；envdPort/header/metadata 收紧已纳入当前任务。
- Progress：见 `progress.md` 的 final validation 条目。
- 发现记录：`findings.md` 已记录端口差异、secret/header 边界、pending-env。
- Regression SSoT：已新增 CubeSandbox targeted regression 与 LV-002 opt-in live hook。
- Lessons：checked-none: task-specific-cubesandbox-provider-adapter
- 收口记录：`walkthrough.md`

## 最终信心依据（Final Confidence Basis）

信心来自：独立 subagent 对协议/安全/材料的对抗性审查、blocking findings 的代码级修复、协议级 HTTP server regression、Java 8 Maven 编译、docs-site build、diff/secret/Harness 检查，以及对 live-provider pending-env 的诚实记录。当前信心只覆盖命令级 CubeSandbox Provider；不覆盖完整云端 coding-agent 产品能力。

## Agent Review Submission

| Field | Value |
| --- | --- |
| Submission ID | ARS-202606201938 |
| Submitted At | 2026-06-20 19:38 |
| Submitted By | agent |
| Task Key | MODULES/agent-runtime/2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb |
| Materials Checklist Hash | c95e45745e1f4c74 |
| Evidence Summary | CubeSandbox sandbox provider adapter ready for review |
| Open Findings Count | 0 |
| Scanner Version | task-scanner/2026-05-25-phase-kind |
| Target | TARGET:coding-agent-harness/planning/modules/agent-runtime/tasks/2026-06-21-cubesandbox-sandbox-provider-adapter-246de1fb |
