---
title: "API Reference"
sidebar_position: 1
description: "Versioned Javadoc entry points for each AI4J Maven module, indexed by capability for Core SDK, Agent, Coding Agent, and extension SPI Java types and signatures."
tags: [reference]
---

# API Reference

Use the conceptual documentation to choose an integration path, then open the rendered Javadoc for exact Java types and method signatures. The links below point to the published `2.4.2` Javadoc, not the repository's next snapshot.

## Rendered Javadoc by module

| Area | Maven artifact | Versioned HTML API |
| --- | --- | --- |
| Models, Tools, Skills, MCP, RAG | `ai4j` | [Open ai4j 2.4.2 Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j/2.4.2/index.html) |
| Generic Agent runtime | `ai4j-agent` | [Open ai4j-agent 2.4.2 Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-agent/2.4.2/index.html) |
| Coding Agent runtime | `ai4j-coding` | [Open ai4j-coding 2.4.2 Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-coding/2.4.2/index.html) |
| CLI, TUI, and ACP host | `ai4j-cli` | [Open ai4j-cli 2.4.2 Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-cli/2.4.2/index.html) |
| Extension and plugin contracts | `ai4j-extension-api` | [Open ai4j-extension-api 2.4.2 Javadoc](https://javadoc.io/doc/io.github.lnyo-cly/ai4j-extension-api/2.4.2/index.html) |

These are browser-rendered API pages, so package navigation, search, inherited members, and linked types work without downloading and unpacking a Javadoc JAR.

## Choose the right reference

1. Start with [Core SDK](/docs/core-sdk/overview) for normal model calls, Tools, Skills, MCP, and RAG.
2. Start with [Agent Runtime](/docs/agent/overview) for orchestration, memory, and permission boundaries.
3. Start with [Coding Agent](/docs/coding-agent/overview) for CLI, workspace, session, and ACP integration.
4. Start with [Extensions](/docs/core-sdk/extension/overview) before depending on an extension or plugin SPI.

The guides explain behavior and design boundaries. Javadoc is the source for Java signatures, overloads, annotations, and package-level types.

## Version navigation

The explicit `2.4.2` URLs make the reference reproducible for a released dependency. To browse a module's published versions, remove the version segment from the corresponding `javadoc.io` URL, for example [ai4j versions](https://javadoc.io/doc/io.github.lnyo-cly/ai4j).

The repository currently develops the next `2.4.3-SNAPSHOT`; snapshots are not release API documentation. Use [Version Compatibility](/docs/reference/version-compatibility) before upgrading, and use the [release and artifacts guide](/docs/reference/release-and-artifacts) to align a multi-module build with the BOM.
