# Esc Stream Cancel Design

## Goal

Make `Esc` in the JLine main-buffer shell behave like a real interruption:

- stop the active model stream
- print `Conversation interrupted by user.`
- return to the `> ` prompt without waiting for the remote stream to finish

## Chosen Approach

Use a two-part cancellation path:

1. `CodingCliSessionRunner` marks the turn interrupted, interrupts the worker thread, and actively cancels any registered model stream for that worker.
2. `ChatModelClient` and `ResponsesModelClient` register the active SSE listener per worker thread so the CLI can cancel the underlying stream immediately.

## Stream Handling Changes

- `SseListener` now stores the `EventSource` on `onOpen(...)` and exposes `cancelStream()`.
- `ResponseSseListener` now stores the `EventSource` on `onOpen(...)` and exposes `cancelStream()`.
- Cancellation suppresses normal error reporting from SSE failure callbacks so user-triggered interrupts are not surfaced as provider errors.

## Provider Integration

Responses services delegate `onOpen(...)`, `onFailure(...)`, and `onClosed(...)` back to `ResponseSseListener` so cancellation can reach the actual `EventSource`.

## Verification Target

- `Esc` interrupts an active main-buffer turn
- the active SSE stream is cancelled
- no final assistant output is flushed after interruption
- the CLI loop resumes cleanly
