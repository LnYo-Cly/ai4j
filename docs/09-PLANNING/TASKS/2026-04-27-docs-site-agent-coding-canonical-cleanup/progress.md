# docs-site Agent and Coding Agent Canonical Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-27 22:45 - Diagnose

- What changed: scanned the `agent/` and `coding-agent/` docs subtrees, confirmed the current sidebar routes, and identified the main old-path residue that still pulls readers back to legacy route names.
- Verification: `rg -n "(/docs/(agent|coding-agent)/|\\]\\(/docs/(agent|coding-agent)/)" docs-site/docs/agent docs-site/docs/coding-agent`
- Next: strengthen the canonical pages and normalize the main subtree links.

### 2026-04-27 23:10 - Canonical cleanup

- What changed: rewrote the main `Agent` and `Coding Agent` canonical pages so they explain the current module boundaries and canonical reading path, then normalized the major old-route references inside both docs subtrees.
- Verification: targeted `rg` scans for the old `agent/` and `coding-agent/` route patterns returned clean.
- Next: run docs-site regression and close out the task.

### 2026-04-27 23:20 - Regression and closeout

- What changed: ran the docs-site regression, confirmed `tsc` passed, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `npm run typecheck`; `npx docusaurus build --out-dir build-agent-coding-canonical-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
