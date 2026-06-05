# Execution Strategy

## Approach

Treat `$ai4j-sdk` as a duplicate maintainer onboarding layer and remove it from the active product surface. Keep historical task artifacts intact, but ensure active README and active Skill folders point only to `$ai4j-app-builder`.

## Work Sequence

1. Confirm current Skill folders and README wording.
2. Delete `skills/ai4j-sdk/**`.
3. Remove `$ai4j-sdk` install and usage text from docs-site README.
4. Update `ai4j-app-builder` frontmatter to route repo maintenance to `AGENTS.md` and harness.
5. Validate remaining Skill and docs-site build.
6. Submit harness review.

## Risk Controls

- Keep historical task directories unchanged.
- Do not delete harness or repository maintenance docs.
- Verify no active public install command remains for `$ai4j-sdk`.
- Keep `$ai4j-app-builder` focused on application users.

## Evidence Strategy

Use implementation commit `f891bdd`, Skill validation, README/content scan, docs-site build, and final harness status.
