## Publish Standard Preset

Release Version: {{release}}
From Version: {{from}}
To Revision: {{to}}
Task Query: {{taskQuery}}

## Publish Boundary

This preset prepares reviewable publish materials only. It does not run `npm publish`; the package owner confirms registry state and executes publish manually.

## Publish Standard Workflow

Run these preset entrypoints from the target root after selecting release evidence through `--task-list` or `--task-query`.

1. `harness preset run publish-standard plan --task {{taskId}} .`
2. `harness preset run publish-standard scaffold --task {{taskId}} .`
3. `harness preset run publish-standard check --task {{taskId}} .`

## Publish Standard Outputs

| Output | Owner |
| --- | --- |
| `{{paths.governanceRoot}}/releases/{{release}}/public-changelog.md` | `presets/publish-standard` |
| `{{paths.governanceRoot}}/releases/{{release}}/technical-summary.md` | `presets/publish-standard` |
| `{{paths.governanceRoot}}/releases/{{release}}/publish-checklist.md` | `presets/publish-standard` |
| `{{paths.governanceRoot}}/releases/{{release}}/pack-report.json` | `presets/publish-standard` |
