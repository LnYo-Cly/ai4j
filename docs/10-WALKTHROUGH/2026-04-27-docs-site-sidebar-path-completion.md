# docs-site Sidebar Path Completion

## Overview

Completed the sidebar-first docs-site completion stream so the canonical reading path now works as both an onboarding funnel and an interview-grade architecture reference.

## Scope

- Modules changed: `docs-site`, `docs/05-TEST-QA`, `docs/09-PLANNING`, `docs/10-WALKTHROUGH`
- Files added: `docs/10-WALKTHROUGH/2026-04-27-docs-site-sidebar-path-completion.md`
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Execution order | finish the site in sidebar-first waves | it matched the approved “先宣传，再进阶” reading journey and kept the highest-traffic path coherent first |
| Agent wave strategy | rewrite the real sidebar pages instead of the already-richer flat deep pages | the bottleneck had shifted from information availability to canonical-page quality |
| Flowgram framing | explicitly separate `Flowgram.ai` as the ByteDance open-source frontend library from AI4J's Java backend runtime layer | the previous wording blurred ownership and made the architecture hard to explain |
| Solutions strategy | treat `solutions/` as scenario-entry pages that point back to canonical modules and legacy deep guides | the repo already had deeper detail pages; what was missing was a strong entry layer |
| Regression stance | keep `RG-008` honest as partial even after successful typecheck and targeted builds | the literal owning primary command `npm run build` was still not rerun in this cycle |

## Verification

- Commands run: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-start-here-wave1-verify`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-core-sdk-wave2-verify`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-spring-boot-wave3-verify`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-agent-coding-flowgram-solutions-verify`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- `RG-008` still records `partial` because the owning primary `npm run build` path was not rerun in this cycle.

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-27-docs-site-sidebar-path-completion/task_plan.md`
- Feature SSoT row: `F-015`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `main`, current workspace; no dedicated worktree was created because this was a continuation docs-site completion stream on the already-active workspace
- Commit: pending
