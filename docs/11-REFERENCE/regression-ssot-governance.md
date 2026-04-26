# Regression SSoT Governance

> Last updated: 2026-04-26

## Purpose

`docs/05-TEST-QA/Regression-SSoT.md` is the control tower for fixed regression surfaces in this repository.

It tracks:

- the active regression gates
- their owning surface
- the primary entrypoint command
- evidence depth
- residual issues that still need work

## ID Conventions

- Regression gates: `RG-001`, `RG-002`, ...
- Residual items: `R-001`, `R-002`, ...
- Shared batches in Cadence Ledger: `SRB-001`, `SRB-002`, ...

## When Updates Are Required

Update the Regression SSoT whenever:

- a new stable surface gets a fixed gate
- a gate's primary command changes
- a gate passes at a higher Evidence Depth
- a gate fails or is paused
- a new residual item is found
- a residual item is resolved

## Evidence Depth For This Repo

| Level | Name | Typical ai4j Example |
|-------|------|----------------------|
| L1 | tests | Module-local JUnit suites |
| L2 | local_smoke | Local package/build or deterministic smoke entrypoints |
| L3 | live | Provider-backed or real demo/backend validation |
| L4 | browser_human_proxy | Browser-driven FlowGram webapp or docs validation |
| L5 | hard_gate | Structured pass/fail automation with non-zero exit semantics |

## Ownership Rules

- The agent or developer who changes a fixed gate is responsible for updating the gate row
- The agent or developer who runs verification is responsible for the `Last Verified` value and notes
- If a gate is mapped in the Cadence Ledger, the SSoT row is the authoritative definition of that gate

## Gate Lifecycle

### Add A Gate

1. Add a new `RG-XXX` row
2. Name the surface precisely
3. Choose one primary entrypoint command
4. Set the current Evidence Depth honestly
5. Add the corresponding trigger mapping in `docs/05-TEST-QA/Cadence-Ledger.md`

### Change A Gate

1. Update the command or notes
2. Update `Last Verified` if the gate was executed
3. Adjust Evidence Depth only when new evidence was actually produced

### Retire A Gate

1. Remove it from active use only when the surface truly disappears or is replaced
2. Record the reason in notes or archive history
3. Clean the stale trigger rules from the Cadence Ledger

## Shared Batch Rules

- Any shared batch log entry must summarize what portion of active gates was executed
- Failed or skipped gates should create or update residual items instead of disappearing silently
- If a batch is mapping-only, state that explicitly in notes

## Separation Of Concerns

- Feature SSoT tracks delivery progress
- Regression SSoT tracks verification truth
- Walkthroughs explain decisions and results

Do not collapse those responsibilities into one document.
