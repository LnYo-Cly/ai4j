# docs-site IA Redesign

## Goal
Restructure `docs-site` around a stable `Start Here -> Core SDK -> Agent / Coding Agent / Flowgram` information architecture with corrected `Function Call`, `Skill`, and `MCP` layering.

## Scope

- In scope: sidebar redesign, target directory tree, migration mapping, high-priority page merge/split decisions, SSoT tracking, and docs-site regression planning
- Out of scope: full prose rewrite of every historical page, SEO tuning, visual theme redesign, and non-docs product behavior changes

## Steps

1. Lock the approved IA design and register the feature in the harness.
2. Replace the sidebar and directory skeleton with the new canonical structure.
3. Migrate or merge high-priority pages in `Start Here`, `Core SDK`, and module overviews.
4. Repair internal links, run the required docs regressions, and close with walkthrough output.

## Acceptance Criteria

- [x] New `docs-site` sidebar reflects the approved IA
- [x] `Core SDK`, `Skill`, `MCP`, and `Function Call` are documented at the correct layer
- [x] Legacy duplicate paths are either migrated, merged, or explicitly deferred
- [x] Required docs-site regression was run or honestly recorded as skipped
- [x] Feature SSoT and walkthrough are updated at closeout

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: docs-only IA migration stayed inside the current workspace because the change was already mid-flight before harness registration and did not require parallel code implementation

## Links

- Feature SSoT row: `F-003` in `docs/09-PLANNING/Feature-SSoT.md`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-26-docs-site-ia-redesign-design.md`
