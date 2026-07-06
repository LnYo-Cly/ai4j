# Regression SSoT - ai4j-sdk

> Last updated: 2026-07-06
> Control tower for fixed regression surfaces in the `ai4j-sdk` monorepo.

## Regression Layers

| Layer | Purpose | Default Use | Credential / Network Policy | Evidence |
|-------|---------|-------------|-----------------------------|----------|
| local-required | deterministic local and CI baseline | required for touched surfaces and merge batches | no secrets, no real provider dependency, no developer-local credential assumptions | L1 tests or L2 local_smoke |
| live-provider-opt-in | real provider behavior and rate-limit-sensitive flows | only when provider/protocol/runtime behavior is the subject of the task or release | explicit human/operator approval; env vars or external secret store only; sanitized logs | L3 live |
| credential-release-opt-in | signing, publishing, deployment, or hosted-demo behavior | release candidate or deployment validation only | explicit operator approval; no local absolute paths or committed secrets | L3 live to L5 hard_gate |

Default task closeout should cite `local-required` evidence. If a task needs a live or credential gate, the task must record the reason, required env var names, command, sanitized evidence, and any skipped condition in its `progress.md` and walkthrough.

## Local Required Baseline

| ID | Status | Surface | Primary Entrypoint | Cadence | Evidence Depth | Last Verified | Notes |
|----|--------|---------|-------------------|---------|----------------|---------------|-------|
| RG-010 | 🟢 | extension API module | `mvn -pl ai4j-extension-api -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-05 pass, 26 tests | manifest model, ServiceLoader discovery, explicit enable/expose gates, runtime inspection snapshot, capability validation, extension resource registry contracts, strict public ID/name validation, `ExtensionValidator` authoring checks, explicit command/Skill/Prompt/Guardrail allowlist, activation plan fail-fast behavior, optional lifecycle hook registration/snapshot/inspection contracts, and strict plugin-classloader resource checks that do not fall back to thread/API classloaders |
| RG-011 | 🟢 | official Ask User plugin module | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-05 pass, 7 plugin tests plus extension API 26 tests | official sample plugin manifest version alignment, ServiceLoader discovery, validator contract, host-mediated `ask_user` tool envelope, 16 KiB `argumentsRaw` cap with `argumentsTruncated`, `ask-user` command envelope, Skill / Prompt resource packaging, and compatibility with the explicit resource activation API |
| RG-001 | 🟢 | core SDK module | `mvn -pl ai4j -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-06 RAG online judge pass, 138 tests | core provider adapters, RAG, MCP, vector, realtime, agentflow contract tests; includes `FirstChatCopyableCodeTest` for the full object-chain docs contract, RAG query planning/retrieval tests, and optional online RAG judge coverage via `RagOnlineEvaluatorTest`; provider-dependent tests are excluded from default runs by `LiveProviderTest` category |
| RG-002 | 🟢 | agent runtime module | `mvn -pl ai4j-agent -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-06 trace/replay error-structure pass, 212 agent tests | agent runtime, workflow, memory, trace, subagent/team orchestration, extension lifecycle hook dispatch, tool approval / permission policy, Agent Blueprint YAML loader / validator / AgentFactory, P2-A/P2-B Sandbox SPI and AgentSession binding, and P2-C Daytona SandboxProvider; 2026-07-06 streaming capture/replay now preserves `outputText` plus raw `outputs`, `TOOL_ERROR` now exposes `errorType`, and the full `mvn -pl ai4j-agent -am -DskipTests=false test` gate passed with extension API 26 tests, core 135 tests, and agent 212 tests; Daytona live smoke is tracked as LV-004 and remains opt-in |
| RG-012 | 🟢 | agent dynamic workflow host runtime | `mvn -pl ai4j-agent -am -Dtest=DynamicWorkflow*Test -DfailIfNoTests=false -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-06 pass, 11 tests | host-side parser and executor for `ai4j.dynamic_workflow.request` envelopes, Nashorn-backed `phase` / `log` / `agent` / `parallel` / `pipeline` primitives, max-agent and invalid-script failure paths, default no-Java host interop hardening, hidden raw bridge binding, and `DynamicWorkflowHostToolExecutor` pass-through/execution behavior |
| RG-003 | 🟢 | coding runtime module | `mvn -pl ai4j-coding -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-05 targeted pass, `CodingSkillSupportTest` 3 tests; broad 2026-06-20 P3 pass, 61 coding tests | coding runtime, tools, outer loop, checkpoint, shell/apply-patch, skill resources, strict extension Skill/Prompt classloader reads, subagent handoff consumption, and P3 `bash exec` sandbox routing; 2026-07-05 targeted `mvn -pl ai4j-coding -am -Dtest=CodingSkillSupportTest -DfailIfNoTests=false -DskipTests=false test` covered extension resource materialization after strict reads |
| RG-004 | 🟢 | CLI/TUI/ACP host | `mvn -pl ai4j-cli -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-07-05 targeted pass, `Ai4jCliTest` 30 tests; broad 2026-06-22 P4 pass, 298 CLI tests | terminal host, session runtime, ACP, rendering, provider/model command behavior with fake/local clients; includes extension runtime inspect output for `lifecycleHooks=`, strict extension resource reads, 2.4.0 plugin scaffold generation, P1-C Blueprint runner, and P4 sandbox runtime binding. 2026-07-05 targeted `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 30 tests |
| RG-005 | 🟢 | Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-10 targeted pass, 6 tests | auto-configuration and config binding; includes first-chat, AgentFlow, and `ExtensionAutoConfigurationTest` for `ai.extensions.enabled`, `ai.extensions.tools.expose`, `ai.extensions.explicit-resource-activation`, and command/Skill/Prompt/Guardrail allow configuration |
| RG-006 | 🟢 | FlowGram starter and task APIs | `mvn -pl ai4j-flowgram-spring-boot-starter -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass | FlowGram runtime facade, controller, task store, trace bridge; local starter/demo gates passed and remote Java regression run `27202972949` includes `ai4j-flowgram-spring-boot-starter` plus `ai4j-flowgram-demo` |
| RG-007 | 🟢 | monorepo package build | `mvn -DskipTests package` | PR, merge-batch, shared build change | L2 local_smoke | 2026-07-06 trace cost docs pass, 11 reactor projects | cross-module packaging and dependency alignment across root plus 10 modules, including extension API, official Ask User plugin, core SDK, agent, coding runtime, CLI, starters, FlowGram demo, and BOM; current trace cost docs task kept Java packaging compatible while avoiding a second pricing abstraction |
| RG-008 | 🟢 | docs-site build | `npm run typecheck`, then `npm run build` in `docs-site/` | touched-surface, docs PR/push, merge-batch | L2 local_smoke | 2026-07-06 trace cost docs pass, `npm ci` + typecheck/build | Fresh worktree lacked ignored `docs-site/node_modules`, so `npm ci` restored local dependencies; `npm run typecheck` and `npm run build` then passed and generated `docs-site/build`. Current trace docs now include a copyable `TracePricingResolver` example, explain per-million-token pricing units, and state that the SDK has no built-in price table because prices drift |
| RG-009 | 🟢 | FlowGram webapp demo build and test | `npm run test`, `npm run lint`, `npm run ts-check`, then `npm run build` in `ai4j-flowgram-webapp-demo/` | touched-surface, PR, merge-batch | L2 local_smoke | 2026-06-10 local and remote pass | `npm test` now runs deterministic backend workflow normalization tests before lint/type/build; local `npm run test`, `npm run lint`, `npm run ts-check`, and `npm run build` passed. GitHub Actions `flowgram-webapp-regression` run `27253773916` passed on `main@b0993f56` with `detect-webapp-changes`, `webapp-checks` steps `Test` / `Lint` / `Typecheck` / `Build`, and aggregate `flowgram-webapp-regression` all successful |

