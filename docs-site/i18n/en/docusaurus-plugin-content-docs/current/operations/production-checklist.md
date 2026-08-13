---
sidebar_position: 1
title: "Production Checklist"
description: "Pre-launch checklist before integrating AI4J into a real project. It does not require enabling every capability at once, but helps you confirm risk boundaries module by module: minimal module selection, secret management, network isolation, tool exposure, local file boundaries."
tags: [reference]
---

# Production Checklist

This page is a go-live checklist to run before integrating AI4J into a real project. It is not about enabling every capability in one shot; it helps you confirm risk boundaries based on the modules you actually use.

## 1. Choose the minimal modules

| What you actually use | Modules to review |
| --- | --- |
| Only model calls or tools | `ai4j` |
| Spring Boot application integration | `ai4j` + `ai4j-spring-boot-starter` |
| RAG / vector / search | `ai4j` + your vector store or search service |
| MCP | `ai4j` + MCP server / gateway configuration |
| Agent runtime | `ai4j` + `ai4j-agent` |
| Coding Agent | `ai4j-coding` + `ai4j-cli` |
| FlowGram backend | `ai4j-flowgram-spring-boot-starter` |

Do not pull in every module ahead of time "just in case I might need it later." Stabilize the current path first, then upgrade incrementally.

## 2. Configuration and secrets

- [ ] Provider keys come from environment variables, a secret manager, or the deployment platform — never committed to the repository.
- [ ] `baseUrl`, `model`, `timeout`, and `proxy` settings can be differentiated per environment.
- [ ] Provider configuration for dev, test, and prod does not cross-contaminate.
- [ ] In Spring Boot projects, each configuration entry has a clear owner.
- [ ] In multi-provider or multi-instance scenarios, the ids in `AiServiceRegistry` are readable and auditable.

## 3. Network and timeouts

- [ ] The HTTP client has a connect timeout, read timeout, and overall call timeout.
- [ ] Streaming scenarios handle cancellation, disconnection, and client-side close.
- [ ] External provider errors degrade gracefully or surface a user-understandable message.
- [ ] MCP SSE / HTTP transport has authentication, timeouts, and a reconnection strategy.
- [ ] Proxies, private `baseUrl` values, and intranet endpoints have been tested.

## 4. Tool and MCP attack surface

- [ ] Tools are not all exposed by default.
- [ ] Function tools are passed in via a per-scenario allowlist.
- [ ] MCP servers are isolated per service, user, or tenant.
- [ ] Tools with side effects have approval, idempotency, or audit logs.
- [ ] Tool inputs are never concatenated directly into SQL, shell, file paths, or URLs.
- [ ] Tool outputs are masked and length-limited as needed before being returned to the model.

## 5. RAG and data

- [ ] Documents ingested into the store record their source, permissions, and update time.
- [ ] Chunks retain permission metadata, and retrieval filters by user permission.
- [ ] The embedding provider and vector store meet data compliance requirements.
- [ ] Rerank inputs do not leak content beyond the current user's permissions.
- [ ] Citation rendering can be traced back to the original source.
- [ ] Index rebuild, incremental update, and failure retry have an operations runbook.

## 6. Agent / Coding Agent

- [ ] `maxSteps`, tool loops, and stop conditions have upper bounds.
- [ ] The memory / session store does not record real secrets.
- [ ] Trace records mask sensitive information.
- [ ] The Coding Agent's file-write, shell, patch, and process tools have approval rules.
- [ ] The workspace root, allowed write paths, and forbidden paths are explicit.
- [ ] Subagents or delegated tasks do not automatically gain more tool permissions than they need.

## 7. FlowGram

- [ ] The `/flowgram/tasks/*` APIs are protected by a gateway or starter auth.
- [ ] The default in-memory task store is not used for production tasks that require persistence.
- [ ] Whether report node details and traces are returned to the frontend has been evaluated.
- [ ] HTTP / CODE / TOOL / KNOWLEDGE node executors have an allowlist and timeouts.
- [ ] The conversion between the frontend editing schema and the backend execution schema has been tested.
- [ ] cancel, report, result, and validate APIs all have error-path coverage.

## 8. Observability and troubleshooting

- [ ] Model requests, tool calls, MCP calls, RAG retrieval, and agent steps carry a correlatable trace id.
- [ ] Log levels do not emit full prompts, secrets, or customer data in production.
- [ ] Common provider errors are mapped and documented.
- [ ] Streaming, tool failure, MCP disconnect, and vector store failure have a troubleshooting path.
- [ ] The docs name the internal owner for each provider, vector store, and MCP server.

## 9. Regression commands

| Scope of change | Suggested command |
| --- | --- |
| docs-site | `npm run build` |
| Core SDK | `mvn -pl ai4j -DskipTests=false test` |
| Agent | `mvn -pl ai4j-agent -DskipTests=false test` |
| Coding Agent / CLI | `mvn -pl ai4j-coding -DskipTests=false test`, `mvn -pl ai4j-cli -DskipTests=false test` |
| Spring Boot starter | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` |
| FlowGram starter | `mvn -pl ai4j-flowgram-spring-boot-starter -DskipTests=false test` |

When live provider tests need real credentials, they must run under an explicit profile or a dedicated environment — default local tests must not depend on external keys.

## 10. Pre-release confirmation

- [ ] Use [Version Compatibility](/docs/reference/version-compatibility) to check versions and modules.
- [ ] Use [Security Overview](/docs/security/overview) to check security boundaries.
- [ ] Use [Troubleshooting](/docs/troubleshooting/overview) to prepare a troubleshooting entry point.
- [ ] Use [Migration Guide](/docs/migration/overview) to mark replacement paths for old APIs, old docs, or old examples.
