# HarnessBench score report format

Run records are the source of truth for numeric benchmark reports. Keep a
sanitized JSON file outside Git when it contains live-run details, then create
a report with:

```bash
python benchmarks/harnessbench-ai4j/report/aggregate_metrics.py results.json \
  --arm ai4j-harness --output report.json
```

Each record has these required fields:

```json
{
  "taskId": "057-interruption-resume",
  "category": "Long-running Autonomy & State Adaptation",
  "arm": "ai4j-harness",
  "sampleId": "2026-09-09-r2",
  "completed": true
}
```

Add `qualityScore` (0 through 1), `processExitCode`, `timedOut`, and
`invariantPassed` when observed. The aggregate deliberately reports the
observed count beside every metric. It does not convert an absent timeout,
process result, or audit check into a passing value.

Interpret `quality.mean` only as a descriptive point estimate for the emitted
records. Use `byTask` sample counts to distinguish repeated samples from a
single run, and report categories with no records as not sampled rather than
as zero quality.
