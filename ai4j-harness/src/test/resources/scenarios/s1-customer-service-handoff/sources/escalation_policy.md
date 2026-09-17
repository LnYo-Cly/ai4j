# Weekend Shift Escalation Policy (rev 3.2)

The outgoing shift MUST produce `handover.csv` listing every ticket still open,
using the exact `ticket_id` values from the ticket-system export. Do not invent
case numbers; the oncoming shift searches the ticket system by these IDs.

## Escalation conditions

- `vip` or `enterprise` tier AND `age_hours` > 24 -> action `escalate`
- `last_reply_minutes` > 240 (SLA breach) -> action `escalate`
- sentiment `angry` or `frustrated` AND `age_hours` > 24 -> action `escalate`
- all remaining open tickets -> action `monitor`
- resolved before handover -> action `close` (do not list `close` rows)

## Vocabulary (do not paraphrase)

- `action` must be one of: `escalate`, `monitor`, `close`
- `approver_role` must be one of the on-call roles in `shift_roster.csv`:
  `shift_lead`, `vip_success_manager`, `billing_ops_lead`

## Approval routing

- billing category escalations -> `billing_ops_lead`
- vip tier escalations -> `vip_success_manager`
- everything else -> `shift_lead`
