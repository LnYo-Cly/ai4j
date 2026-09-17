#!/usr/bin/env python3
"""Evaluate a completed HarnessBench run without changing official scoring.

The official HarnessBench runner owns ``oracle_result`` and ``scoring``.  This
adapter consumes those already-produced records plus the bridge's durable audit
projection and writes a separate, bounded acceptance artifact.  It never reads
task ``ground_truth`` files, prompts, transcripts, or provider credentials.

Usage::

    python evaluate_acceptance.py results/.../057-interruption-resume.json
    python evaluate_acceptance.py run.json --audit sandbox/ai4j-audit/harness_audit.json

Exit codes are deliberately independent from the official runner: 0 means all
required checks passed, 1 means a required check failed, and 2 means an error
or an unevaluated (NOT_RUN) check.  The generated artifact is suitable for
later aggregation, but it is not merged into HarnessBench's ``combined_score``.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


SCHEMA = "harnessbench-acceptance/v1"
EVALUATOR_ID = "ai4j-harnessbench-acceptance"
EVALUATOR_VERSION = "1.0"
ORACLE_CHECK_VERSION = "official-oracle/v1"
AUDIT_CHECK_VERSION = "ai4j-bridge-audit/v1"
MAX_FINDING_CHARS = 2000
MAX_SUMMARY_CHARS = 500
KNOWN_STATUSES = {"PASS", "FAIL", "ERROR", "NOT_RUN"}


class AcceptanceInputError(ValueError):
    """Raised when a run or audit artifact violates the adapter contract."""


def _bounded(value: Any, limit: int = MAX_FINDING_CHARS) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        text = value
    else:
        try:
            text = json.dumps(value, ensure_ascii=False, sort_keys=True)
        except (TypeError, ValueError):
            text = str(value)
    if len(text) <= limit:
        return text
    return text[:limit] + "..."


def _status(value: Any) -> str:
    normalized = str(value or "").strip().upper()
    return normalized if normalized in KNOWN_STATUSES else "ERROR"


def _numeric_score(value: Any) -> float | None:
    # bool is an int subclass, but is not a score measurement.
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        return None
    score = float(value)
    if not math.isfinite(score) or score < 0.0 or score > 1.0:
        return None
    return score


def _check(
    check_id: str,
    status: str,
    summary: str,
    *,
    required: bool,
    source: str,
    label: str | None = None,
    detail: Any = None,
    weight: Any = None,
    version: str,
) -> dict[str, Any]:
    item: dict[str, Any] = {
        "checkId": check_id,
        "status": _status(status),
        "required": bool(required),
        "source": source,
        "version": version,
        "summary": _bounded(summary, MAX_SUMMARY_CHARS),
    }
    if label is not None:
        item["label"] = _bounded(label, MAX_SUMMARY_CHARS)
    if detail is not None:
        item["detail"] = _bounded(detail)
    if isinstance(weight, (int, float)) and not isinstance(weight, bool):
        item["weight"] = float(weight)
    return item


def _load_json(path: Path, description: str) -> dict[str, Any]:
    if not path.is_file():
        raise AcceptanceInputError(f"{description} not found: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise AcceptanceInputError(f"cannot read {description}: {exc}") from exc
    if not isinstance(value, dict):
        raise AcceptanceInputError(f"{description} must contain a JSON object")
    return value


def _resolve_audit_path(result: dict[str, Any], result_path: Path, explicit: Path | None) -> Path | None:
    if explicit is not None:
        return explicit
    sandbox = result.get("sandbox")
    if isinstance(sandbox, str) and sandbox.strip():
        candidate = Path(sandbox) / "ai4j-audit" / "harness_audit.json"
        if candidate.is_file():
            return candidate
    adapter = result.get("adapter_result")
    if isinstance(adapter, dict):
        metadata = adapter.get("metadata")
        if isinstance(metadata, dict):
            raw = metadata.get("audit") or metadata.get("auditPath")
            if isinstance(raw, str) and Path(raw).is_file():
                return Path(raw)
    candidate = result_path.parent / "harness_audit.json"
    return candidate if candidate.is_file() else None


def _oracle_checks(oracle: Any) -> tuple[list[dict[str, Any]], float | None, list[str]]:
    if not isinstance(oracle, dict):
        return [], None, ["official result has no oracle_result object"]

    score = _numeric_score(oracle.get("outcome_score"))
    checks = oracle.get("checks")
    errors: list[str] = []
    output: list[dict[str, Any]] = []
    if checks is not None:
        if not isinstance(checks, list):
            errors.append("oracle_result.checks must be an array")
        else:
            for index, raw in enumerate(checks):
                if not isinstance(raw, dict):
                    errors.append(f"oracle check {index} is not an object")
                    continue
                check_id = str(raw.get("id") or raw.get("checkId") or "").strip()
                if not check_id:
                    errors.append(f"oracle check {index} has no id")
                    continue
                if "pass" not in raw:
                    errors.append(f"oracle check {check_id} has no pass field")
                    continue
                if not isinstance(raw["pass"], bool):
                    errors.append(f"oracle check {check_id}.pass must be boolean")
                    continue
                output.append(
                    _check(
                        check_id,
                        "PASS" if raw["pass"] else "FAIL",
                        str(raw.get("label") or ("oracle check passed" if raw["pass"] else "oracle check failed")),
                        required=bool(raw.get("required", True)),
                        source="official-oracle",
                        label=raw.get("label"),
                        detail=raw.get("detail"),
                        weight=raw.get("weight"),
                        version=ORACLE_CHECK_VERSION,
                    )
                )

    if not output and not errors:
        if score is None:
            if oracle.get("error"):
                errors.append(f"official oracle error: {_bounded(oracle.get('error'))}")
            else:
                output.append(
                    _check(
                        "oracle_outcome",
                        "NOT_RUN",
                        "oracle supplied neither checks nor a valid outcome_score",
                        required=True,
                        source="official-oracle",
                        version=ORACLE_CHECK_VERSION,
                    )
                )
        else:
            # A fractional outcome is a measurement, not a pass claim.  Treat
            # only a perfect official outcome as a PASS check and retain the
            # numeric score separately in the artifact.
            output.append(
                _check(
                    "oracle_outcome",
                    "PASS" if score >= 1.0 else "FAIL",
                    "official outcome_score is 1.0" if score >= 1.0 else "official outcome_score is below 1.0",
                    required=True,
                    source="official-oracle",
                    detail={"outcome_score": score},
                    version=ORACLE_CHECK_VERSION,
                )
            )
    return output, score, errors


def _execution_lineage(audit: dict[str, Any]) -> tuple[dict[str, Any], list[str]]:
    """Read lineage fields without coercing malformed durable data.

    The bridge writes integer counts and non-empty string ids.  Coercing a
    value such as ``"1"`` or silently treating a malformed array as empty can
    turn a corrupt audit into a passing acceptance, so shape errors are
    returned separately and promoted to an ``ERROR`` check by the caller.
    """

    errors: list[str] = []
    raw_lineage = audit.get("lineage")
    if raw_lineage is not None and not isinstance(raw_lineage, dict):
        errors.append("audit.lineage must be an object")
        raw_lineage = None

    def read_id(container: dict[str, Any], field: str, fallback: Any = None) -> str | None:
        value = container[field] if field in container else fallback
        if value is None:
            return None
        if not isinstance(value, str) or not value.strip():
            errors.append(f"lineage.{field} must be a non-empty string")
            return None
        return value

    def read_count(container: dict[str, Any], field: str, default: int) -> int:
        if field not in container:
            return default
        value = container[field]
        if type(value) is not int or value < 0:
            errors.append(f"lineage.{field} must be a non-negative integer")
            return default
        return value

    if isinstance(raw_lineage, dict):
        execution_ids_raw = raw_lineage["executionIds"] if "executionIds" in raw_lineage else []
        if not isinstance(execution_ids_raw, list):
            errors.append("lineage.executionIds must be an array")
            execution_ids: list[str] = []
        else:
            execution_ids = []
            for index, value in enumerate(execution_ids_raw):
                if not isinstance(value, str) or not value.strip():
                    errors.append(f"lineage.executionIds[{index}] must be a non-empty string")
                else:
                    execution_ids.append(value)
            if len(execution_ids) != len(set(execution_ids)):
                errors.append("lineage.executionIds must not contain duplicates")

        acceptances_raw = raw_lineage["acceptances"] if "acceptances" in raw_lineage else []
        if not isinstance(acceptances_raw, list):
            errors.append("lineage.acceptances must be an array")
            acceptances: list[Any] = []
        else:
            acceptances = list(acceptances_raw)

        latest_status = raw_lineage.get("latestAcceptanceStatus")
        if latest_status is not None:
            normalized_latest = str(latest_status).strip().upper()
            if normalized_latest not in KNOWN_STATUSES:
                errors.append("lineage.latestAcceptanceStatus is invalid")
                latest_status = None
            else:
                latest_status = normalized_latest

        provenance_present = raw_lineage.get("evaluatorProvenancePresent", False)
        if type(provenance_present) is not bool:
            errors.append("lineage.evaluatorProvenancePresent must be boolean")
            provenance_present = False

        lineage = {
            "executionId": read_id(raw_lineage, "executionId", audit.get("executionId")),
            "executionIds": execution_ids,
            "executionCount": len(execution_ids),
            "repairCount": read_count(raw_lineage, "repairCount", max(0, len(execution_ids) - 1)),
            "acceptanceCount": read_count(raw_lineage, "acceptanceCount", len(acceptances)),
            "acceptances": acceptances,
            "latestAcceptanceStatus": latest_status,
            "evaluatorProvenancePresent": provenance_present,
        }
        return lineage, errors

    state = audit.get("state")
    executions = state.get("executions") if isinstance(state, dict) else None
    execution_ids = []
    if executions is not None and not isinstance(executions, list):
        errors.append("state.executions must be an array")
    elif isinstance(executions, list):
        for index, item in enumerate(executions):
            if not isinstance(item, dict):
                errors.append(f"state.executions[{index}] must be an object")
                continue
            value = item.get("executionId")
            if not isinstance(value, str) or not value.strip():
                errors.append(f"state.executions[{index}].executionId must be a non-empty string")
                continue
            execution_ids.append(value)
        if len(execution_ids) != len(set(execution_ids)):
            errors.append("state.executions contains duplicate execution ids")

    acceptances = []
    state_acceptances = state.get("acceptances") if isinstance(state, dict) else None
    if state_acceptances is not None and not isinstance(state_acceptances, list):
        errors.append("state.acceptances must be an array")
    elif isinstance(state_acceptances, list):
        acceptances = list(state_acceptances)

    latest_status = None
    if acceptances:
        valid_acceptances: list[tuple[dict[str, Any], int]] = []
        for index, item in enumerate(acceptances):
            if not isinstance(item, dict):
                errors.append(f"state.acceptances[{index}] must be an object")
                continue
            timestamp = item.get("evaluatedAtEpochMs")
            if timestamp is not None and (type(timestamp) is not int or timestamp < 0):
                errors.append(f"state.acceptances[{index}].evaluatedAtEpochMs must be a non-negative integer")
                timestamp = 0
            raw_status = item.get("status")
            normalized_status = str(raw_status or "").strip().upper()
            if normalized_status not in KNOWN_STATUSES:
                errors.append(f"state.acceptances[{index}].status is invalid")
            valid_acceptances.append((item, timestamp if type(timestamp) is int else 0))
        if valid_acceptances:
            latest = max(valid_acceptances, key=lambda pair: (pair[1], str(pair[0].get("acceptanceId", ""))))[0]
            normalized_status = str(latest.get("status") or "").strip().upper()
            if normalized_status in KNOWN_STATUSES:
                latest_status = normalized_status

    return {
        "executionId": read_id(audit, "executionId"),
        "executionIds": execution_ids,
        "executionCount": len(execution_ids),
        "repairCount": max(0, len(execution_ids) - 1),
        "acceptanceCount": len(acceptances),
        "acceptances": acceptances,
        "latestAcceptanceStatus": latest_status,
        "evaluatorProvenancePresent": any(
            isinstance(item, dict) and item.get("acceptanceProvenance") is not None
            for item in acceptances
        ),
    }, errors


def _projection_rows(state: dict[str, Any], field: str, errors: list[str]) -> list[dict[str, Any]]:
    """Return object rows and report malformed projection arrays explicitly."""

    if field not in state:
        return []
    raw = state[field]
    if not isinstance(raw, list):
        errors.append(f"state.{field} must be an array")
        return []
    rows: list[dict[str, Any]] = []
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            errors.append(f"state.{field}[{index}] must be an object")
        else:
            rows.append(item)
    return rows


def _audit_checks(audit: dict[str, Any], *, require_audit: bool) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    lineage, lineage_errors = _execution_lineage(audit)
    if audit.get("schema") != "ai4j-harness-audit/v1":
        return [
            _check(
                "harness_audit",
                "ERROR",
                "bridge audit schema is missing or unsupported",
                required=require_audit,
                source="harness-audit",
                detail={"schema": audit.get("schema")},
                version=AUDIT_CHECK_VERSION,
            )
        ], lineage

    if audit.get("mode") != "harness":
        return [
            _check(
                "harness_audit",
                "NOT_RUN",
                "durable Harness audit is not applicable to bare mode",
                required=False,
                source="harness-audit",
                version=AUDIT_CHECK_VERSION,
            )
        ], lineage

    state = audit.get("state")
    if not isinstance(state, dict):
        status = "ERROR" if require_audit else "NOT_RUN"
        return [
            _check(
                "harness_audit",
                status,
                "harness audit has no state projection",
                required=require_audit,
                source="harness-audit",
                version=AUDIT_CHECK_VERSION,
            )
        ], lineage

    failures: list[str] = []
    errors: list[str] = list(lineage_errors)
    tasks = _projection_rows(state, "tasks", errors)
    executions = _projection_rows(state, "executions", errors)
    acceptances = _projection_rows(state, "acceptances", errors)
    evidence = _projection_rows(state, "evidence", errors)
    submissions = _projection_rows(state, "submissions", errors)

    task_ids: set[str] = set()
    for item in tasks:
        task_id = item.get("taskId")
        if not isinstance(task_id, str) or not task_id.strip():
            errors.append("task projection has no non-empty taskId")
        else:
            task_ids.add(task_id)

    execution_by_id: dict[str, dict[str, Any]] = {}
    for item in executions:
        execution_id = item.get("executionId")
        if not isinstance(execution_id, str) or not execution_id.strip():
            errors.append("execution projection has no non-empty executionId")
            continue
        if execution_id in execution_by_id:
            errors.append(f"duplicate execution projection: {execution_id}")
            continue
        execution_by_id[execution_id] = item

    for item in executions:
        task_id = item.get("taskId")
        if task_id is not None and (not isinstance(task_id, str) or not task_id.strip()):
            errors.append(f"execution {item.get('executionId')} has invalid taskId")
        elif task_id is not None and task_id not in task_ids:
            failures.append(f"execution {item.get('executionId')} references unknown task {task_id}")
        parent = item.get("parentExecutionId")
        if parent is not None and (not isinstance(parent, str) or not parent.strip()):
            errors.append(f"execution {item.get('executionId')} has invalid parentExecutionId")
        elif parent is not None and parent not in execution_by_id:
            failures.append(f"execution {item.get('executionId')} references unknown parent {parent}")

    submission_by_id: dict[str, dict[str, Any]] = {}
    for item in submissions:
        submission_id = item.get("submissionId")
        if not isinstance(submission_id, str) or not submission_id.strip():
            errors.append("submission projection has no non-empty submissionId")
            continue
        if submission_id in submission_by_id:
            errors.append(f"duplicate submission projection: {submission_id}")
            continue
        submission_by_id[submission_id] = item
        submission_execution_id = item.get("executionId")
        if not isinstance(submission_execution_id, str) or not submission_execution_id.strip():
            errors.append(f"submission {submission_id} has invalid executionId")
        elif submission_execution_id not in execution_by_id:
            failures.append(f"submission {submission_id} references unknown execution {item.get('executionId')}")
        submission_task_id = item.get("taskId")
        if submission_task_id is not None and (not isinstance(submission_task_id, str) or not submission_task_id.strip()):
            errors.append(f"submission {submission_id} has invalid taskId")
        elif submission_task_id is not None and submission_task_id not in task_ids:
            failures.append(f"submission {submission_id} references unknown task {item.get('taskId')}")

    evidence_by_acceptance = {}
    for row in evidence:
        content_ref = row.get("contentRef")
        execution_id = row.get("executionId")
        if content_ref is not None and not isinstance(content_ref, str):
            errors.append(f"evidence {row.get('evidenceId')} has invalid contentRef")
            content_ref = None
        if execution_id is not None and (not isinstance(execution_id, str) or not execution_id.strip()):
            errors.append(f"evidence {row.get('evidenceId')} has invalid executionId")
            execution_id = None
        if execution_id is not None and execution_id not in execution_by_id:
            failures.append(f"evidence {row.get('evidenceId')} references unknown execution {execution_id}")
        if content_ref:
            evidence_by_acceptance.setdefault(content_ref, []).append(row)

    acceptance_ids: set[str] = set()
    for item in acceptances:
        acceptance_id = item.get("acceptanceId")
        if not isinstance(acceptance_id, str) or not acceptance_id.strip():
            errors.append("acceptance projection has no non-empty acceptanceId")
        elif acceptance_id in acceptance_ids:
            errors.append(f"duplicate acceptance projection: {acceptance_id}")
        else:
            acceptance_ids.add(acceptance_id)
        raw_status = item.get("status")
        status = str(raw_status or "").strip().upper()
        if status not in KNOWN_STATUSES:
            errors.append(f"acceptance {acceptance_id} has invalid status {raw_status!r}")
        execution_id = item.get("executionId")
        if not isinstance(execution_id, str) or not execution_id.strip():
            errors.append(f"acceptance {acceptance_id} has invalid executionId")
            execution = None
        else:
            execution = execution_by_id.get(execution_id)
        if execution is None:
            failures.append(f"acceptance {item.get('acceptanceId')} references unknown execution {execution_id}")
            continue
        acceptance_task_id = item.get("taskId")
        if acceptance_task_id is not None and not isinstance(acceptance_task_id, str):
            errors.append(f"acceptance {item.get('acceptanceId')} has invalid taskId")
        if acceptance_task_id is not None and acceptance_task_id != execution.get("taskId"):
            failures.append(f"acceptance {item.get('acceptanceId')} task/execution binding mismatch")
        submission_id = item.get("submissionId")
        if submission_id is not None:
            if not isinstance(submission_id, str) or not submission_id.strip():
                errors.append(f"acceptance {item.get('acceptanceId')} has invalid submissionId")
                submission = None
            else:
                submission = submission_by_id.get(submission_id)
            if submission is None:
                failures.append(f"acceptance {item.get('acceptanceId')} references unknown submission {submission_id}")
            elif (submission.get("executionId") != execution_id
                  or submission.get("taskId") != acceptance_task_id):
                failures.append(f"acceptance {item.get('acceptanceId')} submission binding mismatch")
        acceptance_key = acceptance_id if isinstance(acceptance_id, str) else None
        if status == "PASS" and not any(
                row.get("executionId") == execution_id
                for row in evidence_by_acceptance.get(acceptance_key, [])):
            failures.append(f"PASS acceptance {item.get('acceptanceId')} has no durable evidence")

        provenance = item.get("acceptanceProvenance")
        if provenance is not None:
            if not isinstance(provenance, dict):
                errors.append(f"acceptance {item.get('acceptanceId')} provenance is not an object")
            else:
                missing = [key for key in ("evaluatorId", "evaluatorVersion", "checkVersion")
                           if not isinstance(provenance.get(key), str) or not provenance.get(key).strip()]
                if missing:
                    errors.append(f"acceptance {item.get('acceptanceId')} missing {missing}")

    lineage_ids = lineage.get("executionIds") or []
    lineage_id_set = set(lineage_ids)
    if lineage_ids and any(execution_id not in execution_by_id for execution_id in lineage_ids):
        failures.append("lineage contains an execution missing from state projection")
    if lineage.get("executionId") is not None and audit.get("executionId") is not None \
            and lineage.get("executionId") != audit.get("executionId"):
        failures.append("lineage executionId does not match audit executionId")
    if lineage_ids and lineage.get("executionId") != lineage_ids[-1]:
        failures.append("lineage executionId is not the terminal execution")
    for previous_id, current_id in zip(lineage_ids, lineage_ids[1:]):
        current = execution_by_id.get(current_id)
        if current is not None and current.get("parentExecutionId") != previous_id:
            failures.append(f"lineage execution {current_id} does not point to previous execution {previous_id}")

    if lineage_ids and lineage.get("executionCount") != len(lineage_ids):
        failures.append("lineage executionCount does not match executionIds")
    if lineage.get("repairCount", 0) != max(0, len(lineage_ids) - 1):
        failures.append("lineage repairCount does not match execution chain")
    if lineage.get("acceptanceCount", 0) != len(lineage.get("acceptances", []) or []):
        failures.append("lineage acceptanceCount does not match acceptance records")

    lineage_acceptances = lineage.get("acceptances") or []
    lineage_acceptance_ids: set[str] = set()
    for item in lineage_acceptances:
        if not isinstance(item, dict):
            errors.append("lineage.acceptances contains a non-object")
            continue
        acceptance_id = item.get("acceptanceId")
        if not isinstance(acceptance_id, str) or not acceptance_id.strip():
            errors.append("lineage acceptance has no non-empty acceptanceId")
            continue
        if acceptance_id in lineage_acceptance_ids:
            errors.append(f"duplicate lineage acceptance: {acceptance_id}")
        lineage_acceptance_ids.add(acceptance_id)
        stored = next((candidate for candidate in acceptances
                       if candidate.get("acceptanceId") == acceptance_id), None)
        if stored is None:
            failures.append(f"lineage acceptance {acceptance_id} is not present in state")
        else:
            stored_execution_id = stored.get("executionId")
            if (not isinstance(stored_execution_id, str)
                    or stored_execution_id not in lineage_id_set):
                failures.append(f"lineage acceptance {acceptance_id} is outside execution lineage")

    state_lineage_acceptance_ids: set[str] = set()
    for item in acceptances:
        acceptance_id = item.get("acceptanceId")
        execution_id = item.get("executionId")
        if (isinstance(acceptance_id, str) and acceptance_id
                and isinstance(execution_id, str) and execution_id in lineage_id_set):
            state_lineage_acceptance_ids.add(acceptance_id)
    if lineage_ids and lineage_acceptance_ids != state_lineage_acceptance_ids:
        failures.append("lineage acceptance records do not match state records")
    if lineage.get("acceptanceCount", 0) != len(state_lineage_acceptance_ids) and lineage_ids:
        failures.append("lineage acceptanceCount does not match state lineage")

    lineage_timed_acceptances = [
        item for item in acceptances
        if (not lineage_ids)
        or (isinstance(item.get("executionId"), str)
            and item.get("executionId") in lineage_id_set)
    ]
    timed_acceptances = []
    for item in lineage_timed_acceptances:
        timestamp = item.get("evaluatedAtEpochMs")
        if timestamp is not None and (type(timestamp) is not int or timestamp < 0):
            errors.append(f"acceptance {item.get('acceptanceId')} has invalid evaluatedAtEpochMs")
        timed_acceptances.append((item, timestamp if type(timestamp) is int and timestamp >= 0 else 0))
    if timed_acceptances:
        latest_item = max(timed_acceptances, key=lambda pair: (pair[1], str(pair[0].get("acceptanceId", ""))))[0]
        expected_latest = str(latest_item.get("status") or "").strip().upper()
        if lineage.get("latestAcceptanceStatus") != expected_latest:
            failures.append("lineage latestAcceptanceStatus does not match evaluated acceptance")
    elif lineage.get("latestAcceptanceStatus") is not None:
        failures.append("lineage latestAcceptanceStatus is set without acceptance records")

    raw_lineage = audit.get("lineage")
    if isinstance(raw_lineage, dict) and "evaluatorProvenancePresent" in raw_lineage:
        has_provenance = any(
            item.get("acceptanceProvenance") is not None
            for item in lineage_timed_acceptances
        )
        if raw_lineage.get("evaluatorProvenancePresent") != has_provenance:
            failures.append("lineage evaluatorProvenancePresent does not match acceptance records")

    status = "ERROR" if errors else ("FAIL" if failures else "PASS")
    detail: Any
    if errors or failures:
        detail = {"errors": errors, "failures": failures}
    else:
        detail = {"executionCount": len(executions), "acceptanceCount": len(acceptances)}
    return [
        _check(
            "harness_audit",
            status,
            "durable Harness audit projection is internally consistent"
            if not (errors or failures)
            else ("durable Harness audit is malformed" if errors else "durable Harness audit has invariant failures"),
            required=require_audit,
            source="harness-audit",
            detail=detail,
            version=AUDIT_CHECK_VERSION,
        )
    ], lineage


def _aggregate_status(checks: Iterable[dict[str, Any]]) -> str:
    required = [item for item in checks if item.get("required")]
    if any(item.get("status") == "ERROR" for item in required):
        return "ERROR"
    if any(item.get("status") == "FAIL" for item in required):
        return "FAIL"
    if any(item.get("status") == "NOT_RUN" for item in required):
        return "NOT_RUN"
    return "PASS" if required else "NOT_RUN"


def _artifact_count(result: dict[str, Any]) -> int:
    workspace = result.get("workspace")
    if not isinstance(workspace, str) or not workspace.strip():
        return 0
    out = Path(workspace) / "out"
    if not out.is_dir():
        return 0
    try:
        return sum(1 for path in out.rglob("*") if path.is_file())
    except OSError:
        return 0


def evaluate(
    result: dict[str, Any],
    *,
    result_path: Path | None = None,
    audit: dict[str, Any] | None = None,
    require_audit: bool = True,
    evaluator_id: str = EVALUATOR_ID,
    evaluator_version: str = EVALUATOR_VERSION,
) -> dict[str, Any]:
    """Build one acceptance artifact from already-produced run records."""

    oracle_checks, oracle_score, oracle_errors = _oracle_checks(result.get("oracle_result"))
    checks = list(oracle_checks)
    if oracle_errors:
        checks.append(
            _check(
                "official_oracle",
                "ERROR",
                "official oracle result is malformed",
                required=True,
                source="official-oracle",
                detail=oracle_errors,
                version=ORACLE_CHECK_VERSION,
            )
        )

    lineage: dict[str, Any]
    if audit is None:
        checks.append(
            _check(
                "harness_audit",
                "ERROR" if require_audit else "NOT_RUN",
                "bridge harness audit was not supplied",
                required=require_audit,
                source="harness-audit",
                version=AUDIT_CHECK_VERSION,
            )
        )
        lineage = {
            "executionId": result.get("executionId"),
            "executionIds": [],
            "executionCount": 0,
            "repairCount": 0,
            "acceptanceCount": 0,
            "acceptances": [],
            "latestAcceptanceStatus": None,
            "evaluatorProvenancePresent": False,
        }
    else:
        audit_items, lineage = _audit_checks(audit, require_audit=require_audit)
        checks.extend(audit_items)

    status = _aggregate_status(checks)
    findings = [
        {
            "checkId": item.get("checkId"),
            "status": item.get("status"),
            "source": item.get("source"),
            "summary": item.get("summary"),
            "detail": item.get("detail"),
        }
        for item in checks
        if item.get("status") != "PASS"
    ]
    now = datetime.now(timezone.utc)
    result_ref = "result.json#oracle_result" if result_path is None else f"{result_path.name}#oracle_result"
    audit_ref = "audit.json#state" if audit is not None else None
    required_count = sum(1 for item in checks if item.get("required"))
    output: dict[str, Any] = {
        "schema": SCHEMA,
        "status": status,
        "taskId": result.get("task_id") or result.get("taskId"),
        "sessionId": result.get("session_id") or result.get("sessionId"),
        "modelId": result.get("model_id") or result.get("modelId"),
        "sampleId": result.get("sample_id") or result.get("sampleId"),
        "officialStatus": (result.get("adapter_result") or {}).get("ok") if isinstance(result.get("adapter_result"), dict) else None,
        "oracleScore": oracle_score,
        "checks": checks,
        "findings": findings,
        "lineage": lineage,
        "repairCount": lineage.get("repairCount", 0),
        "evaluator": {
            "id": evaluator_id,
            "version": evaluator_version,
            "actor": {"kind": "benchmark-evaluator", "id": evaluator_id},
            "checkVersions": {
                "official-oracle": ORACLE_CHECK_VERSION,
                "harness-audit": AUDIT_CHECK_VERSION,
            },
            "contextSnapshotRefs": [ref for ref in (result_ref, audit_ref) if ref],
            "artifactCount": _artifact_count(result),
            "requirementCount": required_count,
            "evaluatedAtEpochMs": int(now.timestamp() * 1000),
            "evaluatedAt": now.isoformat().replace("+00:00", "Z"),
        },
        "sources": {
            "officialResult": result_ref,
            "bridgeAudit": audit_ref,
            "groundTruthRead": False,
            "officialScoringMutated": False,
        },
    }
    return output


def _write_atomic(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, raw_tmp = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=str(path.parent))
    tmp = Path(raw_tmp)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, path)
    finally:
        try:
            tmp.unlink()
        except FileNotFoundError:
            pass


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result", type=Path, help="official HarnessBench result JSON")
    parser.add_argument("--audit", type=Path, help="bridge harness_audit.json (default: infer from sandbox)")
    parser.add_argument("--output", type=Path, help="acceptance artifact path (default: beside result)")
    parser.add_argument("--allow-missing-audit", action="store_true", help="record missing audit as NOT_RUN instead of required ERROR")
    parser.add_argument("--evaluator-id", default=EVALUATOR_ID)
    parser.add_argument("--evaluator-version", default=EVALUATOR_VERSION)
    args = parser.parse_args(argv)

    try:
        result = _load_json(args.result, "official result")
        audit_path = _resolve_audit_path(result, args.result, args.audit)
        audit = _load_json(audit_path, "bridge audit") if audit_path is not None else None
        artifact = evaluate(
            result,
            result_path=args.result,
            audit=audit,
            require_audit=not args.allow_missing_audit,
            evaluator_id=args.evaluator_id,
            evaluator_version=args.evaluator_version,
        )
        output = args.output or args.result.with_name("harness_acceptance.json")
        _write_atomic(output, artifact)
    except AcceptanceInputError as exc:
        print(f"acceptance error: {exc}", file=sys.stderr)
        return 2
    except (OSError, ValueError, TypeError) as exc:
        print(f"acceptance error: {exc}", file=sys.stderr)
        return 2

    print(json.dumps({"status": artifact["status"], "output": str(output)}, ensure_ascii=False))
    return {"PASS": 0, "FAIL": 1, "ERROR": 2, "NOT_RUN": 2}.get(artifact["status"], 2)


if __name__ == "__main__":
    raise SystemExit(main())
