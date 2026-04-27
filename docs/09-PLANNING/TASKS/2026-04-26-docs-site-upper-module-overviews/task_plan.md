# docs-site Upper Module Overviews

## Goal

Rewrite the `Agent`, `Coding Agent`, and `Flowgram` overview pages into canonical onboarding pages and normalize the most immediate old-path references around those entry points.

## Scope

- In scope: `agent/overview`, `coding-agent/overview`, `flowgram/overview`, plus direct entry-path normalization in their nearby quickstart/why/bridge pages
- Out of scope: full deep-page rewrites for the three module trees and sidebar restructuring

## Steps

1. Diagnose the current overview-page gaps and immediate old-path residue.
2. Rewrite the three overview pages and normalize direct entry-page links.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] The three overview pages clearly explain what each upper module is, why it exists, where its boundaries are, and what to read next
- [x] Immediate old-path references around those entry points are normalized to the current canonical paths
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only continuation in the same active docs-site context and does not require parallel code implementation

## Links

- Feature SSoT row: `F-005`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-26-docs-site-upper-module-overviews-design.md`
