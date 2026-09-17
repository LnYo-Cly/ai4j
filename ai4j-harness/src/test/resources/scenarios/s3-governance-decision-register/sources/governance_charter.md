# Governance Charter — decision register rules

The decision register is the audit-trail SSoT. Weekly updates produce
`out/updated_register.csv` with columns `decision_id`, `title`, `status`,
`conflict_ref`.

Hard rules:

1. Every historical `decision_id` is retained, including `superseded` and
   `withdrawn` entries. Deleting a row falsifies the audit trail.
2. `status` uses only: `active`, `superseded`, `withdrawn`.
3. `decision_id` values are copied verbatim; new decisions are not minted in
   the weekly update — minting is a separate governance act.
4. `conflict_ref` carries any open conflict flag (e.g. `CF-09`) affecting the
   decision, or is left empty.
