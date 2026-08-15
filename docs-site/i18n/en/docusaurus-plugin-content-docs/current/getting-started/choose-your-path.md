---
title: "Choose Your Path"
description: "Pick your main entry into AI4J by goal: the default reading order, plus recommended starting points and when to go deeper for model calls, Spring Boot integration, Tool/MCP/Agent/Coding Agent/FlowGram."
tags: [concept]
---

# Choose Your Path

Different readers should not start from the same page.

This page does not walk you through every module. Instead, it helps you decide first:

- Which main track you should enter from
- What that track will explain first
- At what point you should branch off into a deeper topic tree

## 1. If you are not sure yet, take the default track

The recommended default order is:

1. [Why AI4J](/docs/getting-started/why-ai4j)
2. [Architecture at a Glance](/docs/reference/maps/architecture-at-a-glance)
3. [Quickstart for Java](/docs/getting-started/quickstart-java) or [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)
4. [First Tool Call](/docs/getting-started/first-tool-call)
5. [Core SDK / Overview](/docs/capabilities/overview)

This track is best suited for:

- First-time AI4J adopters
- Anyone who has not yet decided whether to stop at the SDK, Spring Boot, Agent, or Coding Agent
- Readers who want to build a complete, stable mental model first

## 2. Pick an entry point by goal

### 2.1 I just want to send a model call first

Start here:

1. [Quickstart for Java](/docs/getting-started/quickstart-java)
2. [Core SDK / Model Access](/docs/capabilities/models/overview)

This track helps you confirm three things first:

- Whether the dependencies are wired correctly
- Whether the provider configuration is in effect
- Whether your first `Chat` model call runs end to end

### 2.2 I am on a Spring Boot project

Start here:

1. [Quickstart for Spring Boot](/docs/getting-started/quickstart-spring-boot)
2. [Spring Boot / Overview](/docs/integrations/spring-boot/overview)
3. [Spring Boot / Auto Configuration](/docs/integrations/spring-boot/auto-configuration)

This track answers first:

- What the starter is responsible for in your project
- How `AiService` enters the Spring container
- Where the boundaries of auto-configuration, configuration prefixes, and Bean extensions lie

### 2.3 I want to understand Tool / Function Call / Skill / MCP

Start here:

1. [First Tool Call](/docs/getting-started/first-tool-call)
2. [Core SDK / Tools](/docs/capabilities/tools/overview)
3. [Core SDK / Skills](/docs/capabilities/skills/overview)
4. [MCP](/docs/capabilities/mcp/overview)

If you often conflate these concepts, prioritize this track.

### 2.4 I want to integrate MCP

Start here:

1. [MCP Overview](/docs/capabilities/mcp/overview)
2. [MCP Use Cases and Paths](/docs/capabilities/mcp/use-cases-and-paths)
3. [Client Integration](/docs/capabilities/mcp/client-integration)

This track fits if you already know:

- You care about protocol-based external capability integration
- You are not doing local function calls first
- You are not building a general-purpose Agent runtime first

### 2.5 I want to build an Agent

Start here:

1. [Agent / Overview](/docs/agent/overview)
2. [Agent / Why Agent](/docs/agent/why-agent)
3. [Agent / Quickstart](/docs/agent/quickstart)

You will see first:

- What problem the Agent runtime solves
- Where its boundary with the Core SDK lies
- How runtime, memory, tool loop, orchestration, and trace fit together

### 2.6 I want to use the Coding Agent directly

Start here:

1. [Coding Agent / Overview](/docs/products/coding-agent/overview)
2. [Coding Agent / Quickstart](/docs/products/coding-agent/quickstart)
3. [Coding Agent / CLI / TUI](/docs/products/coding-agent/cli-and-tui)

This track fits if:

- You have already decided to work with a local code repository
- You mainly care about CLI / TUI / ACP, sessions, approvals, and workspace-aware tools
- You are not trying to learn a general-purpose Agent framework first

### 2.7 I want to build a workflow platform

Start here:

1. [FlowGram / Overview](/docs/products/flowgram/overview)
2. [FlowGram / Why FlowGram](/docs/products/flowgram/why-flowgram)
3. [FlowGram / Quickstart](/docs/products/flowgram/quickstart)

This track explains first:

- Where FlowGram sits in the AI4J ecosystem
- How it differs from Agent and Coding Agent
- How the backend runtime, nodes, and front-end/back-end integration work together

### 2.8 I want to build a systematic understanding first

Read in this order:

1. [Why AI4J](/docs/getting-started/why-ai4j)
2. [Architecture at a Glance](/docs/reference/maps/architecture-at-a-glance)
3. [Core SDK / Overview](/docs/capabilities/overview)
4. [Core SDK / Strengths and Differentiators](/docs/reference/about/strengths-and-differentiators)
5. Then fill in `Spring Boot / Agent / Coding Agent / FlowGram` based on your focus

This track is best suited for:

- Readers who need to clarify "what AI4J is" first
- Then clarify "how the modules are layered"
- And finally clarify "why layering this way is an advantage"

## 3. One simple principle for reading the docs

Read the canonical page before the deep page.

That is, read these first:

- `overview`
- `why`
- `architecture`
- `quickstart`

Then move on to:

- Capability pages
- API/reference pages
- Solution and case-study pages

This keeps you from getting scattered by details at the very start.
