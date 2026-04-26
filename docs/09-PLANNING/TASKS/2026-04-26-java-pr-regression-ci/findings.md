# Java PR Regression CI - Findings

## Discoveries

### Harness docs were still ignored by git

- Why it mattered: repo-level workflow governance is not durable if the SSoT and `AGENTS.md` files never enter version control
- What was found: `.gitignore` ignored both `AGENTS.md` and the entire `docs/` tree, so the newly added harness files were invisible to `git status`
- Impact on plan: whitelist only the harness-oriented `docs/` subtrees and `AGENTS.md`, while leaving legacy ignored paths untouched

### Java test surfaces are mostly deterministic but not uniformly pure

- Why it mattered: first-phase PR CI should avoid becoming a high-noise gate
- What was found: the main Java modules already have stable JUnit suites, but some tests in `ai4j`, `ai4j-agent`, and `ai4j-coding` contain `Assume`-guarded live/provider paths or optional runtime requirements
- Impact on plan: keep the first workflow limited to deterministic PR-safe Maven gates and document the live-provider residual explicitly

### Monorepo packaging and module tests need separate visibility

- Why it mattered: a root package pass does not localize module-specific failures well
- What was found: the repo needs both a monorepo package smoke and a module-level failure map
- Impact on plan: split the workflow into `package-smoke` and a `module-tests` matrix with `fail-fast: false`

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Harness file tracking | whitelist harness paths in `.gitignore` | version the new harness without suddenly unignoring all legacy docs | remove `docs/` ignore entirely |
| Trigger strategy | PR only for `dev` / `main` | lowest-noise first step | PR + push |
| Scope | Java-only first phase | highest value with lowest complexity | include docs-site/web-demo now |
| Execution model | package smoke + matrix module tests | preserves monorepo view and module localization | single serial job |
