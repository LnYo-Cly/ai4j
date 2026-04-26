# docs-site Entry-Path Residue Cleanup Walkthrough

## Overview

Cleaned the remaining high-impact legacy guidance around `Start Here` and a few adjacent canonical pages so new readers now stay on the current Agent, Flowgram, Core SDK, and Coding Agent routes when entering from the homepage path.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Scope size | keep this task narrow and entry-focused | the remaining high-value issues were concentrated near the top of the docs funnel |
| Agent entry routing | point `choose-your-path` to `overview -> why-agent -> quickstart` | aligns the page with the current “promote first, then deepen” reading order |
| Flowgram entry routing | point `choose-your-path` to `overview -> why-flowgram -> quickstart` | removes the last high-exposure link back to the older use-case page |
| Glossary cleanup | repoint key terms to current canonical pages | keeps the glossary consistent with the current docs-site architecture |

## Verification

- Commands run: `npm run typecheck`; targeted `rg` scan for old entry-path patterns in `start-here/`, `core-sdk/model-access`, `glossary`, and `intro`; `npx docusaurus build --out-dir build-entry-path-residue-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Broader legacy trees such as `ai-basics/`, `guides/`, and `getting-started/` still contain old routes, but they are no longer the primary homepage-to-canonical reading path

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-entry-path-residue-cleanup/task_plan.md`
- Feature SSoT row: `F-010`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
