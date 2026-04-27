# docs-site Solutions Canonical Cleanup Walkthrough

## Overview

Turned the `Solutions` subtree from a set of bridge pages into readable scenario-entry pages that explain each case’s problem, fit, stack, and next-reading path directly, while keeping the old `guides/` pages as supplemental implementation-detail references.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Solutions page depth | make each page a concise scenario entry rather than copying the full guide | improves readability without reopening a large legacy-tree rewrite |
| Guide handling | retain old guide links only in explicit “深入实现细节” sections | preserves useful detail while ending the bridge-page pattern |
| Overview role | make `solutions/overview` a scenario-selection page | aligns Solutions with the rest of the canonical docs-site hierarchy |
| Case-study framing | focus each page on problem, fit, stack, and related mainline docs | matches the docs-site “promote first, then deepen” strategy |

## Verification

- Commands run: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; targeted `rg` scans for bridge-page residue and `guides/` supplemental placement; `npx docusaurus build --out-dir build-solutions-canonical-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- In this workspace, `npm run typecheck` may need a larger local Node heap (`NODE_OPTIONS=--max-old-space-size=8192`) to avoid V8 out-of-memory failures
- Legacy `guides/` pages still exist as detailed implementation material, but `solutions/` pages now stand on their own as canonical scenario entries

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-28-docs-site-solutions-canonical-cleanup/task_plan.md`
- Feature SSoT row: `F-011`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
