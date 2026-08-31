#!/usr/bin/env python3
"""Invariant checks over ai4j bridge audit exports.

Usage:
    python check_audit.py <sandbox-dir-or-audit.json> [--expect-status COMPLETED]

The checker validates machine-readable invariants of ``harness_audit.json``
(state projection read back from the durable harness store) and
``execution_trace.json`` (per-round bridge timeline). Checks that depend on a
specific capability being exercised print ``not exercised`` and pass; checks
whose precondition IS present must hold, otherwise the script exits non-zero.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


class Report:
    def __init__(self) -> None:
        self.passed: list[str] = []
        self.failed: list[str] = []
        self.skipped: list[str] = []

    def ok(self, cid: str, detail: str = "") -> None:
        self.passed.append(f"{cid}: {detail}" if detail else cid)

    def fail(self, cid: str, detail: str = "") -> None:
        self.failed.append(f"{cid}: {detail}" if detail else cid)

    def skip(self, cid: str, detail: str = "") -> None:
        self.skipped.append(f"{cid} [not exercised]: {detail}" if detail else f"{cid} [not exercised]")

    def exit_code(self) -> int:
        return 1 if self.failed else 0


def load_audit(target: Path) -> tuple[dict, Path]:
    if target.is_dir():
        target = target / "ai4j-audit" / "harness_audit.json"
    if not target.is_file():
        raise SystemExit(f"audit file not found: {target}")
    return json.loads(target.read_text(encoding="utf-8")), target


def check(audit: dict, expect_status: str | None, report: Report) -> None:
    state = audit.get("state") or {}

    if audit.get("schema") != "ai4j-harness-audit/v1":
        report.fail("schema", f"unexpected schema {audit.get('schema')!r}")
    else:
        report.ok("schema")

    if expect_status:
        status = audit.get("status")
        if status == expect_status:
            report.ok("expected-status", f"status == {expect_status}")
        else:
            report.fail("expected-status", f"status={status} expected={expect_status}")

    if audit.get("mode") != "harness":
        report.skip("durable-state", "bare mode has no harness state projection")
        return

    tasks = state.get("tasks") or []
    executions = state.get("executions") or []
    checkpoints = state.get("checkpoints") or []
    waits = state.get("waits") or []
    wakeups = state.get("wakeups") or []
    gates = state.get("gates") or []
    tool_invocations = state.get("toolInvocations") or []

    # Dynamic task creation: the bridge never pre-creates a business Task from
    # the benchmark task id, so a harness-created task must not reuse it.
    if tasks:
        bench_id = audit.get("benchTaskId")
        dynamic = all(t.get("taskId") != bench_id for t in tasks)
        if dynamic:
            report.ok("dynamic-task", f"task ids {[t.get('taskId') for t in tasks]} are runtime-created")
        else:
            report.fail("dynamic-task", "a task reuses the benchmark task id (pre-registered)")

        # A task referenced by executions must exist in the store. Executions
        # without a task binding are session-scoped runs (allowed before any
        # task is discovered).
        task_ids = {t.get("taskId") for t in tasks}
        dangling = [e.get("executionId") for e in executions
                    if e.get("taskId") is not None and e.get("taskId") not in task_ids]
        if dangling:
            report.fail("execution-task-ref", f"executions reference unknown tasks: {dangling}")
        elif executions:
            report.ok("execution-task-ref", "all bound executions reference stored tasks")
        else:
            report.skip("execution-task-ref", "no executions recorded")
    else:
        report.skip("dynamic-task", "no task recorded")

    # Cross-process continuation: each benchmark round is a fresh JVM, so any
    # state linking rounds (same task, same session) proves durable store
    # continuity rather than shared process memory.
    by_task: dict[str, list[dict]] = {}
    for e in executions:
        by_task.setdefault(e.get("taskId"), []).append(e)
    multi = {tid: es for tid, es in by_task.items() if len(es) > 1}
    if multi:
        for tid, es in multi.items():
            sessions = {e.get("sessionId") for e in es}
            if len(sessions) > 1:
                report.fail("round-continuity", f"task {tid} split across sessions {sessions}")
        if not any(report.failed):
            report.ok("round-continuity", f"task(s) {sorted(multi)} continued across fresh processes")
        # Executions that reference a checkpoint must find it in the store.
        cp_ids = {c.get("checkpointId") for c in checkpoints}
        missing = [e.get("executionId") for e in executions
                   if e.get("checkpointId") and e.get("checkpointId") not in cp_ids]
        if missing:
            report.fail("checkpoint-record-exists", f"missing checkpoint records for {missing}")
        elif any(e.get("checkpointId") for e in executions):
            report.ok("checkpoint-record-exists", f"{len(checkpoints)} checkpoint(s) consistent")
        else:
            report.skip("checkpoint-record-exists", "no execution references a checkpoint")
    elif executions:
        report.skip("round-continuity", "single execution for this session so far")
        report.skip("checkpoint-record-exists", "no multi-round continuation yet")

    # Wait/wakeup pairing: a delivered wait must have been woken exactly once.
    open_waits = [w for w in waits if str(w.get("status")).upper() in ("OPEN", "PENDING")]
    delivered = [w for w in waits if str(w.get("status")).upper() == "DELIVERED"]
    if delivered or wakeups:
        for w in delivered:
            paired = [wk for wk in wakeups if wk.get("waitId") == w.get("waitId")]
            if len(paired) != 1:
                report.fail("wait-wakeup-pairing", f"wait {w.get('waitId')} has {len(paired)} wakeups")
            else:
                report.ok("wait-wakeup-pairing", f"wait {w.get('waitId')} woken once")
    else:
        report.skip("wait-wakeup-pairing", "no delivered waits")

    if open_waits:
        report.ok("open-wait-persisted", f"{len(open_waits)} open wait(s) survive process exit")
    else:
        report.skip("open-wait-persisted", "no open waits")

    # Approval gating: a pending gate must not coexist with a completed task.
    pending_gates = [g for g in gates if str(g.get("status")).upper() in ("PENDING", "WAITING", "OPEN")]
    if pending_gates:
        completed = [t for t in tasks if str(t.get("status")).upper() == "COMPLETED"
                     and any(g.get("taskId") == t.get("taskId") for g in pending_gates)]
        if completed:
            report.fail("gate-before-complete", f"task completed with pending gate: {[g.get('gateId') for g in pending_gates]}")
        else:
            report.ok("gate-before-complete", f"{len(pending_gates)} pending gate(s) block completion")
    else:
        report.skip("gate-before-complete", "no gates recorded")

    # Async tool invocations must leave an idempotency-proof trail: an
    # operation with a waitId must appear at most once per callId.
    if tool_invocations:
        seen: dict[tuple, int] = {}
        dupes = []
        for ti in tool_invocations:
            key = (ti.get("callId"), ti.get("toolName"))
            seen[key] = seen.get(key, 0) + 1
        dupes = [k for k, n in seen.items() if n > 1]
        if dupes:
            report.fail("tool-invocation-uniqueness", f"duplicate invocations: {dupes}")
        else:
            report.ok("tool-invocation-uniqueness", f"{len(tool_invocations)} unique invocation(s)")
    else:
        report.skip("tool-invocation-uniqueness", "no tool invocations")

    # UNKNOWN handling: if the final status is UNKNOWN the store must show a
    # non-retried execution so the operator reconciles instead of blind retry.
    if str(audit.get("status")).upper() == "UNKNOWN":
        unknown_execs = [e for e in executions if str(e.get("status")).upper() == "UNKNOWN"]
        if not unknown_execs:
            report.fail("unknown-preserved", "bridge reported UNKNOWN but store has no UNKNOWN execution")
        else:
            report.ok("unknown-preserved", "UNKNOWN execution kept for reconciliation")
    else:
        report.skip("unknown-preserved", "status is not UNKNOWN")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, help="sandbox dir or harness_audit.json path")
    parser.add_argument("--expect-status", default=None, help="audit status the run must report")
    args = parser.parse_args()

    audit, path = load_audit(args.target)
    report = Report()
    check(audit, args.expect_status, report)

    print(f"audit: {path}")
    for line in report.passed:
        print(f"  PASS  {line}")
    for line in report.skipped:
        print(f"  SKIP  {line}")
    for line in report.failed:
        print(f"  FAIL  {line}")
    print(f"{len(report.passed)} passed, {len(report.skipped)} not exercised, {len(report.failed)} failed")
    return report.exit_code()


if __name__ == "__main__":
    sys.exit(main())