## Live And Credential Opt-in Gates

| ID | Status | Surface | Primary Entrypoint | Cadence | Evidence Depth | Last Verified | Notes |
|----|--------|---------|-------------------|---------|----------------|---------------|-------|
| LV-001 | 🟡 | core SDK real provider contracts | `mvn -pl ai4j -P live-provider-tests -Dtest=<ProviderTest> -DskipTests=false test` | opt-in on provider/protocol/release tasks | L3 live | 2026-06-04 profile-smoke-skipped-no-credentials | requires documented provider env vars such as `*_API_KEY`; never commit provider keys or reuse local defaults |
| LV-002 | 🟡 | agent and coding real provider orchestration | `mvn -pl ai4j-agent -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` or `mvn -pl ai4j-coding -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` | opt-in on agent/coding runtime provider tasks | L3 live | 2026-06-04 profile-smoke-skipped-no-credentials | covers live model, workflow, CodeAct, and team-delivery usage tests; rate limits and provider unavailability must be recorded as skipped or residual evidence |
| LV-003 | 🟡 | FlowGram demo end-to-end behavior | backend plus web demo/manual or browser-driven scenario | opt-in on FlowGram demo release or integration task | L4 browser_human_proxy | mapped-pending-runbook | requires a documented backend/frontend startup contract before it can become a fixed hard gate |
| LV-004 | 🟢 | real sandbox provider smoke | `mvn -pl ai4j-agent -am -P live-provider-tests -Dtest=<SandboxLiveTest> -DskipTests=false -DfailIfNoTests=false test` | opt-in on sandbox provider tasks | L3 live | 2026-06-21 Daytona smoke pass | Daytona live smoke used env-only credentials, created a disposable sandbox, executed `printf ai4j-daytona-ok`, and closed it; sanitized evidence is in `ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.daytona.DaytonaSandboxLiveSmokeTest.txt`; missing credentials should be recorded as opt-in skip, not local baseline failure |
| CR-001 | 🟡 | release signing and Central publishing | release profile dry run or operator-approved publish command | opt-in on release candidate only | L3 live to L5 hard_gate | mapped-pending-runbook | requires GPG and Central credentials outside git; use configurable `gpg.executable`, not developer-local absolute paths |

