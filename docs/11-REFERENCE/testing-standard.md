# Testing Standard

> Last updated: 2026-06-08

## Test Frameworks

| Surface | Framework / Tooling | Notes |
|---------|----------------------|-------|
| Java modules | JUnit 4 (`junit:junit:4.13.2`) | Primary baseline across the monorepo |
| Spring Boot starters | JUnit 4 + Spring Boot test support | Used for auto-configuration and controller/integration cases |
| HTTP/provider contracts | OkHttp `MockWebServer`, local fixtures, targeted contract tests | Prefer deterministic tests first |
| Persistence/runtime integration | H2 and local integration fixtures | Used in multiple runtime/starter tests |
| `docs-site/` | `npm run build`, `npm run typecheck` | Build-and-type safety rather than unit tests |
| `ai4j-flowgram-webapp-demo/` | `npm run lint`, `npm run ts-check`, `npm run build` | Current `npm test` is a stub |

## Regression Layers

| Layer | When To Use | Contract |
|-------|-------------|----------|
| Local required baseline | every non-trivial task that touches an executable surface | deterministic, no provider credentials, no real external service dependency, safe for local and CI |
| Live provider opt-in | provider/protocol/runtime behavior cannot be proven with local doubles | explicit approval, env-only credentials, sanitized evidence, rate-limit/provider-failure notes |
| Credential release opt-in | signing, publishing, deployment, hosted demo, or browser-human proxy evidence | explicit operator approval, no committed secrets, command and environment assumptions recorded |

Tasks should close on the smallest local-required gate that covers the changed surface. Escalate to live or credential gates only when the task goal explicitly requires behavior that local tests cannot prove.

## Test Directory Structure

| Path | Purpose |
|------|---------|
| `ai4j/src/test/java` | Core SDK and provider-facing tests |
| `ai4j-extension-api/src/test/java` | Extension manifest, discovery, enable/expose, and registry contract tests |
| `ai4j-agent/src/test/java` | Agent runtime, memory, workflow, trace tests |
| `ai4j-coding/src/test/java` | Coding runtime, tools, shell/apply-patch tests |
| `ai4j-cli/src/test/java` | CLI, TUI, ACP, session, rendering tests |
| `ai4j-spring-boot-starter/src/test/java` | Starter auto-configuration tests |
| `ai4j-flowgram-spring-boot-starter/src/test/java` | FlowGram starter and controller/integration tests |

## Naming Conventions

- Java test classes: `*Test.java`
- Java test methods: describe behavior, not implementation detail
- Frontend/docs commands: use existing package scripts instead of ad-hoc shell commands

## Coverage Expectations

- Public API or protocol changes must add or update tests in the narrowest owning module
- Cross-module behavior changes must verify both the owner module and the first consuming surface
- Starter changes should cover both bean wiring and one realistic integration path
- CLI/TUI behavior changes should prefer targeted unit/integration tests before manual smoke
- Docs-site and web-demo changes must at minimum pass their local build/type gates

## Preferred Regression Entrypoints

### Java Modules

- Full package: `mvn -DskipTests package`
- Extension API: `mvn -pl ai4j-extension-api -DskipTests=false test`
- Core SDK: `mvn -pl ai4j -am -DskipTests=false test`
- Agent runtime: `mvn -pl ai4j-agent -am -DskipTests=false test`
- Coding runtime: `mvn -pl ai4j-coding -am -DskipTests=false test`
- CLI host: `mvn -pl ai4j-cli -am -DskipTests=false test`
- Core starter: `mvn -pl ai4j-spring-boot-starter -am -DskipTests=false test`
- FlowGram starter: `mvn -pl ai4j-flowgram-spring-boot-starter -am -DskipTests=false test`

Use `-am` for clean-checkout local and CI verification so upstream module dependencies are built with the tested module. A narrower command without `-am` is acceptable only when the required upstream artifacts are already known to be current.

### Frontend And Docs

- Docs build: `npm run build` in `docs-site/`
- Docs typecheck: `npm run typecheck` in `docs-site/`
- Web demo lint: `npm run lint` in `ai4j-flowgram-webapp-demo/`
- Web demo typecheck: `npm run ts-check` in `ai4j-flowgram-webapp-demo/`
- Web demo build: `npm run build` in `ai4j-flowgram-webapp-demo/`

## Live-Provider Test Policy

- Prefer deterministic local tests for default regression gates
- Tests that need provider credentials or external services must keep secrets outside git and must not provide embedded/default credential values
- Credential-dependent tests must use explicit env vars or an external secret store and skip cleanly when credentials are absent
- Default Maven test runs exclude `io.github.lnyocly.ai4j.test.LiveProviderTest`
- Run live-provider tests only with `-P live-provider-tests`, for example:
  - `mvn -pl ai4j -P live-provider-tests -Dtest=<ProviderTest> -DskipTests=false test`
  - `mvn -pl ai4j-agent -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test`
  - `mvn -pl ai4j-coding -P live-provider-tests -Dtest=<LiveTest> -DskipTests=false test`
- When live-provider validation is required, record it as `live-provider-opt-in` or `credential-release-opt-in` with higher Evidence Depth in `docs/05-TEST-QA/Regression-SSoT.md`
- Do not silently rely on developer-local credentials; document env var names, provider/model assumptions, command, result, and skip/failure reason in task progress and walkthrough output
- Live-provider failures caused by rate limits, quota, provider outage, or missing credentials are not local baseline failures; classify them as opt-in residuals unless the task explicitly made live behavior the release gate

## Regression Control

- Fixed gates are tracked in `docs/05-TEST-QA/Regression-SSoT.md`
- Trigger rules are tracked in `docs/05-TEST-QA/Cadence-Ledger.md`
- New user-facing or integration-facing surfaces must be mapped into both files

## Test Authoring Principles

1. Keep tests deterministic by default; isolate live-provider checks from normal regression.
2. Prefer the narrowest owning module for first-line verification.
3. Use local doubles, fixtures, `MockWebServer`, or in-memory stores before real external dependencies.
4. One failure should explain the broken contract clearly enough for the next agent to continue.
5. When a bug spans modules, add at least one regression at the lowest module that can prevent recurrence.
