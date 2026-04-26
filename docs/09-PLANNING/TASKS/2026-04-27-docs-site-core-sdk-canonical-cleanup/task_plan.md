# docs-site Core SDK Canonical Cleanup

## Goal

Strengthen the current `Core SDK` canonical pages and converge the main reading path back onto the sidebar routes that now define the base-layer information architecture.

## Scope

- In scope: `docs-site/docs/core-sdk/**` and harness closeout files under `docs/`
- Out of scope: sidebar restructuring, full rewrites of every legacy `chat/` and `responses/` page, and changes to `Spring Boot` or other top-level doc trees

## Steps

1. Diagnose the current canonical-page thin spots and legacy reading-path residue in the `core-sdk/` docs tree.
2. Strengthen the key canonical pages and normalize major legacy links to current sidebar routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] Key `Core SDK` canonical pages explain module purpose, boundaries, and reading order without behaving like thin index pages
- [x] Major legacy `chat/`, `responses/`, and root-level capability links point readers back to the current canonical tree
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-008`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-27-docs-site-core-sdk-canonical-cleanup-design.md`
