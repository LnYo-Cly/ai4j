# Extension plugin contract hardening - 审查

## 审查者身份（Reviewer Identity）

| Reviewer | Type | Scope |
| --- | --- | --- |
| Codex coordinator | self | extension API schema/name contract, CLI extension args/scaffold smoke, docs-site trust boundary, regression evidence |

## 审查范围

- 审查类型：adversarial / regression / docs-contract / security-boundary
- 范围内：`ai4j-extension-api` manifest/spec/validator/runtime state、`ai4j-cli` extension command parsing and scaffold test、extension docs-site pages、Regression SSoT/Cadence、task closeout。
- 范围外：remote marketplace、CLI 自动安装依赖、runtime jar hotload、provider 自动注册、command/Skill/Prompt/Guardrail 粒度 allowlist。
- 来源材料：task plan、diff、targeted and broad regression outputs、docs-site typecheck/build、package smoke、Regression SSoT/Cadence updates。

## Agent Review Submission（Agent 提交审查）

本节由 agent 或 coordinator 在审查材料包准备好时填写。它只表示“提交待审”，不表示人工批准。

| Field | Value |
| --- | --- |
| Submission ID | agent-self-review-2026-06-10 |
| Submitted At | 2026-06-10 local |
| Submitted By | Codex coordinator |
| Task Key | 2026-06-10-extension-plugin-contract-hardening-272a10c4 |
| Materials Checklist Hash | lifecycle-cli-pending |
| Evidence Summary | Extension API, agent extension adapter, Ask User plugin, CLI targeted scaffold smoke, docs-site typecheck/build, package smoke, and diff check are recorded in `progress.md`. |
| Open Findings Count | 0 |
| Scanner Version | manual-review-v1 |

### Material Checklist（材料清单）

| Material | Required? | Status | Evidence |
| --- | --- | --- | --- |
| Brief | yes | present | `brief.md` |
| Task plan | yes | present | `task_plan.md` |
| Progress and evidence | yes | present | `progress.md` |
| Visual map | yes | present | `visual_map.md` |
| Lesson candidate decision | yes | present | `lesson_candidates.md` |
| Walkthrough or closeout link | yes | present | `walkthrough.md` |

## 信心挑战（Confidence Challenge）

直接回答：你是否对当前计划、实现和策略有 100% 信心？

- Verdict：no
- 如果不是 100%，剩余漏洞或证据缺口：
  - 本轮没有实现非工具资源细粒度 allowlist；已明确作为 out-of-scope residual。
  - 人工 review confirmation 尚未由用户侧执行；agent 不能代办。
- Fix loop count：2
- 当前结论：可以提交 review；P2 findings 已修复，P3 权限模型扩大项已文档化并路由为后续设计空间。

## 重要发现（Material Findings，表头供 checker 解析）

| ID | Severity | Finding | Evidence Checked | Required Action | Open | Disposition | Blocks Release | Follow-up |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

## 非阻塞备注（Non-Material Notes）

- `ExtensionToolSchemaValidator` 是最小结构校验器，不是完整 JSON Schema 引擎。
- `enable(...)` 的非 tool 资源整包信任边界已经在 docs-site 中明确；后续要做 allowlist 需要单独 API / Spring / CLI 设计。

## 已检查证据（Evidence Checked）

| Evidence ID | Type | Path | Summary |
| --- | --- | --- | --- |
| E-001 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionManifest.java | Public extension/tool/command/resource/guardrail name validation split from generic `requireId`. |
| E-002 | diff | TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/validation/ExtensionToolSchemaValidator.java | New no-dependency JSON object/schema shape validator. |
| E-003 | diff | TARGET:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java | CLI rejects invalid extension args and generated scaffold compiles / loads through ServiceLoader. |
| E-004 | diff | TARGET:docs-site/docs/core-sdk/extension/plugin-packages.md | Docs now state `apply(...)` side-effect-light, strict names, schema validation, and enable trust boundary. |
| E-005 | command | TARGET:. | `mvn -pl ai4j-extension-api -DskipTests=false test` passed with 16 tests. |
| E-006 | command | TARGET:. | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` passed with 5 tests. |
| E-007 | command | TARGET:. | `mvn -pl ai4j-plugin-ask-user -am -DfailIfNoTests=false -DskipTests=false test` passed. |
| E-008 | command | TARGET:. | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 22 tests. |
| E-009 | command | TARGET:docs-site | `npm run typecheck` and `npm run build` passed. |
| E-010 | command | TARGET:. | `mvn -DskipTests package` passed across 11 reactor projects; `git diff --check` passed with CRLF warnings only. |
| E-011 | command | TARGET:. | `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` passed across 6 reactor projects with 514 tests. |

## 无重要发现声明

本轮已检查上述证据，未发现阻塞目标的重要发现。

## 残余风险

| Risk | Owner | Accepted? | Follow-up |
| --- | --- | --- | --- |
| 非工具资源仍无 command / Skill / Prompt / Guardrail 粒度 allowlist | maintainer | yes | 后续插件权限模型设计任务 |
| 人工 review confirmation 未由用户侧执行 | human | yes | 推送后由用户决定是否运行 `review-confirm` 或退回 |

## Lifecycle Queue Routing（生命周期队列路由）

| Queue | Applies? | Reason | Exit condition |
| --- | --- | --- | --- |
| Review | yes | Agent review packet 已准备，等待人工确认或退回。 | 人工确认或退回。 |
| Missing Materials | no | 任务包必需文件已补齐。 | n/a |
| Blocked | no | 无 open blocking finding；P3 allowlist 扩展为 accepted residual。 | n/a |
| Lessons | no | `lesson_candidates.md` 记录 no-candidate。 | 人工审查覆盖 no-candidate 判断时重新路由。 |
| Confirmed / Finalized | no | agent 未运行 human confirmation。 | 人工确认后再 closeout ledger。 |
| Soft-deleted / Superseded | no | 本任务仍为当前 active task。 | n/a |

## 后续路由（Follow-Up Routing）

- 任务计划：已更新，路径 `task_plan.md`。
- Progress：已记录验证和残余路由，路径 `progress.md`。
- 发现记录：已更新 `findings.md`。
- Regression SSoT：调整 RG-010/RG-011/RG-004/RG-007/RG-008；Cadence Ledger 新增 SRB-046。
- Lessons：checked-none: 本任务无新增可复用 harness lesson。
- 收口记录：`walkthrough.md`。

## 最终信心依据（Final Confidence Basis）

最终信心来自 extension API validator/registry tests、agent adapter targeted test、official plugin test、CLI scaffold smoke、完整 CLI 依赖链回归、docs-site typecheck/build、monorepo package smoke、diff check，以及对插件信任边界的 self adversarial review。人工确认仍是用户侧动作，不由 agent 代办。
