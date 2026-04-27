# docs-site Spring Boot Canonical Cleanup Walkthrough

## Overview

Strengthened the six canonical `Spring Boot` pages so they explain the containerized entry path, configuration boundary, Bean extension, and reading order directly, then normalized the immediate old `getting-started` guidance back onto the current `spring-boot/` tree.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Sidebar strategy | keep the current six-page `Spring Boot` tree | the subtree was already compact and understandable; the remaining problem was thin entry pages and legacy guidance |
| Old getting-started guidance | demote to supplemental only | keeps prior long-form pages available without letting them override the canonical path |
| Case-study routing | point `common-patterns` to `solutions/` entry pages | aligns examples with the current top-level docs structure |
| Boundary framing | keep `Spring Boot` as the containerized entry over `Core SDK`, not a separate capability layer | helps readers explain the architecture cleanly in interviews and self-study |

## Verification

- Commands run: `npm run typecheck`; targeted `rg` scan for old Spring Boot `getting-started` guidance in `spring-boot/` and adjacent `start-here/`; `npx docusaurus build --out-dir build-spring-boot-canonical-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Legacy `getting-started` Spring Boot pages still exist as supplemental material, but the canonical reading path now stays on the current `spring-boot/` sidebar routes

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-spring-boot-canonical-cleanup/task_plan.md`
- Feature SSoT row: `F-009`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
