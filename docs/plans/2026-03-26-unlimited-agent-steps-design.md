# Unlimited Agent Steps Design

Date: 2026-03-26

## Goal

Align CLI and SDK runtime semantics so agent steps are unlimited by default, and remove the separate tool-call budget.

## Decisions

1. `maxSteps <= 0` means unlimited.
2. Default `maxSteps` becomes `0` in both SDK and CLI.
3. Remove `maxToolCalls` from:
   - `AgentOptions`
   - CLI option parsing and help text
   - runtime loop control
   - related tests and docs
4. Keep workflow-level limits such as `StateGraphWorkflow.maxSteps` unchanged. Those are graph safety guards, not agent-loop budgets.

## Rationale

- A separate tool-call budget adds another stopping condition that is hard to reason about in practice.
- Users expect the agent loop to stop when the model stops, not because an internal counter hits a default threshold.
- A single step budget with explicit unlimited semantics is easier to document and debug.

## Runtime Semantics

- `maxSteps > 0`: stop when `step >= maxSteps`
- `maxSteps <= 0`: no step cap
- Tool execution is no longer capped by a separate `maxToolCalls` counter

## Risk

Removing both default caps makes bad prompts or provider/tool combinations more likely to loop for too long.

## Follow-up

Add a separate loop-stall guard later, for example:

- repeated identical tool call detection
- consecutive empty-output detection
- final-text-only recovery when an explicit positive step limit is hit
