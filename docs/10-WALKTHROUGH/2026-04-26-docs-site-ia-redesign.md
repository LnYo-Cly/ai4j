# docs-site IA Redesign Walkthrough

## Overview

Restructured `docs-site` around the approved `Start Here -> Core SDK -> Spring Boot -> Agent / Coding Agent / Flowgram -> Solutions` information architecture and aligned the main entry pages to the new canonical paths.

## Scope

- Modules changed: `docs-site`, harness tracking under `docs/`
- Files added: new docs trees under `docs-site/docs/start-here`, `core-sdk`, `spring-boot`, `agent` wrappers, `coding-agent` wrappers, `flowgram` wrappers, and `solutions`
- Files removed: none
- External surfaces touched: `docs-site`

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Base narrative | make `Core SDK` the single base layer | removes the old `ai-basics` vs `core-sdk` split and gives one stable mental model |
| Capability layering | keep `Tools`, `Skills`, and `MCP` as peer sections under `Core SDK` | `Function Call`, `Skill`, and `MCP` solve different problems and should not be collapsed into one branch |
| Onboarding strategy | keep `Start Here` lightweight and path-oriented | supports the user's requirement of "先宣传/入门，再详细讲解/进阶" |
| Legacy deep pages | keep old trees as bridge content for now | reduces rewrite churn while the new canonical navigation settles |
| Regression judgment | accept IA migration as complete with an environment residual | repeated builds showed structural validity; the remaining blocker is Windows file locking, not docs routing |

## Verification

- Commands run: `npm run typecheck`; `npm run build`; `npx docusaurus build --out-dir build-redesign-verify`; `npx docusaurus build --out-dir build-redesign-verify-2`
- Regression gates: `RG-008`
- Result: partial
- Evidence depth reached: `L2`

## Residual

- Windows may lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even after successful bundle compilation
- Legacy detailed pages under old trees still exist as bridge content and are not yet fully rewritten into the new canonical IA

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-26-docs-site-ia-redesign/task_plan.md`
- Feature SSoT row: `F-003`
- Regression SSoT gates: `RG-008`
- Branch / Worktree: `feature/chat-memory-summary-policy` in the current workspace `G:\My_Project\java\ai4j-sdk`
- Commit: `pending`
