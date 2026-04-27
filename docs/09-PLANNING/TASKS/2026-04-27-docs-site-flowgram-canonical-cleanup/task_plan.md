# docs-site Flowgram Canonical Cleanup

## Goal

Normalize the `Flowgram` docs subtree around the current canonical pages and clarify the boundary between `Flowgram.ai` as the upstream frontend library and AI4J as the backend runtime/platform layer.

## Scope

- In scope: `flowgram/runtime`, `flowgram/built-in-nodes`, `flowgram/custom-nodes`, and major old-path link cleanup inside the `flowgram/` docs tree
- Out of scope: full rewrites of every Flowgram deep page and any sidebar restructuring

## Steps

1. Diagnose remaining old-path residue and the current canonical/deep-page split.
2. Strengthen the canonical Flowgram pages and normalize major subtree links.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] `runtime`, `built-in-nodes`, and `custom-nodes` work as self-explanatory canonical pages
- [x] Major old-path references in the `flowgram/` docs tree are normalized to current canonical routes
- [x] `Flowgram.ai` frontend-library vs AI4J backend-runtime boundary is stated clearly where relevant
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the same active docs-site context

## Links

- Feature SSoT row: `F-006`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-27-docs-site-flowgram-canonical-cleanup-design.md`
