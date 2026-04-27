# docs-site Sidebar Path Completion - Findings

## Discoveries

### docs-site is now a content-completion problem, not mainly an IA problem

- Why it mattered: recent work already stabilized the sidebar direction, so the next bottleneck is page quality rather than route taxonomy.
- What was found: many sidebar-visible trees already have the right canonical page names, but several pages still read like thin bridges instead of stable entry or architecture pages.
- Impact on plan: execute the remaining work as a wave-by-wave content-completion stream rather than reopening a site-wide IA redesign.

### The user wants the site to serve both onboarding and interview review

- Why it mattered: the site needs to do two jobs at once, which changes what “complete enough” means.
- What was found: the desired reading order is “first explain and sell the module, then explain the architecture, then support deeper review.”
- Impact on plan: require each completed canonical page to cover positioning, strengths, architecture boundary, and clear next-reading links before moving deeper.

### Wave 1 was weak mainly in action pages, not in the top narrative pages

- Why it mattered: if the thinnest pages sit in the middle of the entry flow, readers still fall out of the main path even when `intro`, `why`, and `architecture` are acceptable.
- What was found: `intro`, `start-here/why-ai4j`, and `start-here/architecture-at-a-glance` were already serviceable, while `choose-your-path`, both quickstarts, `first-chat`, `first-tool-call`, and `troubleshooting` were still too bridge-like.
- Impact on plan: focus Wave 1 edits on the action-oriented canonical pages first, and use `intro` / `FAQ` / `Glossary` only for alignment rather than full rewrites.

### Wave 1 regression is clean with the raised Node heap baseline

- Why it mattered: this completion stream will repeatedly hit `RG-008`, so the docs-site validation path has to be predictable.
- What was found: with `NODE_OPTIONS=--max-old-space-size=8192`, both `npm run typecheck` and `npx docusaurus build --out-dir build-start-here-wave1-verify` passed for the Wave 1 rewrite.
- Impact on plan: keep using the raised-heap docs-site baseline for subsequent waves unless the underlying Node memory issue is fixed.

### Core SDK thin spots were concentrated in canonical capability overviews

- Why it mattered: `Core SDK` already had acceptable top-level narrative pages, so rewriting the wrong files would create churn without improving the reading path.
- What was found: `overview`, `strengths-and-differentiators`, and `architecture-and-module-map` were serviceable, while `package-map`, `service-entry-and-registry`, and the capability overview pages were still too short to act as real canonical entry pages.
- Impact on plan: keep the stronger narrative pages mostly intact and focus Wave 2 on the package/entry and capability-overview layer.

### `AiServiceRegistry` is real, but it needs to be documented with the current factory-layer boundary

- Why it mattered: the old docs wording risked making the registry sound like a purely conceptual abstraction or inventing a new entry layer.
- What was found: the actual code has a concrete `AiServiceRegistry`, `DefaultAiServiceRegistry`, and `AiServiceRegistration` under `service.factory`, while `FreeAiService` is now explicitly a deprecated compatibility shell.
- Impact on plan: rewrite `service-entry-and-registry` around the real `Configuration -> AiService` mainline, then place registry and compatibility APIs as the next layer instead of the first one.

### Wave 2 regression is also clean with the raised Node heap baseline

- Why it mattered: the docs-site completion stream needs a stable verification pattern from wave to wave.
- What was found: with `NODE_OPTIONS=--max-old-space-size=8192`, both `npm run typecheck` and `npx docusaurus build --out-dir build-core-sdk-wave2-verify` passed for the Core SDK canonical rewrite.
- Impact on plan: continue using the same docs-site verification baseline for Wave 3 unless the underlying memory profile changes.

### The Spring Boot tree was uniformly thin, not just one or two pages

- Why it mattered: unlike Wave 2, this tree did not have a few obviously strong canonical pages to leave mostly untouched.
- What was found: all six Spring Boot canonical pages were short and mostly procedural, with the weakest areas around starter identity, auto-configuration semantics, configuration layering, and Bean extension guidance.
- Impact on plan: rewrite the entire canonical Spring Boot set as one cohesive wave instead of trying to patch only one or two pages.

### `AiConfigAutoConfiguration` is the real anchor for the Spring Boot narrative

- Why it mattered: without a concrete starter entry class, the docs risked sounding like abstract Spring guidance instead of repo-grounded documentation.
- What was found: `AiConfigAutoConfiguration` wires `AiService`, `AiServiceFactory`, `AiServiceRegistry`, `FreeAiService`, conditional vector-store beans, and default `RagContextAssembler` / `Reranker` behavior, while `@EnableConfigurationProperties` ties together the `ai.*` property surfaces.
- Impact on plan: center the Spring Boot docs around the actual auto-configuration entry and use that to explain quickstart, configuration, and Bean extension boundaries.

