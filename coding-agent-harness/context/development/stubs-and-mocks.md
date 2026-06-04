# Stub 与 Mock / Stubs and Mocks

Context Doc Type: stubs-and-mocks
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: medium

## Available Stubs

| External Service | Scenario | Stub Path | How To Run | Source Evidence | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| Provider HTTP APIs | Core SDK unit tests can use local mock HTTP behavior rather than live provider credentials. | `ai4j/pom.xml` includes `mockwebserver`; relevant tests under `ai4j/src/test/java` | Run targeted Maven tests, e.g. `mvn -pl ai4j -Dtest=<ClassName> -DskipTests=false test`. | `ai4j/pom.xml`; `ai4j/src/test/java` | 2026-06-04 | medium |
| FlowGram backend mock endpoints | Demo backend exposes mock endpoints for FlowGram demo behavior. | `ai4j-flowgram-demo/src/main/java/io/github/lnyocly/ai4j/flowgram/demo/FlowGramDemoMockController.java` | Start backend per `ai4j-flowgram-demo/README.md`, then call `/flowgram/demo/mock/**`. | `FlowGramDemoMockController.java`; `ai4j-flowgram-demo/README.md` | 2026-06-04 | high |
| FlowGram web demo initial schema/mock values | Frontend has initial workflow data and mock AI URL/API key fields for UI scenarios. | `ai4j-flowgram-webapp-demo/src/initial-data.ts` | Run frontend dev/build commands in `ai4j-flowgram-webapp-demo/`. | `initial-data.ts`; `package.json` | 2026-06-04 | medium |
| FlowGram web to backend proxy | Web demo proxies `/flowgram` to local backend `http://127.0.0.1:18080`. | `ai4j-flowgram-webapp-demo/rsbuild.config.ts` | Start backend on 18080, then start web demo. | `rsbuild.config.ts`; demo README | 2026-06-04 | high |
| Live provider credentials | Live suites/demos rely on env vars and must remain opt-in. | Environment variables, not committed files | Only run with explicit credentials and task scope. | `AGENTS.md`; `docs/05-TEST-QA/Regression-SSoT.md` | 2026-06-04 | high |
