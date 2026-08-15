---
sidebar_position: 1
title: "Troubleshooting"
description: "AI4J production troubleshooting entry point. Instead of listing every exception stack, it locates problems to the right page and checklist by capability layer: model call failures check provider key; Chat works but Responses doesn't, check provider support; Streaming has no incremental output, check the consumption pattern."
tags: [how-to]
---

# Troubleshooting

This page is the production troubleshooting entry point. Rather than enumerating every exception stack, it locates problems to the right page and checklist by AI4J capability layer.

## First, identify the layer

| Symptom | Check first |
| --- | --- |
| Model call fails, 401, 404, model not found | provider key, baseUrl, model, PlatformType |
| Chat works, Responses doesn't | whether the provider supports Responses, whether the model matches |
| Streaming has no incremental output | provider streaming support, HTTP client, frontend consumption pattern |
| Tool never invoked by the model | tool schema, system prompt, tool allowlist, model tool-call capability |
| Tool invoked but execution fails | ToolExecutor, input validation, business exception and timeout |
| MCP cannot connect | transport, server command, URL, token, handshake, timeout |
| RAG recall is empty | whether documents are ingested, whether embedding succeeded, vector store filter |
| RAG answer quality is poor | chunk, recall count, rerank, citation and prompt |
| Agent loop never stops | maxSteps, stop condition, tool result, memory |
| Coding Agent cannot write files or run commands | workspace boundary, approval, tool policy |
| FlowGram task has no result | validate, task store, runtime report, node executor |

## Provider and model call

Check order:

1. Whether the key exists and comes from the correct environment variable.
2. Whether baseUrl contains the correct protocol, domain, and path.
3. Whether the model name is a valid model for the current provider.
4. Whether the current service surface is supported by the provider.
5. Get the non-streaming path working first, then troubleshoot streaming.

Related pages:

- [Model Access Overview](/docs/core-sdk/model-access/overview)
- [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix)
- [Chat vs Responses](/docs/core-sdk/model-access/chat-vs-responses)

## Spring Boot configuration

If plain Java works but Spring Boot doesn't, check first:

- Whether configuration keys land under the correct `ai.*` namespace.
- Whether single-instance configuration and `ai.platforms[]` are mixed, causing routing to behave unexpectedly.
- Whether `AiConfigAutoConfiguration` is picked up by Spring scanning.
- Whether business custom Beans override default Beans.

Related pages:

- [Auto Configuration](/docs/spring-boot/auto-configuration)
- [Configuration Reference](/docs/spring-boot/configuration-reference)
- [Bean Extension](/docs/spring-boot/bean-extension)

## Tool and MCP

For Tool issues, separate "did the model see the tool" from "did the system execute it successfully".

- Model didn't see it: check the tool list, schema, tool whitelist, prompt.
- Model saw it but didn't pick it: check model capability, user instruction, and whether the tool description is clear.
- Model picked it but it failed: check inputs, executor, external API, exception handling.
- MCP tool invisible: check whether the MCP client is connected and whether the gateway registered the tool.

Related pages:

- [Tools Overview](/docs/core-sdk/tools/overview)
- [Tool Execution Model](/docs/core-sdk/tools/tool-execution-model)
- [MCP Overview](/docs/mcp/overview)
- [Gateway Management](/docs/mcp/gateway-management)

## RAG

For RAG troubleshooting, split the flow into three stages: ingestion, retrieval, generation.

| Stage | Check |
| --- | --- |
| Ingestion | whether files are read successfully, whether chunks are generated, whether embeddings are written to the vector store |
| Retrieval | whether query embedding succeeds, whether the filter is too strict, whether topK is too small |
| Generation | whether the prompt carries the context, whether citations are preserved, whether the model ignores the material |

Related pages:

- [Search & RAG Overview](/docs/core-sdk/search-and-rag/overview)
- [Ingestion Pipeline](/docs/core-sdk/search-and-rag/ingestion-pipeline)
- [Hybrid Retrieval](/docs/core-sdk/search-and-rag/hybrid-retrieval)

## Agent / Coding Agent

For a general Agent, check first:

- Whether the runtime is ReAct, CodeAct, or DeepResearch.
- Whether maxSteps is too small or too large.
- Whether memory repeatedly carries old tool results back in.
- Whether the trace can locate which step it stopped at.

For a Coding Agent, additionally check:

- Whether the workspace root is correct.
- Whether file and shell tools require approval.
- Whether the session is resumed from an old state.
- Whether compact / checkpoint affects the current context.

Related pages:

- [Agent Trace](/docs/agent/trace-observability)
- [Coding Agent Session Runtime](/docs/coding-agent/session-runtime)
- [Coding Agent Tools and Approvals](/docs/coding-agent/tools-and-approvals)

## FlowGram

For FlowGram troubleshooting, follow the task lifecycle:

1. Whether `/flowgram/tasks/validate` passes.
2. Whether `/flowgram/tasks/run` returns a taskId.
3. Whether `/flowgram/tasks/{taskId}/report` shows node status.
4. Whether the failed node's executor type and error are clear.
5. Whether `/flowgram/tasks/{taskId}/result` is readable after completion.

Related pages:

- [FlowGram Overview](/docs/flowgram/overview)
- [API and Runtime](/docs/flowgram/api-and-runtime)
- [Runtime](/docs/flowgram/runtime)

## Information to include when filing an issue

- AI4J version and modules used.
- Java / Maven / Spring Boot versions.
- Provider, baseUrl type, and model name — but never paste real keys.
- Minimal reproduction code or configuration snippet.
- Whether you use Tool, MCP, RAG, Agent, Coding Agent, or FlowGram.
- Error stack, trace id, key log snippets.
- Expected behavior and actual behavior.
