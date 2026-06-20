## Codex Thread Orchestration Preset

This task uses the `codex-thread-orchestration` preset. This is a Codex-only method preset for complex tasks that benefit from human-visible background thread coordination.

Codex threads are a coordination layer, not the durable source of truth. The task package remains responsible for scope, acceptance criteria, thread registry, handoffs, verification, review, residual risk, and closeout.

### Required Boundaries

- Use this preset only when the coordinator is running in Codex with background thread tools available.
- Do not assume other agents, npm-only Harness users, or non-Codex runtimes can create or steer Codex threads.
- Do not use background threads for vague work. Each worker thread needs explicit scope, mode, stop conditions, and handoff format.
- Do not treat a thread transcript as sufficient evidence. Summarize durable decisions and handoffs back into this task package.
- If thread tools are unavailable, record the fallback and continue with normal single-thread or subagent coordination.

### Thread Planning Checklist

| Item | Decision |
| --- | --- |
| Coordinator thread title | pending |
| Worker threads needed? | no / yes |
| Independent workstreams | pending |
| Writable worker threads | no / yes |
| Read-only reviewer threads | no / yes |
| Evidence location | `progress.md`, `review.md`, or task-local artifacts |

### Acceptance Addendum

- Every created worker thread is listed in the thread registry.
- Every worker dispatch prompt includes scope, forbidden scope, mode, stop conditions, and handoff format.
- Every worker handoff is read by the coordinator before integration.
- Final task evidence records thread IDs or an explicit `thread-tools-unavailable` fallback.
