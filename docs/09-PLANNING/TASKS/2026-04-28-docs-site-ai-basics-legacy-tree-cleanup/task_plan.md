# docs-site ai-basics legacy tree cleanup

## Goal

Normalize the remaining `docs-site/docs/ai-basics/**` legacy tree so active pages read like technical docs and legacy bridge pages clearly behave as migration indexes.

## Scope

- In scope: `docs-site/docs/ai-basics/**` plus closely-related migration/index pages such as `docs-site/docs/guides/blog-migration-map.md`
- Out of scope: `core-sdk/**` and `solutions/**` pages already completed in earlier waves, Java production-code changes, and another docs-site IA redesign

## Steps

1. Separate active technical pages from legacy bridge pages inside `ai-basics/`.
2. Deepen the thinnest active pages with clearer positioning, key objects, and boundaries.
3. Normalize the migration/index pages so they explicitly route readers to current canonical trees.
4. Run `RG-008` and record outcomes honestly.
5. Close the feature with walkthrough and residual notes.

## Acceptance Criteria

- [x] `ai-basics/` active pages no longer feel like thin notes compared with the canonical technical-doc standard
- [x] Legacy bridge pages in `ai-basics/` and related indexes clearly state their migration role
- [x] `RG-008` is rerun and recorded honestly for this wave
- [x] Feature SSoT, task notes, and walkthrough all match the final scope

## Worktree

- Path: current workspace
- Branch: `main`
- If no dedicated worktree was used, why: this is a docs-only continuation in the already-synced main workspace

## Links

- Feature SSoT row: `F-018`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-28-docs-site-cross-tree-quality-cleanup.md`
