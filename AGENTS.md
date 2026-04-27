# Repository Guidelines

## Project Identity

- **Project**: `ai4j-sdk`
- **Repository type**: Java 8 Maven monorepo with adjacent docs and demo surfaces
- **Primary language**: Java
- **Build tool**: Maven
- **Frontend/docs surfaces**: `docs-site/` and `ai4j-flowgram-webapp-demo/`

## Monorepo Scope

Treat this repository as an 8-module monorepo plus docs/demo surfaces.

| Path | Role |
|------|------|
| `ai4j/` | Core SDK: provider access, Chat/Responses, RAG, MCP, vector, image, audio, realtime |
| `ai4j-agent/` | Agent runtime, workflow, trace, memory, subagent/team orchestration |
| `ai4j-coding/` | Coding-agent runtime, workspace-aware tools, outer loop, compaction |
| `ai4j-cli/` | CLI, TUI, ACP host, session/runtime integration |
| `ai4j-spring-boot-starter/` | Spring Boot auto-configuration for core SDK |
| `ai4j-flowgram-spring-boot-starter/` | FlowGram integration, task APIs, trace bridge, starter-side runtime support |
| `ai4j-flowgram-demo/` | Demo backend for FlowGram starter integration |
| `ai4j-bom/` | Version alignment BOM |
| `docs-site/` | Docusaurus documentation site |
| `ai4j-flowgram-webapp-demo/` | Web demo frontend surface |

## Hard Rules

1. Treat repository guidance as monorepo guidance. Do not plan or review work as if this were only `ai4j/` plus one starter.
2. Keep Java modules compatible with Java 8 unless a task explicitly upgrades the baseline.
3. Never hardcode secrets, provider keys, or local machine paths intended only for one developer. Use env vars or local config.
4. All non-trivial work must use the harness flow: task directory, Feature SSoT update, targeted regression, walkthrough closeout.
5. When a change adds or alters a fixed regression surface, update both `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md`.
6. Do not add new planning or progress files under repo root or legacy `docs/plans` / `docs/tasks`. New work belongs under `docs/09-PLANNING/`.
7. Preserve module boundaries. Core behavior belongs in SDK/runtime modules; starters wire configuration; demos and docs must not become the source of truth for production logic.
8. A feature is not considered closed until verification is recorded and a walkthrough exists in `docs/10-WALKTHROUGH/`.

## Repository Structure

### Production Code

- `ai4j/src/main/java`
- `ai4j-agent/src/main/java`
- `ai4j-coding/src/main/java`
- `ai4j-cli/src/main/java`
- `ai4j-spring-boot-starter/src/main/java`
- `ai4j-flowgram-spring-boot-starter/src/main/java`
- `ai4j-flowgram-demo/src/main/java`

### Tests

- Java tests live under each module's `src/test/java`
- Test framework is primarily JUnit 4
- Some suites are pure local tests; others touch live-provider or integration behavior

### Existing Documentation

- Harness-standard docs live under numbered `docs/` directories
- Legacy historical docs remain under:
  - `docs/plans/`
  - `docs/tasks/`
  - `docs/archive/`
- `AGENT.md` is an architecture note for the agent module, not the repo-wide harness charter

## Build And Test Commands

### Maven

- Full package: `mvn -DskipTests package`
- Build one module with dependencies: `mvn -pl <module> -am -DskipTests package`
- Test one module: `mvn -pl <module> -DskipTests=false test`
- Test one class: `mvn -pl <module> -Dtest=<ClassName> -DskipTests=false test`

### Common Modules

- Core SDK: `mvn -pl ai4j -DskipTests=false test`
- Agent runtime: `mvn -pl ai4j-agent -DskipTests=false test`
- Coding runtime: `mvn -pl ai4j-coding -DskipTests=false test`
- CLI host: `mvn -pl ai4j-cli -DskipTests=false test`
- Spring Boot starter: `mvn -pl ai4j-spring-boot-starter -DskipTests=false test`
- FlowGram starter: `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test`

### Frontend And Docs Surfaces

- `docs-site/`: prefer `npm run build` when docs-site content or config changes
- `ai4j-flowgram-webapp-demo/`: run the module-local frontend build/test command when this surface changes

## Coding Style And Constraints

- Java: 4-space indentation, PascalCase classes, lowerCamelCase methods/fields, UPPER_SNAKE_CASE constants
- Keep packages under `io.github.lnyocly.ai4j`
- Match surrounding style; do not do mechanical repo-wide reformatting
- Favor targeted edits over wide churn across modules

## Testing Notes

- JUnit dependency baseline is `junit:junit:4.13.2`
- Many provider-facing tests need external credentials or live endpoints; keep those secrets out of git
- Prefer the smallest regression command that covers the changed surface, then escalate per Cadence Ledger

## Branch And Worktree Naming

- Existing branch convention in this repo is:
  - `feature/<name>`
  - `fix/<name>`
  - `docs/<name>`
- Harness worktrees should mirror repo conventions:
  - `.worktrees/feature/<name>`
  - `.worktrees/fix/<name>`
  - `.worktrees/docs/<name>`
  - `.worktrees/refactor/<name>`
  - `.worktrees/test/<name>`

## Task-Type Reading Matrix

| Task type | Read first |
|-----------|------------|
| Core SDK / provider / MCP / RAG / vector / agentflow connector changes | `docs/11-REFERENCE/engineering-standard.md` |
| Agent runtime / workflow / trace / subagent changes | `AGENT.md` and `docs/11-REFERENCE/engineering-standard.md` |
| Coding runtime / CLI / TUI / ACP changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Spring Boot / FlowGram starter / demo integration changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Regression / smoke / verification work | `docs/11-REFERENCE/testing-standard.md` and `docs/05-TEST-QA/Regression-SSoT.md` |
| Planning / task tracking / SSoT maintenance | `docs/11-REFERENCE/docs-library-standard.md` and `docs/11-REFERENCE/execution-workflow-standard.md` |
| Walkthrough closeout | `docs/11-REFERENCE/walkthrough-standard.md` |
| Worktree setup / branch isolation / multi-agent coordination | `docs/11-REFERENCE/worktree-standard.md` |

## Harness Files

- **Feature SSoT**: `docs/09-PLANNING/Feature-SSoT.md`
- **Regression SSoT**: `docs/05-TEST-QA/Regression-SSoT.md`
- **Cadence Ledger**: `docs/05-TEST-QA/Cadence-Ledger.md`
- **Task template directory**: `docs/09-PLANNING/TASKS/_task-template/`
- **Walkthrough template**: `docs/10-WALKTHROUGH/_walkthrough-template.md`
- **Reference standards**: `docs/11-REFERENCE/`

## Execution Flow

1. Diagnose the changed surface and module scope.
2. For non-trivial work, create or update a task directory in `docs/09-PLANNING/TASKS/`.
3. Update the matching Feature SSoT entry before substantial implementation.
4. Decide whether the task requires an isolated worktree.
5. Implement in the narrowest correct module boundary.
6. Run targeted regression based on `docs/05-TEST-QA/Cadence-Ledger.md`.
7. Update Regression SSoT if gate status, scope, or evidence depth changes.
8. Write a walkthrough on closeout and capture residual items explicitly.

## Review Focus

When reviewing changes in this repo, prioritize:

1. Cross-module API breakage
2. Java 8 or starter compatibility regressions
3. Runtime / CLI / FlowGram behavioral regressions
4. Missing regression updates for newly touched surfaces
5. Drift between code changes and docs/reference guidance
