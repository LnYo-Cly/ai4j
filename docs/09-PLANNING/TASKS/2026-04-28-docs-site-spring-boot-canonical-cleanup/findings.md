# docs-site Spring Boot Canonical Cleanup - Findings

## Discoveries

### Spring Boot tree is compact but under-explained

- Why it mattered: the `spring-boot/` subtree is already only six pages, so clarity depends far more on page quality than on information architecture.
- What was found: the current pages explain the basics but remain too thin to support the “promote first, then go deep” reading pattern the docs-site is moving toward.
- Impact on plan: rewrite the six canonical pages so they explain module role, entry conditions, and next-step reading order more directly.

### Old guidance still points back to getting-started

- Why it mattered: the new Spring Boot subtree cannot become the stable canonical path if adjacent entry pages still send readers back to legacy `getting-started/*` routes.
- What was found: `spring-boot/auto-configuration`, `spring-boot/configuration-reference`, `start-here/quickstart-spring-boot`, and `start-here/troubleshooting` still reference old route clusters.
- Impact on plan: normalize those links to the current `spring-boot/`, `start-here/`, and `solutions/` routes.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Sidebar strategy | keep current six-page Spring Boot tree | the subtree is already compact and understandable; the issue is reading-path convergence | reopen sidebar design |
| Legacy getting-started handling | keep as supplemental detail only | preserves prior long-form material without letting it dominate the canonical path | delete or fully rewrite all getting-started pages |
| Solution-case links | point common patterns to `solutions/` entry pages | aligns examples with the current top-level docs structure | keep linking old `guides/` routes |
