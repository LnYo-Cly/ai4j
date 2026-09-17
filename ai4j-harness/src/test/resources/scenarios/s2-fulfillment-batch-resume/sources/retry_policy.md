# Overnight Batch Resume Policy — FUL-20240614-N2

Batch FUL-20240614-N2 halted at 03:40 after a WMS outage. On resume, the run
MUST produce `out/resume_ledger.csv` accounting for every `order_id` in
`batch_manifest.csv`. Ledger statuses are the audit vocabulary — downstream
reconciliation joins on them, so do not rename or add alternative columns.

## Ledger status vocabulary (exact values, no synonyms)

- `skipped_completed` — line already completed before the outage; do not
  re-ship (idempotency: re-sending would double-ship)
- `retried` — line failed with a transient error; queued for reprocessing
- `processed_new` — line was pending when the batch halted; first attempt now
- `rejected_unrecoverable` — line failed with a fatal error class; routed to
  exception queue, no retry

## Classification rules

- `state=completed` -> `skipped_completed`
- `state=failed` AND error class `transient` -> `retried`
- `state=failed` AND error class `fatal` -> `rejected_unrecoverable`
- `state=pending` -> `processed_new`

Error classes are defined per order in `failure_log.csv`.
