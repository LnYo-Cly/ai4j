# docs-site Coding Agent Approval Deepening

## Goal

Make the `docs-site` Coding Agent approval documentation explicit about where interception happens, how each host path approves tool calls, and what the current workspace-boundary caveats are.

## Scope

- In scope: `docs-site/docs/coding-agent/**` pages needed to clarify approval interception, SAFE mode behavior, host-specific approval flow, and workspace/path boundary semantics, plus harness closeout files under `docs/`
- Out of scope: approval mechanism code changes, sidebar restructuring, and broad rewrites outside the touched Coding Agent docs pages

## Steps

1. Diagnose the current approval-related docs gaps against the actual `ai4j-cli` and `ai4j-coding` implementation.
2. Strengthen the narrow set of Coding Agent docs pages that own approval, runtime, and session explanations.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] `docs-site` explains that approval interception is implemented through `ToolExecutorDecorator`, not OS or JVM hooks
- [x] `docs-site` explains the current `AUTO / SAFE / MANUAL` behavior precisely enough for implementers and advanced users
- [x] `docs-site` distinguishes CLI/TUI approval prompts from ACP `session/request_permission`
- [x] `docs-site` explains the current workspace-boundary enforcement path and accurately records the known `write_file` caveat
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a narrow docs-only continuation in the current repository workspace

## Links

- Feature SSoT row: `F-014`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-27-docs-site-agent-coding-canonical-cleanup.md`
