# docs-site Solutions Canonical Cleanup - Findings

## Discoveries

### Solutions subtree exists but is still mostly hollow

- Why it mattered: `Solutions` is already visible in the top-level sidebar, so readers reasonably expect these pages to be complete enough for scenario selection and onboarding.
- What was found: almost every scenario page is currently only a one-line bridge to a legacy `guides/` page.
- Impact on plan: rewrite the scenario entries so they become legitimate case-study overviews in their own right.

### Legacy guides still hold the detailed implementation value

- Why it mattered: the old guide pages contain real detail and should not be thrown away just because the sidebar has moved.
- What was found: each guide already has enough stable thematic content to support a shorter canonical scenario page layered on top.
- Impact on plan: keep guide links as supplemental deep dives instead of deleting them.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Solutions page depth | make each page a concise scenario entry, not a full rewrite of the guide | balances readability with scope control | copy the entire guide into Solutions |
| Guide handling | retain old guide links as “implementation details” follow-ups | preserves useful detail while fixing the primary route | remove guide links entirely |
| Case-study framing | emphasize problem/fit/stack/next-reading over raw step-by-step code | matches the current docs-site “promote first, then deepen” strategy | keep pages as minimal placeholders |
