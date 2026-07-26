# Changelog

All notable changes to ai4j are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release notes are also published on the [GitHub Releases](https://github.com/LnYo-Cly/ai4j/releases) page.

## [2.4.2] — 2026-07-23

### Added
- RAG `RagTool` now supports server-side filtering, allowing the vector store to
  narrow candidates before fusion/rerank (#201).
- RAG generation response now carries a usage trace field so callers can inspect
  token costs alongside retrieval metrics (#200).

### Changed
- Polished README capability summary and aligned version references across docs (#199).
- Added a release checklist doc to standardize the publish flow (#198).

### Fixed
- Bumped main to `2.4.3-SNAPSHOT` after the 2.4.2 tag to keep the snapshot line
  ahead of the release (#197).

## [2.4.1] — 2026-07-21

### Fixed
- Corrected Central Portal publishing configuration (`central-publishing-maven-plugin`
  0.11.0 settings) so artifacts upload successfully (#196).
- Ensured consistent 2.4.1 artifacts across modules and bumped main to the next
  snapshot after release (#195).

## [2.4.0] — 2026-07-21

This is the largest release since 2.0: the RAG pipeline gained a query planner,
incremental ingestion, online evaluation, and cost tracking; the agent runtime
added per-node latency and observability tracing; and two new media services
landed.

### Added
- **RAG query planner** — query rewriting and sub-query strategy with pluggable
  prompts, plus a RAG-only mode (#176, #177). History-aware query planning
  extends the planner with multi-turn context (#190).
- **RAG incremental ingest** — append/update documents without re-indexing the
  whole corpus (#187).
- **RAG cost calculation** — token and currency cost estimation per retrieval
  and generation (#186).
- **RAG online evaluation** — run evaluators against live traffic to track
  Precision@K / Recall@K / F1@K / MRR / NDCG over time (#184).
- **Hybrid retriever best-effort mode** — graceful degradation when one retriever
  backend is unavailable (#179).
- **Dynamic workflow executor** for AgentFlow connectors (#185).
- **Suno music service** — task-based music generation (#175).
- **ChatFire OpenAI-compatible video** generation support (#174).
- **Agent per-node latency** — trace now records execution time per graph node (#173).
- Comprehensive agent roadmap and observability documentation (#117, #132–#141).

### Fixed
- Agent observability trace no longer drops payloads on streaming and structured
  error paths (#183, #191, #192).
- Bearer-token cache key collision fixed when multiple providers share a token
  prefix (#101, contributed by @itxaiohanglover).

### Changed
- Split the bilingual README into focused per-topic markdown docs under
  `docs/readme/{zh,en}/` (#193).
- Aligned README release version and published 2.4.0 artifacts (#194).

## [2.2.0] — 2026-04-06

### Added
- AgentFlow connectors and a trace bridge that links AgentFlow runs with the
  agent runtime trace system.

## [2.1.0] — 2026-04-06

### Changed
- Incremental follow-up to 2.0.0 with connector stabilization.

## [2.0.0] — 2026-04-05

### Changed
- Major refactor of the platform layer into a unified multi-module SDK (#95).
  Introduces `ai4j-extension-api`, `ai4j-agent`, `ai4j-coding`, `ai4j-cli`,
  `ai4j-spring-boot-starter`, `ai4j-flowgram-spring-boot-starter`, and `ai4j-bom`.

### Added
- Coding Agent CLI / TUI documentation covering interactive sessions, provider
  profiles, workspace model override, and session/process management (2026-03-26).

### Fixed
- Coding Agent ACP streaming: empty/whitespace-only chunks are now passed through
  as raw deltas instead of being filtered by the runtime (2026-03-28).

## [1.4.3] — 2025-08-24

### Fixed
- SSE-URL requests with authentication parameters no longer lose the API key
  during request construction (2025-08-19).

## [1.4.2] — 2025-08-19

Maintenance release.

## [1.4.1] — 2025-08-08

### Fixed
- Misc stability fixes following the 1.4.0 MCP release.

## [1.4.0] — 2025-08-08

### Added
- **MCP protocol support** — STDIO, SSE, and Streamable HTTP transports. Includes
  MCP Server and MCP Client, an MCP gateway, dynamic MCP data sources, and
  automatic reconnection (2025-08-08).

### Changed
- OpenAI `max_tokens` field is deprecated; use `max_completion_tokens` instead
  (required for GPT-5 and newer models) (2025-08-08).

## [1.3.2] — 2025-06-23

### Fixed
- Ollama streaming errors.
- Ollama function-call errors.
- Moonshot request errors.
- Ollama embedding errors.
- Missing content on reasoning/thinking responses.
- Logging conflicts.
- Added custom exception methods.

## [1.3.1] — 2025-03-17

Maintenance release.

## [1.3.0] — 2025-02-28

### Added
- Ollama platform embedding support (2025-02-28).

## [1.2.3] — 2025-02-28

Maintenance release.

## [1.2.2] — 2025-02-21

Maintenance release.

## [1.2.1] — 2025-02-19

### Added
- DeepSeek platform reasoning model support (2025-02-17).

## [1.2.0] — 2025-02-17

### Added
- Decorator-pattern Chat service enhancement with SearXNG web search — works
  without built-in search or `function_call` support (2024-12-12, backported).

## [1.1.0] — 2025-02-12

### Added
- `Authorization` header for the Ollama platform (2025-02-12).

## [1.0.0] — 2025-02-11

### Added
- Custom Jackson serialization to fix OpenAI multimodal APIs that could no
  longer be driven by raw JSON strings (2025-02-11).

## [0.8.1] — 2024-12-19

### Added
- SearXNG web search enhancement via decorator pattern (2024-12-12).

## Earlier versions

- **0.6.3** (2024-09-26): Pinecone vector store fixes.
- **0.6.2** (2024-09-20): Ollama platform support and bug fixes.
- **0.5.3** (2024-09-19): Unified error handling chain (mapped to OpenAI error
  types); URL-join and interceptor double-response fixes.
- **0.5.2** (2024-09-12): OpenAI parameter fix.
- **0.5.1** (2024-09-12): Spring Boot 2.6 OkHttp 3.14 compatibility;
  `parallel_tool_calls` null-safety fix.
- **0.5.0** (2024-09-09): Lingyi (01.AI) platform support.
- **0.4.0** (2024-09-02): Tencent Hunyuan platform support.
- **0.3.0** (2024-08-29): DeepSeek platform support; `stream_options` for usage
  stats; `ErrorInterceptor`.
- **0.2.0** (2024-08-17): Enhanced `SseListener`.
