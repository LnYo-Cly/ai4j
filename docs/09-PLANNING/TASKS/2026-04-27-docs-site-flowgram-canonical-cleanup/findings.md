# docs-site Flowgram Canonical Cleanup - Findings

## Discoveries

### The next docs-site bottleneck is inside the Flowgram subtree, not at the overview level

- Why it mattered: the overview pages already explain the product line more clearly, but readers can still fall back into old route names once they enter deep Flowgram pages
- What was found: the `flowgram/` tree still has many links to `api-and-runtime`, `builtin-nodes`, `custom-node-extension`, `frontend-custom-node-development`, and `workflow-execution-pipeline`
- Impact on plan: clean the subtree around current canonical pages instead of reopening top-level architecture

### The canonical pages are currently too thin

- Why it mattered: sidebar pages such as `runtime`, `built-in-nodes`, and `custom-nodes` should stand on their own, not behave like near-empty redirect notes
- What was found: these pages currently act as thin bridge pages and do not carry enough explanatory value
- Impact on plan: rewrite those three pages into compact but real canonical topic pages

### The Flowgram.ai boundary must persist below the overview layer

- Why it mattered: the user explicitly pointed out that `Flowgram.ai` is a ByteDance open-source frontend library, and that distinction should not be lost once readers leave the overview page
- What was found: the overview layer now states the boundary, but deeper runtime-oriented pages still assume the split rather than saying it directly
- Impact on plan: carry the frontend-library vs backend-runtime distinction into the canonical Flowgram subtree pages

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Cleanup strategy | strengthen canonical pages and normalize deep-page links | highest leverage with controlled change size | rename or rewrite the whole Flowgram tree |
| Canonical execution page | keep `runtime` as the canonical entry | matches the current sidebar and avoids more IA churn | restore `api-and-runtime` as the sidebar entry |
| Custom node entry | keep `custom-nodes` as the canonical entry | lets one page explain both frontend and backend halves together | keep separate frontend/backend entries in the sidebar |
