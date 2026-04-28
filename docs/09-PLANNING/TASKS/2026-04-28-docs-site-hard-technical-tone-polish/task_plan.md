# docs-site hard technical tone polish

## Goal

Remove the last residual subjective or conversational wording from the canonical docs-site pages so the remaining prose aligns more closely with technical documentation tone.

## Scope

- In scope: the small set of canonical docs pages still containing subjective wording such as `最稳定`, `最稳妥`, `最通用`, `更合适的起点`, or similar expressions
- Out of scope: migration placeholder pages whose role is only link compatibility, large subtree rewrites already completed in earlier waves, and Java production-code changes

## Steps

1. Fix the remaining subjective wording in the identified canonical pages.
2. Re-run `RG-008` and record outcomes honestly.
3. Close the feature with walkthrough and residual notes.

## Acceptance Criteria

- [x] The identified canonical pages no longer rely on subjective promotional wording
- [x] `RG-008` is rerun and recorded honestly for this wave
- [x] Feature SSoT, task notes, and walkthrough all match the final scope

## Worktree

- Path: current workspace
- Branch: `main`
- If no dedicated worktree was used, why: this is a docs-only continuation in the already-synced main workspace

## Links

- Feature SSoT row: `F-020`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-28-docs-site-residual-polish.md`
