import json
import tempfile
import unittest
from pathlib import Path

from evaluate_acceptance import evaluate, main


def passing_result(workspace: Path) -> dict:
    return {
        "task_id": "057-interruption-resume",
        "session_id": "session-1",
        "model_id": "ai4j-harness",
        "sample_id": "sample-a",
        "executionId": "exec-2",
        "workspace": str(workspace),
        "adapter_result": {"ok": True},
        "scoring": {"combined_score": 0.9},
        "oracle_result": {
            "task": "057-interruption-resume",
            "outcome_score": 1.0,
            "checks": [
                {"id": "state_parse", "label": "state parses", "pass": True, "weight": 0.5},
                {"id": "final_content", "label": "final content", "pass": True, "weight": 0.5},
            ],
        },
    }


def passing_audit() -> dict:
    return {
        "schema": "ai4j-harness-audit/v1",
        "mode": "harness",
        "status": "COMPLETED",
        "executionId": "exec-2",
        "lineage": {
            "executionId": "exec-2",
            "executionIds": ["exec-1", "exec-2"],
            "executions": [],
            "repairCount": 1,
            "acceptanceCount": 1,
            "acceptances": [{"acceptanceId": "acc-1", "status": "PASS"}],
            "latestAcceptanceStatus": "PASS",
            "evaluatorProvenancePresent": True,
        },
        "state": {
            "tasks": [{"taskId": "task-1", "status": "ACTIVE"}],
            "executions": [
                {"executionId": "exec-1", "taskId": "task-1", "sessionId": "session-1"},
                {"executionId": "exec-2", "parentExecutionId": "exec-1", "taskId": "task-1", "sessionId": "session-1"},
            ],
            "acceptances": [
                {"acceptanceId": "acc-1", "executionId": "exec-2", "taskId": "task-1", "status": "PASS",
                 "evaluatedAtEpochMs": 20,
                 "acceptanceProvenance": {"evaluatorId": "eval", "evaluatorVersion": "1", "checkVersion": "1"}},
            ],
            "evidence": [{"evidenceId": "e-1", "executionId": "exec-2", "contentRef": "acc-1"}],
        },
    }


class AcceptanceEvaluationTest(unittest.TestCase):
    def test_pass_preserves_official_boundary_and_lineage(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory) / "workspace"
            (workspace / "out").mkdir(parents=True)
            (workspace / "out" / "result.json").write_text("{}", encoding="utf-8")
            result = passing_result(workspace)
            artifact = evaluate(result, result_path=Path(directory) / "official.json", audit=passing_audit())

        self.assertEqual("PASS", artifact["status"])
        self.assertEqual(1, artifact["repairCount"])
        self.assertEqual(1, artifact["lineage"]["acceptanceCount"])
        self.assertEqual(3, artifact["evaluator"]["requirementCount"])
        self.assertFalse(artifact["sources"]["groundTruthRead"])
        self.assertFalse(artifact["sources"]["officialScoringMutated"])
        self.assertEqual("PASS", artifact["checks"][0]["status"])

    def test_failed_oracle_is_a_finding_and_does_not_rewrite_scoring(self):
        result = passing_result(Path("workspace"))
        result["oracle_result"]["checks"][1]["pass"] = False
        original_scoring = dict(result["scoring"])
        artifact = evaluate(result, audit=passing_audit())

        self.assertEqual("FAIL", artifact["status"])
        self.assertTrue(any(item["checkId"] == "final_content" for item in artifact["findings"]))
        self.assertEqual(original_scoring, result["scoring"])

    def test_malformed_oracle_is_error(self):
        artifact = evaluate({"oracle_result": {"checks": [{"id": "bad", "pass": "yes"}]}}, audit=passing_audit())
        self.assertEqual("ERROR", artifact["status"])
        self.assertTrue(any(item["status"] == "ERROR" for item in artifact["checks"]))

    def test_bare_audit_is_informational_not_run(self):
        result = passing_result(Path("workspace"))
        bare = {"schema": "ai4j-harness-audit/v1", "mode": "bare", "state": {"note": "no durable state"}}
        artifact = evaluate(result, audit=bare)
        self.assertEqual("PASS", artifact["status"])
        audit_check = next(item for item in artifact["checks"] if item["checkId"] == "harness_audit")
        self.assertEqual("NOT_RUN", audit_check["status"])
        self.assertFalse(audit_check["required"])

    def test_cli_writes_atomic_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result_path = root / "result.json"
            audit_path = root / "harness_audit.json"
            result_path.write_text(json.dumps(passing_result(root / "workspace")), encoding="utf-8")
            audit_path.write_text(json.dumps(passing_audit()), encoding="utf-8")
            output = root / "out" / "acceptance.json"
            self.assertEqual(0, main([str(result_path), "--audit", str(audit_path), "--output", str(output)]))
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual("harnessbench-acceptance/v1", payload["schema"])
            self.assertEqual("PASS", payload["status"])
            self.assertFalse(list(output.parent.glob(".*.tmp")))

    def test_pass_evidence_must_match_acceptance_execution(self):
        audit = passing_audit()
        audit["state"]["evidence"][0]["executionId"] = "exec-1"
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("FAIL", artifact["status"])
        self.assertTrue(any("durable evidence" in (item.get("detail") or "")
                            for item in artifact["findings"]))

    def test_malformed_projection_array_is_error(self):
        audit = passing_audit()
        audit["state"]["executions"] = {"exec-1": {}}
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("ERROR", artifact["status"])
        self.assertTrue(any(item["checkId"] == "harness_audit" and item["status"] == "ERROR"
                            for item in artifact["checks"]))

    def test_malformed_lineage_count_is_error(self):
        audit = passing_audit()
        audit["lineage"]["repairCount"] = "1"
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("ERROR", artifact["status"])

    def test_unhashable_projection_id_is_error(self):
        audit = passing_audit()
        audit["state"]["acceptances"][0]["executionId"] = ["exec-2"]
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("ERROR", artifact["status"])

    def test_latest_acceptance_status_is_checked(self):
        audit = passing_audit()
        audit["lineage"]["latestAcceptanceStatus"] = "FAIL"
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("FAIL", artifact["status"])
        self.assertTrue(any("latestAcceptanceStatus" in (item.get("detail") or "")
                            for item in artifact["findings"]))

    def test_unrelated_later_acceptance_does_not_change_lineage_latest(self):
        audit = passing_audit()
        audit["state"]["executions"].append(
            {"executionId": "exec-other", "taskId": "task-1", "sessionId": "session-1"}
        )
        audit["state"]["acceptances"].append(
            {"acceptanceId": "acc-other", "executionId": "exec-other", "taskId": "task-1",
             "status": "FAIL", "evaluatedAtEpochMs": 100,
             "acceptanceProvenance": {"evaluatorId": "eval", "evaluatorVersion": "1", "checkVersion": "1"}}
        )
        artifact = evaluate(passing_result(Path("workspace")), audit=audit)
        self.assertEqual("PASS", artifact["status"])
        self.assertEqual("PASS", artifact["lineage"]["latestAcceptanceStatus"])


if __name__ == "__main__":
    unittest.main()
