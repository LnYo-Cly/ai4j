# docs-site Core SDK deep page completion - Findings

## Discoveries

### Canonical deep-page gap shifted below the sidebar layer

- Why it mattered: the previous docs-site stream already fixed top-level information architecture and module overviews, so the next user-visible quality problem moved into the advanced pages under `core-sdk/`
- What was found: the thinnest canonical pages were concentrated in `tools/`, `skills/`, `mcp/`, `search-and-rag/`, `extension/`, plus a smaller set in `memory/` and `model-access/`
- Impact on plan: this task targets deep-page density and source anchoring instead of another route or sidebar refactor

### Existing rich material already exists in adjacent trees

- Why it mattered: the best rewrite strategy is to avoid inventing new narratives when `ai-basics/`, `mcp/`, and older `core-sdk/` pages already contain better raw material
- What was found: richer supporting content exists for embedding, rerank, chat memory, MCP concepts, and SPI HTTP stack, while the canonical deep pages remained too short
- Impact on plan: reuse those pages as support input, but keep `core-sdk/` as the canonical reading path for SDK readers

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Rewrite order | Start with `tools`, `skills`, and `mcp`, then continue into RAG/extension/model-access/memory | these are the most visible “how it actually works” gaps after the earlier sidebar-first cleanup | jump straight to all pages without wave ordering |
| Canonical source | keep rewritten truth in `docs-site/docs/core-sdk/**` | users want one coherent SDK reading path for onboarding and interview review | route advanced readers back into mixed legacy pages |
| Depth standard | replace outline-style bridge pages with source-anchored deep pages that include examples, execution flow, boundaries, pitfalls, and technical summaries | the first rewrite pass removed thin stubs but stayed too shallow for advanced reading and technical onboarding | keep all pages short and treat them as route markers only |
