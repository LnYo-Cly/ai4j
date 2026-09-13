#!/usr/bin/env python3
"""Aggregate raw HarnessBench run records without overstating low-sample data.

Usage:
    python aggregate_metrics.py results.json [--arm ai4j-harness] [--output report.json]

The input is a JSON array, or an object with a ``runs`` array. Each run must
contain ``taskId``, ``category``, ``arm``, ``sampleId``, and ``completed``.
Optional fields are ``qualityScore`` (0..1), ``processExitCode``, ``timedOut``,
and ``invariantPassed``. Missing optional fields stay missing in the report;
they are never silently counted as a pass.
"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any, Iterable


REQUIRED = ("taskId", "category", "arm", "sampleId", "completed")
OPTIONAL_BOOL = ("timedOut", "invariantPassed")


def load_runs(path: Path) -> list[dict[str, Any]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    runs = value["runs"] if isinstance(value, dict) else value
    if not isinstance(runs, list):
        raise ValueError("input must be an array or an object containing a runs array")
    for index, run in enumerate(runs):
        if not isinstance(run, dict):
            raise ValueError("run %d must be an object" % index)
        missing = [key for key in REQUIRED if key not in run]
        if missing:
            raise ValueError("run %d missing required fields: %s" % (index, ", ".join(missing)))
        if not isinstance(run["completed"], bool):
            raise ValueError("run %d completed must be boolean" % index)
        for key in ("qualityScore", "processExitCode", *OPTIONAL_BOOL):
            if key in run and run[key] is None:
                del run[key]
        if "processExitCode" in run and type(run["processExitCode"]) is not int:
            raise ValueError("run %d processExitCode must be an integer or null" % index)
        if "qualityScore" in run and (type(run["qualityScore"]) not in (int, float) or not 0 <= run["qualityScore"] <= 1):
            raise ValueError("run %d qualityScore must be in [0, 1]" % index)
        for key in OPTIONAL_BOOL:
            if key in run and not isinstance(run[key], bool):
                raise ValueError("run %d %s must be boolean" % (index, key))
    return runs


def rate(rows: Iterable[dict[str, Any]], key: str, pass_value: Any) -> dict[str, Any]:
    observed = [row for row in rows if key in row]
    return {"observed": len(observed), "rate": None if not observed else sum(row[key] == pass_value for row in observed) / len(observed)}


def summarize(rows: list[dict[str, Any]]) -> dict[str, Any]:
    quality = [row["qualityScore"] for row in rows if "qualityScore" in row]
    process = [row for row in rows if "processExitCode" in row]
    process_failures = sum(row["processExitCode"] != 0 for row in process)
    return {
        "runs": len(rows),
        "uniqueTasks": len({row["taskId"] for row in rows}),
        "quality": {"observed": len(quality), "mean": None if not quality else sum(quality) / len(quality)},
        "completion": rate(rows, "completed", True),
        "processFailure": {"observed": len(process), "rate": None if not process else process_failures / len(process)},
        "timeout": rate(rows, "timedOut", True),
        "invariantPass": rate(rows, "invariantPassed", True),
    }


def aggregate(runs: list[dict[str, Any]], arm: str | None) -> dict[str, Any]:
    selected = [run for run in runs if arm is None or run["arm"] == arm]
    categories: dict[str, list[dict[str, Any]]] = defaultdict(list)
    tasks: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for run in selected:
        categories[run["category"]].append(run)
        tasks[run["taskId"]].append(run)
    return {
        "schema": "harnessbench-ai4j-metrics/v1",
        "arm": arm,
        "overall": summarize(selected),
        "byCategory": {name: summarize(rows) for name, rows in sorted(categories.items())},
        "byTask": {name: summarize(rows) for name, rows in sorted(tasks.items())},
        "interpretation": "Rates and means are descriptive point estimates. Read each value with its observed count; missing fields are not passes.",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--arm", default=None)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = aggregate(load_runs(args.input), args.arm)
    text = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    else:
        print(text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
