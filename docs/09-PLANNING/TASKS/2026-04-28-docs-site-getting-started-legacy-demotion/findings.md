# docs-site getting-started Legacy Demotion - Findings

## Discoveries

### getting-started still behaves like a second homepage tree

- Why it mattered: even after the canonical trees were cleaned up, old search hits or bookmarks can still pull readers into `getting-started/` and keep them there.
- What was found: the legacy pages still heavily self-link and often route to other old entry pages instead of back to the current docs structure.
- Impact on plan: treat this subtree as an archival zone and explicitly repoint its top-level guidance outward.

### Full rewrites are unnecessary for the goal

- Why it mattered: the legacy pages still contain useful implementation detail, but rewriting every long-form page would be slow and risky.
- What was found: adding clear legacy framing plus updating the highest-leverage guidance links is enough to stop this tree from competing with the current canonical entry flow.
- Impact on plan: keep the body content mostly intact and optimize the top and bottom navigation guidance.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Demotion method | use explicit legacy notices | fastest way to change reader expectations without deleting useful long-form material | silent partial migration |
| Link strategy | repoint only the high-level guidance links | best leverage with limited editing risk | full link-by-link rewrite of every legacy page |
| Coding Agent migration page | keep it as a migration note, but align its target links to current coding-agent routes | it already behaves like a demotion page and only needs route alignment | remove the page entirely |
