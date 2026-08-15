---
title: "Spring Boot Common Patterns"
description: "Summarizes the recommended layering and engineering organization patterns for integrating AI4J with Spring Boot, drawing clear boundaries between Web, AI4J calls, and Tools, RAG, and Workflow responsibilities."
tags: [integration]
---

# Spring Boot Common Patterns

This page covers high-frequency engineering organization patterns, not API syntax.

## 1. What this page actually solves

What trips most people up is not "how do I send the first request" but rather:

- The Spring container is already wired up — how should the business layer be organized?
- Where should Tools, RAG, Workflow, and configuration live?
- How do you keep AI4J calls from getting tangled up with Web/controller logic?

So this page focuses on engineering organization, not on stitching examples together.

## 2. Recommended layering

```text
controller
service
ai/prompts
ai/tools
ai/workflow
config
```

The point of this kind of layering is not aesthetics; it is to split:

- Web entry points
- AI4J calls
- Tool / RAG / Workflow organization
- Spring configuration

into distinct responsibility layers.

## 3. Recommended patterns

- `Controller -> Service -> AI4J interface`
- Route multiple instances through `AiServiceRegistry`
- Use `ChatMemory` to cover basic sessions first, then decide whether to upgrade to an `Agent`
- Organize RAG, Tools, and Workflow into explicit business modules

What these patterns share:

- Stabilize the container and capability entry points first
- Then decide how the business combines those capabilities

## 4. Patterns to avoid

Ways of writing that tend to spiral out of control include:

- Stuffing prompts, platform switching, and tool routing directly into the Controller
- Handling Web parameters, retrieval, memory, and model calls all in the same layer
- RAG, Tools, and Workflow with no explicit directory or responsibility boundary

:::warning These styles run in the short term but are hard to maintain long term
These styles may run in the short term, but they are hard to maintain over time and work against architectural evolution and code governance.
:::

## 5. Key objects that belong here

If you want to land this page in your code structure, focus first on:

- The `AiService` in the Spring container
- The AI4J entry-point interface in the business `Service` layer
- Multi-instance routing scenarios for `AiServiceRegistry`
- Independent business modules for `ChatMemory`, Tools, RAG, and Workflow

Together, these objects determine whether "AI capabilities are organized" or "scattered across controllers".

## 6. The boundaries this page is really trying to establish

- The Web layer is only responsible for request input and output, not for orchestrating AI capabilities
- AI4J entry points should converge on the business service layer, not be scattered across multiple controllers
- Tools, RAG, and Workflow should be explicit modules, not implicit inside prompts or util classes

Once these three boundaries are in place, the Spring Boot integration becomes a maintainable system rather than a runnable example.
