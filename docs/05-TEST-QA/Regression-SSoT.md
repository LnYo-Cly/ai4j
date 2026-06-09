# Regression SSoT - ai4j-sdk

> Last updated: 2026-06-09
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
| RG-010 | 🟢 | extension API module | `mvn -pl ai4j-extension-api -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass, 12 tests | manifest model, ServiceLoader discovery, explicit enable/expose gates, runtime inspection snapshot, capability validation, extension resource registry contracts, and `ExtensionValidator` authoring checks |
| RG-011 | 🟢 | official Ask User plugin module | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass, 6 plugin tests plus extension API dependency tests | official sample plugin manifest, ServiceLoader discovery, validator contract, host-mediated `ask_user` tool envelope, `ask-user` command envelope, and Skill / Prompt resource packaging |
| RG-001 | 🟢 | core SDK module | `mvn -pl ai4j -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-07 pass | core provider adapters, RAG, MCP, vector, realtime, agentflow contract tests; includes `FirstChatCopyableCodeTest` for the full object-chain docs contract; provider-dependent tests are excluded from default runs by `LiveProviderTest` category |
| RG-002 | 🟢 | agent runtime module | `mvn -pl ai4j-agent -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass | agent runtime, workflow, memory, trace, subagent/team orchestration; R-008 closed after `HandoffPolicyTest` allowed-tools and max-depth failures were repaired; `mvn -pl ai4j-agent -am -DfailIfNoTests=false -DskipTests=false test` passed with extension API 12 tests, core 103 tests, and agent 74 tests |
| RG-003 | 🟢 | coding runtime module | `mvn -pl ai4j-coding -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass | coding runtime, tools, outer loop, checkpoint, shell/apply-patch, skill resources, and subagent handoff consumption; `mvn -pl ai4j-coding -am -DfailIfNoTests=false -DskipTests=false test` passed with extension API 12 tests, core 103 tests, agent 74 tests, and coding 59 tests |
| RG-004 | 🟢 | CLI/TUI/ACP host | `mvn -pl ai4j-cli -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass | terminal host, session runtime, ACP, rendering, provider/model command behavior with fake/local clients; R-009 direct CLI regression and upstream R-008 blocker are both closed, and `mvn -pl ai4j-cli -am -DfailIfNoTests=false -DskipTests=false test` passed through CLI with 261 CLI tests |
| RG-005 | 🟢 | Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | 2026-06-09 pass | auto-configuration and config binding; includes first-chat, AgentFlow, and `ExtensionAutoConfigurationTest` for `ai.extensions.enabled` / `ai.extensions.tools.expose` plugin configuration |
| RG-006 | 🟡 | FlowGram starter and task APIs | `mvn -pl ai4j-flowgram-spring-boot-starter -am -DskipTests=false test` | touched-surface, PR, merge-batch | L1 tests | ci-wired-pending-first-run | FlowGram runtime facade, controller, task store, trace bridge; local fixture tests are the default baseline |
| RG-007 | 🟢 | monorepo package build | `mvn -DskipTests package` | PR, merge-batch, shared build change | L2 local_smoke | 2026-06-09 pass | cross-module packaging and dependency alignment across 11 reactor projects: root plus 10 modules, including the official Ask User plugin module |
| RG-008 | 🟢 | docs-site build | `npm run typecheck`, then `npm run build` in `docs-site/` | touched-surface, docs PR/push, merge-batch | L2 local_smoke | 2026-06-09 pass | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `NODE_OPTIONS=--max-old-space-size=8192 npm run build` passed for plugin author cookbook and scaffold documentation updates |
| RG-009 | 🟡 | FlowGram webapp demo build | `npm run lint`, `npm run ts-check`, then `npm run build` in `ai4j-flowgram-webapp-demo/` | touched-surface, merge-batch | L2 local_smoke | bootstrap-mapped | current `npm test` is a stub; lint/type/build are the baseline until real tests exist |

## Live And Credential Opt-in Gates

| ID | Status | Surface | Primary Entrypoint | Cadence | Evidence Depth | Last Verified | Notes |
|----|--------|---------|-------------------|---------|----------------|---------------|-------|
| LV-001 | 🟡 | core SDK real provider contracts | `mvn -pl ai4j -P live-provider-tests -Dtest=<ProviderTest> -DskipTests=false test` | opt-in on provider/protocol/release tasks | L3 live | 2026-06-04 profile-smoke-skipped-no-credentials | requires documented provider env vars such as `*_API_KEY`; never commit provider keys or reuse local defaults |
| LV-002 | 🟡 | agent and coding real provider orchestration | `mvn -pl ai4j-agent -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` or `mvn -pl ai4j-coding -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test` | opt-in on agent/coding runtime provider tasks | L3 live | 2026-06-04 profile-smoke-skipped-no-credentials | covers live model, workflow, CodeAct, and team-delivery usage tests; rate limits and provider unavailability must be recorded as skipped or residual evidence |
| LV-003 | 🟡 | FlowGram demo end-to-end behavior | backend plus web demo/manual or browser-driven scenario | opt-in on FlowGram demo release or integration task | L4 browser_human_proxy | mapped-pending-runbook | requires a documented backend/frontend startup contract before it can become a fixed hard gate |
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
| R-001 | monorepo CI | Java PR workflow exists, but first green run and required branch protection are still pending | P1 | 2026-04-26 |
| R-002 | live-provider validation | resolved 2026-06-04: live provider tests use `-P live-provider-tests`, `LiveProviderTest` category, and `docs/11-REFERENCE/testing-standard.md` runbook | P1 | 2026-04-26 |
| R-003 | FlowGram webapp demo | frontend `test` scripts are placeholders, so build/lint/type gates are the current baseline only | P2 | 2026-04-26 |
| R-004 | docs-site build on Windows | Docusaurus build reaches bundle compilation but may fail during output/cache cleanup with `EPERM` file locks on generated artifacts | P2 | 2026-04-26 |
| R-005 | docs-site typecheck on Windows | default Node heap may OOM during `npm run typecheck`; current workaround is `NODE_OPTIONS=--max-old-space-size=8192` | P2 | 2026-04-27 |
| R-006 | provider test hygiene | resolved 2026-06-04: provider/usage tests were audited for env-only credential reads, JUnit assumptions, and category isolation; remaining fake key hits are local unit fixtures | P1 | 2026-06-04 |
| R-007 | webapp demo CI | RG-009 is mapped locally, but no dedicated CI workflow currently runs the FlowGram webapp lint/type/build baseline | P2 | 2026-06-04 |
| R-008 | agent local regression | resolved 2026-06-09: `HandoffPolicy.FAIL` policy violations now propagate as fail-fast handoff errors instead of being converted into normal `TOOL_ERROR` outputs; `HandoffPolicyTest`, RG-002, RG-003, and RG-004 broad gates pass | P1 | 2026-06-04 |
| R-009 | CLI local regression | resolved 2026-06-09: direct CLI suite now passes; ACP no longer forwards loop-control auto-stop/block summaries as `agent_message_chunk`, and the JLine multiline transcript regression asserts visual text while allowing ANSI styling | P1 | 2026-06-09 |

## Status Legend

- 🟢 pass
- 🟡 mapped_or_partial
- 🔴 fail
- ⏸ paused
