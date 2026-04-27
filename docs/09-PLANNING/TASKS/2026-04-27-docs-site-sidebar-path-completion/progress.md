# docs-site Sidebar Path Completion - Progress

## Status

`completed`

## Log

### 2026-04-27 23:02 - Bootstrap

- What changed: started a new multi-wave docs-site completion feature after design approval, anchored to the sidebar-first reading path and set Wave 1 to `intro + Start Here + FAQ/Glossary`.
- Verification: design approval recorded in the active thread; harness task and Feature SSoT update in progress
- Next: audit the current Wave 1 pages and rewrite the thinnest canonical entry pages first.

### 2026-04-27 23:06 - Wave 1 audit

- What changed: audited the current `intro`, `Start Here`, `FAQ`, and `Glossary` pages, then identified the thinnest entry-path pages as `choose-your-path`, the two quickstarts, `first-chat`, `first-tool-call`, and `troubleshooting`.
- Verification: targeted reads and line-count scan across the Wave 1 page set
- Next: strengthen the entry-path canonical pages first, then lightly realign `intro`, `FAQ`, and `Glossary`.

### 2026-04-27 23:10 - Wave 1 rewrite

- What changed: rewrote the weakest entry-path pages so they now explain reader intent, module boundaries, success criteria, and next-reading order; also aligned `intro`, `FAQ`, and `Glossary` with the new “AI 基座 + canonical page first” framing.
- Verification: `git diff -- docs-site/docs/intro.md docs-site/docs/start-here/choose-your-path.md docs-site/docs/start-here/quickstart-java.md docs-site/docs/start-here/quickstart-spring-boot.md docs-site/docs/start-here/first-chat.md docs-site/docs/start-here/first-tool-call.md docs-site/docs/start-here/troubleshooting.md docs-site/docs/faq.md docs-site/docs/glossary.md`; targeted `rg` link scan across the edited pages
- Next: run `RG-008` and record this wave under the docs-site regression ledger.

### 2026-04-27 23:12 - Wave 1 regression

- What changed: ran the docs-site regression gate for the Wave 1 entry-path rewrite and confirmed the edited pages compile successfully.
- Verification: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-start-here-wave1-verify`
- Next: keep `F-015` active and move into Wave 2 (`Core SDK`) in the next implementation batch.

### 2026-04-27 23:18 - Wave 2 audit

- What changed: audited the `Core SDK` canonical tree and confirmed the main thin spots are no longer the top narrative pages, but the package/entry and capability-overview pages such as `package-map`, `service-entry-and-registry`, and the `model-access / tools / skills / mcp / memory / search-and-rag / extension` overview pages.
- Verification: targeted reads, line-count scan, and source-structure checks across `ai4j/` package directories plus `service.factory`
- Next: rewrite the thin Core SDK canonical pages against the actual `ai4j/` package and service-entry structure.

### 2026-04-27 23:27 - Wave 2 rewrite

- What changed: strengthened the Core SDK package, entry, and capability-overview pages so they now explain real module paths, service-entry semantics, capability boundaries, and recommended reading flow instead of acting as thin topic bridges.
- Verification: `git diff -- docs-site/docs/core-sdk/package-map.md docs-site/docs/core-sdk/service-entry-and-registry.md docs-site/docs/core-sdk/model-access/overview.md docs-site/docs/core-sdk/tools/overview.md docs-site/docs/core-sdk/skills/overview.md docs-site/docs/core-sdk/mcp/overview.md docs-site/docs/core-sdk/memory/overview.md docs-site/docs/core-sdk/search-and-rag/overview.md docs-site/docs/core-sdk/extension/overview.md`; targeted `rg` scan for the linked canonical deep pages and service-entry terms
- Next: run `RG-008` for the Core SDK wave and record the new docs-site batch in the regression ledgers.

### 2026-04-27 23:31 - Wave 2 regression

- What changed: ran the docs-site regression gate for the Core SDK wave and confirmed the rewritten canonical pages compile successfully.
- Verification: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-core-sdk-wave2-verify`
- Next: keep `F-015` active and move into Wave 3 (`Spring Boot`) in the next implementation batch.

### 2026-04-27 23:33 - Wave 3 audit

- What changed: audited the six `Spring Boot` canonical pages and confirmed the whole tree was still too short, with the biggest gaps around starter positioning, auto-configuration semantics, configuration layering, and Bean-extension boundaries.
- Verification: targeted reads across `docs-site/docs/spring-boot/*.md` and source checks around `AiConfigAutoConfiguration` plus the starter property classes
- Next: rewrite the full Spring Boot canonical set so the starter role, Bean graph, and configuration flow are explicit.

### 2026-04-27 23:36 - Wave 3 rewrite

