---
name: ambient-runtime
description: Always-on personal ambient agent guidelines for provenance-gated memory, two-tier heartbeat, and structured channel escalations.
---

# Ambient Runtime Guidelines

1. Keep `SOUL.md`, `USER.md`, and `BOUNDARIES.md` inside the static identity core prefix so prefix prompt caching remains deterministic.
2. Never allow `EXTERNAL_UNTRUSTED` inputs to mutate `IDENTITY_CORE`, `HARNESS_RULE`, or overwrite higher-trust facts.
3. Let `SystemOneSentinel` filter idle or routine ticks before waking the main reasoning model, and respect quiet-hours routing via `SupervisoryAgenda`.
4. Route owner rulings through `EscalationOptionCard` callbacks backed by `ChannelGateway` and `HarnessCommandGateway`.
