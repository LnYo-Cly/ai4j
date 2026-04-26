# docs-site Solutions Canonical Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 01:25 - Diagnose

- What changed: scanned the `solutions/` subtree and the corresponding legacy `guides/` pages, then confirmed that the current canonical case-study pages are still almost entirely bridge pages.
- Verification: `rg -n "(/docs/solutions/|\\]\\(/docs/solutions/|/docs/guides/)" docs-site/docs/solutions`
- Next: rewrite the overview and scenario pages into readable canonical entries.

### 2026-04-28 01:45 - Canonical rewrite

- What changed: rewrote `solutions/overview` plus all nine scenario pages so each one now explains the problem, fit, stack, and related canonical reading path directly, while keeping the old guide links only as supplemental deep-detail references.
- Verification: targeted scans confirmed that `guides/` links remain only in the retained “深入实现细节” sections.
- Next: run docs-site regression and close out the task.

### 2026-04-28 01:55 - Regression and closeout

- What changed: reran docs-site regression, confirmed `tsc` passed after increasing the local Node heap, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; `npx docusaurus build --out-dir build-solutions-canonical-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
- In this workspace, `npm run typecheck` may need a larger local Node heap (`NODE_OPTIONS=--max-old-space-size=8192`) to avoid V8 out-of-memory failures
