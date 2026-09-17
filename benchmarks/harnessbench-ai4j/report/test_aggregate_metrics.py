import importlib.util
import unittest
import json
import tempfile
from pathlib import Path


MODULE = Path(__file__).with_name("aggregate_metrics.py")
SPEC = importlib.util.spec_from_file_location("aggregate_metrics", MODULE)
aggregate_metrics = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(aggregate_metrics)


class AggregateMetricsTest(unittest.TestCase):
    def test_separates_quality_completion_process_timeout_and_invariants(self):
        report = aggregate_metrics.aggregate([
            {"taskId": "001", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "a", "completed": True, "qualityScore": 1.0, "processExitCode": 0, "timedOut": False, "invariantPassed": True},
            {"taskId": "001", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "b", "completed": False, "qualityScore": 0.4, "processExitCode": 1, "timedOut": False, "invariantPassed": False},
            {"taskId": "016", "category": "Software", "arm": "ai4j-harness", "sampleId": "a", "completed": False, "timedOut": True},
            {"taskId": "016", "category": "Software", "arm": "control", "sampleId": "a", "completed": True, "qualityScore": 0.9},
        ], "ai4j-harness")
        overall = report["overall"]
        self.assertEqual(3, overall["runs"])
        self.assertEqual(2, overall["uniqueTasks"])
        self.assertEqual(2, overall["quality"]["observed"])
        self.assertEqual(0.7, overall["quality"]["mean"])
        self.assertEqual(1 / 3, overall["completion"]["rate"])
        self.assertEqual(0.5, overall["processFailure"]["rate"])
        self.assertEqual(1 / 3, overall["timeout"]["rate"])
        self.assertEqual(0.5, overall["invariantPass"]["rate"])

    def test_missing_optional_values_are_not_counted_as_passes(self):
        report = aggregate_metrics.aggregate([
            {"taskId": "001", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "a", "completed": True},
        ], "ai4j-harness")
        self.assertEqual(0, report["overall"]["quality"]["observed"])
        self.assertIsNone(report["overall"]["quality"]["mean"])
        self.assertEqual(0, report["overall"]["timeout"]["observed"])
        self.assertIsNone(report["overall"]["timeout"]["rate"])

    def test_acceptance_oracle_and_repairs_are_separate_measurements(self):
        report = aggregate_metrics.aggregate([
            {"taskId": "057", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "a",
             "completed": True, "oracleScore": 1.0, "acceptanceStatus": "PASS", "repairCount": 2},
            {"taskId": "058", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "a",
             "completed": False, "oracleScore": 0.4, "acceptance": {"status": "FAIL"}, "repairCount": 0},
            {"taskId": "059", "category": "Long-running", "arm": "ai4j-harness", "sampleId": "a",
             "completed": False},
        ], "ai4j-harness")["overall"]
        self.assertEqual({"observed": 2, "mean": 0.7}, report["oracle"])
        self.assertEqual(2, report["acceptance"]["observed"])
        self.assertEqual(1, report["acceptance"]["pass"])
        self.assertEqual(1, report["acceptance"]["fail"])
        self.assertEqual(0.5, report["acceptance"]["passRate"])
        self.assertEqual({"observed": 2, "total": 2, "mean": 1.0}, report["repairs"])

class InputContractTest(unittest.TestCase):
    def load(self, **values):
        row = dict(taskId="001", category="Software", arm="ai4j-harness", sampleId="a", completed=True)
        row.update(values)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "runs.json"
            path.write_text(json.dumps([row]), encoding="utf-8")
            return aggregate_metrics.load_runs(path)

    def test_null_optional_measurements_are_unobserved(self):
        rows = self.load(processExitCode=None, qualityScore=None, oracleScore=None, repairCount=None,
                         timedOut=None, invariantPassed=None)
        report = aggregate_metrics.aggregate(rows, "ai4j-harness")["overall"]
        for key in ("processFailure", "quality", "oracle", "timeout", "invariantPass", "repairs"):
            self.assertEqual(0, report[key]["observed"])
        self.assertIsNone(report["processFailure"]["rate"])

    def test_non_finite_scores_are_rejected(self):
        for field in ("qualityScore", "oracleScore"):
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, field):
                self.load(**{field: float("nan")})

    def test_invalid_exit_code_types_are_rejected(self):
        for value in ("0", False, 0.0, [], {}):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "processExitCode"):
                self.load(processExitCode=value)

    def test_integer_exit_codes_preserve_failure_rate(self):
        rows = self.load(processExitCode=0) + self.load(processExitCode=2)
        self.assertEqual({"observed": 2, "rate": 0.5}, aggregate_metrics.summarize(rows)["processFailure"])

    def test_invalid_acceptance_status_and_repair_count_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "acceptanceStatus"):
            self.load(acceptanceStatus="maybe")
        with self.assertRaisesRegex(ValueError, "repairCount"):
            self.load(repairCount=-1)
