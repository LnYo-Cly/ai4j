# docs-site Agent and Coding Agent Canonical Cleanup - Findings

## Discoveries

### Sidebar vs reading-path split

- Why it mattered: the current sidebar already reflects the intended IA, so the real problem is no longer taxonomy but route convergence.
- What was found: many canonical pages and deep pages still reference old route names such as `minimal-react-agent`, `workflow-stategraph`, `acp-integration`, `provider-profiles`, and `prompt-assembly`.
- Impact on plan: keep the sidebar unchanged and focus this task on canonical-page strengthening plus subtree link normalization.

### Canonical pages are still too thin

- Why it mattered: readers entering the new routes should understand the module boundary before being sent deeper.
- What was found: several new-path pages were only acting as bridge pages and did not yet explain the architecture or concept boundary well enough.
- Impact on plan: rewrite the key canonical pages so they become stable entry points instead of route aliases.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Sidebar strategy | keep current sidebar | IA direction is already validated; the issue is content convergence | reopen the sidebar and rename more routes |
| Legacy deep pages | keep as supporting detail only | preserves existing long-form material without reopening a whole-tree rewrite | delete or fully rewrite all legacy deep pages |
| Coding Agent protocol entry | make `mcp-and-acp` the canonical protocol boundary page | it matches the new sidebar and fixes the split across multiple older pages | continue linking `mcp-integration` and `acp-integration` directly |
