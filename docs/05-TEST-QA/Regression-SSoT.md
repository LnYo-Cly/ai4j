# Regression SSoT - ai4j-sdk

> Last updated: 2026-04-27
> Control tower for fixed regression surfaces in the `ai4j-sdk` monorepo.

## Active Fixed Gates

| ID | Status | Surface | Primary Entrypoint | Evidence Depth | Last Verified | Notes |
|----|--------|---------|-------------------|----------------|---------------|-------|
| RG-001 | 🟡 | core SDK module | `mvn -pl ai4j -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | core provider, RAG, MCP, vector, realtime surface; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-002 | 🟡 | agent runtime module | `mvn -pl ai4j-agent -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | agent runtime, workflow, memory, trace; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-003 | 🟡 | coding runtime module | `mvn -pl ai4j-coding -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | coding loop, tools, apply-patch, shell; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-004 | 🟡 | CLI/TUI/ACP host | `mvn -pl ai4j-cli -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | terminal host, session, ACP, rendering; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-005 | 🟡 | Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | auto-configuration and config binding; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-006 | 🟡 | FlowGram starter and task APIs | `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test` | L1 tests | ci-wired-pending-first-run | FlowGram runtime facade, controller, task store; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-007 | 🟡 | monorepo package build | `mvn -DskipTests package` | L2 local_smoke | ci-wired-pending-first-run | cross-module packaging and dependency alignment; automated on PRs via `.github/workflows/java-regression.yml` |
| RG-008 | 🟡 | docs-site build | `npm run build` | L2 local_smoke | 2026-04-27 partial | `npm run typecheck` passed with `NODE_OPTIONS=--max-old-space-size=8192`, and `npx docusaurus build --out-dir build-agent-coding-flowgram-solutions-verify` passed for the docs-site Waves 4-7 completion batch after the earlier Wave 1-3 verifies; the literal primary `npm run build` path was still not rerun in this cycle |
| RG-009 | 🟡 | FlowGram webapp demo build | `npm run build` | L2 local_smoke | bootstrap-mapped | pair with `npm run lint` and `npm run ts-check` when this surface changes |

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
| R-002 | live-provider validation | live provider suites rely on local credentials and are not yet normalized into explicit opt-in gates | P2 | 2026-04-26 |
| R-003 | FlowGram webapp demo | frontend `test` scripts are placeholders, so build/lint/type gates are the current baseline only | P2 | 2026-04-26 |
| R-004 | docs-site build on Windows | Docusaurus build reaches bundle compilation but may fail during output/cache cleanup with `EPERM` file locks on generated artifacts | P2 | 2026-04-26 |
| R-005 | docs-site typecheck on Windows | default Node heap may OOM during `npm run typecheck`; current workaround is `NODE_OPTIONS=--max-old-space-size=8192` | P2 | 2026-04-27 |

## Status Legend

- 🟢 pass
- 🟡 mapped_or_partial
- 🔴 fail
- ⏸ paused