- What changed: rewrote all six `Spring Boot` canonical pages so they now explain the starter's module role, quickstart success criteria, `AiConfigAutoConfiguration` semantics, configuration layering, Bean extension boundaries, and recommended engineering patterns.
- Verification: `git diff -- docs-site/docs/spring-boot/overview.md docs-site/docs/spring-boot/quickstart.md docs-site/docs/spring-boot/auto-configuration.md docs-site/docs/spring-boot/configuration-reference.md docs-site/docs/spring-boot/bean-extension.md docs-site/docs/spring-boot/common-patterns.md`; targeted `rg` scan for `AiServiceRegistry`, `AiConfigAutoConfiguration`, `ai.platforms[]`, and related canonical links
- Next: run `RG-008` for the Spring Boot wave and record the new docs-site batch in the regression ledgers.

### 2026-04-27 23:38 - Wave 3 regression

- What changed: ran the docs-site regression gate for the Spring Boot wave and confirmed the rewritten canonical pages compile successfully.
- Verification: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-spring-boot-wave3-verify`
- Next: keep `F-015` active and move into Wave 4 (`Agent`) in the next implementation batch.

### 2026-04-27 23:46 - Wave 4 audit

- What changed: audited the `Agent` canonical tree and confirmed the main thin spots had shifted into the real sidebar path: `why-agent`, `quickstart`, `memory-and-state`, `tools-and-registry`, the four `runtimes/*` pages, the three `orchestration/*` entry pages, and `observability/trace`.
- Verification: targeted reads across `docs-site/docs/agent/**`, sidebar inspection, line-count scan, plus source checks around `AgentBuilder`, `BaseAgentRuntime`, `ToolUtilRegistry`, `HandoffPolicy`, `AgentTeam`, and `AgentTraceListener`
- Next: rewrite the Agent canonical pages against the actual package split and execution chain instead of keeping them as bridge pages.

### 2026-04-27 23:54 - Wave 4 rewrite

- What changed: rewrote the Agent canonical path so the sidebar-visible pages now explain runtime selection, memory/state boundaries, tool governance, subagent handoff, teams, and trace with concrete `ai4j-agent/` package anchors.
- Verification: `git diff -- docs-site/docs/agent/why-agent.md docs-site/docs/agent/architecture.md docs-site/docs/agent/quickstart.md docs-site/docs/agent/memory-and-state.md docs-site/docs/agent/tools-and-registry.md docs-site/docs/agent/runtimes/*.md docs-site/docs/agent/orchestration/*.md docs-site/docs/agent/observability/trace.md`; targeted `rg` scans for `AgentBuilder`, `BaseAgentRuntime`, `AgentToolRegistry`, `HandoffPolicy`, `AgentTeam`, and `AgentTraceListener`
- Next: move directly into the remaining sidebar waves instead of pausing after the Agent tree.

### 2026-04-27 23:58 - Waves 5-7 audit and rewrite

- What changed: completed the remaining sidebar-first waves by strengthening the thin `Coding Agent`, `Flowgram`, and `Solutions` canonical pages; the rewrite explicitly separated `Flowgram.ai` as the ByteDance open-source frontend library from AI4J's backend runtime layer, and repositioned `Solutions` as scenario-entry pages that route readers back to canonical module docs plus retained deep-detail guides.
- Verification: `git diff -- docs-site/docs/coding-agent/why-coding-agent.md docs-site/docs/coding-agent/architecture.md docs-site/docs/coding-agent/install-and-release.md docs-site/docs/coding-agent/mcp-and-acp.md docs-site/docs/flowgram/why-flowgram.md docs-site/docs/flowgram/architecture.md docs-site/docs/flowgram/runtime.md docs-site/docs/flowgram/built-in-nodes.md docs-site/docs/flowgram/custom-nodes.md docs-site/docs/solutions/*.md`; targeted source and guide scans for `CliMcpRuntimeManager`, `AcpJsonRpcServer`, `FlowGramTaskController`, `FlowGramRuntimeService`, `JdbcFlowGramTaskStore`, `ChatMemory`, `JdbcAgentMemory`, `VectorStore`, `ChatWithWebSearchEnhance`, and SPI HTTP stack references
- Next: rerun `RG-008`, update the regression ledgers, and close the feature with a walkthrough.

### 2026-04-27 23:59 - Waves 4-7 regression and closeout

- What changed: reran the docs-site regression gate for the remaining sidebar waves and closed the full docs-site sidebar completion stream with the final harness records.
- Verification: `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck`; `NODE_OPTIONS=--max-old-space-size=8192 npx docusaurus build --out-dir build-agent-coding-flowgram-solutions-verify`; walkthrough draft at `docs/10-WALKTHROUGH/2026-04-27-docs-site-sidebar-path-completion.md`
- Next: none; `F-015` is complete.

## Residual

- none
