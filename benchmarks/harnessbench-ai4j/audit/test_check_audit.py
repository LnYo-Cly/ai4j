import unittest
from check_audit import Report, check

def ex(i, status, t, attempt=1):
    return {'executionId': i, 'taskId': 'task-1', 'sessionId': 'session-1', 'status': status, 'attempt': attempt, 'createdAtEpochMs': t, 'updatedAtEpochMs': t, 'finishedAtEpochMs': t}

class UnknownAuditTest(unittest.TestCase):
    def run_check(self, executions):
        report = Report()
        check({'schema': 'ai4j-harness-audit/v1', 'mode': 'harness', 'status': 'UNKNOWN', 'benchTaskId': 'bench-1', 'state': {'tasks': [{'taskId': 'task-1', 'status': 'ACTIVE'}], 'executions': executions}}, None, report)
        return report

    def test_unknown_without_retry_passes(self):
        report = self.run_check([ex('exec-1', 'UNKNOWN', 100)])
        self.assertTrue(any(item.startswith('unknown-preserved:') for item in report.passed))
        self.assertFalse(report.failed)

    def test_unknown_followed_by_retry_fails(self):
        report = self.run_check([ex('exec-1', 'UNKNOWN', 100), ex('exec-2', 'COMPLETED', 200, 2)])
        self.assertTrue(any(item.startswith('unknown-preserved:') for item in report.failed))


class AcceptanceAuditTest(unittest.TestCase):
    def base(self, acceptance_status="PASS", with_evidence=True):
        acceptance = {
            "acceptanceId": "acc-1",
            "taskId": "task-1",
            "executionId": "exec-1",
            "status": acceptance_status,
            "acceptanceProvenance": {
                "evaluatorId": "eval",
                "evaluatorVersion": "1",
                "checkVersion": "1",
            },
        }
        state = {
            "tasks": [{"taskId": "task-1", "status": "ACTIVE"}],
            "executions": [{"executionId": "exec-1", "taskId": "task-1", "sessionId": "session-1"}],
            "acceptances": [acceptance],
            "submissions": [],
            "evidence": ([{"evidenceId": "e-1", "executionId": "exec-1", "contentRef": "acc-1"}]
                         if with_evidence else []),
        }
        return {
            "schema": "ai4j-harness-audit/v1",
            "mode": "harness",
            "status": "COMPLETED",
            "lineage": {
                "executionId": "exec-1",
                "executionIds": ["exec-1"],
                "repairCount": 0,
                "acceptanceCount": 1,
                "acceptances": [{"acceptanceId": "acc-1", "status": acceptance_status}],
            },
            "state": state,
        }

    def test_pass_acceptance_requires_evidence_and_reports_provenance(self):
        report = Report()
        check(self.base(), None, report)
        self.assertTrue(any(item.startswith("acceptance-lineage:") for item in report.passed))
        self.assertTrue(any(item.startswith("acceptance-provenance:") for item in report.passed))
        self.assertFalse(report.failed)

    def test_pass_acceptance_without_evidence_fails(self):
        report = Report()
        check(self.base(with_evidence=False), None, report)
        self.assertTrue(any(item.startswith("acceptance-lineage:") for item in report.failed))

    def test_malformed_projection_array_is_reported_without_crashing(self):
        audit = self.base()
        audit["state"]["executions"] = {"exec-1": {}}
        report = Report()
        check(audit, None, report)
        self.assertTrue(any(item.startswith("projection-shape:") for item in report.failed))

    def test_non_object_projection_row_is_reported_without_crashing(self):
        audit = self.base()
        audit["state"]["evidence"] = ["not-an-object"]
        report = Report()
        check(audit, None, report)
        self.assertTrue(any(item.startswith("projection-shape:") for item in report.failed))

    def test_non_object_state_is_reported_without_crashing(self):
        audit = self.base()
        audit["state"] = ["not-an-object"]
        report = Report()
        check(audit, None, report)
        self.assertTrue(any(item.startswith("projection-shape:") for item in report.failed))

if __name__ == '__main__':
    unittest.main()
