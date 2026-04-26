# docs-site Upper Module Overviews Walkthrough

## Overview

Rewrote the `Agent`, `Coding Agent`, and `Flowgram` overview pages into canonical onboarding pages and normalized the immediate old-path references around their entry clusters.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Rewrite scope | 3 overview pages plus nearby entry-page link normalization | lifts onboarding quality without reopening the full trees |
| Narrative shape | role first, boundary second, reading path third | matches the stronger style already established in `Start Here + Core SDK` |
| Canonical cleanup level | fix the immediate overview cluster only | avoids exploding scope while still improving first-click experience |
| Validation judgment | accept partial `RG-008` with existing Windows residual handling | docs compiled successfully; remaining failure is local artifact locking |

## Verification

- Commands run: `npm run typecheck`; `rg -n "<old path patterns>" <edited overview-cluster files>`; `npx docusaurus build --out-dir build-upper-overview-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Older route names still exist deeper in the `Agent / Coding Agent / Flowgram` trees outside the immediate overview cluster and were intentionally left for later targeted cleanup

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-26-docs-site-upper-module-overviews/task_plan.md`
- Feature SSoT row: `F-005`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
