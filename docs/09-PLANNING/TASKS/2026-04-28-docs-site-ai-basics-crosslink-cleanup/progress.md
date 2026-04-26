# docs-site ai-basics Crosslink Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 02:10 - Diagnose

- What changed: scanned residual old-route links outside the main canonical trees and identified a small set concentrated in `ai-basics/`.
- Verification: `rg -n "/docs/getting-started/|/docs/flowgram/builtin-nodes" docs-site/docs/ai-basics`
- Next: replace those residual links with the closest current canonical routes.

### 2026-04-28 02:20 - Crosslink cleanup

- What changed: replaced the remaining identified `ai-basics/` links that still pointed to downgraded `getting-started` or old Flowgram routes, mapping them to the closest current canonical pages.
- Verification: targeted `rg` scan for the three old route patterns returned clean.
- Next: run docs-site regression and close out the task.

### 2026-04-28 02:30 - Regression and closeout

- What changed: reran docs-site regression, confirmed `tsc` passed with the larger local Node heap, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; `npx docusaurus build --out-dir build-ai-basics-crosslink-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
- In this workspace, `npm run typecheck` may need a larger local Node heap (`NODE_OPTIONS=--max-old-space-size=8192`) to avoid V8 out-of-memory failures
