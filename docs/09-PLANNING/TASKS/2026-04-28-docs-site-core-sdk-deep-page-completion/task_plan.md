# docs-site Core SDK deep page completion

## Goal

Turn the thin canonical `docs-site/docs/core-sdk/**` deep pages into reliable advanced-reading pages that explain mechanics, boundaries, and code anchors for technical onboarding and day-two usage.

## Scope

- In scope: canonical `core-sdk/` deep pages under `tools/`, `skills/`, `mcp/`, `search-and-rag/`, `extension/`, and the remaining thin `memory/` and `model-access/` pages; harness tracking and docs-site regression records required by this wave
- Out of scope: sidebar restructuring, non-canonical legacy guide rewrites outside the touched `core-sdk/` pages, and production-code changes in Java modules

## Steps

1. Confirm the thinnest `core-sdk/` deep pages and group them into rewrite waves.
2. Rewrite each page around the same pattern: positioning, owning code path, mechanics, tradeoffs, and next-reading path.
3. Reuse richer `ai-basics/`, `mcp/`, and legacy `core-sdk/` material only as supporting input while keeping `core-sdk/` canonical.
4. Run `RG-008` with the existing high-heap workaround, then record results in the task files and regression docs.
5. Close the feature with a walkthrough once the selected `core-sdk/` deep-page cluster is complete.

## Acceptance Criteria

- [x] Canonical `core-sdk/` deep pages are no longer thin bridge pages and can stand on their own for advanced reading
- [x] Tool, skill, MCP, RAG, extension, memory, and model-access boundaries are explained with source-anchored detail
- [x] `RG-008` is rerun and recorded honestly for this wave
- [x] Feature SSoT, task notes, and walkthrough all match the final scope

## Worktree

- Path: current workspace
- Branch: `main`
- If no dedicated worktree was used, why: this is a docs-only continuation in the already-synced main workspace

## Links

- Feature SSoT row: `F-016`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-27-docs-site-sidebar-path-completion.md`
