# 服务目录 / Service Catalog

Context Doc Type: service-catalog
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: medium

## Services

| Service Key | Service / Component | Owner | Repo / Path | Responsibility | Interfaces | Data Owned | Dependencies | Service Profile | Development Context | Source Pack | Contract Index | Source Evidence | Last Verified | Stale After | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ai4j-core | Core SDK module | project coordinator | `ai4j/` | Unified provider access, Chat/Responses, RAG, MCP, vector, image, audio, realtime, web search, memory. | Java API, provider HTTP/SSE, vector store clients, MCP transports | SDK DTOs/config only | external providers, vector stores, MCP servers | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | `coding-agent-harness/context/integrations/README.md` | `AGENTS.md`; `ai4j/src/main/java` | 2026-06-04 | 2026-09-04 | high |
| ai4j-agent | Agent runtime module | project coordinator | `ai4j-agent/` | Agent runtime, workflow, trace, memory, subagent/team orchestration and FlowGram runtime bridge. | Java API, trace exporters, FlowGram task models | local runtime state abstractions | core SDK, trace exporters | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | `coding-agent-harness/context/integrations/README.md` | `AGENTS.md`; `AGENT.md`; `ai4j-agent/src/main/java` | 2026-06-04 | 2026-09-04 | high |
| ai4j-coding | Coding-agent runtime module | project coordinator | `ai4j-coding/` | Workspace-aware tools, shell/apply-patch runtime, loop control, compaction, session state. | Java API, local filesystem/shell abstractions | local coding session state | agent runtime, local workspace | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | N/A | `AGENTS.md`; `ai4j-coding/src/main/java` | 2026-06-04 | 2026-09-04 | high |
| ai4j-cli | CLI/TUI/ACP host | project coordinator | `ai4j-cli/` | CLI, TUI, ACP host, provider/MCP config and coding-session integration. | CLI commands, ACP JSON-RPC, TUI | local CLI/session files | coding runtime, MCP servers, model providers | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | `coding-agent-harness/context/integrations/README.md` | `AGENTS.md`; `ai4j-cli/src/main/java` | 2026-06-04 | 2026-09-04 | high |
| spring-starter | Spring Boot starter | project coordinator | `ai4j-spring-boot-starter/` | Auto-configuration and property binding for core SDK and AgentFlow. | Spring Boot config properties and beans | application config binding only | core SDK | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | N/A | `AGENTS.md`; module source | 2026-06-04 | 2026-09-04 | high |
| flowgram-starter | FlowGram starter and task APIs | project coordinator | `ai4j-flowgram-spring-boot-starter/` | FlowGram runtime facade, task controller/store, trace bridge and starter-side support. | Spring MVC/task APIs, FlowGram schema model | task store abstractions | agent runtime, FlowGram web contracts | N/A | `coding-agent-harness/context/development/codebase-map.md` | none | `coding-agent-harness/context/integrations/README.md` | `AGENTS.md`; module source | 2026-06-04 | 2026-09-04 | high |
| flowgram-demo | FlowGram demo backend | project coordinator | `ai4j-flowgram-demo/` | Demo backend and mock controller for FlowGram starter integration. | `/flowgram/**`; `/flowgram/demo/mock/**` | demo task runtime only | FlowGram starter, provider credentials for live demo | N/A | `coding-agent-harness/context/development/stubs-and-mocks.md` | none | `coding-agent-harness/context/integrations/README.md` | `ai4j-flowgram-demo/README.md`; `FlowGramDemoMockController.java` | 2026-06-04 | 2026-09-04 | high |
| docs-site | Documentation site | project coordinator | `docs-site/` | Docusaurus docs surface and GitHub Pages build/deploy flow. | Docusaurus routes and generated static site | docs content only | Node 20, npm dependencies | N/A | `coding-agent-harness/context/development/local-setup.md` | none | N/A | `docs-site/package.json`; `.github/workflows/docs-build.yml` | 2026-06-04 | 2026-09-04 | high |
| flowgram-web-demo | FlowGram webapp demo | project coordinator | `ai4j-flowgram-webapp-demo/` | React/Rsbuild workflow editor demo using FlowGram packages. | browser UI, `/flowgram` proxy to backend | browser local storage draft | FlowGram backend demo, FlowGram npm packages | N/A | `coding-agent-harness/context/development/stubs-and-mocks.md` | none | `coding-agent-harness/context/integrations/README.md` | `ai4j-flowgram-webapp-demo/package.json`; `rsbuild.config.ts` | 2026-06-04 | 2026-09-04 | high |

## Boundary Rule

这个目录只放服务责任、接口摘要和跳转链接。Payload、auth、error code、event schema 放 `coding-agent-harness/context/integrations/`。

一个服务或微服务只占一行。只要该服务影响本仓开发或判断，就补 `services/<service-key>.md`、`coding-agent-harness/context/development/external-context/<service-key>.md` 和相关 `coding-agent-harness/context/integrations/<contract>.md` 链接。
