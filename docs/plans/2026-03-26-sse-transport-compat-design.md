# SSE Transport Compatibility Design

Date: 2026-03-26

## Problem

`SseTransport` can open the SSE connection and receive the initial `endpoint` event, but it times out waiting for the server's `message` event on the real ModelScope `fetch` MCP endpoint. `stdio` and `streamable_http` do not have this issue, so the fault is isolated to the legacy SSE transport receive path.

## Validated Root Cause

- The current implementation uses OkHttp `EventSource` for SSE receive.
- Real validation against `https://mcp.api-inference.modelscope.net/1e1a663049b340/sse` shows the server does send the `message` event after `initialize`.
- The same endpoint works when the SSE stream is read manually with Python `requests`, Java `HttpClient`, and Java 8 `HttpURLConnection`.
- Therefore the compatibility bug is in the current OkHttp SSE receive path, not in MCP initialization, POST sending, or the remote server.

## Chosen Fix

Replace the SSE receive path in `SseTransport` with a Java 8 compatible manual SSE reader built on `HttpURLConnection`, while keeping the existing OkHttp POST sending path.

## Design

### Receive path

- Open the SSE stream with `HttpURLConnection`.
- Read UTF-8 lines manually.
- Parse standard SSE fields: `event`, `data`, and `id`.
- Treat unnamed events as default `message` events.
- Join multiple `data:` lines with `\\n` per the SSE standard.
- Dispatch `endpoint` and `message` events to the existing handlers.

### Send path

- Keep the existing OkHttp POST implementation.
- Broaden `Accept` to `application/json, text/event-stream` for better transport compatibility.

### Shutdown behavior

- Track the reader thread, connection, and input stream explicitly.
- On stop, close the input stream, disconnect the HTTP connection, interrupt the reader thread, and wait briefly for it to exit.
- Preserve the existing public transport contract.

## Testing

- Add a local unit test that simulates a legacy SSE MCP server and verifies:
  - explicit `event: message` delivery
  - unnamed default `message` delivery
  - multi-line `data:` reassembly
- Rebuild the CLI fat jar and validate the real `fetch` MCP endpoint end to end.

## Notes

- This fix only changes the legacy SSE transport.
- `streamable_http` remains the preferred path for current MCP servers aligned with the `2025-03-26` transport spec.
