## Legacy Migration Preset

This Complex Task uses the `{{preset}}` preset package. The preset only scaffolds the migration task and records evidence at creation time. It does not run migration, rewrite historical task bodies, stage files, or commit changes.

- Preset version: `{{presetVersion}}`
- Baseline session: `{{evidenceBundle}}/session.json`
- Migration plan: `{{evidenceBundle}}/migrate-plan.json`
- Strict deferred: {{strictDeferred}}
- Full-cutover claim allowed now: {{fullCutoverClaimAllowed}}
