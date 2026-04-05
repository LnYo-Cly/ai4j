# JLine Status Observability Design

Date: 2026-03-27

## Goal

Improve long-wait observability in the JLine CLI so the user can tell whether the agent is:

- opening a model stream,
- still receiving output,
- temporarily quiet,
- or likely stuck and safe to interrupt with `Esc`.

This is a P0 UX fix for the main-buffer and JLine status line. It is not a full execution timeline redesign.

## Problem

Today the CLI can appear frozen during long model waits:

- the spinner keeps moving, but the user cannot tell whether the model is connecting, thinking, or stalled;
- a half-finished turn can leave the user unsure whether the process is still alive;
- there is no explicit escalation from normal waiting to a more actionable stalled state.

This is especially confusing when the provider stream is slow or the network is unstable.

## Chosen Approach

Use a small state machine in `JlineShellTerminalIO` and surface it through the existing compact status line:

- `Connecting` when a model request starts;
- `Responding` while output is actively streaming;
- `Waiting` after a configurable silence window;
- `Stalled` after a larger configurable silence window, with an explicit `press Esc to interrupt` message;
- keep `Working` for tool execution and `Thinking` for pre-stream model work.

The CLI keeps the existing spinner and status-line rendering model. The change is state-aware rather than layout-heavy.

## Why This Approach

This is the smallest change that fixes the trust problem:

- it reuses the current status bar instead of adding a new pane;
- it provides clear state transitions without inventing backend behavior;
- it keeps future room for real retry accounting once provider-side reconnect loops exist.

We explicitly do not show fake retry counters such as `Retrying (1/5)` unless the runtime actually performs tracked retries.

## Event Mapping

- `MODEL_REQUEST` -> `Connecting`
- first model output/reasoning delta -> `Responding` or `Thinking`
- tool call start -> `Working`
- no new progress for `waiting-ms` -> `Waiting`
- no new progress for `stalled-ms` -> `Stalled`

Progress timestamps are measured with a monotonic clock so waiting/stalled transitions are stable across platforms.

## Configuration

System properties control the thresholds:

- `ai4j.jline.status-tick-ms`
- `ai4j.jline.waiting-ms`
- `ai4j.jline.stalled-ms`

Defaults remain conservative for normal use. Tests can lower these thresholds to exercise state transitions quickly.

## Non-Goals

- no provider/runtime reconnect implementation in this change;
- no fake retry counters;
- no new execution panel or timeline UI;
- no append-only/raw TUI parity work in this P0.

## Validation

Targeted coverage should verify:

- `Connecting` escalates to `Waiting`/`Stalled` when no model events arrive;
- `Responding` becomes `Waiting` when the stream goes quiet;
- existing turn interruption behavior remains intact;
- the compact status line still renders correctly in dumb terminals.
