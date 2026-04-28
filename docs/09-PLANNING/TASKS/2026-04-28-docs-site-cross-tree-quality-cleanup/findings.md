# docs-site cross-tree quality cleanup - Findings

## Discoveries

### Quality drift moved outside `core-sdk`

- Why it mattered: after the `core-sdk` wave was completed, the remaining docs-site quality issues were no longer concentrated in one subtree
- What was found: interview-oriented phrasing and bridge-page density now remain across `start-here`, `intro`, `solutions`, selected `spring-boot`, `agent`, `getting-started`, and `mcp` pages
- Impact on plan: treat this as a cross-tree cleanup wave instead of reopening the already-closed `core-sdk` task

### `solutions/` is the thinnest visible cluster

- Why it mattered: the scan showed multiple `solutions/` pages under 70 lines, which means the entry scenarios still read more like route markers than technical docs
- What was found: `solutions/overview.md` plus scenario pages like RAG, memory, MCP, and Flowgram workflows are consistently the shortest visible pages
- Impact on plan: after style normalization, prioritize `solutions/` and other thin canonical pages for deepening

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Task scope | open a new docs-site cleanup wave outside `core-sdk` | the prior feature is already closed and the remaining work spans multiple trees | keep editing ad hoc without a new harness task |
| Rewrite priority | fix style residue first, then deepen the thinnest canonical pages | the user explicitly rejected interview-style wording, and the line-count scan shows where the next structural gaps are | deepen pages first and clean language later |
| First deepening target | prioritize `solutions/`, then selected `start-here` / `spring-boot` / `mcp` / `agent` pages | these surfaces remain user-facing and still contain the highest density of thin or outdated content | spread the edits evenly across every subtree |
