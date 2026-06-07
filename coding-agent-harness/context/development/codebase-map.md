# 代码地图 / Codebase Map

Context Doc Type: codebase-map
Owner: project coordinator
Last Verified: 2026-06-07
Confidence: medium

## Entry Points

| Area | Path | Responsibility | Read When | Source Evidence | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- | --- |
| Repo operating rules | `AGENTS.md` | Monorepo identity, hard rules, build/test commands and reading matrix. | Always first. | `AGENTS.md` | 2026-06-04 | high |
| Agent module architecture | `AGENT.md` | Architecture note for `ai4j-agent/`. | Agent runtime/workflow/trace/subagent changes. | `AGENTS.md`; `AGENT.md` | 2026-06-04 | high |
| Parent Maven build | `pom.xml` | Java baseline, module list, release profile. | Any Java dependency/build/release change. | `pom.xml` | 2026-06-04 | high |
| Core SDK source | `ai4j/src/main/java` | Provider, RAG, MCP, vector, image, audio, realtime and memory source. | Core SDK/provider/RAG/MCP/vector changes. | `AGENTS.md`; file scan 2026-06-04 | 2026-06-04 | high |
| Core SDK tests | `ai4j/src/test/java` | Unit/local/provider-facing tests for core SDK. | Core SDK regression planning. | file scan 2026-06-04 | 2026-06-04 | high |
| Agent runtime source | `ai4j-agent/src/main/java` | Agent runtime, workflow, trace, memory, subagent/team orchestration. | Agent behavior changes. | `AGENTS.md`; file scan 2026-06-04 | 2026-06-04 | high |
| Coding runtime source | `ai4j-coding/src/main/java` | Workspace-aware coding tools, shell/apply-patch, loop and compaction. | Coding-agent changes. | `AGENTS.md`; file scan 2026-06-04 | 2026-06-04 | high |
| CLI host source | `ai4j-cli/src/main/java` | CLI/TUI/ACP host and session/runtime integration. | CLI/TUI/ACP changes. | `AGENTS.md`; file scan 2026-06-04 | 2026-06-04 | high |
| Starters and demo backend | `ai4j-spring-boot-starter/`, `ai4j-flowgram-spring-boot-starter/`, `ai4j-flowgram-demo/` | Spring Boot auto-config, FlowGram task APIs and backend demo. | Starter/demo integration changes. | `AGENTS.md`; module source | 2026-06-04 | high |
| Docs site | `docs-site/` | Docusaurus docs. | Docs content/config changes. | `docs-site/package.json`; workflows | 2026-06-04 | high |
| FlowGram web demo | `ai4j-flowgram-webapp-demo/` | React/Rsbuild frontend demo. | FlowGram frontend demo changes. | `ai4j-flowgram-webapp-demo/package.json`; `rsbuild.config.ts` | 2026-06-04 | high |
| Harness task SSoT | `coding-agent-harness/planning/tasks/` | Default home for new task packages, progress, review, lesson candidates, and walkthrough closeout. | Any new non-trivial work. | `AGENTS.md`; `coding-agent-harness/harness.yaml` | 2026-06-07 | high |
| Existing planning history | `docs/09-PLANNING/` | Historical/summary numbered planning material. New tasks should not be added here by default. | Legacy or existing numbered-docs continuation only. | `AGENTS.md`; `docs/09-PLANNING/Feature-SSoT.md` | 2026-06-07 | medium |
| Existing regression SSoT | `docs/05-TEST-QA/` | Current regression gate map and cadence ledger. | Any change affecting fixed regression surfaces. | `AGENTS.md`; `docs/05-TEST-QA/*.md` | 2026-06-04 | high |
| Harness v2 context | `coding-agent-harness/` | CLI scaffolded v2 context, dashboard inputs and generated metadata. | Harness CLI/dashboard/context work. | `coding-agent-harness/harness.yaml` | 2026-06-04 | high |
