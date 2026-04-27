# Harness Bootstrap Walkthrough

## Overview

Established the first complete coding-agent harness baseline for the `ai4j-sdk` monorepo.

## Scope

- Modules changed: no production Java or frontend modules; repo governance and docs surfaces only
- Files added: `docs/` harness skeleton, `docs/11-REFERENCE/` standards, planning templates, SSoT files, cadence file, walkthrough templates
- Files removed: none
- External surfaces touched: none

## Key Decisions

| Decision | Choice | Reason |
|----------|--------|--------|
| Harness size | Full | the repository is an 8-module monorepo with multiple user and integration surfaces |
| Legacy docs handling | preserve in place | avoids risky migration while still moving all new work onto the numbered harness layout |
| Root repo charter | replace `AGENTS.md` | the old file materially misrepresented the current repository shape |
| Worktree naming | align to `feature/` / `fix/` / `docs/` | matches current repo branch practice instead of forcing generic template prefixes |
| Initial regression batch | mapping-first | the repo did not yet have one deterministic full-batch command spanning core live-provider and frontend dependency realities |

## Verification

- Commands run:
  - structural path existence check for the minimum deliverables
  - `AGENTS.md` line-count check
  - reference-file count check
  - `git status --short`
- Regression gates: `RG-001` to `RG-009` were established and linked through the Cadence Ledger
- Result: bootstrap deliverables pass; executable regression batch intentionally left mapped-but-not-run
- Evidence depth reached: L2 local_smoke for harness structure validation

## Residual

- `R-001`: no repo-wide GitHub Actions workflow yet runs the core Maven regression gates
- `R-002`: live-provider validation still depends on local credentials and needs explicit opt-in gate normalization
- `R-003`: `ai4j-flowgram-webapp-demo` still lacks real test scripts beyond lint/type/build baselines

## Links

- Task Plan: `docs/09-PLANNING/TASKS/2026-04-26-harness-bootstrap/`
- Feature SSoT row: `F-001`
- Regression SSoT gates: `RG-001` to `RG-009`
- Branch / Worktree: current workspace on `feature/chat-memory-summary-policy`; no dedicated worktree because bootstrap was requested directly in place
- Commit: pending
