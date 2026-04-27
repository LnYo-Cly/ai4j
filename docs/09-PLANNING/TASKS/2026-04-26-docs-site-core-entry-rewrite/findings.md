# docs-site Core Entry Rewrite - Findings

## Discoveries

### The main problem moved from structure to explanation quality

- Why it mattered: the IA now points readers to the right places, but the core entry pages still do not explain the product strongly enough
- What was found: the pages are mostly short outlines, with limited value framing, weak boundary explanations, and little support for interview-style retelling
- Impact on plan: rewrite the core entry pages instead of continuing to move directories around

### The strongest pages must align with the real module graph

- Why it mattered: entry pages are the most likely to be quoted or memorized, so drift from the actual repo would create long-term confusion
- What was found: the root POM and module POMs confirm a clear upward path from `ai4j` to `ai4j-agent`, `ai4j-coding`, `ai4j-cli`, and the Flowgram starter/demo surfaces
- Impact on plan: make the module map page explicitly track the current Maven modules and dependency direction

### `Function Call`, `Skill`, and `MCP` remain the key conceptual boundary test

- Why it mattered: if these three are not clearly separated in the entry pages, the rest of the docs will feel structurally correct but conceptually muddy
- What was found: the IA already places them correctly, but the current prose still does not explain their relationship crisply enough
- Impact on plan: reinforce capability ownership in both the start-here architecture page and the core SDK overview pages

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Rewrite scope | only the 5 `Start Here + Core SDK` entry pages | strongest leverage with controlled risk | rewrite all overview pages in one pass |
| Source of truth for module claims | root/module POMs plus current repo layout | most reliable local evidence | rely on older docs wording |
| Validation standard | `RG-008` with honest Windows residual handling | consistent with the existing harness regression policy | skip build validation entirely |
