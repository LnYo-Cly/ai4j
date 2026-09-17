# S3 — Project governance decision register

Role: PMO analyst producing the weekly register update. Produce
`out/updated_register.csv` reflecting this week's stakeholder positions.

Inputs (all under `sources/`):

- `decision_register.csv` — current register, including superseded entries
- `stakeholder_positions.md` — marketing/compliance conflict `CF-09`
- `governance_charter.md` — retention rules, status vocabulary, conflict
  reference format

Rules: every existing `decision_id` retained verbatim, including superseded
rows; `status` stays inside the charter vocabulary; open conflict flags are
recorded in `conflict_ref` for the decisions they affect.
