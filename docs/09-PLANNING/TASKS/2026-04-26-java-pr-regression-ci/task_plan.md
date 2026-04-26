# Java PR Regression CI

## Goal
Add a repo-level GitHub Actions workflow that runs deterministic Java regression gates for PRs targeting `dev` or `main`.

## Scope

- In scope: a new GitHub Actions workflow for Java regression, SSoT/cadence updates, task records, walkthrough closeout, and any minimal git-tracking fix required for the new harness files
- Out of scope: docs-site CI integration, web-demo CI integration, live-provider gates, branch-protection configuration in GitHub UI

## Steps

1. Confirm the fixed Java regression surface and check for live/provider test caveats.
2. Add a PR-only Java regression workflow with package smoke plus module test matrix.
3. Update Regression SSoT and Cadence Ledger to reflect the new automated gate.
4. Record findings, progress, and walkthrough output.

## Acceptance Criteria

- [x] `.github/workflows/java-regression.yml` exists and targets PRs to `dev` / `main`
- [x] workflow runs package smoke and the 6 Java module test gates
- [x] `Regression-SSoT.md` reflects the new CI-managed gate state honestly
- [x] `Cadence-Ledger.md` maps Java PR changes to the workflow
- [x] walkthrough is written and Feature SSoT is updated

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: user requested direct continuation in the current repository and the workspace was clean

## Links

- Feature SSoT row: `F-002` in `docs/09-PLANNING/Feature-SSoT.md`
- Regression gates: `RG-001` to `RG-007`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-26-harness-bootstrap.md`
