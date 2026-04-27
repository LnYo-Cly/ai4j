# docs-site Solutions Canonical Cleanup

## Goal

Turn the `Solutions` subtree from a set of bridge pages into readable scenario-entry pages that stand on the current docs-site architecture instead of deferring immediately to legacy guides.

## Scope

- In scope: `docs-site/docs/solutions/**` and harness closeout files under `docs/`
- Out of scope: full rewrites of the legacy `guides/` pages, sidebar restructuring, and broad cleanup of other legacy trees

## Steps

1. Diagnose the current Solutions bridge-page problem and guide dependence.
2. Rewrite the overview and scenario pages so they explain problem, stack, fit, and next reading paths directly.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] `Solutions` overview clearly explains the role of the scenario layer
- [x] Each scenario page is readable on its own and no longer functions only as a bridge to `guides/`
- [x] Old `guides/` links, where retained, are clearly supplemental rather than the primary body
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-011`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-28-docs-site-solutions-canonical-cleanup-design.md`
