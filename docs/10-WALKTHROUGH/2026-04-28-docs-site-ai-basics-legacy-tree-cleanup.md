# docs-site ai-basics Legacy Tree Cleanup Walkthrough

## Overview

Normalized the `ai-basics` legacy subtree so active low-level pages better match the current technical-doc standard and the remaining bridge pages clearly behave as migration indexes.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Tree treatment | keep `ai-basics/` as a valid legacy long-form subtree instead of deleting it | it still contains useful low-level capability material, but it needed clearer role boundaries |
| Deepening target | patch only the remaining thin active pages such as `services/embedding` and `responses/chat-vs-responses` | most of the subtree already had enough long-form detail |
| Bridge-page handling | keep `enhancements/*` and related index pages as migration-oriented docs | those pages are no longer intended to be canonical technical references |

## Verification

- Commands run: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npm run build`
- Regression gates: `RG-008`
- Result: pass
- Evidence depth reached: `L2`

## Residual

- Explicit migration/index pages remain intentionally short where their only job is to route legacy links into the current canonical trees

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-ai-basics-legacy-tree-cleanup/task_plan.md`
- Feature SSoT row: `F-018`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `main` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