### Wave 3 regression is also clean with the raised Node heap baseline

- Why it mattered: repeated successful docs-site validation lowers the risk of continuing directly into later waves without pausing for environment debugging.
- What was found: with `NODE_OPTIONS=--max-old-space-size=8192`, both `npm run typecheck` and `npx docusaurus build --out-dir build-spring-boot-wave3-verify` passed for the Spring Boot rewrite.
- Impact on plan: continue using the same verification baseline for Wave 4 unless the docs-site memory profile changes again.

### The Agent tree had good deep detail pages, but its real sidebar path was still too bridge-like

- Why it mattered: without checking both the canonical sidebar path and the older flat detail pages, it would have been easy to rewrite the wrong layer or duplicate content.
- What was found: the `agent/` subtree already had richer older pages such as `memory-management`, `workflow-stategraph`, `subagent-handoff-policy`, and `trace-observability`, while the actual sidebar-bound canonical entry pages under `agent/`, `agent/runtimes/`, `agent/orchestration/`, and `agent/observability/` were still thin.
- Impact on plan: keep the deep detail pages as supplemental material, but move the essential package boundaries and runtime semantics into the real sidebar-visible canonical pages.

### Agent documentation had to be grounded in `AgentBuilder` and `BaseAgentRuntime`, not just concepts

- Why it mattered: the user wants docs that can support both onboarding and interview review, so vague descriptions of “智能体框架” are not enough.
- What was found: the real execution anchor is `AgentBuilder -> AgentContext -> AgentRuntime -> AgentModelClient / AgentToolRegistry / AgentMemory / AgentEventPublisher`, with `BaseAgentRuntime` holding the default loop semantics and `CodeActRuntime` diverging into executable-code flow.
- Impact on plan: rewrite the Agent wave around concrete package anchors and execution chains so readers can map narrative pages back to source classes quickly.

### Flowgram docs must explicitly separate the ByteDance frontend library from AI4J's backend runtime layer

- Why it mattered: the user explicitly called out that the docs had not clearly said `Flowgram.ai` is a ByteDance open-source frontend library, which made the architecture read as if AI4J owned the whole stack.
- What was found: the actual repo splits the concern across `ai4j-flowgram-webapp-demo/` on the frontend side, `ai4j-flowgram-spring-boot-starter/` for HTTP/task-store/platform wiring, and `ai4j-agent/.../flowgram` for the runtime engine.
- Impact on plan: rewrite `why-flowgram`, `architecture`, and `runtime` so they clearly frame AI4J's role as the Java backend execution layer around the external frontend canvas library.

### The Solutions tree works best as scenario-entry pages that route into canonical modules and retained deep guides

- Why it mattered: the user wants the docs-site to “先宣传，再进阶”, while the Solutions pages were previously too short to sell the scenario or explain when each pattern should be chosen.
- What was found: the repo already contains deeper implementation guides under `docs-site/docs/guides/`, so the missing piece was not raw detail but a clear canonical scenario layer telling readers what each solution solves, which modules it combines, and when to stop or upgrade.
- Impact on plan: turn `solutions/` into readable entry pages and keep the old guide pages only as deeper implementation references instead of the primary sidebar story.

### Waves 4-7 regression stayed clean under the same raised-heap docs baseline

- Why it mattered: the final docs-site completion batch touched four major sidebar sections at once, so a stable owning gate was needed before closing the feature.
- What was found: with `NODE_OPTIONS=--max-old-space-size=8192`, `npm run typecheck` and `npx docusaurus build --out-dir build-agent-coding-flowgram-solutions-verify` both completed successfully for the remaining sidebar waves.
- Impact on plan: close `F-015` and record the docs-site completion stream as finished while keeping `RG-008` marked partial until the literal primary `npm run build` path is rerun in a future cycle.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Primary execution order | sidebar-first waves | it matches the approved reader journey and keeps the highest-traffic pages coherent first | rewrite trees opportunistically by whichever page feels weakest |
| Wave 1 scope | `intro`, `Start Here`, `FAQ`, `Glossary` | the whole site reads better only after the shared entry path is strong | jump directly into another module tree before stabilizing the site entry |
| Feature structure | one active master feature with wave-by-wave progress | the user asked for a sequential whole-site补全 stream rather than isolated one-off tasks | create one separate feature row for every tiny docs batch |
