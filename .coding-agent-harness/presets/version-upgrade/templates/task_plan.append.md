## Version Upgrade Preset

Upgrade edge: `{{fromVersion}}` -> `{{toVersion}}`

Run the workflow from the target project root:

1. `harness preset run version-upgrade plan --task {{taskId}} --allow-scripts`
2. Review `artifacts/version-upgrade/upgrade-plan.json`.
3. `harness preset action version-upgrade apply-safe --task {{taskId}} --allow-scripts`
4. Complete the manual confirmations recorded by the plan.
5. `harness preset run version-upgrade check --task {{taskId}} --allow-scripts`

The preset must keep all generated evidence task-local. It must not rewrite human review confirmations, delete historical task records, or mutate target project files outside the runner materialization manifest.
