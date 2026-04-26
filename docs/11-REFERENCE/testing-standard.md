# Testing Standard

> Last updated: 2026-04-26

## Test Frameworks

| Surface | Framework / Tooling | Notes |
|---------|----------------------|-------|
| Java modules | JUnit 4 (`junit:junit:4.13.2`) | Primary baseline across the monorepo |
| Spring Boot starters | JUnit 4 + Spring Boot test support | Used for auto-configuration and controller/integration cases |
| HTTP/provider contracts | OkHttp `MockWebServer`, local fixtures, targeted contract tests | Prefer deterministic tests first |
| Persistence/runtime integration | H2 and local integration fixtures | Used in multiple runtime/starter tests |
| `docs-site/` | `npm run build`, `npm run typecheck` | Build-and-type safety rather than unit tests |
| `ai4j-flowgram-webapp-demo/` | `npm run lint`, `npm run ts-check`, `npm run build` | Current `npm test` is a stub |

## Test Directory Structure

| Path | Purpose |
|------|---------|
| `ai4j/src/test/java` | Core SDK and provider-facing tests |
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
- Core SDK: `mvn -pl ai4j -DskipTests=false test`
- Agent runtime: `mvn -pl ai4j-agent -DskipTests=false test`
- Coding runtime: `mvn -pl ai4j-coding -DskipTests=false test`
- CLI host: `mvn -pl ai4j-cli -DskipTests=false test`
- Core starter: `mvn -pl ai4j-spring-boot-starter -DskipTests=false test`
- FlowGram starter: `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test`

### Frontend And Docs

- Docs build: `npm run build` in `docs-site/`
- Docs typecheck: `npm run typecheck` in `docs-site/`
- Web demo lint: `npm run lint` in `ai4j-flowgram-webapp-demo/`
- Web demo typecheck: `npm run ts-check` in `ai4j-flowgram-webapp-demo/`
- Web demo build: `npm run build` in `ai4j-flowgram-webapp-demo/`

## Live-Provider Test Policy

- Prefer deterministic local tests for default regression gates
- Tests that need provider credentials or external services must keep secrets outside git
- When live-provider validation is required, record it as higher Evidence Depth in `docs/05-TEST-QA/Regression-SSoT.md`
- Do not silently rely on developer-local credentials; document the dependency in task progress and walkthrough output

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
