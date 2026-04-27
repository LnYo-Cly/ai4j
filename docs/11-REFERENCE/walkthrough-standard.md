# Walkthrough Standard

> Last updated: 2026-04-26

## Purpose

Walkthroughs are closeout records for completed features, waves, or meaningful delivery slices. They exist so the next agent can continue without reverse-engineering old commits.

## File Location

- Final records live under `docs/10-WALKTHROUGH/`
- Use date-prefixed names: `YYYY-MM-DD-<feature-name>.md`
- Start from `docs/10-WALKTHROUGH/_walkthrough-template.md`

## When A Walkthrough Is Required

- After a feature is completed and ready to merge or has merged
- After a multi-phase wave finishes with meaningful verification
- After harness or regression-governance changes that alter how future work executes

## Required Sections

1. **Overview**: what this wave changed
2. **Scope**: modules/files/surfaces touched
3. **Key decisions**: what was chosen and why
4. **Verification**: exact commands or flows used
5. **Evidence depth**: what level the outcome reached
6. **Residual**: open items, caveats, or "none"
7. **Links**: task-plan path, SSoT entry, regression gates, commit hash

## Repo-Specific Expectations

- Mention the exact modules touched, such as `ai4j-cli` or `ai4j-flowgram-spring-boot-starter`
- Name the Regression SSoT gate IDs that were exercised or changed
- If live-provider validation was skipped, say so explicitly
- If the work stayed on the current main worktree rather than a dedicated worktree, note why

## Writing Rules

- Focus on decisions and verification, not line-by-line code explanation
- Keep residual items concrete enough to become the next task input
- Use tables where they make the result faster to scan
- Do not mark a walkthrough complete while hiding skipped verification
