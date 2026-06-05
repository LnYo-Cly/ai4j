# Task Plan

## Goal

Create a user-facing AI4J Skill that helps developers build applications with AI4J, then expose its install command in the docs-site README.

## Scope

- Create `skills/ai4j-app-builder/`.
- Keep the existing `skills/ai4j-sdk/` as the repository-maintainer Skill.
- Add focused references for dependency selection, recipes, and verification.
- Update `docs-site/README.md` to document both Skill install paths.
- Validate Skill metadata and docs-site build.

## Non-Goals

- No Java runtime API changes.
- No docs-site page restructuring beyond README Skill entry.
- No remote push.
- No real-user online A/B experiment in this task.

## Plan

| Step | Status | Evidence |
| --- | --- | --- |
| Diagnose existing Skill/docs boundary | done | Existing `$ai4j-sdk` and docs README inspected |
| Scaffold app-builder Skill | done | `skills/ai4j-app-builder/` created by `init_skill.py` |
| Write user-side Skill content | done | `SKILL.md` plus three reference files |
| Update docs-site README install commands | done | README lists `$ai4j-app-builder` and `$ai4j-sdk` |
| Validate Skill and docs-site | done | `quick_validate.py` and `npm run build` passed |
| Submit harness review packet | pending | Run lifecycle commands after task files are repaired |

## Verification Plan

- `python C:\Users\1\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\ai4j-app-builder`
- `python C:\Users\1\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\ai4j-sdk`
- scan `skills\ai4j-app-builder` and `docs-site\README.md` for generated placeholder markers
- `cd docs-site && npm run build`
- `npx --yes coding-agent-harness status --json .`

## Review Criteria

- Skill trigger description is specific enough to route application-development requests.
- `SKILL.md` stays concise and loads detailed recipe/reference files only when needed.
- README does not imply the app-builder Skill is for repository maintenance.
- No secrets or local-only paths are introduced.
