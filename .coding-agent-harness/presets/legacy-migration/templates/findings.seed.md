## Legacy Migration Action Buckets

| Bucket | Count | Owner | Status | Next Action |
| --- | ---: | --- | --- | --- |
| warnings | {{warnings}} | coordinator | open | Triage before increasing target level |
| taskActions | {{taskActions}} | coordinator | open | Upgrade only current/reopened/current-evidence tasks |
| legacyResiduals | {{legacyResiduals}} | coordinator | open | Assign real owner before full cutover |

## Residual Policy

Residuals require reason, owner, trigger, next action, and reviewer. Placeholder owner `migration-owner` is not a real owner.

## Status Conflict Table

| Item | Competing Evidence | Chosen Classification | Confidence | Human Needed |
| --- | --- | --- | --- | --- |
| pending | session / SSoT / progress / git | pending | medium | yes |
