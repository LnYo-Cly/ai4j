# docs-site IA Redesign - Progress

## Status

`completed`

## Log

### 2026-04-26 00:00 - IA design approval and harness registration

- What changed: finalized the information-architecture direction with the user, wrote `docs/plans/2026-04-26-docs-site-ia-redesign-design.md`, and opened this task directory under the numbered planning flow
- Verification: approved design discussion, design document present, and task three-piece created under `docs/09-PLANNING/TASKS/2026-04-26-docs-site-ia-redesign/`
- Next: update the live `docs-site` sidebar and directory skeleton, then start migrating high-priority pages

### 2026-04-26 00:00 - Sidebar and skeleton migration

- What changed: replaced `docs-site/sidebars.ts` with the new top-level IA, rewrote `intro.md`, added `Start Here`, `Spring Boot`, `Solutions`, and a new `Core SDK` skeleton with `Model Access`, `Tools`, `Skills`, `MCP`, `Memory`, `Search & RAG`, and `Extension` sections
- Verification: file review plus directory scan confirmed the new docs trees exist under `docs-site/docs/`
- Next: validate the docs build, capture residual route gaps, and decide the next content migration slice

### 2026-04-26 00:00 - Docs-site validation

- What changed: updated `docusaurus.config.ts` include rules so the new docs trees are actually loaded, then ran `npm run typecheck` and Docusaurus build validation
- Verification: `npm run typecheck` passed; Docusaurus resolved the new sidebar/doc IDs and compiled client/server bundles successfully, but final build output failed on Windows `EPERM` file-lock errors while unlinking generated files
- Next: treat the current docs structure as syntactically valid, record the Windows build artifact lock as residual, and continue migrating higher-value module pages

### 2026-04-26 00:00 - Entry-page normalization and closeout

- What changed: aligned `intro`, `faq`, `glossary`, `Agent overview`, and `Coding Agent session runtime` to the new canonical IA paths; created walkthrough closeout and updated Feature/Regression harness records
- Verification: `npm run typecheck` passed again; two fresh-output Docusaurus builds compiled client/server bundles successfully and failed only during output cleanup with Windows `EPERM` locks on generated files
- Next: feature closed; remaining legacy deep pages and Windows artifact locking are tracked as residual follow-up, not blockers for the IA migration itself

## Residual

- Windows `EPERM` locks in generated build output still prevent a clean final Docusaurus artifact write despite successful typecheck and bundle compilation
- legacy deep-detail pages under `getting-started`, `ai-basics`, `mcp`, and older module trees are intentionally retained as bridge content and have not been fully rewritten into the new IA
