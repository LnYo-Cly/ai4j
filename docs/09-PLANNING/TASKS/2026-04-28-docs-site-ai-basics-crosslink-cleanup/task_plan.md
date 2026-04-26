# docs-site ai-basics Crosslink Cleanup

## Goal

Repoint the remaining high-impact old cross-links inside `ai-basics/` to the current canonical docs tree.

## Scope

- In scope: the five identified `ai-basics/` pages with residual old-route links, plus harness closeout files under `docs/`
- Out of scope: broad `ai-basics/` rewriting, sidebar changes, or full `getting-started/` migration

## Steps

1. Diagnose the specific residual cross-links.
2. Replace them with the closest current canonical routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] The identified `ai-basics/` residual old-route links now point to current canonical routes
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-012`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-28-docs-site-ai-basics-crosslink-cleanup-design.md`
