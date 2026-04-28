# docs-site cross-tree quality cleanup

## Goal

Normalize the remaining non-`core-sdk` `docs-site` pages into consistent open-source technical docs, removing interview-oriented phrasing and deepening the thinnest canonical pages.

## Scope

- In scope: `docs-site/docs/start-here/**`, `intro.md`, selected `getting-started/**`, `spring-boot/**`, `agent/**`, `mcp/**`, `solutions/**`, and adjacent thin canonical pages that still read like bridge notes
- Out of scope: `core-sdk/**` pages already completed under `F-016`, Java production-code changes, and broad sidebar or routing redesign

## Steps

1. Diagnose the remaining stale style and thin-page surfaces outside `core-sdk/`.
2. Rewrite the highest-signal pages around positioning, characteristics, implementation, boundaries, and next-reading flow.
3. Deepen the thinnest canonical pages so they can stand on their own as technical docs.
4. Run `RG-008` and record outcomes in the harness docs.
5. Close the task with walkthrough and residual notes.

## Acceptance Criteria

- [x] Remaining interview-oriented phrasing in the selected canonical docs wave is removed
- [x] The selected thin canonical pages are deepened into usable technical docs
- [x] `RG-008` is rerun and recorded honestly for this wave
- [x] Feature SSoT, task notes, and walkthrough all match the final scope

## Worktree

- Path: current workspace
- Branch: `main`
- If no dedicated worktree was used, why: this is a docs-only continuation in the already-synced main workspace

## Links

- Feature SSoT row: `F-017`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-28-docs-site-core-sdk-deep-page-completion.md`
