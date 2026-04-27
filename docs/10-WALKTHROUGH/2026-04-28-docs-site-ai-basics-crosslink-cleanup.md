# docs-site ai-basics Crosslink Cleanup Walkthrough

## Overview

Cleaned a small set of residual `ai-basics/` cross-links that still pointed to downgraded `getting-started` or old Flowgram routes, bringing those legacy-adjacent pages back into alignment with the current canonical docs tree.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Scope size | keep the task link-focused | the remaining issues were concentrated and did not justify a full `ai-basics/` rewrite |
| Spring Boot route mapping | point old starter links to `spring-boot/auto-configuration` | preserves intent while aligning with the current canonical tree |
| Service-matrix route mapping | point old matrix links to `core-sdk/service-entry-and-registry` | best current canonical match for provider/service capability orientation |
| Flowgram route mapping | replace `builtin-nodes` with `built-in-nodes` | aligns the retained legacy page with the current Flowgram canonical path |

## Verification

- Commands run: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; targeted `rg` scans for `getting-started/platforms-and-service-matrix`, `getting-started/spring-boot-autoconfiguration`, and `flowgram/builtin-nodes` inside `ai-basics/`; `npx docusaurus build --out-dir build-ai-basics-crosslink-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- In this workspace, `npm run typecheck` may need a larger local Node heap (`NODE_OPTIONS=--max-old-space-size=8192`) to avoid V8 out-of-memory failures
- `ai-basics/` still contains broader legacy content, but the identified high-impact residual cross-links are now aligned with the current canonical routes

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-ai-basics-crosslink-cleanup/task_plan.md`
- Feature SSoT row: `F-012`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
