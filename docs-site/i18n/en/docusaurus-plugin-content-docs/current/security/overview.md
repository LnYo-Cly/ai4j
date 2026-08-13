---
sidebar_position: 1
title: "Security Overview"
description: "AI4J's security boundary is composed of multiple layers: secrets, network, Tool, MCP, RAG, Agent, Coding Agent, and FlowGram. Before integration, clarify: which capabilities can be seen, called, or written back to users or logs by the model. No secrets in the repo, minimal Tool exposure, local boundary constraints."
tags: [concept]
---

# Security Overview

AI4J's security boundary is not a single switch but is composed of multiple layers: secrets, network, Tool, MCP, RAG, Agent, Coding Agent, and FlowGram. Before integration, you should first clarify: which capabilities can be seen by the model, which can be called by the model, and which results can be written back to users or logs.

## Basic Principles

| Principle | Requirement |
| --- | --- |
| No secrets in the repo | provider key, MCP token, and database passwords must only flow through environment variables or external configuration |
| Tools default to minimal exposure | Only pass the Function Tool or MCP Tool needed for the current task to the model |
| Local files and commands must have boundaries | Coding Agent's workspace, shell, patch, and process capabilities must be constrained by approval and path rules |
| MCP is not a trusted boundary | Tools, resources, and prompts from third-party MCP servers must all be treated as external systems |
| RAG is not a permission system | The retrieval layer must inherit business permissions; do not index all documents into a single global knowledge base |
| Trace may contain sensitive information | prompt, tool parameters, model output, and node report may all end up in logs or UI |

## Secrets and Configuration

Do not write real secrets in code, doc examples, or test data. Recommended:

```bash
export OPENAI_API_KEY=...
export AI4J_PROVIDER_API_KEY=...
```

In Spring Boot projects, put secrets in environment variables, a secret management system, or deployment platform secrets, then reference them from configuration. Do not write real keys into `application.yml` and commit them.

## Tool Security

The Tool security boundary has two layers:

- `ToolRegistry` or the tool list determines what the model can see.
- `ToolExecutor` or the actual executor determines what happens on call.

Recommendations:

1. Only expose tools required by the current business.
2. Validate tool inputs explicitly; do not directly concatenate SQL, shell, or URL.
3. Add confirmation, idempotency keys, or audit logs for tools with side effects.
4. Set timeout, retry limit, and error fallback for external API tools.
5. Mask tool return content before passing it back to the model or user.

Related pages:

- [Tools Overview](/docs/core-sdk/tools/overview)
- [Tool Whitelist and Security](/docs/core-sdk/tools/tool-whitelist-and-security)
- [MCP Tool Exposure Semantics](/docs/mcp/tool-exposure-semantics)

## MCP Security

AI4J's MCP mainline includes client, transport, gateway, and server publishing. Different paths have different security concerns.

| MCP Path | Main Risk | Recommendation |
| --- | --- | --- |
| Connecting a third-party MCP server | Opaque tool behavior, resource leaks, prompt injection | allowlist servers, restrict tools, isolate tokens |
| Local stdio MCP | Excessive process permissions, env var leaks | Run as a dedicated user or in a sandbox, with an explicit working directory |
| SSE / HTTP MCP | Network exposure, insufficient authentication, man-in-the-middle risk | Use HTTPS, authentication, timeout, and allowlist |
| MCP Gateway | Mixed multi-service usage, incorrect user isolation | Clarify the key rules for the global client and per-user client |
| Publishing a Java MCP server | Accidental exposure of internal methods or resources | Minimize the annotation scan scope, mask resource returns |

The top-level [MCP Overview](/docs/mcp/overview) is the official mainline; `core-sdk/mcp/*` only serves as a transitional in-depth reference.

## RAG and Knowledge Base Security

The biggest risk in RAG is usually not the model but indexing and permissions.

When integrating in production, confirm:

- Whether sensitive information filtering is performed before documents are ingested.
- Whether chunks still carry the original permission metadata after splitting.
- Whether retrieval is filtered by user, tenant, department, or project.
- Whether citations and trace will expose unauthorized document titles or fragments.
- Whether the vector store, embedding provider, and rerank provider meet data compliance requirements.

Related pages:

- [Search & RAG Overview](/docs/core-sdk/search-and-rag/overview)
- [Citations and Trace](/docs/core-sdk/search-and-rag/citations-and-trace)

## Agent and Coding Agent Security

For general agents, focus on the tool loop, memory, and trace. Coding Agent additionally requires attention to workspace, shell, patch, and approval.

Recommendations:

- Treat shell, file writes, network requests, and package management commands as high-risk tools by default.
- Require approval for writing files, changing configuration, and running external commands.
- When persisting sessions, avoid recording real secrets, private code snippets, and unmasked customer data.
- Subagents or delegated tasks must inherit minimum permissions rather than automatically inheriting all tools.

Related pages:

- [Agent Tools and Registry](/docs/agent/tools-and-registry)
- [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)

## FlowGram Security

The FlowGram starter defaults to a demo / intranet integration posture. Before going live, confirm:

- Whether `auth.enabled` is turned on or protected by a business gateway.
- Whether `/flowgram/tasks/*` is open only to authorized users.
- Whether task report returns node details and trace.
- Whether HTTP, CODE, TOOL, and KNOWLEDGE nodes are constrained by an allowlist.
- Whether the default in-memory task store meets production persistence requirements.

Related pages:

- [FlowGram Overview](/docs/flowgram/overview)
- [Production Checklist](/docs/operations/production-checklist)

## Minimum Security Checklist Before Going Live

- [ ] No real provider key, MCP token, or database password enters the repo.
- [ ] Tool / MCP exposure scope is narrowed by an allowlist per business scenario.
- [ ] RAG retrieval inherits business permissions and tenant isolation.
- [ ] Trace, logs, task report, and session store handle sensitive information.
- [ ] Coding Agent's file, shell, patch, and process tools have approval boundaries.
- [ ] FlowGram task API has authentication or gateway protection.
- [ ] All external providers, MCP servers, and vector stores have timeout and failure handling.
