# docs-site getting-started Legacy Demotion Walkthrough

## Overview

Demoted the high-traffic legacy `getting-started/` pages from a competing entry tree into archival long-form guides by adding explicit legacy framing and repointing their primary next-step guidance back to the current canonical docs trees.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Demotion method | use explicit legacy notices plus top-level guidance repointing | changes reader behavior without deleting useful deep-dive content |
| Link policy | prioritize canonical `start-here/`, `core-sdk/`, `spring-boot/`, and `coding-agent/` routes | keeps the current IA as the default reading path |
| Compatibility detail handling | retain legacy long-form compatibility and module notes, but stop advertising them as the primary next step | preserves detail value while removing the competing entry loop |

## Verification

- Commands run: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; `rg -n "/docs/getting-started/" docs-site/docs/getting-started`; `rg -n "/docs/getting-started/" docs-site/docs -g "!getting-started/**" -g "!guides/**"`; `rg -n "/docs/coding-agent/release-and-installation" docs-site/docs/getting-started -g "*.md"`; `npx docusaurus build --out-dir build-getting-started-legacy-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when client/server bundle compilation succeeds
- In this workspace, `npm run typecheck` may need a larger local Node heap (`NODE_OPTIONS=--max-old-space-size=8192`) to avoid V8 out-of-memory failures
- The legacy `getting-started/` pages still exist intentionally as archival deep-dive material; only their competing entry behavior was removed

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-getting-started-legacy-demotion/task_plan.md`
- Feature SSoT row: `F-013`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
