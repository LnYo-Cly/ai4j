---
title: "Version Compatibility"
sidebar_position: 1
description: "AI4J version assessment and pre-upgrade checks: baseline, module compatibility matrix, Java 8 compatibility notes, provider capability differences, and recommended upgrade order."
tags: [reference]
---

# Version Compatibility

This page is for version assessment and pre-upgrade checks. It does not promise that every capability of every provider is fully symmetric; rather, it lays out the compatibility boundaries currently recommended by this docs site.

## Baseline

| Item | Current boundary |
| --- | --- |
| AI4J version | `2.4.2` |
| Maven groupId | `io.github.lnyo-cly` |
| Java baseline | Java 8 source / target |
| Build tool | Maven |
| Test mainline | JUnit 4; live provider tests excluded by default |
| Docs site Node baseline | `docs-site` requires Node.js `>=20.0` |

## Module compatibility matrix

| Artifact | Corresponding source module | Minimal usage scenario | Internal dependencies |
| --- | --- | --- | --- |
| `ai4j` | `ai4j/` | Core SDK capabilities: models, Tool, Skill, MCP, RAG, Memory, etc. | No AI4J internal dependencies |
| `ai4j-spring-boot-starter` | `ai4j-spring-boot-starter/` | Configuration-driven wiring for Spring Boot applications | `ai4j` |
| `ai4j-agent` | `ai4j-agent/` | General-purpose Agent runtime, workflow, trace, team | `ai4j` |
| `ai4j-coding` | `ai4j-coding/` | Local codebase task runtime, workspace tools, compaction | `ai4j`, `ai4j-agent` |
| `ai4j-cli` | `ai4j-cli/` | CLI, TUI, ACP host, session entry point | `ai4j-coding` and its base dependencies |
| `ai4j-flowgram-spring-boot-starter` | `ai4j-flowgram-spring-boot-starter/` | FlowGram.ai canvas backend execution layer | `ai4j-agent`, `ai4j-spring-boot-starter` |
| `ai4j-bom` | `ai4j-bom/` | Multi-module version alignment | Manages release artifact versions |

## Java 8 notes

AI4J's Java modules are still designed for Java 8 compatibility. Projects may run on a higher JDK, but the code and public API should not actively depend on Java 9+ language features unless a given task explicitly raises the baseline.

Two points to note:

- `ai4j` contains runtime profiles targeting higher JDK versions, such as Nashorn / GraalPy-related profiles; these are optional runtime paths and do not change the main code baseline.
- `docs-site` is a Docusaurus site whose Node.js baseline is independent of the Java SDK; it does not mean the Java modules require Node.js.

## Provider capabilities are not fully symmetric

What AI4J unifies is the entry point, the request model, and the engineering mental model — not wrapping every provider into an identical capability surface. Before actual use, confirm against the [Platform and Service Matrix](/docs/core-sdk/platform-service-matrix):

- The supported scope of Chat / Responses.
- The supported scope of Embedding / Rerank.
- Whether Image / Audio / Realtime are available only on specific provider paths.
- Whether tool calling, streaming, and multimodal have provider-specific differences.

When wiring up a project, we recommend writing the provider capability matrix into your own onboarding docs rather than relying solely on default configuration.

## Spring Boot compatibility

The Spring Boot starter is responsible for configuration binding, auto-configuration, and Bean lifecycle wiring. Before integrating, confirm:

- Whether the project can adopt the current version of `ai4j-spring-boot-starter`.
- Whether configuration keys fall under the `ai.*` namespace.
- Whether both the single-instance configuration and the `ai.platforms[]` multi-instance registry need to coexist.
- Whether you need to customize `OkHttpClient`, `AiService`, `AiServiceRegistry`, or business-side Beans.

We recommend starting from [Spring Boot Overview](/docs/spring-boot/overview) and [Configuration Reference](/docs/spring-boot/configuration-reference).

## Upgrade order

When using multiple AI4J modules at once, we recommend:

1. Pin a single version with `ai4j-bom`.
2. Upgrade `ai4j` and the minimal quickstart first.
3. Then upgrade the starter, Agent, Coding Agent, or FlowGram.
4. Cross-check against the [Production Checklist](/docs/operations/production-checklist) to review keys, timeouts, logging, tool allowlists, MCP configuration, and regression commands.

## Regression recommendations

| Change | Minimal check |
| --- | --- |
| Only docs-site content changed | `npm run build` |
| Java API or provider support changed | The corresponding module: `mvn -pl <module> -DskipTests=false test` |
| Starter configuration changed | `mvn -pl ai4j-spring-boot-starter -DskipTests=false test` |
| Agent / Coding Agent changed | The corresponding module tests + CLI or session-layer smoke |
| FlowGram starter changed | Starter tests + demo or task API smoke |

:::danger Do not commit keys to the repository
If live provider tests require real keys, run them explicitly under the live profile — do not commit keys to the repository.
:::
