# Worktree Standard

> Last updated: 2026-04-26

## Purpose

Worktrees isolate non-trivial tasks so multi-agent or multi-branch work does not collide in the main workspace.

## When A Dedicated Worktree Is Required

- The change spans multiple modules or many files
- The task is expected to run across multiple sessions
- The task changes regression, harness, approval, or release behavior
- The current workspace already contains unrelated in-progress edits

## When Reusing The Current Workspace Is Acceptable

- Read-only analysis
- Small documentation corrections
- The user explicitly wants direct in-place changes
- The task is a direct continuation of the current branch's existing work

## Naming For This Repo

Use repository-aligned branch names:

- `feature/<name>`
- `fix/<name>`
- `docs/<name>`
- `refactor/<name>`
- `test/<name>`

Recommended worktree paths:

- `.worktrees/feature/<name>`
- `.worktrees/fix/<name>`
- `.worktrees/docs/<name>`
- `.worktrees/refactor/<name>`
- `.worktrees/test/<name>`

## Recording Rules

Every non-trivial task must record:

- worktree path
- branch name
- reason if no dedicated worktree was used

Record that in `task_plan.md` and update `progress.md` if the situation changes.

## Coordination Rules

1. One agent, one worktree.
2. Shared-file edits should be serialized deliberately.
3. Merge order is decided by the human owner, not assumed by agents.
4. Complex merge conflicts must be reported, not hand-waved away.

## Cleanup Rules

- Remove the worktree after merge unless there is an explicit reason to keep it
- Delete merged branches when no longer needed
- If a worktree stays around for validation or comparison, record the reason in `progress.md`
