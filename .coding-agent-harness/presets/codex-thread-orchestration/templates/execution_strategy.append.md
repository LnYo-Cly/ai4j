## Codex Thread Orchestration

This section defines the coordinator method for Codex background threads. It is intentionally conceptual and runtime-specific: Harness can generate this protocol into the task package, but npm Harness does not create or manage Codex App threads by itself.

### Coordinator Responsibilities

| Responsibility | Required Handling |
| --- | --- |
| Workstream design | Split only genuinely independent work into background threads. |
| Prompt packet | Give each thread enough context to operate without hidden coordinator state. |
| Scope control | Define allowed scope, forbidden scope, mode, and stop conditions before dispatch. |
| Handoff intake | Read every worker thread before using its findings or commits. |
| Evidence capture | Copy durable outcomes into `progress.md`, `review.md`, or task-local artifacts. |
| Closeout | Archive or unpin completed worker threads only after evidence capture. |

### Thread Registry

Maintain this registry as threads are created, read, blocked, completed, or archived.

| Workstream | Thread Title | Thread ID | Mode | Scope | Status | Last Read | Handoff Evidence | Next Action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| coordinator | pending | current thread | integration-owner | whole task | active | n/a | `progress.md` | decide worker split |
| worker-1 | pending | pending | read-only / writable | pending | not-started | never | pending | create or skip |

### Dispatch Prompt Requirements

Every worker thread prompt must include:

- task id and title,
- role: coordinator support, read-only researcher, read-only reviewer, or writable worker,
- required reads,
- assigned scope,
- forbidden scope,
- mode: read-only or writable,
- worktree or local path when writable work is allowed,
- verification commands or evidence expectations,
- stop conditions,
- exact handoff format.

### Handoff Format

Each worker thread should report:

| Field | Required Content |
| --- | --- |
| Thread ID | Codex thread id, or `unavailable`. |
| Thread title | Human-readable title used in Codex. |
| Mode | `read-only`, `writable`, or `research`. |
| Scope covered | Files, docs, task sections, or topic boundaries covered. |
| Changes made | Commit SHA or `n/a`. |
| Verification | Commands, inspections, or review checks run. |
| Findings | Confirmed findings with references. |
| Residual risk | Assumptions, skipped checks, or blockers. |

### Fallback

If Codex thread tools are unavailable, do not invent thread evidence. Record:

```text
Thread fallback: thread-tools-unavailable; using normal coordinator workflow.
```

Then continue with the smallest adequate coordination model and keep all normal task evidence requirements intact.
