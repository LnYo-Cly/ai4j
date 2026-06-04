## Release Closeout Strategy

| Phase ID | Depends On | State | Completion | Output | Required Evidence | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | ---: | --- | --- | --- | --- | --- |
| RC-PLAN | none | planned | 0 | Release task inventory and archive eligibility plan | `harness preset run release-closeout plan --task {{taskId}} .` | missing | none | coordinator |
| RC-SCAFFOLD | RC-PLAN | planned | 0 | Version package materialized under governance releases | `harness preset run release-closeout scaffold --task {{taskId}} .` | missing | none | coordinator |
| RC-CHECK | RC-SCAFFOLD | planned | 0 | Release package validation report | `harness preset run release-closeout check --task {{taskId}} .` | missing | none | coordinator |
