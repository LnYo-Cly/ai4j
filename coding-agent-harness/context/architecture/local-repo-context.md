# 本仓上下文 / Local Repo Context

Context Doc Type: local-repo-context
Owner: project coordinator
Last Verified: 2026-06-09
Confidence: medium

## Responsibility

本仓负责 `io.github.lnyocly.ai4j` Java SDK/runtime/starter/CLI/demo/docs 的源码、测试、构建、文档和本地演示面。外部模型平台、向量数据库、FlowGram.ai 前端库、Dify/Coze/n8n 等系统只作为集成目标或依赖，不在本仓实现。

## Main Components

| Component | Path | Responsibility | Source Evidence | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- |
| Extension API | `ai4j-extension-api/` | Lightweight third-party extension contract, manifest, discovery, explicit enable/expose gates, neutral tool/command/skill/prompt/guardrail specs. | `AGENTS.md`; `pom.xml`; `ai4j-extension-api/src/main/java` | 2026-06-08 | high |
| Ask User plugin | `ai4j-plugin-ask-user/` | Official sample plugin package for host-mediated user clarification via tool, command, Skill, and Prompt resources. | `AGENTS.md`; `pom.xml`; `ai4j-plugin-ask-user/src/main/java` | 2026-06-09 | high |
| Core SDK | `ai4j/` | Provider access, Chat/Responses, RAG, MCP, vector, image, audio, realtime, web search, memory. | `AGENTS.md`; `pom.xml`; `ai4j/src/main/java` | 2026-06-04 | high |
| Agent runtime | `ai4j-agent/` | Agent runtime, workflow, trace, memory, subagent/team orchestration, FlowGram runtime model. | `AGENTS.md`; `AGENT.md`; `ai4j-agent/src/main/java` | 2026-06-04 | high |
| Coding runtime | `ai4j-coding/` | Workspace-aware coding tools, shell/apply-patch runtime, loop policy, compaction, session state. | `AGENTS.md`; `ai4j-coding/src/main/java` | 2026-06-04 | high |
| CLI host | `ai4j-cli/` | CLI, TUI, ACP host, session/runtime integration, provider/MCP config. | `AGENTS.md`; `ai4j-cli/src/main/java` | 2026-06-04 | high |
| Spring Boot starter | `ai4j-spring-boot-starter/` | Auto-configuration and property binding for core SDK and AgentFlow. | `AGENTS.md`; `ai4j-spring-boot-starter/src/main/java` | 2026-06-04 | high |
| FlowGram starter | `ai4j-flowgram-spring-boot-starter/` | FlowGram task APIs, runtime bridge, trace bridge and starter support. | `AGENTS.md`; `ai4j-flowgram-spring-boot-starter/src/main/java` | 2026-06-04 | high |
| FlowGram demo backend | `ai4j-flowgram-demo/` | Spring Boot demo backend and mock controller for FlowGram starter integration. | `AGENTS.md`; `ai4j-flowgram-demo/src/main/java`; `ai4j-flowgram-demo/README.md` | 2026-06-04 | high |
| BOM | `ai4j-bom/` | Dependency/version alignment for published modules. | `pom.xml`; `ai4j-bom/pom.xml` | 2026-06-04 | high |
| Docs site | `docs-site/` | Docusaurus documentation site built with Node 20+. | `docs-site/package.json`; `.github/workflows/docs-build.yml` | 2026-06-04 | high |
| FlowGram web demo | `ai4j-flowgram-webapp-demo/` | React/Rsbuild frontend demo surface for FlowGram workflow editing. | `ai4j-flowgram-webapp-demo/package.json`; `ai4j-flowgram-webapp-demo/README.md` | 2026-06-04 | high |
| Harness v2 context | `coding-agent-harness/` | CLI-managed harness context, regression mirrors, dashboard inputs and generated metadata. | `coding-agent-harness/harness.yaml`; init report 2026-06-04 | 2026-06-04 | high |

## External Dependencies

| Dependency | Why It Matters | Architecture Link | Integration Link | Development Link |
| --- | --- | --- | --- | --- |
| Model provider APIs | Core SDK and agent/coding runtimes normalize provider requests, streaming, auth and errors. | `service-catalog.md` | `coding-agent-harness/context/integrations/README.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` |
| Vector stores | RAG/vector surfaces integrate Pinecone, Qdrant, pgvector and Milvus-style backends. | `service-catalog.md` | `coding-agent-harness/context/integrations/README.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` |
| MCP clients/servers | SDK and CLI expose MCP transport/config/runtime surfaces. | `system-map.md` | `coding-agent-harness/context/integrations/README.md` | `coding-agent-harness/context/development/codebase-map.md` |
| FlowGram.ai frontend/runtime contracts | Starter, backend demo and web demo share FlowGram task/schema expectations. | `service-catalog.md` | `coding-agent-harness/context/integrations/README.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` |
| AgentFlow endpoints | Dify, Coze and n8n connectors are SDK integration surfaces, not local services. | `service-catalog.md` | `coding-agent-harness/context/integrations/README.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` |
