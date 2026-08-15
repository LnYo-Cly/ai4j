---
title: "First Tool Call"
description: "Explains what the \"first tool call\" really means in AI4J: the shortest local Function Call example, why Function Call, Skill, and MCP must be kept distinct, and the next-step topic tree to follow."
tags: [concept]
---

# First Tool Call

The goal of this step is not to cover the entire tool system, but to first let you know:

- What the "first tool call" in AI4J is really about
- Why `Function Call`, `Skill`, and `MCP` must be kept distinct
- Which topic tree you should follow next

## 1. Which kind of Tool this page focuses on

This page first covers the most common first tool track:

- Local Java `Function Call` / `Tool`

That is:

- tool schema is declared inside your local application
- tool execution happens inside your local application
- the model decides whether to use it via tool-call semantics

This is usually the best entry point for most Java users to understand the "first Tool Call".

## 2. First, tell these three things apart

- `Function Call`: local Java tool declaration and invocation
- `Skill`: discoverable, on-demand-read methodology resource
- `MCP`: protocol-based integration of external capabilities

They are all foundation capabilities, but they are not the same concept.

## 3. What the first Tool Call actually proves

Once the first local tool call works, what you have actually verified is:

- the model has seen an invocable tool
- the model can decide to call it
- the tool-call arguments can reach the execution layer
- the execution result can return to the model's main flow

So the "first Tool Call" is not just an extra function name — it is the moment the model's call chain first enters the "invocable capability" stage.

## 4. The shortest `Function Call` example

```java
ChatCompletion req = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("Look up Beijing weather and give a suggestion"))
        .functions("queryWeather")
        .build();

ChatCompletionResponse resp = chatService.chatCompletion(req);
```

This pipeline goes through tool exposure and execution by default.

## 5. Why `Skill` is not a tool

A `Skill` itself is not called directly by the model.

It is more like:

- a `SKILL.md` file
- an on-demand-read method description

Typically the model first sees the skill list, then reads the relevant `SKILL.md` via `read_file`.

In other words, a `Skill` is more like:

- a documentation asset
- a template asset
- a methodology asset

rather than the structured, executable capability itself.

## 6. Why `MCP` is not a subset of local tools either

`MCP` is not only about tools — it also covers:

- `resource`
- `prompt`
- `transport`
- `gateway`
- `server publish`

So in the documentation structure it sits at the same level as `Tools`.

More precisely:

- `Function Call` first solves "how local tools are exposed and executed"
- `MCP` then solves "how external capabilities are integrated by protocol"

## 7. What to read next

If you want to keep following the local-tool track next, read on:

1. [Core SDK / Tools](/docs/capabilities/tools/overview)
2. [Core SDK / Function Calling](/docs/capabilities/tools/function-calling)
3. [Core SDK / Tool Execution Model](/docs/capabilities/tools/tool-execution-model)

If you want to nail down the conceptual boundaries next, read:

1. [Core SDK / Skills](/docs/capabilities/skills/overview)
2. [Core SDK / Skills / Skill vs Tool vs MCP](/docs/capabilities/skills/skill-vs-tool-vs-mcp)
3. [MCP](/docs/capabilities/mcp/overview)

If the first tool call did not fire here, go back to:

- [Troubleshooting](/docs/production/troubleshooting)
