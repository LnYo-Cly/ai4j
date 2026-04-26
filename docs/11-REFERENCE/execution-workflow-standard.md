# Execution Workflow Standard

> Last updated: 2026-04-26

## Start Of Task

For every non-trivial task:

1. Read `AGENTS.md`
2. Read `docs/09-PLANNING/Feature-SSoT.md`
3. Create or update a task directory under `docs/09-PLANNING/TASKS/`
4. Decide whether the work needs a dedicated worktree
5. Record scope, branch/worktree, and expected gates before substantial implementation

## During Execution

1. Update `progress.md` after each meaningful phase
2. Record research and scope discoveries in `findings.md`
3. Keep commits small and meaningful
4. When scope changes, update `task_plan.md` and the matching Feature SSoT row
5. If a change introduces or reshapes a fixed regression gate, update the Regression SSoT and Cadence Ledger in the same workstream

## Completion Rules

1. Run the targeted regression required by the touched surface
2. Update the matching Feature SSoT entry
3. Update Regression SSoT or Cadence Ledger if verification state changed
4. Write a walkthrough in `docs/10-WALKTHROUGH/`
5. Clean the worktree if one was created

## Commit Convention

This repository already uses short prefix commits. Keep using that style:

- `feat: ...`
- `fix: ...`
- `docs: ...`
- `refactor: ...`
- `test: ...`
- `chore: ...`
- `build: ...`
- `update: ...`

Add the module or surface in the description when helpful.

Examples:

- `feat: add chat memory summary policy`
- `fix: stabilize flowgram task report mapping`
- `docs: align harness references with monorepo layout`
- `test: add cli session regression for checkpoint replay`

## Branch And PR Convention

- Branch naming should follow repo practice: `feature/<name>`, `fix/<name>`, `docs/<name>`, `refactor/<name>`, `test/<name>`
- Open PRs against `dev` unless the task explicitly says otherwise
- PR descriptions should include:
  - what changed
  - why it changed
  - task-plan path
  - relevant SSoT row
  - test or regression results
  - skipped verification and why

## Prohibited Shortcuts

- Do not start non-trivial implementation without a task directory
- Do not create new planning/progress artifacts in repo root
- Do not treat legacy `docs/plans` / `docs/tasks` as the default destination for new work
- Do not merge significant changes without recording verification outcome
- Do not close a feature without a walkthrough or explicit residual note
