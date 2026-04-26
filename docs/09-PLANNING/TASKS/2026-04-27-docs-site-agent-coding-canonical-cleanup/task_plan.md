# docs-site Agent and Coding Agent Canonical Cleanup

## Goal

Strengthen the current `Agent` and `Coding Agent` canonical pages and converge their main reading paths onto the sidebar routes that already exist.

## Scope

- In scope: `docs-site/docs/agent/**`, `docs-site/docs/coding-agent/**`, and harness closeout files under `docs/`
- Out of scope: sidebar restructuring, full rewrites of every legacy deep page, and changes to `Core SDK` or `Flowgram`

## Steps

1. Diagnose the current canonical/deep-page split and old-path residue inside the `agent/` and `coding-agent/` subtrees.
2. Strengthen the key canonical pages and normalize major subtree links to current sidebar routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] `Agent` canonical pages explain architecture, memory, runtime, orchestration, and observability without bouncing the reader back to old route names
- [x] `Coding Agent` canonical pages explain architecture, install/release, and `MCP` vs `ACP` using current sidebar routes
- [x] Major old-path references in the `agent/` and `coding-agent/` docs trees are normalized to current canonical routes
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-007`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-27-docs-site-agent-coding-canonical-cleanup-design.md`
