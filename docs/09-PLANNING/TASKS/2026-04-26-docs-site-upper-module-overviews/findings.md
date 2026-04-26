# docs-site Upper Module Overviews - Findings

## Discoveries

### The next bottleneck is upper-module onboarding quality

- Why it mattered: the base entry path is now coherent, but readers still hit weaker overview pages when moving into `Agent`, `Coding Agent`, and `Flowgram`
- What was found: all three overview pages still read more like topic inventories than persuasive onboarding pages
- Impact on plan: rewrite the three overview pages rather than only touching link syntax

### Old canonical path residue is concentrated around the overview cluster

- Why it mattered: even a strong overview page feels unfinished if its first outbound links jump back to old route names
- What was found: immediate entry pages still reference older paths such as `acp-integration`, `release-and-installation`, `use-cases-and-paths`, `builtin-nodes`, and related legacy route names
- Impact on plan: fix the nearby quickstart/why/bridge pages in the same pass, without broadening into a full tree migration

### The module boundaries are already strong enough to narrate cleanly

- Why it mattered: overview-page quality depends on whether each module has a distinct job to explain
- What was found: the current repo structure and module POMs support a clear story:
  `ai4j-agent` is the general runtime layer, `ai4j-coding + ai4j-cli` is the productized coding-agent path, and `ai4j-flowgram-spring-boot-starter + demo` is the visual workflow platform path
- Impact on plan: anchor each overview page around one stable sentence and one clear boundary section

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Rewrite scope | 3 overview pages plus nearby entry-link normalization | highest leverage without reopening the whole module trees | fix links only |
| Narrative emphasis | module role first, detailed inventory second | readers need positioning before subtopic lists | keep feature lists as the dominant structure |
| Validation standard | `RG-008` with existing Windows residual handling | consistent with prior docs-site tasks | skip build verification |
