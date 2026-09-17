# S2 — Fulfillment batch resume ledger

Role: night-shift fulfillment operator. Batch FUL-20240614-N2 halted mid-run;
produce `out/resume_ledger.csv` so the resume job knows what to do with every
order line.

Inputs (all under `sources/`):

- `batch_manifest.csv` — the batch's order lines and pre-halt state
- `retry_policy.md` — classification rules and the ledger status vocabulary
  (`skipped_completed`, `retried`, `processed_new`, `rejected_unrecoverable`)
- `failure_log.csv` — error class per failed order

Rules: one row per manifest `order_id`; `ledger_status` uses only the policy
vocabulary; a single status column — do not add a second status/action column
that could shadow it.
