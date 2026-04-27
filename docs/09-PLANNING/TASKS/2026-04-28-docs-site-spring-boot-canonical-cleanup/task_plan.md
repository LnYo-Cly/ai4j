# docs-site Spring Boot Canonical Cleanup

## Goal

Strengthen the current `Spring Boot` canonical pages and converge the main reading path onto the six-page sidebar tree instead of the older `getting-started` route cluster.

## Scope

- In scope: `docs-site/docs/spring-boot/**`, immediate `Spring Boot` entry guidance in `docs-site/docs/start-here/**`, and harness closeout files under `docs/`
- Out of scope: full rewrites of the legacy `getting-started/` pages, sidebar restructuring, and broad `solutions/` subtree cleanup

## Steps

1. Diagnose the current `Spring Boot` canonical thin spots and old guidance residue.
2. Strengthen the canonical pages and normalize immediate old links to current sidebar routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] The six `Spring Boot` canonical pages explain purpose, boundary, and reading order directly
- [x] Immediate old `getting-started` guidance in `spring-boot/` and adjacent `start-here` pages points readers back to current canonical routes
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-009`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-28-docs-site-spring-boot-canonical-cleanup-design.md`
