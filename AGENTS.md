# Repository Guidelines

## Project Identity

- **Project**: `ai4j-sdk`
- **Repository type**: Java 8 Maven monorepo with adjacent docs and demo surfaces
- **Primary language**: Java
- **Build tool**: Maven
- **Frontend/docs surfaces**: `docs-site/` and `ai4j-flowgram-webapp-demo/`

## Monorepo Scope

Treat this repository as a 10-module monorepo plus docs/demo surfaces.

| Path | Role |
|------|------|
| `ai4j-extension-api/` | Lightweight public extension contract: manifest, ServiceLoader discovery, explicit enable/expose gates, extension resources |
| `ai4j-plugin-ask-user/` | Official sample plugin package: host-mediated user clarification tool, command, Skill, and Prompt |
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
4. All non-trivial work must use the harness flow: task directory under `coding-agent-harness/planning/tasks/`, task-local SSoT/progress/review updates, targeted regression, and task-local walkthrough closeout.
5. When a change adds or alters a fixed regression surface, update both `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md`.
6. Do not add new planning, progress, review, or walkthrough files under repo root, legacy `docs/plans` / `docs/tasks`, `docs/09-PLANNING/`, or `docs/10-WALKTHROUGH/`. New task work belongs under `coding-agent-harness/planning/tasks/`.
7. Preserve module boundaries. Core behavior belongs in SDK/runtime modules; starters wire configuration; demos and docs must not become the source of truth for production logic.
8. A feature is not considered closed until verification is recorded and a task-local walkthrough exists in `coding-agent-harness/planning/tasks/<task>/walkthrough.md`.

## Repository Structure

### Production Code

- `ai4j/src/main/java`
- `ai4j-extension-api/src/main/java`
- `ai4j-plugin-ask-user/src/main/java`
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

- Historical harness-standard docs remain under numbered `docs/` directories
- The v2 CLI harness scaffold lives under `coding-agent-harness/`; use it as the default location for new task packages, dashboard/context/governance projections, review packets, and walkthrough closeouts
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
- Extension API: `mvn -pl ai4j-extension-api -DskipTests=false test`
- Ask User plugin: `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test`
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
| Extension API / plugin ecosystem contract changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Official plugin package changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Core SDK / provider / MCP / RAG / vector / agentflow connector changes | `docs/11-REFERENCE/engineering-standard.md` |
| Agent runtime / workflow / trace / subagent changes | `AGENT.md` and `docs/11-REFERENCE/engineering-standard.md` |
| Coding runtime / CLI / TUI / ACP changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Spring Boot / FlowGram starter / demo integration changes | `docs/11-REFERENCE/engineering-standard.md` and `docs/11-REFERENCE/testing-standard.md` |
| Regression / smoke / verification work | `docs/11-REFERENCE/testing-standard.md` and `docs/05-TEST-QA/Regression-SSoT.md` |
| Planning / task tracking / SSoT maintenance | `docs/11-REFERENCE/docs-library-standard.md` and `docs/11-REFERENCE/execution-workflow-standard.md` |
| Walkthrough closeout | `docs/11-REFERENCE/walkthrough-standard.md` |
| Worktree setup / branch isolation / multi-agent coordination | `docs/11-REFERENCE/worktree-standard.md` |

## Harness Files

- **Harness v2 config**: `coding-agent-harness/harness.yaml`
- **Harness v2 context**: `coding-agent-harness/context/`
- **Harness v2 regression projection**: `coding-agent-harness/governance/regression/`
- **Harness v2 generated metadata**: `coding-agent-harness/governance/generated/`
- **Feature SSoT**: `docs/09-PLANNING/Feature-SSoT.md` is historical/summary only; new task truth lives in `coding-agent-harness/planning/tasks/<task>/`
- **Regression SSoT**: `docs/05-TEST-QA/Regression-SSoT.md`
- **Cadence Ledger**: `docs/05-TEST-QA/Cadence-Ledger.md`
- **Task package directory**: `coding-agent-harness/planning/tasks/`
- **Walkthrough template**: task-local `walkthrough.md` generated by the harness task package
- **Reference standards**: `docs/11-REFERENCE/`

### Harness v2 Transition Rule

- During the transition, numbered `docs/` files remain historical SSoT for already-existing planning records, regression history, and archived walkthroughs.
- `coding-agent-harness/` is the default SSoT for new task packages, review state, closeout walkthroughs, dashboard context, and generated governance metadata.
- Do not create new `docs/09-PLANNING/TASKS/` or `docs/10-WALKTHROUGH/` files unless an explicit migration task says to preserve or repair historical numbered-docs material.
- Use `npx --yes coding-agent-harness <command> .` for v2 CLI operations unless a task explicitly allows another installed harness binary.
- Do not hard-delete legacy task directories during migration; archive or supersede with explicit owner/action/status.

## Execution Flow

1. Diagnose the changed surface and module scope.
2. For non-trivial work, create or update a task directory in `coding-agent-harness/planning/tasks/`.
3. Update the matching task package and, only when needed for historical summary, the Feature SSoT entry before substantial implementation.
4. Decide whether the task requires an isolated worktree.
5. Implement in the narrowest correct module boundary.
6. Run targeted regression based on `docs/05-TEST-QA/Cadence-Ledger.md`.
7. Update Regression SSoT if gate status, scope, or evidence depth changes.
8. Write or update the task-local `walkthrough.md` on closeout and capture residual items explicitly.

## Review Focus

When reviewing changes in this repo, prioritize:

1. Cross-module API breakage
2. Java 8 or starter compatibility regressions
3. Runtime / CLI / FlowGram behavioral regressions
4. Missing regression updates for newly touched surfaces
5. Drift between code changes and docs/reference guidance
