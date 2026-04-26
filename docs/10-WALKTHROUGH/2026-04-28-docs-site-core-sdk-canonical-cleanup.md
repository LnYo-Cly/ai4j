# docs-site Core SDK Canonical Cleanup Walkthrough

## Overview

Strengthened the canonical `Core SDK` entry pages so they explain the base-layer module boundaries and reading order directly, then normalized the main legacy `chat/`, `responses/`, and selected root-level capability guidance links back to the current sidebar tree.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: planning record, task three-piece, and this walkthrough
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Sidebar strategy | keep current `Core SDK` sidebar | the IA direction was already validated; the remaining problem was canonical-page quality and path convergence |
| Canonical page focus | strengthen `service-entry-and-registry`, `model-access`, `tools`, `skills`, `mcp`, `memory`, `search-and-rag`, and `extension` | these pages define the base-layer reading path and were still too thin |
| Legacy page handling | keep `chat/`, `responses/`, and older capability pages as supporting detail | preserves existing material without reopening a risky full-tree rewrite |
| Function Call positioning | keep `Tools` as the explicit base-layer home of local function tools | reinforces the `Function Call` vs `Skill` vs `MCP` split the docs-site now relies on |

## Verification

- Commands run: `npm run typecheck`; targeted `rg` scan for legacy `core-sdk` guidance outside `chat/` and `responses/`; `npx docusaurus build --out-dir build-core-sdk-canonical-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when bundle compilation succeeds
- Legacy `chat/`, `responses/`, and selected root-level capability pages still exist as supporting material, but the canonical reading path now stays on the current `Core SDK` sidebar routes

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-27-docs-site-core-sdk-canonical-cleanup/task_plan.md`
- Feature SSoT row: `F-008`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
