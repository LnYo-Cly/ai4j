# Java PR Regression CI Walkthrough

## Overview

Added the first repo-level GitHub Actions workflow for deterministic Java regression on PRs targeting `dev` or `main`.

## Scope

- Modules changed: repository workflow/governance only; no production Java module behavior changed
- Files added: `.github/workflows/java-regression.yml`, task records, walkthrough
- Files removed: none
- External surfaces touched: GitHub Actions workflow orchestration only

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Trigger strategy | PR-only for `dev` / `main` | lowest-noise first phase |
| Regression scope | Java-only | highest value without pulling in frontend/docs noise |
| Execution shape | root package smoke + 6-module matrix tests | preserves monorepo integration signal and module-level failure localization |
| Maven module commands | `-pl <module> -am -DskipTests=false test` | each matrix job runs on a fresh runner and must bring required reactor dependencies |
| Harness tracking fix | whitelist harness paths in `.gitignore` | the new `docs/` harness and `AGENTS.md` were otherwise invisible to git |

## Verification

- Commands run:
  - `rg` scan for env/live-provider assumptions in Java test modules
  - `git check-ignore -v ...` for harness docs
  - `python -c "import pathlib; import yaml; yaml.safe_load(...); print('YAML_OK')"`
  - `git status --short --untracked-files=all`
- Regression gates: `RG-001` to `RG-007` are now wired to the PR workflow
- Result: workflow file and governance mapping are in place; first GitHub green run is still pending
- Evidence depth reached: L2 local_smoke for workflow/document validation

## Residual

- `R-001`: first green run and required-status enforcement in GitHub are still pending
- `R-002`: live-provider validation remains opt-in and is not part of this first-phase PR CI

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-26-java-pr-regression-ci/`
- Feature SSoT row: `F-002`
- Regression SSoT gates: `RG-001` to `RG-007`
- Branch / Worktree: current workspace on `feature/chat-memory-summary-policy`; no dedicated worktree because the user requested direct continuation in place
- Commit: pending
