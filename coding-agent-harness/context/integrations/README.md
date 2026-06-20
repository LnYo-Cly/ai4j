# 接口契约 / Integrations

Context Doc Type: integration-index
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: medium

## Purpose

这个文件夹只放具体接口契约：API、event、webhook、SDK、第三方集成、auth、payload、error 和 contract tests。

Keep the English field names and section headings because CLI checks rely on them.

## Boundary

- 服务拓扑和职责归属放 `coding-agent-harness/context/architecture/`。
- 开发 mock 和调试说明放 `coding-agent-harness/context/development/`。
- endpoint、payload、error、auth、event、webhook、SDK 契约放这里。

## Structure Contract

| 文件 / 路径 | 必须维护的事实 | 写入规则 |
| --- | --- | --- |
| `<service-key>-api.md` | API endpoint、auth、payload、error、contract test | 从 `api-contract.md` 创建 |
| `<event-name>-event.md` | event producer/consumer、schema、delivery、retry | 从 `event-contract.md` 创建 |
| `<webhook-name>-webhook.md` | webhook source、target、signature、payload、retry | 从 `webhook-contract.md` 创建 |
| `third-party/<vendor-key>.md` | 第三方平台、账号/权限边界、SDK 使用、限制 | 从 `third-party/vendor-template.md` 创建 |

## Contract Rule

每个接口契约必须是独立文件，并链接回对应服务：

- 服务职责和上下游关系：`coding-agent-harness/context/architecture/service-catalog.md` 或 `services/<service-key>.md`
- 本地 mock / stub / debug：`coding-agent-harness/context/development/external-context/<service-key>.md`
- 具体 payload、auth、error、contract test：本文件夹

不要在一个“接口说明”大文档里混写多个服务。多个微服务就维护多个契约文件；README 里的 Contract Index 负责让人和 Agent 快速定位。

## Contract Index

| Contract | Type | Producer | Consumer | Service Profile | Development Context | Contract Tests | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| model-provider-api | HTTP/SSE/API SDK contract category | external model providers | `ai4j-core`, `ai4j-agent`, `ai4j-coding`, `ai4j-cli` | `coding-agent-harness/context/architecture/service-catalog.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` | module tests under `ai4j/src/test/java`, provider live tests opt-in | 2026-06-04 | medium |
| vector-store-api | SDK/client contract category | Pinecone/Qdrant/pgvector/Milvus-style stores | `ai4j-core` RAG/vector surfaces | `coding-agent-harness/context/architecture/service-catalog.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` | `ai4j/src/test/java/io/github/lnyocly/ai4j/vector` | 2026-06-04 | medium |
| mcp-protocol | protocol/transport category | MCP clients/servers | `ai4j-core`, `ai4j-cli` | `coding-agent-harness/context/architecture/service-catalog.md` | `coding-agent-harness/context/development/codebase-map.md` | `ai4j/src/test/java/io/github/lnyocly/ai4j/mcp`; `ai4j-cli/src/test/java` | 2026-06-04 | medium |
| agentflow-connectors | API/webhook-style connector category | Dify, Coze, n8n endpoints | `ai4j-core` AgentFlow service tests | `coding-agent-harness/context/architecture/service-catalog.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` | `ai4j/src/test/java/io/github/lnyocly/ai4j/agentflow` | 2026-06-04 | medium |
| flowgram-task-api | local demo/starter API category | `ai4j-flowgram-spring-boot-starter`, `ai4j-flowgram-demo` | `ai4j-flowgram-webapp-demo` and local callers | `coding-agent-harness/context/architecture/service-catalog.md` | `coding-agent-harness/context/development/stubs-and-mocks.md` | `ai4j-flowgram-spring-boot-starter/src/test/java`; demo README smoke commands | 2026-06-04 | medium |
