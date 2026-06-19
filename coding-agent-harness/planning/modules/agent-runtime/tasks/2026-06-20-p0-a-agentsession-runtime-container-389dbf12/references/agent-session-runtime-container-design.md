# AgentSession runtime container design

## Design boundary

P0-A only creates the durable runtime container foundation for `ai4j-agent`. It intentionally stops before compact, sandbox, runner, fork, rewind, artifact, and CLI UX work.

## Runtime shape

```text
Agent.newSession()
  -> AgentSessionMetadata.create()
  -> InMemoryAgentSessionEventLog
  -> AgentContext.toBuilder()
       .memory(memorySupplier.get())
       .eventPublisher(sessionPublisher(baseListeners + eventLog))
  -> AgentSession(runtime, sessionContext, metadata, eventLog, store)
```

## Public contracts

- `AgentSession.getSessionId()` returns stable id.
- `AgentSession.getMetadata()` returns a defensive copy.
- `AgentSession.getMetadataAttributes()` returns a defensive copy.
- `AgentSession.snapshot()` returns defensive copies of metadata, memory and events.
- `AgentSession.restore(snapshot)` restores memory, metadata and events into the current session object.
- `AgentSession.save()` stores the current snapshot only if a store is configured.
- `Agent.resumeSession(sessionId)` requires a configured store and restores the saved snapshot into a fresh session runtime context.

## Compatibility rule

`Agent.run(...)`, `runStream(...)`, and `runStreamResult(...)` continue to use the base context. Only `Agent.newSession()` opts into session-local event log and store semantics.

## Security note

Session snapshots and event payloads can contain prompt text, model output, tool arguments, and tool results. Production stores must apply their own tenant isolation, retention, and redaction policy.
