# docs-site Entry Path Residue Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 00:55 - Diagnose

- What changed: scanned `Start Here` and nearby canonical pages, then isolated the remaining high-impact old-route guidance that still affects the homepage-to-canonical reading path.
- Verification: `rg -n "/docs/(getting-started/|agent/(minimal-react-agent|use-cases-and-paths)|flowgram/use-cases-and-paths|coding-agent/provider-profiles)" docs-site/docs/start-here docs-site/docs/core-sdk/model-access docs-site/docs/glossary docs-site/docs/intro.md`
- Next: normalize those entry-path links to the current canonical routes.

### 2026-04-28 01:05 - Entry cleanup

- What changed: updated `choose-your-path`, `quickstart-java`, `model-access/multimodal`, and `glossary` so they point readers to the current Agent, Flowgram, Core SDK, and Coding Agent canonical routes instead of deprecated entry paths.
- Verification: targeted `rg` scan for the old entry-path patterns returned clean.
- Next: run docs-site regression and close out the task.

### 2026-04-28 01:15 - Regression and closeout

- What changed: ran the docs-site regression, confirmed `tsc` passed, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `npm run typecheck`; `npx docusaurus build --out-dir build-entry-path-residue-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
