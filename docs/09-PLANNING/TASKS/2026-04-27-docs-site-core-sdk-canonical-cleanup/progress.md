# docs-site Core SDK Canonical Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-27 23:40 - Diagnose

- What changed: scanned the `core-sdk/` docs tree, confirmed the current sidebar routes, and identified the main issue as thin canonical entry pages plus a remaining alternate path through legacy `chat/`, `responses/`, and older capability pages.
- Verification: `rg -n "(/docs/core-sdk/|\\]\\(/docs/core-sdk/)" docs-site/docs/core-sdk`
- Next: strengthen the canonical pages and normalize the main legacy guidance links.

### 2026-04-27 23:55 - Canonical cleanup

- What changed: rewrote the main `Core SDK` canonical entry pages so they explain purpose, boundaries, and reading order directly, then normalized the major legacy guidance links in the old `chat/`, `responses/`, and selected root-level capability pages.
- Verification: targeted `rg` scan for legacy `core-sdk` route guidance outside `chat/` and `responses/` returned clean.
- Next: run docs-site regression and close out the task.

### 2026-04-28 00:05 - Regression and closeout

- What changed: ran the docs-site regression, confirmed `tsc` passed, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `npm run typecheck`; `npx docusaurus build --out-dir build-core-sdk-canonical-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
