---
sidebar_position: 1
title: "FlowGram Overview"
description: "AI4J FlowGram is the Java backend execution layer built around the FlowGram.ai canvas, converting workflow graphs drawn on the frontend into verifiable, runnable, cancellable, and observable asynchronous tasks."
tags: [concept]
---

# FlowGram Overview

The AI4J FlowGram direction is not the frontend canvas itself, but rather the Java backend execution layer built around the FlowGram.ai canvas.

Let's clarify the boundaries first:

- `FlowGram.ai` is the frontend workflow canvas / editor.
- `ai4j-flowgram-spring-boot-starter` provides the Java backend task API, runtime facade, node execution, and trace bridge.
- `ai4j-flowgram-demo` and `ai4j-flowgram-webapp-demo` are demo surfaces for demonstration and integration testing.

## In One Sentence

AI4J FlowGram solves:

> Converting workflow graphs drawn on the frontend into verifiable, runnable, cancellable, and observable Java tasks on the backend.

It is not about wrapping an Agent in a visual UI, but rather centers on an explicit workflow schema, task lifecycle, and node executor contract.

## When to Use FlowGram

| Scenario | Suitable? |
| --- | --- |
| The task is naturally a node graph, with conditional branches, loops, and stable inputs/outputs | Suitable |
| The frontend needs to let users drag, edit, and run workflows | Suitable |
| The platform needs validate, run, report, result, and cancel APIs | Suitable |
| You want the model to freely decide the next step at each step | See Agent instead |
| Local repo read/write, shell, patch, approval | See Coding Agent instead |
| Just a single model call | Only Core SDK needed |

## System Layers

| Layer | Module / Entry Point | Responsibility |
| --- | --- | --- |
| Frontend canvas | `ai4j-flowgram-webapp-demo`, FlowGram.ai | Edit nodes, connections, forms, and the run UI |
| Spring Boot integration | `ai4j-flowgram-spring-boot-starter` | task API, configuration, authentication extensions, task store |
| Execution engine | `FlowGramRuntimeService` | Parse schema, create tasks, schedule nodes, finalize results |
| Node extensions | `FlowGramNodeExecutor` | HTTP, VARIABLE, CODE, TOOL, KNOWLEDGE, and other node extensions |
| AI capabilities | `ai4j`, `ai4j-agent` | Reuse of LLM nodes, Tool, Knowledge, and trace |

## Default Task API

By default, the starter exposes the task control plane around `/flowgram`:

| API | Purpose |
| --- | --- |
| `POST /flowgram/tasks/validate` | Validate the workflow schema |
| `POST /flowgram/tasks/run` | Start an asynchronous task |
| `GET /flowgram/tasks/{taskId}/report` | View node status, trace, and process report |
| `GET /flowgram/tasks/{taskId}/result` | Get the final result |
| `POST /flowgram/tasks/{taskId}/cancel` | Cancel the task |

This shows that FlowGram's default form is an asynchronous task backend, not a "single HTTP request that returns model text directly".

## Relationship with Agent

FlowGram and Agent can reuse the same set of AI4J capabilities, but advance differently.

| Dimension | Agent | FlowGram |
| --- | --- | --- |
| Advancement | The model and runtime decide the next step | The workflow graph decides the next step |
| Structure | Free-form loops, tool loops, handoffs | Explicit nodes, edges, conditions, and task lifecycle |
| Suitable for | Multi-step reasoning, dynamic tool selection | Stable processes, platform-level orchestration, visual operations |
| Observability | Agent event / trace | task report / node status / trace bridge |

LLM nodes can reuse the Agent runtime, but FlowGram does not turn the entire workflow back into free-form reasoning.

## What to Confirm Before Going Live

:::warning Default config targets demos, not production-ready
The default configuration is more suited to demos and intranet integration testing. Before going to production, confirm:

- Whether the API has authentication or gateway protection.
- Whether the task store has been switched from memory to a persistence solution that meets your needs.
- Whether report is allowed to return node details and trace.
- Whether HTTP / CODE / TOOL / KNOWLEDGE nodes have allowlists, timeouts, and audit.
- Whether the conversion from the frontend editing schema to the backend execution schema is stable.
- Whether cancel, failure retry, exception display, and log masking are covered.
:::

For more checklist items, see [Production Checklist](/docs/production/production-checklist).

## Recommended Reading Order

1. [Why FlowGram](/docs/products/flowgram/why-flowgram)
2. [Use Cases and Paths](/docs/products/flowgram/use-cases-and-paths)
3. [Quickstart](/docs/products/flowgram/quickstart)
4. [Architecture](/docs/products/flowgram/architecture)
5. [Runtime](/docs/products/flowgram/runtime)
6. [Frontend / Backend Integration](/docs/products/flowgram/frontend-backend-integration)
7. [API and Runtime](/docs/products/flowgram/api-and-runtime)
8. [Built-in Nodes](/docs/products/flowgram/built-in-nodes)
9. [Custom Nodes](/docs/products/flowgram/custom-nodes)

If you are working on local repo tasks, you should start with [Coding Agent](/docs/products/coding-agent/overview); if you are building a general-purpose business agent, you should start with [Agent](/docs/agent/overview).
