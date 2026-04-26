# docs-site Agent and Coding Agent Canonical Cleanup Walkthrough

## Overview

Strengthened the canonical `Agent` and `Coding Agent` topic pages so they explain the current module boundaries and reading path directly, then normalized the main old-route residue inside both docs subtrees.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Sidebar strategy | keep current sidebar | IA direction was already validated; the remaining problem was reading-path convergence |
| Agent canonicalization | make `architecture`, `memory-and-state`, `runtimes/*`, `orchestration/*`, and `observability/trace` self-explanatory | new routes should not behave like aliases to old pages |
| Coding Agent protocol entry | make `mcp-and-acp` the canonical protocol boundary page | avoids splitting the main reading path across `mcp-integration` and `acp-integration` |
| Legacy deep pages | keep them as supporting detail only | preserves existing detail docs without reopening a full-tree rewrite |

## Verification

- Commands run: `npm run typecheck`; targeted `rg` scans for old `agent/` and `coding-agent/` route patterns; `npx docusaurus build --out-dir build-agent-coding-canonical-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Legacy deep-detail pages still exist as supporting material, but the canonical reading path now stays on the current sidebar routes for `Agent` and `Coding Agent`

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-27-docs-site-agent-coding-canonical-cleanup/task_plan.md`
- Feature SSoT row: `F-007`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
