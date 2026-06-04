# 本地启动 / Local Setup

Context Doc Type: local-setup
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: medium

## Commands

| Task | Command | Expected Result | Source Evidence | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- |
| Full Java package smoke | `mvn -DskipTests package` | All Maven modules package without running tests. | `AGENTS.md`; `.github/workflows/java-regression.yml` | 2026-06-04 | high |
| Build one Java module with dependencies | `mvn -pl <module> -am -DskipTests package` | Selected module and dependencies package. | `AGENTS.md` | 2026-06-04 | high |
| Test one Java module | `mvn -pl <module> -DskipTests=false test` | Module JUnit 4 tests run. | `AGENTS.md`; `.github/workflows/java-regression.yml` | 2026-06-04 | high |
| Test one Java class | `mvn -pl <module> -Dtest=<ClassName> -DskipTests=false test` | One JUnit class runs. | `AGENTS.md` | 2026-06-04 | high |
| Docs-site build | `cd docs-site; npm run build` | Docusaurus static site builds. | `docs-site/package.json`; `.github/workflows/docs-build.yml` | 2026-06-04 | high |
| Docs-site typecheck | `cd docs-site; npm run typecheck` | TypeScript typecheck passes; use `NODE_OPTIONS=--max-old-space-size=8192` on Windows if needed. | `docs-site/package.json`; `docs/05-TEST-QA/Regression-SSoT.md` | 2026-06-04 | high |
| FlowGram web demo build | `cd ai4j-flowgram-webapp-demo; npm run build` | Rsbuild production bundle builds. | `ai4j-flowgram-webapp-demo/package.json` | 2026-06-04 | high |
| FlowGram web demo lint/typecheck | `cd ai4j-flowgram-webapp-demo; npm run lint; npm run ts-check` | Frontend lint and TypeScript checks pass. | `ai4j-flowgram-webapp-demo/package.json`; `docs/05-TEST-QA/Cadence-Ledger.md` | 2026-06-04 | high |
| Harness status | `npx --yes coding-agent-harness status --json .` | CLI reads v2 harness state. | `coding-agent-harness/harness.yaml`; init report 2026-06-04 | 2026-06-04 | high |
| Harness dashboard dev server | `npx --yes coding-agent-harness dev .` | Local read-only dashboard starts for daily review. | user instruction 2026-06-04 | 2026-06-04 | high |

## Environment

| Variable | Required | Purpose | Source Evidence |
| --- | --- | --- | --- |
| `JAVA_HOME` | required for Maven work | Java 8-compatible JDK for Maven build/test. | `pom.xml`; `.github/workflows/java-regression.yml` |
| `MAVEN_OPTS` | optional | Maven memory/proxy tuning when local environment needs it. | build tool convention |
| `NODE_OPTIONS=--max-old-space-size=8192` | conditional | Work around docs-site typecheck/build heap pressure on Windows. | `docs/05-TEST-QA/Regression-SSoT.md` |
| provider API keys such as `OPENAI_API_KEY`, `ZHIPU_API_KEY`, `MINIMAX_API_KEY` | conditional | Live-provider tests and demos; never commit secrets. | `AGENTS.md`; `ai4j-flowgram-demo/src/main/resources/application.yml` |
| `GITHUB_PAGES=true` | CI only | Docs Pages build behavior. | `.github/workflows/docs-pages.yml` |
