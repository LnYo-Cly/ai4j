# docs-site Sidebar Path Completion

## Goal

Systematically complete the `docs-site` content along the main sidebar reading path so the site works as both an entry funnel and a deep technical reference.

## Scope

- In scope: canonical `docs-site/docs/**` pages reached from the sidebar-first reading path, their major supporting pages, harness tracking under `docs/`, and the regression records needed for each wave
- Out of scope: broad sidebar restructuring, non-canonical legacy deep pages that are not yet reached by the active wave, and product-code changes outside docs-only verification needs

## Steps

1. Record the approved wave design and keep one active harness feature for the full docs-site completion stream.
2. Execute waves in sidebar order: `Start Here -> Core SDK -> Spring Boot -> Agent -> Coding Agent -> Flowgram -> Solutions -> FAQ/Glossary -> legacy deep-page residue`.
3. In each wave, strengthen canonical entry pages first, then the key capability pages, then route residue and deep-page alignment.
4. Run `RG-008` for each wave and close each sub-wave with explicit progress and residual notes.

## Acceptance Criteria

- [ ] Sidebar-first readers can move through each completed wave without relying on historical route names or thin bridge pages
- [ ] Each completed canonical page clearly explains positioning, module boundary, strengths, use cases, and next reading path
- [ ] Each wave records regression honestly under `RG-008`
- [ ] Feature SSoT remains current while the multi-wave docs stream is active
- [ ] Walkthrough closeout is produced when the full feature is closed

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-site continuation stream in the current working branch

## Links

- Feature SSoT row: `F-015`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/10-WALKTHROUGH/2026-04-27-docs-site-coding-agent-approval-deepening.md`
