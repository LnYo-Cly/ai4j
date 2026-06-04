## Legacy Migration Preset Gate

`migration-full-cutover` can only be claimed when the final session proves all gates:

- final session result is `complete`
- strict check passes
- `migrate-verify --full-cutover` passes
- warnings/actions/residuals/strictDeferred are zero
- dashboard evidence is readable
- review has no open P0/P1/P2 blocker

Current achieved level: `{{migrationAchievedLevel}}`.
