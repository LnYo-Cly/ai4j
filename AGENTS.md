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

## Harness Anything

- Use `ha` (Harness Anything) for all new project-management work.
- `harness/` is the active private HA ledger with its own nested Git repository. `.harness/` is generated projection/cache state. Neither belongs in code PRs; commit ledger changes inside `harness/` separately when they must be retained.
- Before substantive work, run `ha doctor --json`, ensure the project has been initialized with `ha init --name ai4j-sdk`, and inspect `ha status --json` / `ha check --profile target-project --strict --json`.
- HA writes require explicit actor attribution. Agents use `HARNESS_ACTOR=agent:<id>` or an explicit agent actor; humans use `ha --actor human:<id>`. Do not export a human actor for child processes. Configure `HARNESS_GIT_AUTHOR_NAME` and `HARNESS_GIT_AUTHOR_EMAIL` for local ledger commits.
- The normal write path is the daemon-backed CLI. `HARNESS_DAEMON_MODE=direct` is only for bootstrap, recovery, or isolated tests, and must carry `HARNESS_DIRECT_WRITE_REASON=recovery|test`.
- Complete work through the current HA lifecycle: claim an Execution, record progress/evidence, obtain a typed human review/consent when required, and run `ha task complete`. `ha task review` is legacy compatibility lint, not the approval gate.

## Hard Rules

1. Treat repository guidance as monorepo guidance. Do not plan or review work as if this were only `ai4j/` plus one starter.
2. Keep Java modules compatible with Java 8 unless a task explicitly upgrades the baseline.
3. Never hardcode secrets, provider keys, or local machine paths intended only for one developer. Use env vars or local config.
4. All non-trivial work must use the HA flow: a task under `harness/tasks/`, task-local progress/facts/evidence, targeted regression, typed review, and `ha task complete`.
5. When a change adds or alters a fixed regression surface, update both `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md`.
6. Do not add new planning, progress, review, or walkthrough files under repo root, legacy `docs/plans` / `docs/tasks`, `docs/09-PLANNING/`, or `docs/10-WALKTHROUGH/`. New task work belongs under the private `harness/tasks/` ledger; existing numbered docs remain tracked regression/history surfaces only.
7. Preserve module boundaries. Core behavior belongs in SDK/runtime modules; starters wire configuration; demos and docs must not become the source of truth for production logic.
8. A feature is not considered closed until verification is recorded in the HA task and `ha task complete <id>` succeeds.

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

- `docs/05-TEST-QA/Regression-SSoT.md` and `docs/05-TEST-QA/Cadence-Ledger.md` remain tracked regression governance and are updated when a fixed gate changes.
- `docs/11-REFERENCE/harness-anything-standard.md` is the tracked maintainer reference for the active HA boundary.
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
| Extension API / plugin ecosystem contract changes | `AGENTS.md`, `docs/11-REFERENCE/harness-anything-standard.md`, and `docs/11-REFERENCE/testing-standard.md` |
| Official plugin package changes | `AGENTS.md`, `docs/11-REFERENCE/harness-anything-standard.md`, and `docs/11-REFERENCE/testing-standard.md` |
| Core SDK / provider / MCP / RAG / vector / agentflow connector changes | `AGENTS.md` and `docs/11-REFERENCE/harness-anything-standard.md` |
| Agent runtime / workflow / trace / subagent changes | `AGENTS.md` and `docs/11-REFERENCE/harness-anything-standard.md` |
| Coding runtime / CLI / TUI / ACP changes | `AGENTS.md`, `docs/11-REFERENCE/harness-anything-standard.md`, and `docs/11-REFERENCE/testing-standard.md` |
| Spring Boot / FlowGram starter / demo integration changes | `AGENTS.md`, `docs/11-REFERENCE/harness-anything-standard.md`, and `docs/11-REFERENCE/testing-standard.md` |
| Regression / smoke / verification work | `docs/11-REFERENCE/testing-standard.md` and `docs/05-TEST-QA/Regression-SSoT.md` |
| Planning / task tracking / SSoT maintenance | `docs/11-REFERENCE/harness-anything-standard.md` and `AGENTS.md` |
| Walkthrough closeout | `docs/11-REFERENCE/harness-anything-standard.md` and the HA task `closeout.md` |
| Worktree setup / branch isolation / multi-agent coordination | `docs/11-REFERENCE/harness-anything-standard.md` and `ha worktree --help` |

## Harness Files

- **HA config**: `harness/harness.yaml`
- **HA task ledger**: `harness/tasks/<task-id>-<slug>/`
- **HA decisions/facts/context**: `harness/decisions/`, `harness/tasks/*/facts.md`, and `harness/context/`
- **HA local projection/cache**: `.harness/` (rebuildable; never the source of truth)
- **Historical Feature SSoT**: `docs/09-PLANNING/Feature-SSoT.md` remains summary/history only
- **Regression SSoT**: `docs/05-TEST-QA/Regression-SSoT.md`
- **Cadence Ledger**: `docs/05-TEST-QA/Cadence-Ledger.md`
- **Maintainer reference**: `docs/11-REFERENCE/harness-anything-standard.md`

### Governance Rule

- New tasks, facts, decisions, relations, reviews, and closeouts use HA under `harness/`.
- Do not add new `docs/09-PLANNING/TASKS/` or `docs/10-WALKTHROUGH/` records as a substitute for HA.
- Use `ha --help` and `ha capabilities --json` as the authoritative current command/schema reference; upstream README examples are orientation only.
- Do not hard-delete legacy task directories during migration; archive or supersede them only in a dedicated migration decision/task.

## Execution Flow

1. Run `ha doctor --json`, inspect `ha status --json`, and initialize/register the active HA workspace when needed.
2. Create and claim an HA task before substantive editing; use `ha worktree create --task <id>` when isolation is needed.
3. Record scope and meaningful progress with `ha task progress append`; promote durable observations to Facts and architectural choices to Decisions.
4. Implement in the narrowest correct module boundary.
5. Run targeted regression based on `docs/05-TEST-QA/Cadence-Ledger.md` and record command evidence in the HA task.
6. Update the tracked Regression SSoT/Cadence Ledger if a fixed gate or evidence scope changes.
7. Move the task to review, obtain typed human review/consent where required, and run `ha task complete <id>`.
8. Commit outer code/docs separately from the private `harness/` ledger; keep `.harness/` untracked and rebuildable.

## Review Focus

When reviewing changes in this repo, prioritize:

1. Cross-module API breakage
2. Java 8 or starter compatibility regressions
3. Runtime / CLI / FlowGram behavioral regressions
4. Missing regression updates for newly touched surfaces
5. Drift between code changes and docs/reference guidance
