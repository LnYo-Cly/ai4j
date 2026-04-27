# docs-site Core SDK Canonical Cleanup - Findings

## Discoveries

### Canonical tree exists, but entry quality is uneven

- Why it mattered: the `Core SDK` sidebar is already much clearer than the old mixed tree, so the remaining problem is not taxonomy but entry-page quality.
- What was found: several key canonical pages such as `service-entry-and-registry`, `mcp/overview`, `skills/overview`, `memory/overview`, `search-and-rag/overview`, and `extension/overview` are still too thin to serve as stable entry points.
- Impact on plan: rewrite those pages so they explain purpose, boundary, and reading order directly.

### Legacy deep pages still create path splits

- Why it mattered: readers can still land on the older `chat/`, `responses/`, and root-level capability pages and follow links that bypass the new canonical tree.
- What was found: the strongest remaining residue is not a large number of broken links, but a lingering alternate reading path built around legacy page clusters.
- Impact on plan: keep the legacy pages, but normalize their main guidance links so the current sidebar remains the canonical reading path.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Sidebar strategy | keep current `Core SDK` sidebar | the IA direction is already validated; content convergence is now the main problem | reopen the sidebar and rename more routes |
| Legacy deep pages | keep as supporting detail only | preserves existing content while reducing path confusion | delete or fully rewrite all legacy pages |
| Scope focus | target canonical entries plus major legacy guide-links | highest impact on readability with limited risk | broad full-tree rewrite |
