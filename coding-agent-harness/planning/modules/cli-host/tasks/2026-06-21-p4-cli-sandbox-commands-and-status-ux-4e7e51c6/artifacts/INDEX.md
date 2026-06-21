# Artifacts Index - P4 CLI sandbox commands and status UX

| ID | 类型 | 路径 | 状态 | 摘要 |
| --- | --- | --- | --- | --- |
| A-001 | command | TARGET:. | done | `git status --short --branch --untracked-files=all` confirmed work happened on `docs/agent-final-roadmap-record` with P4 CLI/task files dirty before closeout. |
| A-002 | command | TARGET:ai4j-cli | pass | `mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest,CliSandboxCommandTest,CliSandboxSessionResolverTest,CodingCliSessionRunnerSandboxTest" -DskipTests=false -DfailIfNoTests=false test` passed with 61 tests. |
| A-003 | command | TARGET:ai4j-cli | pass | `mvn -pl ai4j-cli -am -DskipTests=false test` passed with extension API 25, core 103, agent 124, coding 61, and CLI 298 tests. |
| A-004 | command | TARGET:docs-site | not-required | Docs-site content/config was not touched in this P4 slice; no docs-site build required. |
| A-005 | command | TARGET:. | pass-with-dirty-warning | `npx --yes coding-agent-harness status --json .` returned failures=0, warnings=1 dirty-state before commit; current task `materialsReady=true`, `reviewSubmitted=true`, `reviewQueueState=ready-to-confirm`. |
| A-006 | live-provider-opt-in | TARGET:ai4j-agent | skipped-no-env | Env presence check only: `DAYTONA_API_KEY=False`, `DAYTONA_API_URL=False`, `E2B_API_KEY=False`; no secret value printed, live rerun skipped. |
| A-007 | report | TARGET:docs/05-TEST-QA/Regression-SSoT.md | done | RG-004 updated to 2026-06-22 P4 pass with targeted/broad CLI evidence. |
| A-008 | report | TARGET:docs/05-TEST-QA/Cadence-Ledger.md | done | SRB-059 added for P4 CLI sandbox UX. |
