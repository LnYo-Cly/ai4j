# Harness Bootstrap

## Goal
Establish the coding-agent harness baseline for the `ai4j-sdk` monorepo so future work follows one consistent planning, regression, and closeout system.

## Scope

- In scope: `AGENTS.md`, numbered `docs/` harness structure, reference standards, planning templates, Feature SSoT, Regression SSoT, Cadence Ledger, walkthrough template, and worktree guidance
- Out of scope: migrating all legacy historical docs, changing production Java or frontend behavior, or rewriting existing task history into the new system

## Steps

1. Diagnose the repository structure, surfaces, and current documentation state.
2. Choose the harness scale and target directory layout.
3. Create the docs skeleton and rewrite `AGENTS.md`.
4. Add reference standards, planning templates, SSoT files, regression governance, walkthrough template, and worktree rules.
5. Validate the minimum deliverables and publish a bootstrap summary.

## Acceptance Criteria

- [x] Root `AGENTS.md` reflects the current monorepo and harness flow
- [x] `docs/11-REFERENCE/` contains the required standards
- [x] `docs/09-PLANNING/TASKS/_task-template/` contains the planning three-piece template
- [x] Feature SSoT and Regression SSoT exist
- [x] Cadence Ledger exists
- [x] Walkthrough template exists
- [x] Bootstrap summary is delivered with residual items and next actions

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: user requested direct bootstrap in the current repository, and the workspace was clean enough to continue safely in place

## Links

- Feature SSoT row: `F-001` in `docs/09-PLANNING/Feature-SSoT.md`
- Regression gates: `RG-001` to `RG-009` in `docs/05-TEST-QA/Regression-SSoT.md`
- Previous task or dependency: none
