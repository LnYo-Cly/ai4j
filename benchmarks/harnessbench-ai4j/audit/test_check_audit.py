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

if __name__ == '__main__':
    unittest.main()
