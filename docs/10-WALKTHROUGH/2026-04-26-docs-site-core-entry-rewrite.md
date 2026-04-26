# docs-site Core Entry Rewrite Walkthrough

## Overview

Rewrote the five canonical `Start Here + Core SDK` entry pages so the docs-site explains AI4J's positioning, capability boundaries, module structure, and recommended reading path more clearly for onboarding, self-study, and interview preparation.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Rewrite scope | only the five core entry pages | highest leverage without reopening the whole site |
| Primary narrative | shift from outline-style notes to reusable positioning pages | the structure was already fixed; the main gap was explanation quality |
| Module truth source | use root/module POMs and current repo layout | keeps docs aligned with the actual shipped modules |
| Validation judgment | accept partial `RG-008` again with honest residual tracking | docs compiled successfully; the remaining blocker is local Windows file locking |

## Verification

- Commands run: `npm run typecheck`; `npx docusaurus build --out-dir build-core-entry-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- `Agent` / `Coding Agent` / `Flowgram` overview pages have not yet been rewritten to match the stronger entry-page style established in this task

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-26-docs-site-core-entry-rewrite/task_plan.md`
- Feature SSoT row: `F-004`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
