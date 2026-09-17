# S1 — Customer-service weekend shift handoff

Role: outgoing weekend shift lead assistant. Produce `out/handover.csv` — the
escalation ledger the oncoming shift will work from.

Inputs (all under `sources/`):

- `tickets_export.csv` — ticket-system export of open tickets
- `escalation_policy.md` — escalation conditions, action vocabulary, approver
  roles and routing rules
- `shift_roster.csv` — this weekend's on-call role assignments
- `previous_handoff.csv` — last shift's ledger (format reference)

Rules: every open ticket appears exactly once; `ticket_id` values are copied
verbatim from the export; `action` and `approver_role` use only the
policy-declared vocabulary; no extra status-style columns.
