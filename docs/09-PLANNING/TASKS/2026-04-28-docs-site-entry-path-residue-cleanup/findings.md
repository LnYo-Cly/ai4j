# docs-site Entry Path Residue Cleanup - Findings

## Discoveries

### Residuals are now concentrated around entry pages

- Why it mattered: after the module-level cleanup rounds, the most important remaining defects are no longer in deep topic trees but in entry-path pages that many readers will see first.
- What was found: `choose-your-path`, `quickstart-java`, `model-access/multimodal`, and `glossary` still contain a small number of high-impact links to downgraded paths.
- Impact on plan: keep the scope small and optimize for route stability at the top of the docs funnel.

### Legacy areas still exist, but are lower priority

- Why it mattered: many old links still exist inside obviously legacy sections such as `getting-started/`, `guides/`, and `ai-basics/`, but they do not affect the primary entry path as directly.
- What was found: the highest-value remaining cleanup is now selective rather than tree-wide.
- Impact on plan: do not reopen those larger legacy trees in this task.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Scope size | keep this task narrow and entry-focused | highest leverage with the lowest regression risk | reopen a broad legacy-tree migration |
| Agent route guidance | point `Start Here` to current overview/why/quickstart paths | matches the current canonical structure and the “promote first, then deepen” reading order | keep linking old scenario and runtime pages |
| Flowgram route guidance | point `Start Here` to overview/why/quickstart | aligns Flowgram with the current sidebar mainline | keep linking old use-case pages |
