# docs-site ai-basics Crosslink Cleanup - Findings

## Discoveries

### The remaining legacy links are now very concentrated

- Why it mattered: once the top-level canonical trees were cleaned up, the remaining problems shifted from whole subtrees to a few targeted cross-links.
- What was found: only a handful of `ai-basics/` pages still reference downgraded `getting-started` or old Flowgram routes.
- Impact on plan: keep this task small and link-focused.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Scope size | fix only the identified cross-links | highest leverage with minimal regression risk | reopen a broad `ai-basics/` rewrite |
| Route mapping | map each old link to the nearest current canonical page | preserves reader intent while aligning with the new docs structure | leave links untouched until a future full migration |
