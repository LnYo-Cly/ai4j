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


class InputContractTest(unittest.TestCase):
    def load(self, **values):
        row = dict(taskId="001", category="Software", arm="ai4j-harness", sampleId="a", completed=True)
        row.update(values)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "runs.json"
            path.write_text(json.dumps([row]), encoding="utf-8")
            return aggregate_metrics.load_runs(path)

    def test_null_optional_measurements_are_unobserved(self):
        rows = self.load(processExitCode=None, qualityScore=None, timedOut=None, invariantPassed=None)
        report = aggregate_metrics.aggregate(rows, "ai4j-harness")["overall"]
        for key in ("processFailure", "quality", "timeout", "invariantPass"):
            self.assertEqual(0, report[key]["observed"])
        self.assertIsNone(report["processFailure"]["rate"])

    def test_invalid_exit_code_types_are_rejected(self):
        for value in ("0", False, 0.0, [], {}):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "processExitCode"):
                self.load(processExitCode=value)

    def test_integer_exit_codes_preserve_failure_rate(self):
        rows = self.load(processExitCode=0) + self.load(processExitCode=2)
        self.assertEqual({"observed": 2, "rate": 0.5}, aggregate_metrics.summarize(rows)["processFailure"])
