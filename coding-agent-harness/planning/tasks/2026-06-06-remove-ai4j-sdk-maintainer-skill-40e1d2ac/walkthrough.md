# Walkthrough

## Summary

Removed the public `$ai4j-sdk` maintainer Skill and kept `$ai4j-app-builder` as the only active Skill. Repository maintenance now points back to `AGENTS.md` and `coding-agent-harness/`. A review finding also exposed a Plain Java onboarding gap, so `Configuration` now provides a default `OkHttpClient` and the app-builder recipe reflects that lower-friction path.

## What Changed

- Deleted `skills/ai4j-sdk/SKILL.md`.
- Deleted `skills/ai4j-sdk/agents/openai.yaml`.
- Deleted `skills/ai4j-sdk/references/development-workflow.md`.
- Deleted `skills/ai4j-sdk/references/repo-map.md`.
- Updated `skills/ai4j-app-builder/SKILL.md` description.
- Updated `docs-site/README.md` to only document `ai4j-app-builder`.
- Updated `ai4j/src/main/java/io/github/lnyocly/ai4j/service/Configuration.java` so Plain Java users get a default `OkHttpClient`.
- Added `ai4j/src/test/java/io/github/lnyocly/ai4j/service/ConfigurationTest.java`.
- Updated `skills/ai4j-app-builder/references/recipes.md` with missing imports and default/custom client guidance.

## Verification

- `quick_validate.py skills\ai4j-app-builder` passed.
- Active README/Skill scan found no `$ai4j-sdk` install entry.
- `npm run build` in `docs-site/` passed.
- `mvn -pl ai4j -Dtest=ConfigurationTest -DskipTests=false test` passed.
- `mvn -pl ai4j -am -DskipTests=false test` passed with 101 tests.
- `mvn -DskipTests package` passed across 9 reactor modules.
- `git diff --check` passed.

## Implementation Commit

- `f891bdd chore: remove ai4j sdk maintainer skill`
- Follow-up local commit records the Plain Java onboarding fix and refreshed task evidence.

## Residual Items

- Remote push is not performed in this task.
- Future release notes may mention that repository maintenance uses `AGENTS.md` and harness instead of a separate Skill.

## Lessons Reflection

No shared harness lesson is promoted. The practical product lesson is already reflected in code and recipe: if AI4J can provide a safe default for onboarding infrastructure, prefer the SDK default over asking beginners to wire it by hand.
