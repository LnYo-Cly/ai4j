# docs-site Core Entry Rewrite

## Goal

Rewrite the five canonical `Start Here` and `Core SDK` entry pages so the docs-site explains AI4J's value, architecture, boundaries, and reading path clearly enough for onboarding, self-study, and interview preparation.

## Scope

- In scope: `why-ai4j`, `architecture-at-a-glance`, `core-sdk/overview`, `core-sdk/strengths-and-differentiators`, and `core-sdk/architecture-and-module-map`
- Out of scope: sidebar changes, deep legacy page migration, and `Agent` / `Coding Agent` / `Flowgram` overview rewrites

## Steps

1. Diagnose the current entry-page gaps and align claims with the real repo modules.
2. Rewrite the five target pages with stronger positioning, boundaries, and reading guidance.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] The five target pages clearly explain what AI4J is, why it matters, where its boundaries are, and what to read next
- [x] Claims about repo modules and capability layering match the current repository structure
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only rewrite on top of the already-active docs-site branch context and does not require parallel code implementation

## Links

- Feature SSoT row: `F-004`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-26-docs-site-core-entry-rewrite-design.md`
