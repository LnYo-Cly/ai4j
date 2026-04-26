# docs-site IA Redesign - Findings

## Discoveries

### The current docs-site splits one base story across multiple top-level trees

- Why it mattered: the same conceptual layer currently appears in `getting-started`, `ai-basics`, `core-sdk`, and `mcp`, so readers cannot build a stable mental model
- What was found: `ai-basics` is the live base entry, `core-sdk` contains overlapping draft content, and `mcp` plus quickstart pages duplicate parts of the same foundation story
- Impact on plan: collapse the base narrative into one canonical `Core SDK` tree and leave `Start Here` responsible only for orientation and first success paths

### `MCP`, `Function Call`, and `Skill` were under-explained as separate but related base capabilities

- Why it mattered: these three concepts define how AI4J connects model calls, local execution, and external capability systems
- What was found: `Function Call` was fragmented across quickstart and chat pages, `MCP` risked being treated as just a tool subtype, and `Skill` had both base-layer and coding-agent-layer meanings
- Impact on plan: keep `Tools`, `Skills`, and `MCP` as separate peer sections under `Core SDK`, with `Coding Agent` retaining only the productized `Skills` usage entry

### The primary issue is information architecture, not raw page count

- Why it mattered: jumping into wholesale content rewriting before stabilizing structure would create more churn and duplicated work
- What was found: many current pages already contain usable material, but their placement, naming, and overlap make the site feel incomplete and confusing
- Impact on plan: finish IA registration first, then migrate and rewrite pages in priority order instead of rewriting the whole site in one pass

### The docs plugin was still whitelisting only legacy content trees

- Why it mattered: new `start-here`, `core-sdk`, `spring-boot`, and `solutions` pages were invisible to Docusaurus even though the files existed
- What was found: `docusaurus.config.ts` used an explicit `include` whitelist that only covered legacy trees such as `getting-started`, `ai-basics`, `mcp`, and `guides`
- Impact on plan: expand the docs include list before treating sidebar errors as content problems

### The remaining docs-site build failure is environmental, not structural

- Why it mattered: the closeout needed to distinguish real IA/link regressions from local machine artifact-lock noise
- What was found: repeated Docusaurus builds compiled both client and server bundles successfully after the sidebar and page migrations, but final artifact cleanup failed on Windows `EPERM` locks in output and webpack cache files
- Impact on plan: treat `RG-008` as partially verified with honest residual tracking instead of reopening the IA redesign for non-content reasons

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Base entry | unify around `Core SDK` | removes `ai-basics` / `core-sdk` dual-track drift | keep both trees and cross-link them |
| Orientation strategy | separate `Start Here` from reference depth | preserves a clean onboarding path | keep detailed implementation pages in quickstart |
| `MCP` placement | `Core SDK / MCP` as a peer of `Tools` | `MCP` covers protocol, transport, gateway, and publishing, not only tool calls | nest `MCP` under `Tools` |
| `Skill` placement | `Core SDK / Skills` plus `Coding Agent / Skills` product entry | matches actual capability ownership and user-facing product usage | keep `Skill` only in `Coding Agent` |
| Upper-module migration pace | normalize `Agent` / `Coding Agent` / `Flowgram` navigation first and defer deep route renames | reduces risk while the base IA is still settling | rename all upper-module files in the same pass |
