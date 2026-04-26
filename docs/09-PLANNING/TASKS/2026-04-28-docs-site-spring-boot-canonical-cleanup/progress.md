# docs-site Spring Boot Canonical Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 00:20 - Diagnose

- What changed: scanned the `spring-boot/` subtree and the adjacent `start-here` entry pages, then identified the main issue as thin canonical pages plus lingering guidance back to the old `getting-started` route cluster.
- Verification: `rg -n "(/docs/spring-boot/|\\]\\(/docs/spring-boot/)" docs-site/docs/spring-boot docs-site/docs`
- Next: strengthen the six canonical pages and normalize immediate guidance links.

### 2026-04-28 00:35 - Canonical cleanup

- What changed: rewrote the six `Spring Boot` canonical pages so they explain module role, configuration boundary, Bean extension, and reading order directly, then normalized the immediate old `getting-started` guidance in `spring-boot/` and adjacent `start-here` pages.
- Verification: targeted `rg` scan for old `getting-started` Spring Boot guidance in `spring-boot/` and `start-here/` returned clean.
- Next: run docs-site regression and close out the task.

### 2026-04-28 00:45 - Regression and closeout

- What changed: ran the docs-site regression, confirmed `tsc` passed, and recorded the recurring Windows `EPERM` residual after successful Docusaurus client/server bundle compilation.
- Verification: `npm run typecheck`; `npx docusaurus build --out-dir build-spring-boot-canonical-verify`
- Next: update SSoT, cadence, and walkthrough records.

## Residual

- Windows may still lock Docusaurus output or webpack cache artifacts during final cleanup even when bundle compilation itself succeeds
