# Execution Strategy

## Approach

Use the existing project Skill as a boundary reference, but create a separate user-side Skill rather than overloading `$ai4j-sdk`. The new Skill should optimize for beginner application integration: choose dependency, configure provider safely, write a minimal runnable slice, and verify.

## Work Sequence

1. Inspect existing docs-site README and `$ai4j-sdk` Skill.
2. Scaffold `skills/ai4j-app-builder` with skill-creator.
3. Replace generated placeholders with concise procedural guidance.
4. Add references for app paths, recipes, and verification.
5. Update docs-site README with install commands for both Skills.
6. Run Skill validation and docs-site build.
7. Submit harness review.

## Risk Controls

- Keep the new Skill separate from repository-maintainer guidance.
- Keep examples generic and env-var based to avoid leaking secrets.
- Avoid claiming live user evaluation; validation is structural plus build verification.
- Do not modify Java modules or generated docs-site build output.

## Evidence Strategy

Record command evidence in `progress.md`, and use commit SHA `c23fb08` as implementation boundary.
