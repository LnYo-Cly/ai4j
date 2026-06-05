# Visual Map

```text
user asks "先做 10"
        |
        v
create app-builder Skill
        |
        +--> SKILL.md: startup workflow, module selection, output contract
        |
        +--> references/app-paths.md: dependency and docs routing
        |
        +--> references/recipes.md: compact implementation patterns
        |
        +--> references/verification.md: test and troubleshooting ladder
        |
        v
docs-site/README.md
        |
        +--> ai4j-app-builder install command
        +--> ai4j-sdk maintainer Skill install command
```

## Lifecycle Map

| Phase | State | Completion | Evidence | Residual |
| --- | --- | ---: | --- | --- |
| INIT-01 | done | 100 | task registered and started by harness CLI | none |
| DESIGN-01 | done | 100 | user selected item 10; design kept separate from maintainer Skill | none |
| EXEC-01 | done | 100 | commit `c23fb08` | none |
| VERIFY-01 | done | 100 | Skill validation and docs-site build passed | none |
| REVIEW-01 | pending | 0 | task-review command still to run | waiting for lifecycle submission |
