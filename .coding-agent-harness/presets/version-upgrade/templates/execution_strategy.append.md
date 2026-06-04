## Version Upgrade Execution

- Treat the bundled release manifest as the upgrade contract for this task.
- Classify each manifest item as safe, manual, or blocked before applying anything.
- Apply only safe actions through `apply-safe`; blocked and manual items remain evidence, not automation.
- Re-run `check` after every action and stop while verification status is `blocked`.
