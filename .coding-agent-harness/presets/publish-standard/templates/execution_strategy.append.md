## Publish Standard Strategy

| Phase ID | Depends On | State | Completion | Output | Required Evidence | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | ---: | --- | --- | --- | --- | --- |
| PS-SELECT | none | planned | 0 | Selected task evidence for the version window | `--task-list` or `--task-query` recorded on the preset task | missing | selector drift | coordinator |
| PS-DRAFT | PS-SELECT | planned | 0 | User-facing changelog and technical trace summary | `harness preset run publish-standard scaffold --task {{taskId}} .` | missing | public wording may expose internals | coordinator |
| PS-PACK-GATES | PS-DRAFT | planned | 0 | npm package readiness checklist and pack report placeholder | `npm run check`, `npm run prepublishOnly`, `npm run pack:dry-run` evidence attached by owner | missing | package leakage | package owner |
| PS-CHECK | PS-PACK-GATES | planned | 0 | Publish standard validation report | `harness preset run publish-standard check --task {{taskId}} .` | missing | none | coordinator |
