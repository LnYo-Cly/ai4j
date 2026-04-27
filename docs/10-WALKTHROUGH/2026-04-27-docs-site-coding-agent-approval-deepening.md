# docs-site Coding Agent Approval Deepening Walkthrough

## Overview

Strengthened the Coding Agent docs so they now explain the approval interception path concretely, distinguish host-specific approval flows, and document the current workspace-boundary caveat honestly.

## Scope

- Modules changed: `docs-site`
- Files added: `none`
- Files removed: `none`
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Owning explanation page | deepen `tools-and-approvals.md` and support it from runtime/session pages | approval semantics belong with the Tool surface, but implementers also need the runtime path in the architecture pages |
| Security wording | document the current `write_file` caveat | the docs should reflect the actual path-resolution behavior instead of overstating workspace isolation |
| Regression closeout | accept `RG-008` as partial for this task | the docs built successfully through an explicit Docusaurus out-dir build, but the primary `npm run build` path was not rerun in this cycle |

## Verification

- Commands run: `npm run typecheck` (initial OOM on default heap, rerun with `NODE_OPTIONS=--max-old-space-size=8192` passed); `npx docusaurus build --out-dir build-approval-deepening-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- `docs-site` typecheck may still require `NODE_OPTIONS=--max-old-space-size=8192` on this Windows environment
- the canonical `npm run build` command was not rerun in this cycle, so `RG-008` remains partial even though the explicit Docusaurus build passed

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-27-docs-site-coding-agent-approval-deepening/task_plan.md`
- Feature SSoT row: `F-014`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy`, current workspace
- Commit: `pending`
