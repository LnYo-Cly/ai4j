# Findings

## Material Findings

No open material findings.

## Decisions

### Separate Skill Boundary

The user-facing app-building workflow belongs in a new `$ai4j-app-builder` Skill instead of extending `$ai4j-sdk`. This prevents beginners who want to build their own app from being routed into repository-maintenance instructions.

### Reference Split

Detailed dependency choices, recipes, and verification guidance were moved into references so `SKILL.md` remains concise and follows progressive disclosure.

### Verification Depth

This task changed Skill/docs artifacts only. Structural Skill validation and docs-site production build are sufficient. Java module regression is not required because no Java source or Maven metadata was changed.

## Residual Risk

- The Skill has not been tested through an independent live agent session in this task. Existing validation confirms structure and docs build, not real external user behavior.
- API examples are compact skeletons. A future pass should align them with the highest-quality docs-site examples after the docs-site rewrite settles.
