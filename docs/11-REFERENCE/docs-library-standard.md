# Docs Library Standard

> Last updated: 2026-04-26

## Directory Model

New documentation follows the harness numbered layout:

- `01-GOVERNANCE`
- `03-ARCHITECTURE`
- `04-DEVELOPMENT`
- `05-TEST-QA`
- `06-INTEGRATIONS`
- `07-OPERATIONS`
- `08-SECURITY`
- `09-PLANNING`
- `10-WALKTHROUGH`
- `11-REFERENCE`
- `99-TMP`

## Legacy Preservation

The repository already contains historical planning material in:

- `docs/plans/`
- `docs/tasks/`
- `docs/archive/`

These paths are preserved for continuity. They are not the default location for new harness work.

## Naming Rules

### Directories

- Use numbered uppercase names for top-level harness directories
- Use `_task-template` for task templates
- Use date-prefixed directories for concrete tasks: `YYYY-MM-DD-<task-name>`

### Files

- Use kebab-case for standards and stable docs
- Use date-prefixed filenames for walkthroughs and time-sequenced records
- Template files may start with `_` when meant for copying

## Placement Rules

| Doc Type | Location |
|----------|----------|
| Task plans and execution notes | `docs/09-PLANNING/TASKS/<task>/` |
| Feature SSoT | `docs/09-PLANNING/Feature-SSoT.md` |
| Regression control docs | `docs/05-TEST-QA/` |
| Walkthrough closeout docs | `docs/10-WALKTHROUGH/` |
| Agent-readable standards | `docs/11-REFERENCE/` |
| Temporary scratch output | `docs/99-TMP/` |

## Writing Rules

1. Each file should have one job.
2. Prefer tables for structured state that agents need to parse quickly.
3. Put the current truth in SSoT files, not inside old task notes.
4. Keep standards focused; split them when they become multi-purpose.
5. Add a `Last updated` line to standard documents.

## Archive And Retention

- Completed task directories stay in place unless a later migration task moves them deliberately
- `99-TMP/` should be cleaned regularly
- Legacy docs may be referenced from new harness docs, but should not be edited casually as a substitute for SSoT updates
- If a legacy document becomes the current truth again, migrate that truth into the harness structure first
