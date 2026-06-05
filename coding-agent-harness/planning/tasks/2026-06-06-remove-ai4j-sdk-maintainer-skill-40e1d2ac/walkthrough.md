# Walkthrough

## Summary

Removed the public `$ai4j-sdk` maintainer Skill and kept `$ai4j-app-builder` as the only active Skill. Repository maintenance now points back to `AGENTS.md` and `coding-agent-harness/`.

## What Changed

- Deleted `skills/ai4j-sdk/SKILL.md`.
- Deleted `skills/ai4j-sdk/agents/openai.yaml`.
- Deleted `skills/ai4j-sdk/references/development-workflow.md`.
- Deleted `skills/ai4j-sdk/references/repo-map.md`.
- Updated `skills/ai4j-app-builder/SKILL.md` description.
- Updated `docs-site/README.md` to only document `ai4j-app-builder`.

## Verification

- `quick_validate.py skills\ai4j-app-builder` passed.
- Active README/Skill scan found no `$ai4j-sdk` install entry.
- `npm run build` in `docs-site/` passed.

## Implementation Commit

`f891bdd chore: remove ai4j sdk maintainer skill`

## Residual Items

- Remote push is not performed in this task.
- Future release notes may mention that repository maintenance uses `AGENTS.md` and harness instead of a separate Skill.

## Lessons Reflection

No shared lesson is promoted. This is a project/product surface cleanup, not a reusable harness governance change.