## Evidence Depth Legend

| Level | Name | Description |
|-------|------|-------------|
| L1 | tests | module-local automated tests |
| L2 | local_smoke | deterministic local build or smoke command |
| L3 | live | real provider or real demo/backend validation |
| L4 | browser_human_proxy | browser-driven interaction validation |
| L5 | hard_gate | structured pass/fail gate with non-zero exit semantics |

## Residual Items

| ID | Surface | Issue | Priority | Created |
|----|---------|-------|----------|---------|
| R-001 | monorepo CI | resolved 2026-06-09: remote Java regression run `27202972949` passed on `main@41ca7bd`; `main` and `dev` branch protection now require strict `java-regression` status check | P1 | 2026-04-26 |
| R-002 | live-provider validation | resolved 2026-06-04: live provider tests use `-P live-provider-tests`, `LiveProviderTest` category, and `docs/11-REFERENCE/testing-standard.md` runbook | P1 | 2026-04-26 |
| R-003 | FlowGram webapp demo | resolved 2026-06-10: frontend `test`, `test:cov`, and `watch` scripts are no longer placeholders; `npm test` runs backend workflow normalization/serialization tests and CI runs it before lint/type/build. Remote `flowgram-webapp-regression` run `27253773916` passed on `main@b0993f56` with `Test` / `Lint` / `Typecheck` / `Build` and the aggregate gate successful | P2 | 2026-04-26 |
| R-004 | docs-site build on Windows | Docusaurus build reaches bundle compilation but may fail during output/cache cleanup with `EPERM` file locks on generated artifacts | P2 | 2026-04-26 |
| R-005 | docs-site typecheck on Windows | resolved 2026-06-10: `docs-site/package.json` now bakes `node --max-old-space-size=8192` into `npm run typecheck` and `npm run build`; both commands pass without external `NODE_OPTIONS`, and docs-build run `27220942110` plus docs-pages run `27220942127` passed on `main@0df7094` | P2 | 2026-04-27 |
| R-006 | provider test hygiene | resolved 2026-06-04: provider/usage tests were audited for env-only credential reads, JUnit assumptions, and category isolation; remaining fake key hits are local unit fixtures | P1 | 2026-06-04 |
| R-007 | webapp demo CI | resolved 2026-06-09: `.github/workflows/flowgram-webapp-regression.yml` runs the FlowGram webapp lint/type/build baseline and `main` / `dev` branch protection now require strict `flowgram-webapp-regression` alongside `java-regression` | P2 | 2026-06-04 |
| R-008 | agent local regression | resolved 2026-06-09: `HandoffPolicy.FAIL` policy violations now propagate as fail-fast handoff errors instead of being converted into normal `TOOL_ERROR` outputs; `HandoffPolicyTest`, RG-002, RG-003, and RG-004 broad gates pass | P1 | 2026-06-04 |
| R-009 | CLI local regression | resolved 2026-06-09: direct CLI suite now passes; ACP no longer forwards loop-control auto-stop/block summaries as `agent_message_chunk`, and the JLine multiline transcript regression asserts visual text while allowing ANSI styling | P1 | 2026-06-09 |

## Status Legend

- 🟢 pass
- 🟡 mapped_or_partial
- 🔴 fail
- ⏸ paused
