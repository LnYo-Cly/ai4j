# docs-site Flowgram Canonical Cleanup Walkthrough

## Overview

Strengthened the canonical `Flowgram` topic pages and normalized the main old-route residue inside the `flowgram/` docs subtree, while making the `Flowgram.ai` frontend-library vs AI4J backend-runtime boundary explicit below the overview layer.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Canonical execution page | keep `runtime` as the main entry | matches the current sidebar without reopening IA |
| Canonical node pages | keep `built-in-nodes` and `custom-nodes` as the reader-facing entries | gives a stable top-level path for node capability explanations |
| Cleanup strategy | strengthen canonical pages and redirect deep-page reading paths back to them | reduces route confusion without a risky whole-tree rename |
| Flowgram.ai messaging | state the frontend-library vs backend-runtime split directly in canonical and runtime-adjacent pages | prevents readers from assuming AI4J owns the frontend library itself |

## Verification

- Commands run: `npm run typecheck`; `rg -n "<old flowgram route patterns>" docs-site/docs/flowgram`; `npx docusaurus build --out-dir build-flowgram-canonical-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Flowgram deep-detail pages still exist as supporting material, but the canonical reading path now stays on the current sidebar routes

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-27-docs-site-flowgram-canonical-cleanup/task_plan.md`
- Feature SSoT row: `F-006`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
