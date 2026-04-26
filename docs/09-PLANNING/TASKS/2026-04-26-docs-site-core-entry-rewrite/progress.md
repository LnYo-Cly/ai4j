# docs-site Core Entry Rewrite - Progress

## Status

`completed`

## Log

### 2026-04-26 00:00 - Task registration and repo alignment

- What changed: created the design record and planning task, reviewed the five target pages, and re-checked the root/module structure from the current Maven repo
- Verification: file review of `README.md`, root `pom.xml`, module `pom.xml` files, and the target docs pages
- Next: rewrite the five pages and run `RG-008`

### 2026-04-26 00:00 - Core entry rewrite and regression

- What changed: rewrote `why-ai4j`, `architecture-at-a-glance`, `core-sdk/overview`, `core-sdk/strengths-and-differentiators`, and `core-sdk/architecture-and-module-map` around stronger positioning, boundary explanation, and reading-path guidance
- Verification: `npm run typecheck` passed; fresh Docusaurus build compiled client/server bundles successfully and failed only during Windows artifact cleanup with `EPERM` locks in output/cache files
- Next: close the feature with walkthrough and keep the Windows build lock as a known docs-site residual

## Residual

- Windows `EPERM` locks still prevent a clean final Docusaurus artifact write even when docs compilation succeeds
