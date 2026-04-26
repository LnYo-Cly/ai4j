# docs-site getting-started Legacy Demotion

## Goal

Demote the legacy `getting-started/` tree from a competing entry path into an archival long-form zone that routes readers back to the current canonical docs trees.

## Scope

- In scope: the identified high-traffic `getting-started/` pages and harness closeout files under `docs/`
- Out of scope: full legacy-body rewrites, deleting `getting-started/`, or moving that subtree into the new sidebar

## Steps

1. Diagnose the legacy pages that still behave like a competing entry tree.
2. Add legacy notices and repoint major guidance links to current canonical routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] High-traffic `getting-started/` pages clearly signal that they are legacy long-form guides
- [x] Their primary “next step” guidance points to current canonical routes
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is the final docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-013`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-28-docs-site-getting-started-legacy-demotion-design.md`
